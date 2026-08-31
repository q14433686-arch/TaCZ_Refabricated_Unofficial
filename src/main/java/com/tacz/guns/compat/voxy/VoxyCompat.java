package com.tacz.guns.compat.voxy;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;

/**
 * 拿到 Voxy 的 {@code VoxyRenderSystem} 实例。
 *
 * <p>取法照抄 Voxy 自己的写法（{@code IVoxyRenderSystemHolder}，0.2.19 源码）：
 * <pre>
 * static VoxyRenderSystem getNullable() {
 *     var lr = (IVoxyRenderSystemHolder) Minecraft.getInstance().levelRenderer;
 *     return lr == null ? null : lr.voxy$getRenderSystem();
 * }
 * </pre>
 * 这里不去调那个接口的静态方法（Mixin 也好、反射也好，接口静态方法都容易踩坑），
 * 而是直接对 {@code levelRenderer} 调 {@code voxy$getRenderSystem()} —— 同一件事，
 * 但走的是普通的接口实例方法，稳当得多。
 */
@Environment(EnvType.CLIENT)
public final class VoxyCompat {

    private static boolean resolved;
    private static Method getRenderSystem;

    private VoxyCompat() {
    }

    /** @return {@code VoxyRenderSystem} 实例；Voxy 不在、还没建好或取不到时返回 {@code null} */
    public static Object renderSystem() {
        if (!resolved) {
            resolved = true;
            try {
                if (FabricLoader.getInstance().isModLoaded("voxy")) {
                    getRenderSystem = Class.forName("me.cortex.voxy.client.core.IVoxyRenderSystemHolder")
                            .getMethod("voxy$getRenderSystem");
                }
            } catch (Throwable ignored) {
                getRenderSystem = null;
            }
        }
        if (getRenderSystem == null) {
            return null;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.levelRenderer == null) {
                return null;
            }
            return getRenderSystem.invoke(mc.levelRenderer);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
