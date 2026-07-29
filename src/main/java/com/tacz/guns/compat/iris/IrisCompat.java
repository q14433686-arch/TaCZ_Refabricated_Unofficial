package com.tacz.guns.compat.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.tacz.guns.GunMod;
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
    private static boolean scopePipelinesAssigned = false;
    private static int scopePipelineAssignSuccesses = 0;
    private static boolean loggedScopePipelineAssign = false;

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
                Boolean inUse = (Boolean) irisApiClass.getMethod("isShaderPackInUse").invoke(instance);
                return inUse;
            } catch (Exception e) {
                return false;
            }
        }
        // Sulkan/Vulkan shader path: 目前没有稳定公开 API 可查询“是否已启用具体光影包”。
        // 但只要 Sulkan 存在，scope 的离屏 mask + 自定义 pipeline 与其 pass 调度就有兼容风险；
        // 先按“存在即启用安全回退”处理，宁可失去镜内裁剪，也不要镜身/雾效/自发光层缺失。
        if (FabricLoader.getInstance().isModLoaded("sulkan")) {
            return true;
        }
        return false;
    }

    /**
     * 把 TACZ 自定义 scope pipeline 显式归类到 Iris 的 hand program。
     *
     * <p>26.x Iris 的 {@code IrisPipelines} 只内置映射 vanilla {@code RenderPipelines.*}。
     * 我们的 {@code scope_body_clipped}/{@code scope_reticle_clipped} 是自建
     * {@link RenderPipeline}；若不调用 Iris API 的 {@code assignPipeline}，Iris 不知道它属于
     * hand/entity 哪个 gbuffer program，光影下就可能出现镜身不画、雾效/发光层不进入预期 pass。
     * 这里使用反射避免对 Iris 硬依赖。</p>
     */
    public static boolean assignScopePipelineToHand(RenderPipeline pipeline, String debugName) {
        if (!FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS)) {
            return false;
        }
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = irisApiClass.getMethod("getInstance").invoke(null);
            int minor = (Integer) irisApiClass.getMethod("getMinorApiRevision").invoke(instance);
            if (minor < 3) {
                if (!loggedScopePipelineAssign) {
                    loggedScopePipelineAssign = true;
                    GunMod.LOGGER.warn("[TACZ Scope] Iris API revision {} has no assignPipeline support; scope mask will fall back under shaders.", minor);
                }
                return false;
            }
            Class<?> irisProgramClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object handProgram = Enum.valueOf((Class<? extends Enum>) irisProgramClass.asSubclass(Enum.class), "HAND");
            irisApiClass.getMethod("assignPipeline", RenderPipeline.class, irisProgramClass)
                    .invoke(instance, pipeline, handProgram);
            scopePipelineAssignSuccesses++;
            scopePipelinesAssigned = scopePipelineAssignSuccesses >= 2;
            if (!loggedScopePipelineAssign) {
                loggedScopePipelineAssign = true;
                GunMod.LOGGER.info("[TACZ Scope] Assigned custom scope pipelines to Iris HAND program (latest: {}).", debugName);
            }
            return true;
        } catch (Throwable t) {
            if (!loggedScopePipelineAssign) {
                loggedScopePipelineAssign = true;
                GunMod.LOGGER.warn("[TACZ Scope] Failed to assign custom scope pipelines to Iris; scope mask will fall back under shaders.", t);
            }
            return false;
        }
    }

    public static boolean shouldDisableScopeMaskUnderShaderPack() {
        // Sulkan 暂无公开等价 API；同样保守回退。
        if (FabricLoader.getInstance().isModLoaded("sulkan")) {
            return true;
        }
        // Iris 深度兼容实验：不再在 shader pack 下直接关闭 scope mask。
        // 路线是 assignPipeline -> Iris HAND program，同时由 tacz.iris.mixins.json 给 Iris
        // HAND shader 注入默认关闭的 tacz_ScopeMaskMode 分支；只有当前 draw 携带
        // ScopeMaskSampler 且 pipeline 是 tacz:pipeline/scope_* 时才启用裁切。
        return false;
    }

    /**
     * @return 当前是否正在 Iris 自己的第一人称手部渲染通道内。
     *
     * <p>26.x Iris 开启 shader pack 后不会只走 vanilla {@code GameRenderer#renderItemInHand}。
     * 它会在 {@code HandRenderer#renderSolid/renderTranslucent} 中直接调用
     * {@code ItemInHandRenderer#renderHandsWithItems}，并在这个期间把
     * {@code HandRenderer.ACTIVE} 置为 true。TACZ 原先只靠
     * {@code GameRendererMixin#renderItemInHand HEAD/RETURN} 判断“手部 pass”，
     * 因此 Iris hand pass 里会把 {@code bobView} 当成世界 bob，而不是手持物 bob，
     * 导致持枪/ADS 的自定义走路动画又叠了一层 vanilla view bob —— 表现为光影开启时
     * 瞄准移动幅度异常变大。</p>
     */
    public static boolean isHandRendererActive() {
        if (!FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS) || !isUsingRenderPack()) {
            return false;
        }
        try {
            Class<?> handRendererClass = Class.forName("net.irisshaders.iris.pathways.HandRenderer");
            Object instance = handRendererClass.getField("INSTANCE").get(null);
            return (Boolean) handRendererClass.getMethod("isActive").invoke(instance);
        } catch (Throwable ignored) {
            return false;
        }
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
