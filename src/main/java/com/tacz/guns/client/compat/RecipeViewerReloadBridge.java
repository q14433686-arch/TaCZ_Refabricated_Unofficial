package com.tacz.guns.client.compat;

import com.tacz.guns.GunMod;
import com.tacz.guns.resource.CommonAssetsManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Rebuilds optional recipe-viewer registrations after TACZ's authoritative gun-pack cache arrives.
 *
 * <p>JEI and REI build TACZ categories and displays from the synchronized common indexes. A remote
 * gun-pack cache can arrive after the viewers' first registration pass, so registering categories
 * alone cannot make newly synced guns, workbenches, attachments, or ammo-query entries visible.
 * Requests are deliberately coalesced and deferred to {@link #tick(Minecraft)}: packet handling,
 * viewer internals, and client resources must all remain on the Minecraft client thread.</p>
 *
 * <p>The lightweight entry points are optional implementation APIs. This class only probes
 * installed viewers and uses reflection for REI. JEI uses its non-destructive lifecycle reload
 * listener, captured by an optional mixin; REI uses
 * {@code me.shedaniel.rei.RoughlyEnoughItemsCoreClient#reloadPlugins} with two nullable arguments. Never synthesize JEI's {@code AFTER_RECIPES_UPDATED} event:
 * newer JEI versions interpret it without a preceding recipe-sync notification as a server switch
 * and clear the authoritative recipe map, leaving only the vanilla client fallback. If either
 * installed viewer has moved its entry point, one normal client resource reload is used as a safe
 * fallback for the connection; it is never retriggered by the fallback itself.</p>
 */
@Environment(EnvType.CLIENT)
public final class RecipeViewerReloadBridge {
    // This listener belongs to JEI's client lifecycle, not a connection. Keep it across clear().
    private static ResourceManagerReloadListener jeiReloadListener;
    private static boolean reloadRequested;
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
        if (hasJei() || hasRei()) {
            reloadRequested = true;
        }
    }

    /** Drops pending work when the client leaves before the synchronized cache can be used. */
    public static void clear() {
        reloadRequested = false;
        reloadInProgress = false;
        resourceFallbackUsed = false;
    }

    /** Runs from {@code END_CLIENT_TICK} after the client has a level and player. */
    public static void tick(Minecraft client) {
        if (!reloadRequested || reloadInProgress || client.level == null || client.player == null) {
            return;
        }

        reloadRequested = false;
        reloadInProgress = true;
        int tableCount = CommonAssetsManager.get().getAllBlocks().size();
        int recipeCount = CommonAssetsManager.get().getAllTableRecipes().size();
        GunMod.LOGGER.info("[TACZ Recipe Viewer] Refreshing after gun-pack sync ({} table(s), {} recipe(s)).",
                tableCount, recipeCount);

        // Do not short-circuit: when both viewers are installed each receives its own refresh attempt.
        boolean requiresResourceFallback = false;
        if (hasJei() && !refreshJei(client)) {
            requiresResourceFallback = true;
        }
        if (hasRei() && !refreshRei()) {
            requiresResourceFallback = true;
        }
        if (!requiresResourceFallback || resourceFallbackUsed) {
            reloadInProgress = false;
            if (requiresResourceFallback) {
                GunMod.LOGGER.warn("[TACZ Recipe Viewer] Lightweight refresh is unavailable; the one fallback for this connection was already used.");
            } else {
                GunMod.LOGGER.info("[TACZ Recipe Viewer] JEI/REI refresh completed.");
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

    /**
     * JEI's own lifecycle reload listener rebuilds plugins without treating this as a new recipe
     * packet or clearing the server recipe cache. Present in both the pinned 30.13.0.86 and newer
     * 26.2 JEI builds. The optional mixin captures it without linking against JEI implementation
     * classes; no synthetic recipe-sync events or private state writes are needed.
     */
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

    /** REI 26.2.820 reloads all plugin stages through reloadPlugins(MutableLong, ReloadStage). */
    private static boolean refreshRei() {
        try {
            Class<?> coreClient = Class.forName("me.shedaniel.rei.RoughlyEnoughItemsCoreClient");
            Method reload = null;
            for (Method candidate : coreClient.getMethods()) {
                if (candidate.getName().equals("reloadPlugins")
                        && Modifier.isStatic(candidate.getModifiers())
                        && candidate.getParameterCount() == 2) {
                    reload = candidate;
                    break;
                }
            }
            if (reload == null) {
                throw new NoSuchMethodException("RoughlyEnoughItemsCoreClient.reloadPlugins(MutableLong, ReloadStage)");
            }
            // Null requests the full plugin-stage reload; REI's own 26.2 UI uses this same
            // two-argument entry point for a manual reload.
            reload.invoke(null, null, null);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            GunMod.LOGGER.debug("[TACZ Recipe Viewer] REI lightweight refresh unavailable.", exception);
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
