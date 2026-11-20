package com.tacz.guns.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.GunMod;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.block.entity.TargetBlockEntity;
import com.tacz.guns.client.renderer.block.TargetRenderer.TargetRenderState;
import com.tacz.guns.init.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Measure the motion in the deferred vertices of the shipped plate and skin, not just a field on the state. */
final class TargetPoseSmoke {
    static void run(Minecraft mc) {
        for (boolean rising : new boolean[]{false, true}) {
            var block = ModBlocks.TARGET.defaultBlockState().setValue(TargetBlock.FACING, Direction.SOUTH).setValue(TargetBlock.STAND, rising);
            var target = new TargetBlockEntity(BlockPos.ZERO, block);
            target.setOwner(net.minecraft.world.item.component.ResolvableProfile.createUnresolved(mc.player.getGameProfile().name()));
            target.rot = rising ? 90 : 0;
            TargetBlockEntity.clientTick(mc.level, BlockPos.ZERO, block, target);
            var dispatcher = mc.getBlockEntityRenderDispatcher();
            var renderer = dispatcher.<TargetBlockEntity, TargetRenderState>getRenderer(target);
            var camera = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
            List<Frame> frames = new ArrayList<>();
            for (float t : new float[]{0, .5f, 1}) {
                var state = renderer.createRenderState();
                renderer.extractRenderState(target, state, t, Vec3.ZERO, null);
                var frame = new Frame(mc.playerSkinRenderCache().getOrDefault(state.profile).renderType());
                renderer.submit(state, new PoseStack(), frame, camera);
                frames.add(frame);
            }
            // Submit an unnamed block using the same model *before* consuming any named draw.
            var unnamed = new TargetBlockEntity(BlockPos.ZERO, block);
            var state = renderer.createRenderState();
            renderer.extractRenderState(unnamed, state, 1, Vec3.ZERO, null);
            var plain = new Frame(frames.getFirst().skinMaterial);
            renderer.submit(state, new PoseStack(), plain, camera);
            frames.forEach(Frame::flush);
            plain.flush();
            if (!plain.skin.isEmpty() || plain.plate.size() != frames.getFirst().plate.size())
                throw new AssertionError("Skin geometry leaked between named and unnamed targets");
            double expected = rising ? -9 : 22.5; // 90 → 81 → 72 or 0 → 22.5 → 45
            checkArc(frames.get(0).plate, frames.get(1).plate, frames.get(2).plate, expected);
            checkArc(frames.get(0).skin, frames.get(1).skin, frames.get(2).skin, expected);
            GunMod.LOGGER.info("PORT_TARGET_POSE: {} half-steps of {} degrees on {} plate / {} skin vertices",
                    rising ? "rise" : "fall", expected, plain.plate.size(), frames.getFirst().skin.size());
        }
    }

    /** Facing south, the chords of two equal half-steps rotate in the YZ plane by the half-angle. */
    private static void checkArc(List<Vec3> a, List<Vec3> b, List<Vec3> c, double degrees) {
        if (a.isEmpty() || a.size() != b.size() || b.size() != c.size()) throw new AssertionError("Missing target geometry");
        int outer = 0;
        for (int i = 1; i < a.size(); i++)
            if (a.get(i).distanceToSqr(c.get(i)) > a.get(outer).distanceToSqr(c.get(outer))) outer = i;
        Vec3 first = b.get(outer).subtract(a.get(outer)), second = c.get(outer).subtract(b.get(outer));
        double turn = Math.toDegrees(Math.atan2(first.y * second.z - first.z * second.y, first.dot(second)));
        if (first.length() < .01 || Math.abs(first.length() - second.length()) > .0001 || Math.abs(turn - degrees) > .01)
            throw new AssertionError("Expected two equal " + degrees + "° steps; got " + turn + "°, lengths " + first.length() + "/" + second.length());
    }

    private static final class Frame extends SubmitNodeStorage {
        final RenderType skinMaterial;
        final List<Vec3> plate = new ArrayList<>(), skin = new ArrayList<>();
        final List<Runnable> draws = new ArrayList<>();
        Frame(RenderType skinMaterial) { this.skinMaterial = skinMaterial; }
        @Override public void submitCustomGeometry(PoseStack pose, RenderType type, CustomGeometryRenderer draw) {
            var saved = pose.last().copy();
            draws.add(() -> draw.render(saved, new Positions(type == skinMaterial ? skin : plate)));
        }
        void flush() { draws.forEach(Runnable::run); }
    }

    private record Positions(List<Vec3> vertices) implements VertexConsumer {
        public VertexConsumer addVertex(float x, float y, float z) { vertices.add(new Vec3(x,y,z)); return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setColor(int argb) { return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
        public VertexConsumer setLineWidth(float width) { return this; }
    }
}
