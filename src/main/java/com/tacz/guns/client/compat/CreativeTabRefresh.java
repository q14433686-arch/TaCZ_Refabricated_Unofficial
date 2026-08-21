package com.tacz.guns.client.compat;

import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.flag.FeatureFlagSet;

import java.lang.reflect.Method;

/**
 * Forces the vanilla creative-mode tab contents to be rebuilt after a gun-pack
 * sync packet has populated the client common indexes.
 *
 * <h2>Why this is needed on a dedicated server</h2>
 * TaCZ fills its creative tabs lazily from the common gun/ammo/attachment/block
 * indexes (the {@code fillItemCategory(...)} calls iterate
 * {@code TimelessAPI.getAllCommonXxxIndex()}). In singleplayer/LAN the integrated
 * server shares the in-JVM {@code CommonAssetsManager.INSTANCE}, which is already
 * populated by the time the client first builds its creative tabs, so everything
 * appears normally.
 *
 * <p>On a <b>dedicated server</b>, however, the client's common indexes start
 * empty and are filled only later, when {@code ServerMessageSyncGunPack} arrives
 * (during/after configuration). Vanilla builds its creative tab contents
 * <b>once</b> when the creative inventory is first opened (or on join), i.e.
 * before the synchronized indexes exist. The cached contents therefore contain
 * none of the gun/ammo/attachment/workbench stacks: opening the TaCZ creative
 * tabs shows empty/bare items, and any path that derives items from those cached
 * contents yields the unregistered/no-NBT "purple-black" items.
 *
 * <p>The NeoForge sister port hits the same fault and fixes it in its sync-gun-pack
 * handler by invoking {@code CreativeModeTabs.tryRebuildTabContents(...)} after
 * rebuilding its client indexes. We do the same here.
 *
 * <h2>The same-input skip, and why we call it twice</h2>
 * {@code tryRebuildTabContents} short-circuits when the supplied
 * {@code (FeatureFlagSet, hasPermissions, HolderLookup.Provider)} are identical
 * to the previous build (it compares against the parameters used last time and
 * skips if nothing changed). After a gun-pack sync those parameters are usually
 * identical to the empty build's parameters, so a single call with the real
 * values is silently ignored. The sister port works around this by first
 * invoking it once with a toggled {@code hasPermissions} to invalidate the
 * cached "same contents" guard, then invoking it again with the player's real
 * permission state so the final contents match the player's actual permissions.
 * We mirror that two-call sequence.
 *
 * <h2>Why reflection</h2>
 * The method's signature changed across versions:
 * <ul>
 *   <li>1.20.6+ (including 26.2): {@code tryRebuildTabContents(FeatureFlagSet, boolean, HolderLookup.Provider)}</li>
 *   <li>older: {@code tryRebuildTabContents(FeatureFlagSet, boolean)}</li>
 * </ul>
 * We probe for the 3-arg form first and fall back to the 2-arg form, so this
 * helper keeps compiling without hard-coding a HolderLookup import whose mapped
 * name may differ across the three parallel branches.
 */
@Environment(EnvType.CLIENT)
public final class CreativeTabRefresh {
    private CreativeTabRefresh() {
    }

    /**
     * Rebuild the creative tab contents using the local player's current feature
     * flags, operator-permission state, and registry lookup. Must be called on the
     * client thread (vanilla mutates client-side tab state).
     */
    public static void rebuildAfterGunPackSync() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        ClientPacketListener connection = client.getConnection();
        FeatureFlagSet enabledFeatures = connection != null
                ? connection.enabledFeatures()
                : FeatureFlagSet.of();

        // Vanilla derives "hasPermissions" from whether the player may use
        // operator blocks/items in the creative menu (permission level >= 2).
        boolean hasPermissions = player.connection.hasPermission(2);

        // Registry lookup: the 3-arg form in 1.20.6+ needs it. The client level
        // provides a registry access once a world is loaded.
        Object holders = client.level != null ? client.level.registryAccess() : null;

        try {
            Method rebuild = findRebuildMethod();
            if (rebuild == null) {
                GunMod.LOGGER.warn("[CreativeTabRefresh] CreativeModeTabs.tryRebuildTabContents not found; creative tabs may be stale until relog.");
                return;
            }
            rebuild.setAccessible(true);

            // First call flips the permission flag so the "same inputs" guard
            // invalidates; its contents are discarded by the second call.
            invokeRebuild(rebuild, enabledFeatures, !hasPermissions, holders);
            // Second call uses the player's real permission state.
            boolean changed = invokeRebuild(rebuild, enabledFeatures, hasPermissions, holders);
            GunMod.LOGGER.info("[CreativeTabRefresh] Rebuilt creative tabs after gun-pack sync (changed={}).", changed);
        } catch (Throwable t) {
            GunMod.LOGGER.warn("[CreativeTabRefresh] Failed to rebuild creative tabs after gun-pack sync.", t);
        }
    }

    private static Method findRebuildMethod() {
        Class<?> creativeModeTabs;
        try {
            creativeModeTabs = Class.forName("net.minecraft.world.item.CreativeModeTabs");
        } catch (ClassNotFoundException e) {
            return null;
        }
        // Prefer the 3-arg form (1.20.6+ / 26.2).
        for (Method candidate : creativeModeTabs.getDeclaredMethods()) {
            if (!candidate.getName().equals("tryRebuildTabContents")) {
                continue;
            }
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length == 3
                    && params[0] == FeatureFlagSet.class
                    && params[1] == boolean.class) {
                return candidate;
            }
        }
        // Fall back to the 2-arg form.
        for (Method candidate : creativeModeTabs.getDeclaredMethods()) {
            if (candidate.getName().equals("tryRebuildTabContents")
                    && candidate.getParameterCount() == 2) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean invokeRebuild(Method rebuild, FeatureFlagSet features,
                                         boolean hasPermissions, Object holders) throws ReflectiveOperationException {
        Object result;
        if (rebuild.getParameterCount() == 3) {
            result = rebuild.invoke(null, features, hasPermissions, holders);
        } else {
            result = rebuild.invoke(null, features, hasPermissions);
        }
        return result instanceof Boolean b && b;
    }
}
