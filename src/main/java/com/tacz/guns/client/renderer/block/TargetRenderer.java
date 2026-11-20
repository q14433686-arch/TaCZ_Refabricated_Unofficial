package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.block.entity.TargetBlockEntity;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class TargetRenderer implements BlockEntityRenderer<TargetBlockEntity, TargetRenderer.TargetRenderState> {
    private static final String UPPER_NAME = "target_upper";
    private static final String HEAD_NAME = "head";
    private final PlayerSkinRenderCache skinCache;

    public TargetRenderer(BlockEntityRendererProvider.Context context) {
        this.skinCache = context.playerSkinRenderCache();
    }

    /** Custom render state for target block entity */
    public static class TargetRenderState extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public float tiltDegrees;
        public Identifier skinTexture;
        public boolean hasOwner;
    }

    @Override
    public TargetRenderState createRenderState() {
        return new TargetRenderState();
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.TARGET_MODEL_LOCATION);
    }

    @Override
    public void extractRenderState(TargetBlockEntity blockEntity, TargetRenderState state, float partialTick, Vec3 cameraPos, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(blockEntity, state, crumblingOverlay);
        BlockState blockState = blockEntity.getBlockState();
        state.facing = blockState.getValue(TargetBlock.FACING);
        state.tiltDegrees = -Mth.lerp(partialTick, blockEntity.oRot, blockEntity.rot);
        ResolvableProfile owner = blockEntity.getOwnerProfile();
        if (owner != null) {
            state.hasOwner = true;
            try {
                // 与 26.3 原版玩家头颅相同：异步解析名字/UUID 和纹理，未就绪时使用默认皮肤。
                state.skinTexture = this.skinCache.getOrDefault(owner).playerSkin().body().texturePath();
            } catch (RuntimeException exception) {
                state.skinTexture = DefaultPlayerSkin.get(owner.partialProfile()).body().texturePath();
            }
        } else {
            state.hasOwner = false;
            state.skinTexture = null;
        }
    }

    @Override
    public void submit(TargetRenderState state, PoseStack poseStack, SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {
        getModel().ifPresent(model -> {
            int combinedLightIn = state.lightCoords;
            int combinedOverlayIn = OverlayTexture.NO_OVERLAY;
            BedrockPart head = model.getNode(HEAD_NAME);
            BedrockPart upper = model.getNode(UPPER_NAME);
            float oldRotation = upper.xRot;
            boolean oldHeadVisible = head.visible;
            poseStack.pushPose();
            try {
                upper.xRot = (float) Math.toRadians(state.tiltDegrees);
                head.visible = false;
                poseStack.translate(0.5, 0.225, 0.5);
                poseStack.rotate(Axis.YN.rotationDegrees(state.facing.get2DDataValue() * 90));
                poseStack.rotate(Axis.ZN.rotationDegrees(180));
                poseStack.translate(0, -1.275, 0.0125);
                RenderType body = RenderTypes.entityTranslucent(InternalAssetLoader.TARGET_TEXTURE_LOCATION);
                model.submit(poseStack, ItemDisplayContext.NONE, collector, body, combinedLightIn, combinedOverlayIn);

                if (state.hasOwner && state.skinTexture != null) {
                    poseStack.translate(0, 1.25, 0);
                    poseStack.rotate(Axis.XP.rotationDegrees(state.tiltDegrees));
                    head.visible = true;
                    // Capture now, just like the plate. Another target can hide or rotate
                    // bones on this shared model before the collector replays its draws.
                    head.translateAndRotateAndScale(poseStack);
                    BedrockRenderSnapshot.captureSubtree(head, poseStack, ItemDisplayContext.NONE,
                            combinedLightIn, combinedOverlayIn, 1, 1, 1, 1)
                            .submitGeometry(collector, RenderTypes.entityCutout(state.skinTexture), false);
                }
            } finally {
                head.visible = oldHeadVisible;
                upper.xRot = oldRotation;
                poseStack.popPose();
            }
        });
    }

    @Override
    public int getViewDistance() {
        return RenderConfig.TARGET_RENDER_DISTANCE.get();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
