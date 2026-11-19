package com.tacz.guns.client.render.light;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Sample exposed terrain throughout a small light volume, not six fixed-size decals below/on
 * axis with the source. Build only on admission. No chunk rebuilds, new lights or depth reads. */
final class SurfaceProjection {
    static final int MUZZLE_RAYS = 144, BLAST_RAYS = 384;
    private static final double AIR_GAP = .10;
    private static final Direction[] FACES = Direction.values();

    // Near-first so the cost of a solid, irregular room is bounded too. Distances between
    // two block centres may differ from point-to-corner distances by at most sqrt(3).
    private record Offset(int x, int y, int z, int distanceSq) {}
    private static final Offset[] OFFSETS = offsets();
    private static Offset[] offsets() {
        int n = (int)Math.ceil(SpillField.MAX_RADIUS + Math.sqrt(3));
        var values = new ArrayList<Offset>();
        for (int x=-n; x<=n; x++) for (int y=-n; y<=n; y++) for (int z=-n; z<=n; z++) {
            int ds = x*x + y*y + z*z;
            if (ds <= n*n) values.add(new Offset(x,y,z,ds));
        }
        values.sort(Comparator.comparingInt(Offset::distanceSq));
        return values.toArray(Offset[]::new);
    }

    private static BlockHitResult clip(ClientLevel world, Vec3 from, Vec3 to) {
        return world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
    }

    static Vec3 visibleOrigin(ClientLevel world, Vec3 eye, Vec3 muzzle) {
        var hit = clip(world, eye, muzzle);
        return hit.getType() == HitResult.Type.MISS ? muzzle : hit.getLocation().relative(hit.getDirection(), AIR_GAP);
    }

    /** Block impacts commonly lie exactly on a grid plane, even when floor(center) is AIR.
     * Give grazing rays an air-side origin. Don't search out of a wall or move toward the camera. */
    private static @Nullable Vec3 airSide(ClientLevel world, Vec3 center) {
        var cell = BlockPos.containing(center);
        if (world.getBlockState(cell).isSolidRender()) {
            Direction side = null;
            double nearest = .20;
            for (Direction face : FACES) {
                var outside = cell.relative(face);
                double d = Math.abs(center.get(face.getAxis()) - plane(cell,face));
                if (d <= nearest && world.getBlockState(outside).getCollisionShape(world,outside).isEmpty()) {
                    nearest = d; side = face;
                }
            }
            return side == null ? null : center.relative(side, nearest + AIR_GAP);
        }
        for (Direction face : FACES) {
            double d = Math.abs(center.get(face.getAxis()) - plane(cell,face));
            if (d < AIR_GAP && world.getBlockState(cell.relative(face)).isSolidRender())
                center = center.relative(face.getOpposite(), AIR_GAP - d);
        }
        return center;
    }

    static List<Patch> scatter(ClientLevel world, Vec3 origin, Vec3 bias, double radius, Vec3 camera, int rayBudget) {
        var source = airSide(world,origin);
        if (source == null) return List.of();
        var field = new SpillField(source,bias,radius);
        var center = field.boundingCenter();
        var base = BlockPos.containing(center);
        double limit = Math.pow(field.boundingRadius() + Math.sqrt(3), 2);
        var tile = new BlockPos.MutableBlockPos();
        var air = new BlockPos.MutableBlockPos();
        var result = new ArrayList<Patch>();
        int rays = 0;
        int cutoffSq = -1;
        scan: for (var offset : OFFSETS) {
            if (offset.distanceSq > limit) break;
            tile.set(base.getX()+offset.x, base.getY()+offset.y, base.getZ()+offset.z);
            var state=world.getBlockState(tile);
            if (!state.isSolidRender() || state.getRenderShape()!=RenderShape.MODEL) continue;
            for (var face : FACES) {
                var axis = face.getAxis();
                int sign = sign(face), plane = plane(tile,face);
                if ((source.get(axis)-plane)*sign <= 0 || (camera.get(axis)-plane)*sign <= 0) continue;
                air.setWithOffset(tile,face);
                if (world.getBlockState(air).isSolidRender()) continue;
                var target = Vec3.atCenterOf(tile).relative(face,.5);
                if (field.gain(target.x,target.y,target.z) < .006f) continue;
                if (rays++ == rayBudget) { cutoffSq=offset.distanceSq; break scan; }
                // Stop OUTSIDE the face. A ray aimed *inside* a coplanar floor at a shallow
                // angle hits the preceding tile instead, especially at a surface detonation.
                if (clip(world, source, target.relative(face,.012)).getType() != HitResult.Type.MISS) continue;
                float ambient = 1 - .65f * world.getMaxLocalRawBrightness(air) / 15f;
                int u = u(tile,axis), v = v(tile,axis);
                result.add(new Patch(face,plane,u,v, gain(field,axis,plane,u,v)*ambient,
                        gain(field,axis,plane,u+1,v)*ambient, gain(field,axis,plane,u+1,v+1)*ambient,
                        gain(field,axis,plane,u,v+1)*ambient));
            }
        }
        if (cutoffSq>=0) {
            // In a tight room the budget can end halfway through a bright ring of blocks.
            // A missing tile can start half a diagonal before its centre. Fade to zero by
            // that distance, rather than leave a staircase cut through the wall highlight.
            var gridCenter=Vec3.atCenterOf(base);
            double outer=Math.max(0,Math.sqrt(cutoffSq)-Math.sqrt(3)/2), inner=Math.max(0,outer-.85);
            result.replaceAll(p->p.taper(gridCenter,inner*inner,outer*outer));
        }
        return List.copyOf(result);
    }

