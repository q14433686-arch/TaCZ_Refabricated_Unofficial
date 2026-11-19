package com.tacz.guns.client.render.light;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Project a radial footprint onto actual receivers. No chunk rebuild, background jobs,
 * depth-buffer readback, block mutation, or quads floating in air. Partial/non-opaque shapes
 * stop the ray but aren't painted as full cubes. Coordinates remain doubles until camera subtraction. */
final class SurfaceProjection {
    private static final double BIAS = .002;
    private static final Direction[] DIRECTIONS = Direction.values();

    record Patch(Direction face, double plane, double l, double b, double r, double t,
                 float u0, float v0, float u1, float v1, float gain) {
        void draw(PoseStack.Pose pose, VertexConsumer out, Vec3 camera, int rgb, float strength) {
            var axis = face.getAxis();
            if ((normal(camera, axis) - plane) * sign(face) <= 0) return;
            int a = Math.clamp(Math.round(255 * gain * strength), 0, 255);
            if (a == 0) return;
            int argb = a << 24 | (rgb & 0xffffff);
            double depth = plane - normal(camera, axis) + sign(face) * BIAS;
            double x = u(camera, axis), y = v(camera, axis);
            vertex(pose, out, face, depth, l-x, b-y, u0, v0, argb);
            vertex(pose, out, face, depth, r-x, b-y, u1, v0, argb);
            vertex(pose, out, face, depth, r-x, t-y, u1, v1, argb);
            vertex(pose, out, face, depth, l-x, t-y, u0, v1, argb);
        }
    }

    static BlockHitResult clip(ClientLevel world, Vec3 from, Vec3 to) {
        return world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
    }

    /** A viewmodel/long barrel must not put the source on the other side of a thin wall. */
    static Vec3 visibleOrigin(ClientLevel world, Vec3 eye, Vec3 muzzle) {
        var hit = clip(world, eye, muzzle);
        return hit.getType() == HitResult.Type.MISS ? muzzle : hit.getLocation().relative(hit.getDirection(), .015);
    }

    static @Nullable Patch laser(ClientLevel world, BlockHitResult hit, double size, float gain) {
        if (!receiver(world, hit)) return null;
        var face = hit.getDirection();
        return patch(hit.getBlockPos(), face, hit.getLocation(), size, gain);
    }

    /** Six primary rays, at most 3x3 coplanar receivers each. Reuse across all frames of
     * this pulse. Test visibility from the source to each extra patch, not from the camera. */
    static List<Patch> scatter(ClientLevel world, Vec3 center, double range) {
        var patches = new ArrayList<Patch>();
        // An explosive impact can be exactly on an integer face. Move it to the nearest
        // exposed side of that face, but never project out of a source buried in solid terrain.
        BlockPos cell = BlockPos.containing(center);
        if (world.getBlockState(cell).isSolidRender()) {
            double best = .03;
            Direction side = null;
            for (Direction dir : DIRECTIONS) {
                double dist = Math.abs(normal(center, dir.getAxis()) - plane(cell, dir));
                if (dist < best && world.getBlockState(cell.relative(dir)).isAir()) { best = dist; side = dir; }
            }
            if (side == null) return List.of();
            center = center.relative(side, best + .015);
        }
        for (Direction ray : DIRECTIONS) {
            BlockHitResult hit = clip(world, center, center.relative(ray, range));
            if (!receiver(world, hit)) continue;
            Direction face = hit.getDirection();
            if (face != ray.getOpposite()) continue;
            var pos = hit.getLocation();
            double d = center.distanceTo(pos) / range;
            double size = Math.min(1.45, range * .5) * Math.sqrt(Math.max(0, 1-d*d));
            if (size < .05) continue;
            float falloff = (float)(1 - d*d);
            for (int i=-1; i<=1; i++) for (int j=-1; j<=1; j++) {
                BlockPos tile = offset(hit.getBlockPos(), face.getAxis(), i, j);
                if (!world.getBlockState(tile).isSolidRender() || world.getBlockState(tile.relative(face)).isSolidRender()) continue;
                float ambient = world.getMaxLocalRawBrightness(tile.relative(face)) / 15f;
                var p = patch(tile, face, pos, size, falloff * (1 - .7f * ambient));
                if (p == null) continue;
                if (i != 0 || j != 0) {
                    Vec3 target = xyz(face.getAxis(), p.plane - sign(face)*.01, (p.l+p.r)/2, (p.b+p.t)/2);
                    var visible = clip(world, center, target);
                    if (!receiver(world, visible) || visible.getDirection() != face || !visible.getBlockPos().equals(tile)) continue;
                }
                patches.add(p);
            }
        }
        return List.copyOf(patches);
    }

    private static boolean receiver(ClientLevel world, BlockHitResult hit) {
        return hit.getType() == HitResult.Type.BLOCK && !hit.isInside() && world.getBlockState(hit.getBlockPos()).isSolidRender()
                && Math.abs(normal(hit.getLocation(), hit.getDirection().getAxis()) - plane(hit.getBlockPos(), hit.getDirection())) < .001;
    }
    private static @Nullable Patch patch(BlockPos tile, Direction face, Vec3 center, double radius, float gain) {
        var axis = face.getAxis();
        double x = u(center,axis), y = v(center,axis);
        var origin = Vec3.atLowerCornerOf(tile);
        double l = Math.max(x-radius,u(origin,axis)), r = Math.min(x+radius,u(origin,axis)+1);
        double b = Math.max(y-radius,v(origin,axis)), t = Math.min(y+radius,v(origin,axis)+1);
        if (l>=r || b>=t) return null;
        return new Patch(face, plane(tile,face), l,b,r,t, uv(l,x,radius),uv(b,y,radius),uv(r,x,radius),uv(t,y,radius),gain);
    }
    private static float uv(double p,double c,double radius) { return (float)(.5+(p-c)/(2*radius)); }
    private static int sign(Direction d) { return d.getStepX()+d.getStepY()+d.getStepZ(); }
    private static double plane(BlockPos p, Direction d) { return p.get(d.getAxis()) + (sign(d)>0 ? 1 : 0); }
    private static double normal(Vec3 p,Direction.Axis a) { return p.get(a); }
    private static double u(Vec3 p,Direction.Axis a) { return a==Direction.Axis.X?p.y:p.x; }
    private static double v(Vec3 p,Direction.Axis a) { return a==Direction.Axis.Z?p.y:p.z; }
    private static BlockPos offset(BlockPos p,Direction.Axis a,int i,int j) {
        return switch(a){ case X -> p.offset(0,i,j); case Y -> p.offset(i,0,j); case Z -> p.offset(i,j,0); };
    }
    private static Vec3 xyz(Direction.Axis a,double n,double u,double v) {
        return switch(a){ case X -> new Vec3(n,u,v); case Y -> new Vec3(u,n,v); case Z -> new Vec3(u,v,n); };
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Direction face, double n, double u, double v, float s, float t, int color) {
        var a = face.getAxis();
        out.addVertex(pose.pose(), (float)(a==Direction.Axis.X?n:u), (float)(a==Direction.Axis.Y?n:a==Direction.Axis.X?u:v), (float)(a==Direction.Axis.Z?n:v))
                .setColor(color).setUv(s,t).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880)
                .setNormal(pose,face.getStepX(),face.getStepY(),face.getStepZ());
    }
    private SurfaceProjection() {}
}
