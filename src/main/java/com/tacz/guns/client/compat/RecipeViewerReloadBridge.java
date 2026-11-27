package com.tacz.guns.client.compat;

import cn.sh1rocu.tacz.compat.rei.ReiPackRefresh;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.CommonAssetsManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Rebuilds optional recipe-viewer registrations after TACZ's authoritative gun-pack cache arrives.
 *
 * <p>JEI and REI build TACZ categories and displays from the synchronized common indexes. A remote
 * gun-pack cache can arrive after the viewers' first registration pass, so registering categories
 * alone cannot make newly synced guns, workbenches, attachments, or ammo-query entries visible.
 * Requests are deliberately coalesced and deferred to {@link #tick(Minecraft)}: packet handling,
 * viewer internals, and client resources must all remain on the Minecraft client thread.</p>
 *
 * <p>JEI uses its lifecycle listener. REI's server displays are one-shot additions to the current
 * holder, not registrations replayed by plugins. Only replace our own gun-pack entries there.</p>
 */
@Environment(EnvType.CLIENT)
public final class RecipeViewerReloadBridge {
    // This listener belongs to JEI's client lifecycle, not a connection. Keep it across clear().
    private static ResourceManagerReloadListener jeiReloadListener;
    private static boolean reloadRequested;
    private static boolean reiPending;
    private static boolean reloadInProgress;
    private static boolean resourceFallbackUsed;

    private RecipeViewerReloadBridge() {
    }

    /** Called by the optional JEI mixin when JEI creates its lifecycle reload listener. */
    public static void setJeiReloadListener(ResourceManagerReloadListener listener) {
        jeiReloadListener = listener;
    }

    /** Queues one coalesced reload after cache installation and index rebuilding have completed. */
    public static void requestReload() {
        reloadRequested = hasJei();
        reiPending = hasRei();
    }

    /** Drops pending work when the client leaves before the synchronized cache can be used. */
    public static void clear() {
        reiPending = false;
        reloadRequested = false;
        reloadInProgress = false;
        resourceFallbackUsed = false;
    }

    /** Runs from {@code END_CLIENT_TICK} after the client has a level and player. */
    public static void tick(Minecraft client) {
        if (reloadInProgress || client.level == null || client.player == null) {
            return;
        }

        if (reiPending) {
            try {
                reiPending = !ReiPackRefresh.refreshWhenReady();
            } catch (RuntimeException | LinkageError ex) {
                reiPending = false;
                // Never use a destructive full reload as a fallback on an unfamiliar REI runtime.
                GunMod.LOGGER.warn("[TACZ Recipe Viewer] Could not refresh REI gun-pack entries; leaving server displays alone.", ex);
            }
        }
        if (!reloadRequested) return;
        reloadRequested = false;
        reloadInProgress = true;
        int tableCount = com.tacz.guns.resource.network.CommonNetworkCache.INSTANCE.getAllBlocks().size();
        int recipeCount = com.tacz.guns.resource.network.CommonNetworkCache.INSTANCE.getAllTableRecipes().size();
        GunMod.LOGGER.info("[TACZ Recipe Viewer] Refreshing after gun-pack sync ({} table(s), {} recipe(s)).",
                tableCount, recipeCount);

        boolean requiresResourceFallback = !refreshJei(client);
        if (!requiresResourceFallback || resourceFallbackUsed) {
            reloadInProgress = false;
            if (requiresResourceFallback) {
                GunMod.LOGGER.warn("[TACZ Recipe Viewer] Lightweight refresh is unavailable; the one fallback for this connection was already used.");
            }
            return;
        }

        // An unrecognised viewer implementation gets exactly one resource-reload fallback per
        // connection. Resource reload does not enqueue this bridge, preventing a reload loop.
        resourceFallbackUsed = true;
        GunMod.LOGGER.warn("[TACZ Recipe Viewer] Viewer reload hook unavailable; falling back once to a client resource reload.");
        try {
            client.reloadResourcePacks().whenComplete((unused, throwable) -> client.execute(() -> {
                reloadInProgress = false;
                if (throwable == null) {
                    GunMod.LOGGER.info("[TACZ Recipe Viewer] Fallback client resource refresh completed.");
                } else {
                    GunMod.LOGGER.warn("[TACZ Recipe Viewer] Client resource refresh failed; recipe viewer data may be stale.",
                            throwable);
                }
            }));
        } catch (RuntimeException exception) {
            reloadInProgress = false;
            GunMod.LOGGER.warn("[TACZ Recipe Viewer] Could not start the client resource refresh.", exception);
        }
    }

    /** Keep JEI 27.23's captured client-lifetime listener; never synthesize a recipe update. */
    private static boolean refreshJei(Minecraft client) {
        if (jeiReloadListener == null) {
            return false;
        }
        try {
            jeiReloadListener.onResourceManagerReload(client.getResourceManager());
            return true;
        } catch (LinkageError | RuntimeException exception) {
            GunMod.LOGGER.debug("[TACZ Recipe Viewer] JEI lightweight refresh unavailable.", exception);
            return false;
        }
    }

    private static boolean hasJei() {
        return FabricLoader.getInstance().isModLoaded("jei");
    }

    private static boolean hasRei() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded("roughlyenoughitems") || loader.isModLoaded("rei");
    }
}
