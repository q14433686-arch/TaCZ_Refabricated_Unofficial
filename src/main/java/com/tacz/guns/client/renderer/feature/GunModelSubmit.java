package com.tacz.guns.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * 26.2 Feature Rendering 的<b>未启用实验节点</b>。
 *
 * <p>2026-08-12 调用链复核：唯一构造入口 {@code FeatureRenderCompat#submit}
 * 全仓零调用；在役路径是 {@code BedrockModel#submit -> submitCustomGeometry}。
 * 因此本 record 与其 renderer/type 当前不会收到节点，不能描述成“完整迁移”。
 * 保留它只是将来若整体切换 Fabric FeatureRenderer API 时的脚手。</p>
 *
 * <p>原型 API 来源 (2026-07-21 已拉取):</p>
 * <ul>
 *   <li>https://github.com/FabricMC/fabric-api/blob/26.2/fabric-rendering-v1/src/testmodClient/java/net/fabricmc/fabric/test/rendering/client/FeatureRendererTest.java</li>
 *   <li>https://github.com/FabricMC/fabric-api/blob/26.2/fabric-rendering-v1/src/client/java/net/fabricmc/fabric/api/client/rendering/v1/SubmitRenderPhases.java</li>
 * </ul>
 * <p>
 * 原型设计:
 * <ul>
 *   <li>实现 {@link SubmitNode}, 提供 {@link #featureType()}</li>
 *   <li>原型固定走 {@code SOLID} 阶段且未实现 {@code TranslucentSubmit}；由于整条
 *       原型链不可达，这目前不是玩家功能缺口。若未来启用该架构，必须重新审视排序。</li>
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
