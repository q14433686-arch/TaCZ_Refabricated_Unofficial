package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.renderer.SubmitNodeCollector;
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

import java.util.Optional;

public class GunSmithTableRenderer implements BlockEntityRenderer<GunSmithTableBlockEntity, GunSmithTableRenderer.GunSmithTableRenderState> {

    public GunSmithTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** Custom render state that caches block entity data for render-thread use */
    public static class GunSmithTableRenderState extends BlockEntityRenderState {
        public ClientBlockIndex blockIndex;
        public Direction facing;
        public float rotation;
        public boolean isRoot;
    }

    @Override
    public GunSmithTableRenderState createRenderState() {
        return new GunSmithTableRenderState();
    }

    public Optional<ClientBlockIndex> getIndex(GunSmithTableBlockEntity blockEntity) {
        Identifier id = blockEntity.getId();
        if (id == null || id.equals(DefaultAssets.EMPTY_BLOCK_ID)) {
            return Optional.empty();
        }
        return TimelessAPI.getClientBlockIndex(id);
    }

    public static Optional<ClientBlockIndex> getIndex(ItemStack stack) {
        if (stack.getItem() instanceof IBlock iBlock) {
            Identifier id = iBlock.getBlockId(stack);
            if (id.equals(DefaultAssets.EMPTY_BLOCK_ID)) {
                return Optional.empty();
            }
            return TimelessAPI.getClientBlockIndex(id);
        }
        return Optional.empty();
    }

    @Override
    public void extractRenderState(GunSmithTableBlockEntity blockEntity, GunSmithTableRenderState state, float partialTick, net.minecraft.world.phys.Vec3 cameraPos, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(blockEntity, state, crumblingOverlay);
        state.blockIndex = null;
        state.isRoot = false;
        getIndex(blockEntity).ifPresent(index -> {
            state.blockIndex = index;
            BlockState blockState = blockEntity.getBlockState();
            if (blockState.getBlock() instanceof AbstractGunSmithTableBlock block) {
                state.isRoot = block.isRoot(blockState);
                state.facing = blockState.getValue(AbstractGunSmithTableBlock.FACING);
                state.rotation = block.parseRotation(state.facing);
            }
        });
    }

    @Override
    public void submit(GunSmithTableRenderState state, PoseStack poseStack, SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {
        ClientBlockIndex index = state.blockIndex;
        if (index == null || !state.isRoot) {
            return;
        }
        BedrockModel model = index.getModel();
        Identifier texture = index.getTexture();
        if (model == null) {
            return;
        }
        int combinedLightIn = state.lightCoords;
        int combinedOverlayIn = OverlayTexture.NO_OVERLAY;

        poseStack.pushPose();
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Axis.ZN.rotationDegrees(180));
        poseStack.mulPose(Axis.YN.rotationDegrees(state.rotation));
        RenderType renderType = RenderConfig.BLOCK_ENTITY_TRANSLUCENT.get() ?
                RenderTypes.entityTranslucent(texture) :
                RenderTypes.entityCutout(texture);
        model.submit(poseStack, ItemDisplayContext.NONE, collector, renderType, combinedLightIn, combinedOverlayIn);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}