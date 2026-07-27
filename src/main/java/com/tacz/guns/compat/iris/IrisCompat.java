package com.tacz.guns.compat.iris;

import com.tacz.guns.compat.iris.legacy.IrisCompatLegacy;
import com.tacz.guns.compat.iris.newly.IrisCompatNewly;
import com.tacz.guns.init.CompatRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.renderer.SubmitNodeCollector;

import java.util.function.Supplier;

/**
 * 26.2 迁移: Iris (OpenGL) 不兼容 Vulkan 后端，已被 Sulkan 取代
 * 此类重构为:
 * - 若检测到 Iris (旧后端) -> 保留旧逻辑但使用反射避免硬依赖
 * - 若检测到 Sulkan (Vulkan) -> 使用 Sulkan API (待实现，暂时返回 false)
 * - 否则返回 false (无 shader)
 *
 * 同时移除对 MultiBufferSource.BufferSource 的直接依赖，因为 26.2 已移除 MultiBufferSource
 * 新增对 SubmitNodeCollector 的兼容
 */
public final class IrisCompat {
    private static final Version VERSION;

    static {
        try {
            VERSION = Version.parse("1.7.0");
        } catch (VersionParsingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Supplier<Boolean> IS_RENDER_SHADOW_SUPPER = () -> false;

    public static void initCompat() {
        // Iris 检测 (OpenGL only) - 26.2 下通常不会加载
        FabricLoader.getInstance().getModContainer(CompatRegistry.IRIS).ifPresent(mod -> {
            try {
                if (mod.getMetadata().getVersion().compareTo(VERSION) >= 0) {
                    IS_RENDER_SHADOW_SUPPER = IrisCompatNewly::isRenderShadow;
                } else {
                    IS_RENDER_SHADOW_SUPPER = IrisCompatLegacy::isRenderShadow;
                }
            } catch (Exception e) {
                IS_RENDER_SHADOW_SUPPER = () -> false;
            }
        });
        // Sulkan 检测 (Vulkan) - TODO: 实现 Sulkan API 调用
        // if (FabricLoader.getInstance().isModLoaded("sulkan")) { ... }
    }

    public static boolean isRenderShadow() {
        if (FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS)) {
            try {
                return IS_RENDER_SHADOW_SUPPER.get();
            } catch (Exception e) {
                return false;
            }
        }
        // Sulkan shadow check placeholder
        return false;
    }

    public static boolean isUsingRenderPack() {
        // Iris 检查 - 使用反射避免硬依赖
        if (FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS)) {
            try {
                Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object instance = irisApiClass.getMethod("getInstance").invoke(null);
                Boolean inUse = (Boolean) instance.getClass().getMethod("isShaderPackInUse").invoke(instance);
                return inUse;
            } catch (Exception e) {
                return false;
            }
        }
        // Sulkan 检查 - TODO
        if (FabricLoader.getInstance().isModLoaded("sulkan")) {
            // 假设 Sulkan API: SulkanApi.isShaderEnabled() - 待确认
            return false;
        }
        return false;
    }

    // 旧 API - MultiBufferSource 已在 26.2 移除，保留兼容但返回 false
    @Deprecated
    public static boolean endBatch(Object bufferSource) {
        // 26.2 不再需要手动 endBatch，Feature Rendering 系统自动处理
        return false;
    }

    // 新 API - 针对 SubmitNodeCollector (26.2 Feature Rendering)
    public static boolean endBatch(SubmitNodeCollector collector) {
        return false;
    }
}
