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
 * Rebuilds optional recipe-viewer registrations after TACZ's authoritative gun-pack cache arrives.
 *
 * <p>JEI and REI build TACZ categories and displays from the synchronized common indexes. A remote
 * gun-pack cache can arrive after the viewers' first registration pass, so registering categories
 * alone cannot make newly synced guns, workbenches, attachments, or ammo-query entries visible.
 * Requests are deliberately coalesced and deferred to {@link #tick(Minecraft)}: packet handling,
 * viewer internals, and client resources must all remain on the Minecraft client thread.</p>
 *
 * <p>The lightweight entry points are optional implementation APIs, so this class uses reflection
 * and only probes a viewer that is installed. The verified 26.3 shapes are JEI
 * {@code mezz.jei.common.Internal#restartJei()} (with the 26.2 event
 * {@code mezz.jei.fabric.events.JeiLifecycleEvents.AFTER_RECIPES_UPDATED} as fallback — see
 * {@link #refreshJei()} for why the event must not be the first choice) and REI {@code me.shedaniel.rei.RoughlyEnoughItemsCoreClient#reloadPlugins}
 * with two nullable arguments. If either installed viewer has moved its entry point, one normal
 * client resource reload is used as a safe fallback for the connection; it is never retriggered by
 * the fallback itself.</p>
 */
@Environment(EnvType.CLIENT)
public final class RecipeViewerReloadBridge {
    private static boolean reloadRequested;
    private static boolean reloadInProgress;
    private static boolean resourceFallbackUsed;

    private RecipeViewerReloadBridge() {
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
        if (hasJei() && !refreshJei()) {
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
     * Restart JEI so it re-runs plugin registration with the freshly synced gun-pack cache.
     *
     * <h2>Why {@code Internal.restartJei()} and not {@code AFTER_RECIPES_UPDATED}</h2>
     * <p>The event was the 26.2 entry point, but JEI Fabric's listener for it is
     * (26.2 and 26.3 source, {@code ClientLifecycleHandler#registerEvents}):</p>
     * <pre>
     *   if (!receivedRecipeSync) Internal.clearClientRecipes();
     *   receivedRecipeSync = false;
     *   stopJei(); startJei();
     * </pre>
     * <p>The flag is only set by a real Fabric recipe-sync packet and is consumed by the
     * first start. Firing the event by hand afterwards therefore <b>throws away the
     * server-synced recipe map</b>; JEI then falls back to
     * {@code VanillaClientRecipeLoader}, which loads recipes from the vanilla pack only
     * ("Loaded N vanilla recipes from the client recipe registry" + "This fabric server does
     * not provide recipes to JEI"). Every {@code minecraft:crafting_*} recipe shipped by a mod
     * datapack — TACZ's own workbench/ammo-box/target recipes and any gun pack's — silently
     * disappears from JEI, while {@code tacz:gun_smith_table_crafting} entries survive only
     * because our plugin builds them from the gun-pack cache instead of the recipe map.</p>
     *
     * <p>JEI 26.3 exposes {@code mezz.jei.common.Internal#restartJei()}, which does the same
     * stop/start <b>without</b> clearing the synced recipes. Prefer it; fall back to the event
     * only when that API is absent (older JEI), accepting the old behaviour there.</p>
     */
    private static boolean refreshJei() {
        try {
            Class<?> internal = Class.forName("mezz.jei.common.Internal");
            Method restart = internal.getMethod("restartJei");
            restart.invoke(null);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            GunMod.LOGGER.debug("[TACZ Recipe Viewer] JEI Internal.restartJei unavailable; trying the recipes-updated event.", exception);
        }
        try {
            Class<?> lifecycleEvents = Class.forName("mezz.jei.fabric.events.JeiLifecycleEvents");
            Object event = lifecycleEvents.getField("AFTER_RECIPES_UPDATED").get(null);
            Object invoker = event.getClass().getMethod("invoker").invoke(event);
            if (!(invoker instanceof Runnable runnable)) {
                throw new IllegalStateException("JEI recipe-update invoker is not Runnable");
            }
            GunMod.LOGGER.warn("[TACZ Recipe Viewer] Using JEI's AFTER_RECIPES_UPDATED event as a fallback; "
                    + "this JEI build may drop server-synced vanilla-type recipes from its view.");
            runnable.run();
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
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