    private static float gain(SpillField f, Direction.Axis a, int p, int u, int v) {
        return switch (a) { case X -> f.gain(p,u,v); case Y -> f.gain(u,p,v); case Z -> f.gain(u,v,p); };
    }
    private static int sign(Direction d) { return d.getStepX()+d.getStepY()+d.getStepZ(); }
    private static int plane(BlockPos pos,Direction d) { return pos.get(d.getAxis()) + (sign(d)>0 ? 1 : 0); }
    private static int u(BlockPos p,Direction.Axis a) { return a==Direction.Axis.X ? p.getY() : p.getX(); }
    private static int v(BlockPos p,Direction.Axis a) { return a==Direction.Axis.Z ? p.getY() : p.getZ(); }

    /** World coordinates stay integer/double until camera subtraction. Attenuation is shared
     * at the corners of adjacent tiles; no circular alpha mask is repeated per block. */
    record Patch(Direction face, int plane, int u, int v, float bl, float br, float tr, float tl) {
        Patch taper(Vec3 center,double innerSq,double outerSq) {
            var a=face.getAxis();
            double n=plane-center.get(a), x=u-(a==Direction.Axis.X?center.y:center.x), y=v-(a==Direction.Axis.Z?center.y:center.z);
            if (n*n+Math.max(x*x,(x+1)*(x+1))+Math.max(y*y,(y+1)*(y+1))<=innerSq) return this;
            return new Patch(face,plane,u,v,bl*edge(n,x,y,innerSq,outerSq),br*edge(n,x+1,y,innerSq,outerSq),
                    tr*edge(n,x+1,y+1,innerSq,outerSq),tl*edge(n,x,y+1,innerSq,outerSq));
        }
        private static float edge(double n,double u,double v,double inner,double outer) {
            return (float)Math.clamp((outer-(n*n+u*u+v*v))/Math.max(.01,outer-inner),0,1);
        }
        void draw(PoseStack.Pose pose, VertexConsumer out, Vec3 camera, int rgb, float strength,double depthBias) {
            if (bl+br+tr+tl<=0) return;
            var axis = face.getAxis();
            double n = plane - camera.get(axis);
            if (n * sign(face) >= 0) return;
            n += sign(face)*depthBias;
            double x = u - (axis==Direction.Axis.X ? camera.y : camera.x);
            double y = v - (axis==Direction.Axis.Z ? camera.y : camera.z);
            boolean flip = (axis == Direction.Axis.Y) == (sign(face) > 0);
            vertex(pose,out,n,x,y,bl*strength,rgb);
            if (flip) vertex(pose,out,n,x,y+1,tl*strength,rgb); else vertex(pose,out,n,x+1,y,br*strength,rgb);
            vertex(pose,out,n,x+1,y+1,tr*strength,rgb);
            if (flip) vertex(pose,out,n,x+1,y,br*strength,rgb); else vertex(pose,out,n,x,y+1,tl*strength,rgb);
        }
        private void vertex(PoseStack.Pose pose, VertexConsumer out, double n, double u, double v, float gain, int rgb) {
            var axis = face.getAxis();
            out.addVertex(pose.pose(), (float)(axis==Direction.Axis.X?n:u), (float)(axis==Direction.Axis.Y?n:axis==Direction.Axis.X?u:v), (float)(axis==Direction.Axis.Z?n:v))
                    .setColor(Math.round((rgb>>16&255)*gain),Math.round((rgb>>8&255)*gain),Math.round((rgb&255)*gain),255)
                    .setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose,face.getStepX(),face.getStepY(),face.getStepZ());
        }
    }
    private SurfaceProjection() {}
}
