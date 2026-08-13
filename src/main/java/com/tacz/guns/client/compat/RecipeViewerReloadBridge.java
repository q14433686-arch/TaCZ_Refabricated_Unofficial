package com.tacz.guns.client.compat;

import com.tacz.guns.GunMod;
import com.tacz.guns.resource.CommonAssetsManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

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
 * <p>Both viewers have a version-specific lightweight reload hook, but neither exposes it as a
 * stable common API. This bridge invokes those hooks reflectively only when the matching optional
 * mod is loaded. If a future viewer version moves the hook, it fails closed and falls back to one
 * normal Minecraft client-resource reload rather than leaving stale recipes behind. The normal
 * current path does <b>not</b> reload all resources (and therefore does not unnecessarily recompile
 * shader packs) on every server join.
 */
@Environment(EnvType.CLIENT)
public final class RecipeViewerReloadBridge {
    private static boolean reloadRequested;
    private static boolean reloadInProgress;

    private RecipeViewerReloadBridge() {
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
        if (hasJei() && !refreshJei()) {
            requiresResourceFallback = true;
        }
        if (hasRei() && !refreshRei()) {
            requiresResourceFallback = true;
        }

        if (!requiresResourceFallback) {
            reloadInProgress = false;
            GunMod.LOGGER.info("[TACZ Recipe Viewer] JEI/REI refresh completed.");
            return;
        }

        // This fallback is intentionally rare: it is only for an unrecognised optional viewer
        // version. A normal client resource reload makes both viewers rebuild their registrations.
        GunMod.LOGGER.warn("[TACZ Recipe Viewer] Viewer reload hook unavailable; falling back to a client resource reload.");
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

    /** JEI Fabric 1.21.11 restarts its plugin registry when this lifecycle event is invoked. */
    private static boolean refreshJei() {
        try {
            Class<?> lifecycleEvents = Class.forName("mezz.jei.fabric.events.JeiLifecycleEvents");
            Object event = lifecycleEvents.getField("AFTER_RECIPES_UPDATED").get(null);
            Object invoker = event.getClass().getMethod("invoker").invoke(event);
            if (!(invoker instanceof Runnable runnable)) {
                throw new IllegalStateException("JEI recipe-update invoker is not Runnable");
            }
            runnable.run();
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
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
