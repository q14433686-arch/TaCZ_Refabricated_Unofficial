package com.tacz.guns.client.compat;

import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Refresh optional recipe viewers after the server has delivered TACZ data.
 *
 * <p>This class deliberately uses reflection for JEI. The normal mod must load
 * without JEI installed, while the JEI plugin itself has compile-only API
 * references. The JEI callback refreshes both synchronized Create processes
 * and the gun/feed reference relationship. REI uses live display generators
 * and therefore reads the same cache lazily without a refresh call.</p>
 */
@Environment(EnvType.CLIENT)
public final class RecipeViewerSyncBridge {
    private static final String JEI_MOD_ID = "jei";
    private static final String JEI_PLUGIN = "com.tacz.guns.compat.jei.GunModPlugin";
    private static final String JEI_REFRESH_METHOD = "onIndustryProcessesSynchronized";

    private RecipeViewerSyncBridge() {
    }

    /** Queue the JEI runtime update on the client thread after common-data sync. */
    public static void onCommonDataSynchronized() {
        if (!FabricLoader.getInstance().isModLoaded(JEI_MOD_ID)) {
            return;
        }
        Minecraft.getInstance().execute(RecipeViewerSyncBridge::refreshJeiIndustryProcesses);
    }

    private static void refreshJeiIndustryProcesses() {
        try {
            Class<?> plugin = Class.forName(JEI_PLUGIN);
            Method method = plugin.getMethod(JEI_REFRESH_METHOD);
            method.invoke(null);
        } catch (ClassNotFoundException ignored) {
            // JEI reported itself as loaded but its plugin entrypoint is not
            // available yet. The plugin's onRuntimeAvailable callback retries
            // from the already synchronized cache.
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError exception) {
            GunMod.LOGGER.warn("Unable to refresh JEI synchronized recipe-viewer data after TACZ sync", exception);
        }
    }
}
