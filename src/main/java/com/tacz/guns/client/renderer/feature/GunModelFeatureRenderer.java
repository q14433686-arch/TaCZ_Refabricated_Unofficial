package com.tacz.guns.client.renderer.feature;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;

import java.util.List;

/**
 * 26.2 Feature Rendering - Gun Model 特性渲染器
 * <p>
 * 完整迁移自旧 {@code BufferSource + getBuffer(RenderType)} 模式到 26.2 新 FeatureRenderer 模式.
 * <p>
 * 真实 API 来源 (2026-07-21 已拉取):
 * <ul>
 *   <li>https://github.com/FabricMC/fabric-api/blob/26.2/fabric-rendering-v1/src/testmodClient/java/net/fabricmc/fabric/test/rendering/client/FeatureRendererTest.java</li>
 *   <li>https://github.com/FabricMC/fabric-api/blob/26.2/fabric-rendering-v1/src/client/java/net/fabricmc/fabric/api/client/rendering/v1/FeatureRendererRegistry.java</li>
 * </ul>
 * <p>
 * 注册方式 (在 {@code onInitializeClient} 中):
 * <pre>{@code
 * FeatureRendererRegistry.register(GunModelFeatureRenderer.TYPE, GunModelFeatureRenderer::new);
 * }</pre>
 * <p>
 * 工作流程:
 * <ol>
 *   <li>{@code buildGroup} 在 prepare 阶段被调用, 接收同一帧所有 GunModelSubmit</li>
 *   <li>对每个 submit, 用 {@link #getVertexBuilder} 获取 RenderType 对应的 VertexConsumer</li>
 *   <li>调用 {@link BedrockPart#render} 写入顶点 (签名 1.20.1 已兼容: {@code (PoseStack, ItemDisplayContext, VertexConsumer, int, int, float, float, float, float)})</li>
 * </ol>
 */
public class GunModelFeatureRenderer extends RenderTypeFeatureRenderer<GunModelSubmit> {

    /**
     * FeatureRenderer 类型标识符 - 注册时使用
     * <p>
     * 命名空间 {@code tacz}, 路径 {@code gun_model}
     * 真实 API: {@code FeatureRendererType.create(String name)} (在 26.2 分支源码确认)
     */
    public static final FeatureRendererType TYPE = FeatureRendererType.create("tacz:gun_model");

    @Override
    protected void buildGroup(FeatureFrameContext context, List<GunModelSubmit> submits) {
        if (submits.isEmpty()) {
            return;
        }

        for (GunModelSubmit submit : submits) {
            // getVertexBuilder 是 RenderTypeFeatureRenderer 的实例方法,
            // 内部委托给 FeatureFrameContext, 返回与 submit.renderType 匹配的 VertexConsumer
            VertexConsumer builder = getVertexBuilder(submit.renderType());

            // 遍历 BedrockModel 中应渲染的部件
            for (BedrockPart part : submit.model().getShouldRender()) {
                // BedrockPart.render 签名: (PoseStack, ItemDisplayContext, VertexConsumer, int light, int overlay, float r, float g, float b, float a)
                part.render(
                        submit.poseStack(),
                        submit.transformType(),
                        builder,
                        submit.light(),
                        submit.overlay(),
                        submit.r() / 255.0f,
                        submit.g() / 255.0f,
                        submit.b() / 255.0f,
                        submit.a() / 255.0f
                );
            }
        }
    }
}
