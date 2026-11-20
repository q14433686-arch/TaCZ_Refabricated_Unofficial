package com.tacz.guns.client.render.light;

import com.tacz.guns.client.renderer.block.DisplayRackRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Only visible world submissions. Never intercept global light queries (inventory,
 * first-person hands, chunk cache, shadow/PIP replays or the server's light engine). */
public final class WorldFlashFrame {
    record Source(SpillField field, float strength, int rgb) {}
    private static @Nullable WorldFlashFrame active;
    private static final List<Runnable> RESTORE = new ArrayList<>();
    private static boolean registered;

    final ClientLevel level;
    final List<Source> sources;
    final List<SurfaceLights.Draw> draws;
    private int objectRays = 96, rackRays = 96, racks = 16;

    WorldFlashFrame(ClientLevel level, List<Source> sources, List<SurfaceLights.Draw> draws) {
        this.level = level; this.sources = sources; this.draws = draws;
    }

    public static @Nullable WorldFlashFrame current() {
        return active;
    }

    public static void register() {
        if (registered) return;
        registered = true;
        // On 26.1.2, AFTER_OPAQUE_TERRAIN fires immediately before submitEntities + submitBlockEntities,
        // COLLECT_SUBMITS fires right after them (where NativeMuzzleEffects calls SurfaceLights.submit),
        // and AFTER_SOLID_FEATURES / END_MAIN clean up and restore any mutated state fields.
        LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(context -> begin(context.levelState()));
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> end());
        LevelRenderEvents.END_MAIN.register(context -> end());
    }

    public static void begin(LevelRenderState state) {
        end();
        if (state == null || !state.cameraRenderState.initialized) return;
        var frame = SurfaceLights.prepare(state.cameraRenderState.pos);
        if (frame == null) return;
        active = frame;
        frame.applyLights(state, RESTORE);
    }

    public static void end() {
        active = null;
        if (!RESTORE.isEmpty()) {
            for (var undo : RESTORE) undo.run();
            RESTORE.clear();
        }
    }

    public static void submitWorld(LevelRenderState state, Runnable original) {
        begin(state);
        try {
            original.run();
        } finally {
            end();
        }
    }

    private void applyLights(LevelRenderState state, List<Runnable> restore) {
        for (EntityRenderState e : state.entityRenderStates) {
            int old = e.lightCoords;
            int lit = light(old, e.x, e.y + .55 * e.boundingBoxHeight, e.z, null, null);
            if (lit != old) { restore.add(() -> e.lightCoords = old); e.lightCoords = lit; }
        }
        for (BlockEntityRenderState b : state.blockEntityRenderStates) {
            if (b instanceof DisplayRackRenderer.State) continue; // each rack face has its own probe
            var pos = b.blockPos;
            int old = b.lightCoords;
            int lit = light(old, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, pos, null);
            if (lit != old) { restore.add(() -> b.lightCoords = old); b.lightCoords = lit; }
        }
    }

    /** Only raise the block column; ordinary equipment inherits this from the body. Existing
     * higher light, sky and deliberately fullbright layers remain authoritative. */
    int light(int ambient, double x, double y, double z, @Nullable BlockPos self, @Nullable Direction face) {
        int best = LightCoordsUtil.block(ambient), original = best;
        for (var s : sources) {
            if (best == 15 || objectRays == 0) break;
            float gain = s.field.gain(x, y, z) * s.strength;
            int value = Math.round(15 * (float) Math.sqrt(gain));
            if (value <= best || !facing(s, x, y, z, face)) continue;
            objectRays--;
            if (visible(s, new Vec3(x, y, z), self, face)) best = value;
        }
        return best == original ? ambient : LightCoordsUtil.withBlock(ambient, best);
    }

    public int rackSlotLight(int ambient, BlockPos pos, Direction facing) {
        var p = Vec3.atCenterOf(pos).relative(facing, -.25);
        return light(ambient, p.x, p.y, p.z, pos, facing);
    }

    boolean admitRack(BlockPos pos) {
        if (racks == 0 || rackRays == 0) return false;
        for (var s : sources) {
            double range = s.field.boundingRadius() + 1;
            if (s.field.boundingCenter().distanceToSqr(Vec3.atCenterOf(pos)) < range * range) { racks--; return true; }
        }
        return false;
    }

    int reflectedColor(Vec3 p, Direction normal, BlockPos self, float ambient) {
        float best = 0;
        int rgb = 0;
        for (var s : sources) {
            float gain = s.field.gain(p.x, p.y, p.z) * s.strength * ambient;
            if (gain <= Math.max(.006f, best) || !facing(s, p.x, p.y, p.z, normal) || rackRays == 0) continue;
            rackRays--;
            if (visible(s, p.relative(normal, .012), self, normal)) { best = gain; rgb = s.rgb; }
        }
        float diffuse = (float) Math.sqrt(best);
        return Math.round((rgb >> 16 & 255) * diffuse) << 16
                | Math.round((rgb >> 8 & 255) * diffuse) << 8
                | Math.round((rgb & 255) * diffuse);
    }

    private static boolean facing(Source s, double x, double y, double z, @Nullable Direction face) {
        if (face == null) return true;
        var o = s.field.source();
        return (o.x - x) * face.getStepX() + (o.y - y) * face.getStepY() + (o.z - z) * face.getStepZ() > 0;
    }

    private boolean visible(Source s, Vec3 target, @Nullable BlockPos self, @Nullable Direction face) {
        var hit = SurfaceProjection.clip(level, s.field.source(), target);
        return hit.getType() == HitResult.Type.MISS
                || self != null && self.equals(hit.getBlockPos()) && (face == null || hit.getDirection() == face);
    }
}
