package com.tacz.guns.client.event;

import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.GunDisplayCaseSlotHelper;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.entity.DisplayRackBlockEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.List;

/** Extract the targeted cell (or all occupied cells when crouching); don't read the BE during submission. */
public final class DisplayRackHighlight {
    private record Cell(AABB box, int color) {}
    private static final RenderStateDataKey<List<Cell>> CELLS = RenderStateDataKey.create(() -> "tacz display cells");

    public static void register() {
        LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register((context, target) -> {
            var outline = context.levelState().blockOutlineRenderState;
            if (outline == null || !(target instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
            var pos = hit.getBlockPos();
            if (!(context.level().getBlockEntity(pos) instanceof DisplayRackBlockEntity rack)) return;
            var state = rack.getBlockState();
            boolean cabinet = state.getBlock() instanceof GunDisplayCaseBlock;
            if (cabinet && state.getValue(GunDisplayCaseBlock.LOCKED)) return;
            var player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) return;
            int aimed = cabinet ? GunDisplayCaseSlotHelper.getSlot(state, pos, hit.getLocation()) : GunPegboardSlotHelper.getSlot(state, pos, hit.getLocation());
            var cells = new ArrayList<Cell>();
            for (int i = 0; i < rack.slotCount(); ++i) {
                boolean occupied = !rack.getItem(i).isEmpty();
                if (player.isShiftKeyDown() ? !occupied : i != aimed) continue;
                var box = cabinet ? GunDisplayCaseSlotHelper.getSlotAabb(state, BlockPos.ZERO, i) : GunPegboardSlotHelper.getSlotAabb(state, BlockPos.ZERO, i);
                cells.add(new Cell(box, occupied ? 0xffff4444 : 0xffffff00));
            }
            outline.setData(CELLS, List.copyOf(cells));
        });
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
            List<Cell> cells = outline.getData(CELLS);
            if (cells != null && !cells.isEmpty()) {
                var pose = context.poseStack();
                var camera = context.levelState().cameraRenderState.pos;
                var pos = outline.pos();
                pose.pushPose();
                pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                for (var cell : cells) {
                    var shape = Shapes.create(cell.box);
                    // A stored model can cover the very cell the player is selecting. Vanilla's
                    // secondary outline draws the depth-hidden part, the normal pass the visible part.
                    // Both are native line pipelines (also mapped by Iris); only the picked rack qualifies.
                    var out = context.submitNodeCollector();
                    out.submitShapeOutline(pose, shape, RenderTypes.secondaryBlockOutline(), cell.color, 1f, false);
                    out.submitShapeOutline(pose, shape, RenderTypes.lines(), cell.color, 1f, false);
                }
                pose.popPose();
            }
            return true;
        });
    }
}
