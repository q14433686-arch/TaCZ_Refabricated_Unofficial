package com.tacz.guns.client.event;

import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.GunDisplayCaseSlotHelper;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.entity.DisplayRackBlockEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.tacz.guns.client.render.scope.ScopeRenderTypes;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShapeRenderer;
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
            // 26.2: RenderPipeline.builder(LINES_SNIPPET) + DepthStencilState(ALWAYS_PASS, 不写深度)
            //      + ColorTargetState.DEFAULT，然后注册给 Iris 的 LINES 程序。
            // 1.21.11：LINES_SNIPPET 是私有的，等价底子是 RenderPipelines.LINES_TRANSLUCENT
            //      （LINES_SNIPPET + TRANSLUCENT 混合 + depthWrite=false）。这里只把深度测试改成
            //      NO_DEPTH_TEST —— 1.21.11 没有 ALWAYS_PASS，NO_DEPTH_TEST 就是"无条件通过"，
            //      且继承来的 depthWrite=false 保证不污染世界深度。
            var pipeline = ScopeRenderTypes.clonePipeline(RenderPipelines.LINES_TRANSLUCENT,
                            Identifier.parse("tacz:pipeline/rack_cell_outline"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build();
            pipeline = RenderPipelines.register(pipeline);
            com.tacz.guns.compat.iris.IrisCompat.assignPipelineToIris(pipeline, "LINES", "rack_cell_outline");
            return RenderType.create("tacz_rack_cell_outline", RenderSetup.builder(pipeline).createRenderSetup());
        }
    }

    public static void register() {
        // 26.2 的 LevelExtractionEvents/LevelRenderEvents 在 1.21.11 的 Fabric API 里叫
        // WorldRenderEvents（同名事件、同一阶段），上下文访问器名也不同：
        // levelState() → worldState()、level() → world()、poseStack() → matrices()、
        // submitNodeCollector() → commandQueue()。
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
                var pose = context.matrices();
                var camera = context.worldState().cameraRenderState.pos;
                var pos = outline.pos();
                pose.pushPose();
                pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                // The picked rack is already visibility/range checked by vanilla's block pick.
                // Do not let a stored item's depth hide the edges of its own selected cell.
                // 26.2: OrderedSubmitNodeCollector#submitShapeOutline(pose, shape, Material.LINES, color, 1f, false)。
                // 1.21.11 没有形状提交节点，只有 submitCustomGeometry；等价做法是在几何回调里直接用
                // ShapeRenderer.renderShape（LevelRenderer 画方块描边用的就是它），线宽同为 1。
                // 注意：回调延迟到 feature 阶段才执行，所以每个格子的 PoseStack 必须各自独立，
                // 不能复用外层那个会被 popPose 掉的实例。
                var out = context.commandQueue().order(1);
                for (var cell : cells) {
                    var shape = Shapes.create(cell.box);
                    var cellPose = new PoseStack();
                    cellPose.last().set(pose.last());
                    out.submitCustomGeometry(cellPose, Material.LINES, (entryPose, consumer) ->
                            ShapeRenderer.renderShape(entryPose, consumer, shape, 0.0D, 0.0D, 0.0D, cell.color, 1.0F));
                }
                pose.popPose();
            }
            return true;
        });
    }
}
