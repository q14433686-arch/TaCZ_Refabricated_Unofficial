package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.GunDisplayCaseSlotHelper;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.entity.DisplayRackBlockEntity;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.client.render.light.RackSurfaceLight;
import com.tacz.guns.client.render.light.WorldFlashFrame;
import net.minecraft.world.level.block.state.BlockState;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.util.GunPegboardItemConfigRead;
import com.tacz.guns.item.GunPegboardItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Native 1.21.11 item model extraction (including modded FIXED renderers) for both display blocks. */
public class DisplayRackRenderer<T extends DisplayRackBlockEntity> implements BlockEntityRenderer<T, DisplayRackRenderer.State> {
    public DisplayRackRenderer(BlockEntityRendererProvider.Context context) {}
    private record Slot(int index, Direction facing, int rotation, ItemStackRenderState item, boolean preview) {}
    public static class State extends BlockEntityRenderState {
        private final List<Slot> slots = new ArrayList<>();
        private boolean glowing;
        private BlockState modelState;
        private int dye;
    }
    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(T rack, State state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderState.extractBase(rack, state, overlay);
        state.slots.clear();
        state.glowing = rack.isGlowing();
        state.modelState = rack.getBlockState();
        state.dye = rack instanceof GunPegboardBlockEntity board ? board.getColor() : -1;
        var level = rack.getLevel();
        if (level == null) return;
        var mc = Minecraft.getInstance();
        var resolver = mc.getItemModelResolver();
        int preview = previewSlot(rack, mc.player, mc.hitResult);
        for (int i = 0; i < rack.slotCount(); ++i) {
            var stack = rack.getItem(i);
            if (i == preview) stack = mc.player.getMainHandItem().copyWithCount(1);
            else if (stack.isEmpty()) continue;
            var item = new ItemStackRenderState();
            resolver.updateForTopItem(item, stack, ItemDisplayContext.FIXED, level, null, i);
            state.slots.add(new Slot(i % DisplayRackBlockEntity.SLOTS_PER_FACE, rack.getSlotFacing(rack.getBlockState(), i), rack.getRotation(i), item, i == preview));
        }
    }

    /** Use the same hit-to-cell and item eligibility rules as placement, without changing the BE or hand. */
    public static int previewSlot(DisplayRackBlockEntity rack, Player player, HitResult target) {
        if (player == null || !player.isAlive() || !player.mayBuild() || player.isSpectator() || player.isShiftKeyDown()
                || !(target instanceof BlockHitResult hit) || target.getType() != HitResult.Type.BLOCK
                || !rack.getBlockPos().equals(hit.getBlockPos()) || !GunPegboardItemConfigRead.canPlace(player.getMainHandItem())) return -1;
        var state = rack.getBlockState();
        int slot;
        if (state.getBlock() instanceof GunDisplayCaseBlock) {
            if (player.getMainHandItem().is(state.getBlock().asItem()) || state.getValue(GunDisplayCaseBlock.LOCKED)) return -1;
            slot = GunDisplayCaseSlotHelper.getSlot(state, rack.getBlockPos(), hit.getLocation());
        } else {
            var held = player.getMainHandItem();
            if (held.is(Items.DEBUG_STICK) || held.getItem() instanceof GunPegboardItem) return -1;
            slot = GunPegboardSlotHelper.getSlot(state, rack.getBlockPos(), hit.getLocation());
        }
        return slot >= 0 && rack.getItem(slot).isEmpty() ? slot : -1;
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords;
        var flash = WorldFlashFrame.current();
        if (flash != null) RackSurfaceLight.submit(flash, state.modelState, state.blockPos, state.dye, camera.pos, pose, collector);
        Direction previous = null;
        for (var slot : state.slots) {
            if (slot.facing != previous) {
                light = slotLight(state, flash, slot.facing);
                previous = slot.facing;
            }
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180 - slot.facing.get2DDataValue() * 90));
            pose.translate((slot.index % 4 + 0.5) / 4 - 0.5, (slot.index / 4 + 0.5) / 4 - 0.5, 0.25);
            pose.mulPose(Axis.ZP.rotationDegrees(slot.rotation * 45));
            pose.scale(0.5f, 0.5f, 0.5f);
            slot.item.submit(pose, slot.preview ? RackPreviewCollector.wrap(collector) : collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    /** Deliberately glowing contents retain fullbright; only ordinary slots receive spill. */
    private static int slotLight(State state, WorldFlashFrame flash, Direction face) {
        return state.glowing ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : flash == null
                ? state.lightCoords : flash.rackSlotLight(state.lightCoords, state.blockPos, face);
    }

    @Override public int getViewDistance() { return RenderConfig.TARGET_RENDER_DISTANCE.get(); }
    @Override public boolean shouldRenderOffScreen() { return true; }
}
