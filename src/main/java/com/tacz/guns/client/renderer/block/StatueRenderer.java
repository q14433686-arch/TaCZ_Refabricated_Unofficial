package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.block.entity.StatueBlockEntity;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
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
        state.facing = blockState.getValue(TargetBlock.FACING);
        state.gunItem = blockEntity.getGunItem();
    }

    @Override
    public void submit(StatueRenderState state, PoseStack poseStack, SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {
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

            // Render gun item using ItemStackRenderState
            if (!state.gunItem.isEmpty()) {
                ItemStackRenderState itemRenderState = new ItemStackRenderState();
                Minecraft.getInstance().getItemModelResolver().updateForNonLiving(
                        itemRenderState, state.gunItem, ItemDisplayContext.FIXED, null);
                itemRenderState.submit(poseStack, collector, 15728880, OverlayTexture.NO_OVERLAY, 0);
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