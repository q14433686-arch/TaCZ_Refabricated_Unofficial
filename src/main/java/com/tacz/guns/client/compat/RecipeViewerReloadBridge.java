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
 * Rebuilds recipe-viewer registrations after TACZ's server-authoritative gun-pack cache arrives.
 *
 * <p>JEI and REI build their categories/catalysts from {@code BlockId}-bearing workbench stacks.
 * In 1.21.11 the vanilla client no longer receives TACZ's full custom recipe table, so that table
 * reaches a remote client later through {@code ServerMessageSyncGunPack}. Without a subsequent
 * viewer reload, a viewer can retain categories built before the sync and collapse a custom
 * {@code workbench_b} into whichever generic table happened to register first.
 *
 * <p>JEI's recipe-update event is NOT a safe reload hook: without a preceding genuine recipe
 * sync, JEI clears its server-synced recipe map. Instead, an optional mixin captures JEI's
 * client-lifetime resource reload listener; it restarts JEI without changing recipe-sync state.
 * REI retains its reflective reload hook. If either hook is unavailable, one normal client
 * resource reload per connection is used as a fallback (never a synthetic recipe-sync event).
 * The 1.21.11 JEI ClientLifecycleHandler#getReloadListener implementation and its
 * receivedRecipeSync guard were checked against the upstream 1.21.11 source.
 */
@Environment(EnvType.CLIENT)
public final class RecipeViewerReloadBridge {
    // Owned by JEI's client lifecycle, not by a server connection: do not clear on disconnect.
    private static ResourceManagerReloadListener jeiReloadListener;
    private static boolean resourceFallbackUsed;
    private static boolean reloadRequested;
    private static boolean reloadInProgress;

    private RecipeViewerReloadBridge() {
    }

    /** Called by the optional JEI mixin when JEI creates its own reload listener. */
    public static void setJeiReloadListener(ResourceManagerReloadListener listener) {
        jeiReloadListener = listener;
    }

    /** Called after the synchronized common gun-pack cache has been installed on the client. */
    public static void requestReload() {
        if (hasJei() || hasRei()) {
            reloadRequested = true;
        }
    }

    /** Drops a queued refresh when leaving a server before its sync has completed. */
    public static void clear() {
        reloadRequested = false;
        reloadInProgress = false;
        resourceFallbackUsed = false;
    }

    /** Runs on the client tick so packet ordering and initial world setup have completed first. */
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
                GunMod.LOGGER.warn("[TACZ Recipe Viewer] Viewer hook unavailable; resource fallback already used for this connection.");
            } else {
                GunMod.LOGGER.info("[TACZ Recipe Viewer] JEI/REI refresh completed.");
            }
            return;
        }

        // This fallback is intentionally rare: it is only for an unrecognised optional viewer
        // version. A normal client resource reload makes both viewers rebuild their registrations.
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

    /** Invoke JEI's own stop/start resource listener, without faking a recipe-sync event. */
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

    /** REI 21.11 rebuilds categories/displays through this all-stage plugin reload entry point. */
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
                throw new NoSuchMethodException("RoughlyEnoughItemsCoreClient.reloadPlugins(_, _)");
            }
            // Passing null for the optional debounce timestamp and start stage requests every
            // plugin stage, including CategoryRegistry and DisplayRegistry.
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
        // REI uses roughlyenoughitems on Fabric. Keep rei as a harmless alternate guard for
        // repackaged builds that retain the API but choose a different metadata id.
        return loader.isModLoaded("roughlyenoughitems") || loader.isModLoaded("rei");
    }
}
