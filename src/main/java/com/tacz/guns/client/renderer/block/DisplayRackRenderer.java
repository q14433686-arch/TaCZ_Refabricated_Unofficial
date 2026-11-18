package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.block.entity.DisplayRackBlockEntity;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Vanilla 26.3 item model extraction (including modded FIXED renderers) for both display blocks. */
public class DisplayRackRenderer<T extends DisplayRackBlockEntity> implements BlockEntityRenderer<T, DisplayRackRenderer.State> {
    public DisplayRackRenderer(BlockEntityRendererProvider.Context context) {}
    private record Slot(int index, Direction facing, int rotation, ItemStackRenderState item) {}
    public static class State extends BlockEntityRenderState {
        private final List<Slot> slots = new ArrayList<>();
        private boolean glowing;
    }
    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(T rack, State state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderState.extractBase(rack, state, overlay);
        state.slots.clear();
        state.glowing = rack.isGlowing();
        var level = rack.getLevel();
        if (level == null) return;
        var resolver = Minecraft.getInstance().getItemModelResolver();
        for (int i = 0; i < rack.slotCount(); ++i) {
            var stack = rack.getItem(i);
            if (stack.isEmpty()) continue;
            var item = new ItemStackRenderState();
            resolver.updateForTopItem(item, stack, ItemDisplayContext.FIXED, level, null, i);
            state.slots.add(new Slot(i % DisplayRackBlockEntity.SLOTS_PER_FACE, rack.getSlotFacing(rack.getBlockState(), i), rack.getRotation(i), item));
        }
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.glowing ? 15728880 : state.lightCoords;
        for (var slot : state.slots) {
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.rotate(Axis.YP.rotationDegrees(180 - slot.facing.get2DDataValue() * 90));
            pose.translate((slot.index % 4 + 0.5) / 4 - 0.5, (slot.index / 4 + 0.5) / 4 - 0.5, 0.25);
            pose.rotate(Axis.ZP.rotationDegrees(slot.rotation * 45));
            pose.scale(0.5f, 0.5f, 0.5f);
            slot.item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    @Override public int getViewDistance() { return RenderConfig.TARGET_RENDER_DISTANCE.get(); }
    @Override public boolean shouldRenderOffScreen() { return true; }
}
