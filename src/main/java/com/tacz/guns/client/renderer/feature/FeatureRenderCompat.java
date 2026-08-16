package com.tacz.guns.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import net.fabricmc.fabric.api.client.rendering.v1.FabricOrderedSubmitNodeCollector;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * 26.2 渲染兼容层 - 集中处理 SubmitNodeCollector 的提交逻辑
 * <p>
 * 设计目的:
 * <ul>
 *   <li>在 {@link BedrockModel#submit} 中无需关心 collector 类型</li>
 *   <li>OpenGL fallback 路径通过反射处理 (避免直接依赖 26.2 编译期类型)</li>
 *   <li>Vulkan 路径直接使用 Fabric API</li>
 * </ul>
 * <p>
 * 用法 (在 BedrockModel.submit 中):
 * <pre>{@code
 * public void submit(..., SubmitNodeCollector collector, ...) {
 *     FeatureRenderCompat.submit(poseStack, ctx, this, renderType, light, overlay, r, g, b, a, collector);
 * }
 * }</pre>
 */
public final class FeatureRenderCompat {

    private FeatureRenderCompat() {
    }

    // 【2026-08-11 核实 · 死脚手警示】本类的 submit(...) 全仓<b>零调用方</b>：
    // BedrockModel.submit 走的是 collector.submitCustomGeometry 直接提交，
    // 从未经过本兼容层。因此 GunModelSubmit 只在本类里被实例化、
    // GunModelFeatureRenderer 虽在客户端 init 注册了其 TYPE，但没有任何节点
    // 会被实际喂给它 ——  renderer/feature/ 整包目前是「注册了但永不触发」的死脚手。
    // 保留无害；若启用 Feature Rendering 自定义节点，先接本类的调用链。

    /**
     * 提交 GunModelSubmit 到 collector
     *
     * @param collector 26.2 的 SubmitNodeCollector (或 OpenGL fallback 时的 BufferSource)
     * @return true 表示走 Feature Rendering 路径; false 表示已 fallback 到旧路径
     */
    public static boolean submit(PoseStack poseStack, ItemDisplayContext transformType,
                                 BedrockModel model, RenderType renderType,
                                 int light, int overlay,
                                 int r, int g, int b, int a,
                                 Object collector) {
        // 路径 1: 26.2 Feature Rendering (Vulkan/现代 OpenGL)
        if (collector instanceof OrderedSubmitNodeCollector osc
                && osc instanceof FabricOrderedSubmitNodeCollector fabric) {
            GunModelSubmit node = new GunModelSubmit(
                    poseStack, transformType, model, renderType, light, overlay, r, g, b, a
            );
            // 用 SOLID 阶段: 简单可靠, 避免 TranslucentSubmit 排序约束
            // 若将来 BedrockModel 需要半透明排序, 应改为 TRANSLUCENT_MODELS 并 implements TranslucentSubmit
            fabric.submitCustom(SubmitRenderPhases.SOLID, node);
            return true;
        }

        // 路径 2: OpenGL/旧路径 fallback
        // 调用方应已实现 render(...) 旧方法, 此处不直接处理
        return false;
    }
}
