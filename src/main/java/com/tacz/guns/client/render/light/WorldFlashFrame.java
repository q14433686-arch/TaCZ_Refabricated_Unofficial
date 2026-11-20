package com.tacz.guns.client.render.light;

import com.tacz.guns.client.renderer.block.DisplayRackRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Only visible world submissions. Never intercept global light queries (inventory,
 * first-person hands, chunk cache, shadow/PIP replays or the server's light engine). */
public final class WorldFlashFrame {
    record Source(SpillField field, float strength, int rgb) {}
    private static final ThreadLocal<WorldFlashFrame> ACTIVE = new ThreadLocal<>();
    /** 本帧写入的 lightCoords 还原动作（{@link #beginWorld}/{@link #endWorld} 成对使用）。 */
    private static final ThreadLocal<List<Runnable>> RESTORE = new ThreadLocal<>();
    /** 嵌套窗口（例如光影窄遍）里被我们盖住的上一层帧。 */
    private static final ThreadLocal<WorldFlashFrame> PREVIOUS = new ThreadLocal<>();
    final ClientLevel level;
    final List<Source> sources;
    final List<SurfaceLights.Draw> draws;
    private int objectRays = 96, rackRays = 96, racks = 16;

    WorldFlashFrame(ClientLevel level, List<Source> sources, List<SurfaceLights.Draw> draws) {
        this.level = level; this.sources = sources; this.draws = draws;
    }
    public static @Nullable WorldFlashFrame current() { return ACTIVE.get(); }

    /**
     * 1.21.11 版提交窗口（替代 26.2 的 {@code LevelRenderer#submitFeatures} mixin）。
     *
     * <p>26.2 用 {@code @WrapMethod(LevelRenderer#submitFeatures)} 把"应用光照 → 原样提交 →
     * 还原"包成一个同步窗口。1.21.11 <b>没有</b> {@code submitFeatures}：实体的提交发生在
     * {@code addMainPass} 里的 lambda（{@code method_62214}）中，编译期名字是合成名，
     * 本分支明令禁止 mixin 挂合成名（AGENTS.md §3）。因此这里改用接收端自己的公开事件：
     * Fabric API 的 {@code WorldRenderEvents.BEFORE_ENTITIES}（主 pass 内、所有实体/方块实体
     * 提交<b>之前</b>）与 {@code AFTER_ENTITIES}（本批绘制完成之后）。</p>
     *
     * <p>时序上等价：光照写进 {@code EntityRenderState#lightCoords} 后，提交会把它抄进
     * {@code SubmitNodeStorage.ModelSubmit}，所以"提交后立刻还原"不影响本帧画出来的亮度
     * —— 26.2 的窗口语义同样只保证"提交那一刻生效"。</p>
     */
    public static void beginWorld(LevelRenderState state) {
        var frame = SurfaceLights.prepare(state.cameraRenderState.pos);
        var previous = ACTIVE.get();
        if (frame == null) {
            if (previous != null) ACTIVE.remove();
            RESTORE.remove();
            return;
        }
        if (previous != null) PREVIOUS.set(previous);
        ACTIVE.set(frame);
        var restore = new ArrayList<Runnable>();
        RESTORE.set(restore);
        // 二次渲染/光影窄遍会复用同一批提取状态，退出时逐条还原。
        frame.applyLights(state, restore);
    }

    public static void endWorld() {
        var restore = RESTORE.get();
        RESTORE.remove();
        if (restore != null) {
            for (var undo : restore) undo.run();
        }
        var previous = PREVIOUS.get();
        PREVIOUS.remove();
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
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
            int lit = light(old, pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, pos, null);
            if (lit != old) { restore.add(() -> b.lightCoords = old); b.lightCoords = lit; }
        }
    }

    /** Only raise the block column; ordinary equipment inherits this from the body. Existing
     * higher light, sky and deliberately fullbright layers remain authoritative. */
    int light(int ambient, double x, double y, double z, @Nullable BlockPos self, @Nullable Direction face) {
        int best = LightTexture.block(ambient), original = best;
        for (var s : sources) {
            if (best == 15 || objectRays == 0) break;
            float gain = s.field.gain(x,y,z) * s.strength;
            int value = Math.round(15 * (float)Math.sqrt(gain));
            if (value <= best || !facing(s, x,y,z, face)) continue;
            objectRays--;
            if (visible(s, new Vec3(x,y,z), self, face)) best = value;
        }
        return best == original ? ambient : LightTexture.pack(best, LightTexture.sky(ambient));
    }

    public int rackSlotLight(int ambient, BlockPos pos, Direction facing) {
        var p = Vec3.atCenterOf(pos).relative(facing, -.25);
        return light(ambient, p.x,p.y,p.z, pos, facing);
    }
    boolean admitRack(BlockPos pos) {
        if (racks == 0 || rackRays == 0) return false;
        for (var s : sources) {
            double range = s.field.boundingRadius()+1;
            if (s.field.boundingCenter().distanceToSqr(Vec3.atCenterOf(pos)) < range*range) { racks--; return true; }
        }
        return false;
    }
    int reflectedColor(Vec3 p, Direction normal, BlockPos self, float ambient) {
        float best = 0;
        int rgb = 0;
        for (var s : sources) {
            float gain = s.field.gain(p.x,p.y,p.z) * s.strength * ambient;
            if (gain <= Math.max(.006f,best) || !facing(s, p.x,p.y,p.z, normal) || rackRays == 0) continue;
            rackRays--;
            if (visible(s, p.relative(normal,.012), self, normal)) { best = gain; rgb = s.rgb; }
        }
        // The colour attribute modulates the rack's actual atlas texels. Convert the
        // linear gain to a display-space multiplier, like the object light column above;
        // using linear gain again here crushes dark albedo (notably the open case back).
        float diffuse = (float)Math.sqrt(best);
        return Math.round((rgb>>16 & 255)*diffuse)<<16 | Math.round((rgb>>8 & 255)*diffuse)<<8 | Math.round((rgb & 255)*diffuse);
    }
    private static boolean facing(Source s,double x,double y,double z,@Nullable Direction face) {
        if (face == null) return true;
        var o = s.field.source();
        return (o.x-x)*face.getStepX() + (o.y-y)*face.getStepY() + (o.z-z)*face.getStepZ() > 0;
    }
    private boolean visible(Source s, Vec3 target, @Nullable BlockPos self, @Nullable Direction face) {
        var hit = SurfaceProjection.clip(level, s.field.source(), target);
        // Collision hulls (e.g. locked cabinet, table) can be thicker than the mesh. A
        // different block is always a blocker, as is a rack's own opposite-facing wall.
        return hit.getType() == HitResult.Type.MISS || self != null && self.equals(hit.getBlockPos()) && (face == null || hit.getDirection()==face);
    }
}
