package com.tacz.guns.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * 26.2 Feature Rendering - Gun Model 提交节点
 * <p>
 * 完整迁移自 1.20.1 旧 {@code BedrockModel.render(BufferSource)} 模式到 26.2 新 SubmitNode 模式.
 * <p>
 * 真实 API 来源 (2026-07-21 已拉取):
 * <ul>
 *   <li>https://github.com/FabricMC/fabric-api/blob/26.2/fabric-rendering-v1/src/testmodClient/java/net/fabricmc/fabric/test/rendering/client/FeatureRendererTest.java</li>
 *   <li>https://github.com/FabricMC/fabric-api/blob/26.2/fabric-rendering-v1/src/client/java/net/fabricmc/fabric/api/client/rendering/v1/SubmitRenderPhases.java</li>
 * </ul>
 * <p>
 * 关键设计:
 * <ul>
 *   <li>实现 {@link SubmitNode}, 提供 {@link #featureType()}</li>
 *   <li>由于 TACZ 模型通常半透明 (透明枪械贴图), 用 {@code SOLID} 阶段而非 TRANSLUCENT_* (避免 TranslucentSubmit 接口约束和排序复杂度)</li>
 *   <li>本 record 暂未实现 {@code TranslucentSubmit}, 留待确认 BedrockModel 是否真的需要半透明排序</li>
 * </ul>
 *
 * @param poseStack PoseStack 上下文
 * @param transformType 物品展示上下文 (FIRST_PERSON, THIRD_PERSON, GUI 等)
 * @param model 要渲染的 BedrockModel
 * @param renderType 渲染类型 (实体实心/半透明/带切out 等)
 * @param light 光照
 * @param overlay 覆盖层 (例如暴击闪光)
 * @param r 红色 (0-255)
 * @param g 绿色 (0-255)
 * @param b 蓝色 (0-255)
 * @param a 透明度 (0-255)
 */
public record GunModelSubmit(
        PoseStack poseStack,
        ItemDisplayContext transformType,
        BedrockModel model,
        RenderType renderType,
        int light,
        int overlay,
        int r, int g, int b, int a
) implements SubmitNode {

    @Override
    public FeatureRendererType featureType() {
        return GunModelFeatureRenderer.TYPE;
    }

    /**
     * 辅助构造方法 - 用 float 颜色 (向后兼容旧调用方)
     */
    public static GunModelSubmit of(PoseStack poseStack, ItemDisplayContext ctx,
                                    BedrockModel model, RenderType renderType,
                                    int light, int overlay,
                                    float r, float g, float b, float a) {
        return new GunModelSubmit(
                poseStack, ctx, model, renderType, light, overlay,
                (int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255)
        );
    }
}
