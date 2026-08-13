package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.block.StatueBlock;
import com.tacz.guns.block.entity.StatueBlockEntity;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class StatueRenderer implements BlockEntityRenderer<StatueBlockEntity, StatueRenderer.StatueRenderState> {

    public StatueRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** Custom render state for statue */
    public static class StatueRenderState extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public ItemStack gunItem = ItemStack.EMPTY;
        /**
         * 与 {@code ThrowableEntityRenderer} 同一手法：物品模型在 extract 阶段
         * 解析好存进快照，submit 阶段只负责提交，不再触碰世界状态。
         */
        public final ItemStackRenderState gunRenderState = new ItemStackRenderState();
    }

    @Override
    public StatueRenderState createRenderState() {
        return new StatueRenderState();
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.STATUE_MODEL_LOCATION);
    }

    @Override
    public void extractRenderState(StatueBlockEntity blockEntity, StatueRenderState state, float partialTick, Vec3 cameraPos, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(blockEntity, state, crumblingOverlay);
        BlockState blockState = blockEntity.getBlockState();
        state.facing = blockState.getValue(StatueBlock.FACING);
        state.gunItem = blockEntity.getGunItem();
        // 在这里解析物品模型，而不是 submit 阶段：
        // 1. 修复崩溃 —— 旧实现调用 ItemModelResolver#updateForNonLiving(..., null)。
        //    该方法是给物品展示框/掉落物这类「非生物实体」用的，内部第一行就解引用
        //    entity.level()，传 null 必定 NPE（雕像一旦放入枪，每帧渲染都崩）。
        // 2. 符合 26.2 extract→submit 管线语义：extract 阶段才允许读世界状态。
        //    updateForTopItem 接受 (level, @Nullable LivingEntity, seed)，Level 从
        //    方块实体取，生物传 null，与 ThrowableEntityRenderer 的用法一致。
        state.gunRenderState.clear();
        if (!state.gunItem.isEmpty()) {
            ItemModelResolver resolver = Minecraft.getInstance().getItemModelResolver();
            Level level = blockEntity.getLevel();
            if (resolver != null && level != null) {
                // ItemDisplayContext.FIXED + 全亮 + seed 0：与上游 1.20.1 的
                // renderStatic(stack, FIXED, 15728880, ..., null, 0) 逐参数等价
                resolver.updateForTopItem(state.gunRenderState, state.gunItem,
                        ItemDisplayContext.FIXED, level, null, 0);
            }
        }
    }

    @Override
    public void submit(StatueRenderState state, PoseStack poseStack, SubmitNodeCollector collector, net.minecraft.client.renderer.state.CameraRenderState cameraState) {
        getModel().ifPresent(model -> {
            int combinedLightIn = state.lightCoords;
            int combinedOverlayIn = OverlayTexture.NO_OVERLAY;

            poseStack.pushPose();
            poseStack.translate(0.5, 1.5, 0.5);
            poseStack.mulPose(Axis.YN.rotationDegrees((state.facing.get2DDataValue() + 2) % 4 * 90));
            poseStack.mulPose(Axis.ZN.rotationDegrees(180));

            RenderType renderType = RenderConfig.BLOCK_ENTITY_TRANSLUCENT.get() ?
                    RenderTypes.entityTranslucent(getTextureLocation()) :
                    RenderTypes.entityCutout(getTextureLocation());
            model.submit(poseStack, ItemDisplayContext.NONE, collector, renderType, combinedLightIn, combinedOverlayIn);

            poseStack.scale(0.5f, 0.5f, 0.5f);
            poseStack.translate(0, -0.875, -1.2);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180));

            double offset = Math.sin(Util.getMillis() / 500.0) * 0.1;
            poseStack.translate(0, offset, 0);

            // 直接提交 extract 阶段解析好的物品渲染状态全亮渲染，与旧行为一致
            if (!state.gunItem.isEmpty()) {
                state.gunRenderState.submit(poseStack, collector, 15728880, OverlayTexture.NO_OVERLAY, 0);
            }

            poseStack.popPose();
        });
    }

    public static Identifier getTextureLocation() {
        return InternalAssetLoader.STATUE_TEXTURE_LOCATION;
    }

    @Override
    public int getViewDistance() {
        return RenderConfig.TARGET_RENDER_DISTANCE.get();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public boolean shouldRender(StatueBlockEntity pBlockEntity, Vec3 pCameraPos) {
        return Vec3.atCenterOf(pBlockEntity.getBlockPos().above()).closerThan(pCameraPos, this.getViewDistance());
    }
}
