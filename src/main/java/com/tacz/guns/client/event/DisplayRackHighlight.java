package com.tacz.guns.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.GunDisplayCaseSlotHelper;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.entity.DisplayRackBlockEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
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

    private static class Material {
        static final RenderType LINES = create();
        private static RenderType create() {
            var pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.parse("tacz:pipeline/rack_cell_outline"))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withColorTargetState(ColorTargetState.DEFAULT).build());
            com.tacz.guns.compat.iris.IrisCompat.assignRackOutlinePipeline(pipeline);
            return RenderType.create("tacz_rack_cell_outline", RenderSetup.builder(pipeline).createRenderSetup());
        }
    }

    public static void register() {
        LevelRenderEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register((context, target) -> {
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
                // The picked rack is already visibility/range checked by vanilla's block pick.
                // Do not let a stored item's depth hide the edges of its own selected cell.
                var out = context.submitNodeCollector().order(1);
                for (var cell : cells) {
                    var shape = Shapes.create(cell.box);
                    int color = cell.color;
                    out.submitCustomGeometry(pose, Material.LINES, (entryPose, consumer) ->
                            shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> emitLine(entryPose, consumer, x1, y1, z1, x2, y2, z2, color, 1.0f)));
                }
                pose.popPose();
            }
            return true;
        });
    }

    private static void emitLine(PoseStack.Pose pose, VertexConsumer consumer,
                                 double x1, double y1, double z1, double x2, double y2, double z2,
                                 int color, float lineWidth) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0e-6f) {
            dx /= len;
            dy /= len;
            dz /= len;
        }
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
    }
}
