package com.tacz.guns.client.renderer.item;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.renderer.block.GunSmithTableRenderer;
import com.tacz.guns.client.resource.pojo.display.block.BlockTransformParser;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public class GunSmithTableItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final SlotModel SLOT_BLOCK_MODEL = new SlotModel();

    public static final Supplier<GunSmithTableItemRenderer> INSTANCE = Suppliers.memoize(GunSmithTableItemRenderer::new);

    public GunSmithTableItemRenderer() {
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, SubmitNodeCollector collector, int light, int overlay) {
        renderByItem(stack, mode, matrices, collector, light, overlay);
    }

    public void renderByItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext transformType, @Nonnull PoseStack poseStack, @Nonnull SubmitNodeCollector collector, int pPackedLight, int pPackedOverlay) {
        GunSmithTableRenderer.getIndex(stack).ifPresentOrElse(index -> {
            BedrockModel model = index.getModel();
            Identifier texture = index.getTexture();
            if (model == null) {
                return;
            }
            poseStack.pushPose();

            // 26.2 修复：恢复上游的 display transforms 应用。移植时 ClientBlockIndex 的
            // transforms 解析被删除，这一段也随之消失，导致手持模型按方块原始尺寸(1m³)渲染
            // —— 默认包声明 scale 0.25，即实际大了 4 倍。
            //
            // 上游 1.21.1 写法：
            //   poseStack.translate(0.5F, 0.5F, 0.5F);
            //   transforms.getTransform(ctx).apply(false, poseStack);
            //   poseStack.translate(-0.5F, -0.5F, -0.5F);
            //
            // 26.2 差异（均由反编译确认）：
            //   1) ItemTransform#apply 第二参数是 PoseStack.Pose，不是 PoseStack；
            //   2) apply 内部已自带 translate(-0.5,-0.5,-0.5)，故调用方不再补最后那一次；
            //   3) 左手上下文需传 applyLeftHandFix=true，上游硬编码 false（左手镜像有误）。
            ItemTransforms transforms = index.getTransforms();
            if (transforms != null && transforms != ItemTransforms.NO_TRANSFORMS) {
                poseStack.translate(0.5F, 0.5F, 0.5F);
                transforms.getTransform(transformType)
                        .apply(BlockTransformParser.isLeftHand(transformType), poseStack.last());
            }

            poseStack.translate(0.5, 1.5, 0.5);
            poseStack.mulPose(Axis.ZN.rotationDegrees(180));
            RenderType renderType = RenderTypes.entityTranslucent(texture);
            model.submit(poseStack, transformType, collector, renderType, pPackedLight, pPackedOverlay);
            poseStack.popPose();
        }, () -> {
            poseStack.translate(0.5, 1.5, 0.5);
            poseStack.mulPose(Axis.ZN.rotationDegrees(180));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(MissingTextureAtlasSprite.getLocation()), (pose, buffer) -> {
                // 26.2: 必须使用回调参数 pose（= 提交那一刻 poseStack.last().copy() 的快照），
                // 而不是外层 poseStack —— 回调执行时它早已被 popPose/复用，
                // 结果就是图标被画到错误位置（物品栏一片空白）。
                PoseStack tacz$snapshotPose = new PoseStack();
                tacz$snapshotPose.last().pose().set(pose.pose());
                tacz$snapshotPose.last().normal().set(pose.normal());
                SLOT_BLOCK_MODEL.renderToBuffer(tacz$snapshotPose, buffer, pPackedLight, pPackedOverlay, 1, 1, 1, 1);
            });
        });
    }
}
