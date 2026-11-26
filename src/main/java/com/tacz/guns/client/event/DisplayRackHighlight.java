package com.tacz.guns.client.event;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.GunDisplayCaseSlotHelper;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.entity.DisplayRackBlockEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
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
        // Register before ShaderManager reload. This material draws in vanilla's outline batch,
        // never the QUADS-only custom geometry pass. It must not alter scene depth.
        var pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                .withLocation(Identifier.parse("tacz:pipeline/rack_cell_outline"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withoutBlend().build());
        com.tacz.guns.compat.iris.IrisCompat.assignPipelineToIris(pipeline, "LINES", "rack_cell_outline");
        var lines = RenderType.create("tacz_rack_cell_outline", RenderSetup.builder(pipeline).createRenderSetup());
        WorldRenderEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register((context, target) -> {
            var outline = context.worldState().blockOutlineRenderState;
            if (outline == null || !(target instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
            var pos = hit.getBlockPos();
            if (!(context.world().getBlockEntity(pos) instanceof DisplayRackBlockEntity rack)) return;
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
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
            List<Cell> cells = outline.getData(CELLS);
            if (cells != null && !cells.isEmpty()) {
                var camera = context.worldState().cameraRenderState.pos;
                var pos = outline.pos();
                // The block-outline pass runs *after* solid/translucent features.
                // Submitting to their collector here strands the lines until a different pass.
                // Write into vanilla's current outline pass; its caller owns the batch flush.
                var vertices = context.consumers().getBuffer(lines);
                for (var cell : cells) {
                    ShapeRenderer.renderShape(context.matrices(), vertices, Shapes.create(cell.box),
                            pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z, cell.color, 1f);
                }
            }
            return true;
        });
    }
}
