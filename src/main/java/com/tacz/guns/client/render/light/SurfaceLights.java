package com.tacz.guns.client.render.light;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.particle.NativeMuzzleEffects;
import com.tacz.guns.client.render.FlashMaterials;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.time.GunTime;
import me.xjqsh.lrtactical.entity.StunGrenadeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded per-observer eye candy, not a light engine. Spawn only on successful shots or
 * existing client detonation notifications; rendering a gun (or replaying a PIP) never fires it. */
public final class SurfaceLights {
    private static final int MAX_PULSES = 4, MAX_MUZZLES = 2, MAX_ANCHORS = 16;
    private static final double MUZZLE_DISTANCE_SQR = 24*24, BLAST_DISTANCE_SQR = 64*64;
    private static final int WARM = 0xffbd85, BLAST = 0xffcc90, WHITE = 0xf4f5ff;
    private static final List<Pulse> pulses = new ArrayList<>(MAX_PULSES);
    private static final Map<Integer, Anchor> anchors = new HashMap<>();
    private static @Nullable ClientLevel level;
    private static long buildTick;
    private static int builds, muzzleBuilds;
    private static long muzzleEvents, explosionEvents, stunEvents, projections, coincident;
    public record Diagnostics(int active, long muzzles, long explosions, long stuns, long projections, long coincident) {}
    public static Diagnostics diagnostics() {
        return new Diagnostics(pulses.size(), muzzleEvents, explosionEvents, stunEvents, projections, coincident);
    }

    private record Ray(Vec3 origin, Vec3 delta) {}
    private record Anchor(Identifier gun, Identifier display, boolean first, long frame, Ray ray) {}
    private record Draw(List<SurfaceProjection.Patch> patches, int rgb, float strength, double bias) {}
    private static final class Pulse {
        final Vec3 pos, eye, bias;
        final double range;
        final int rgb, hold, duration, owner;
        final float strength;
        final boolean local;
        final long born;
        List<SurfaceProjection.Patch> patches;
        long start, builtTick;
        boolean refreshCrater;
        Pulse(Vec3 pos, @Nullable Vec3 eye, Vec3 bias, double range, int rgb, float strength, int hold, int duration, int owner, boolean local) {
            this.pos=pos; this.eye=eye; this.bias=bias; this.range=range; this.hold=hold; this.rgb=rgb; this.strength=strength;
            this.duration=duration; this.owner=owner; this.local=local; born=GunTime.nowMillis();
        }
        float intensity(long now) {
            float t = Math.clamp((float)(now-start-hold)/(duration-hold), 0f, 1f);
            return strength * (1-t*t*(3-2*t));
        }
        boolean expired(long now) { return now < born || (patches == null ? now-born>200 : now-start>=duration); }
    }

    public static void clear() {
        level=null; pulses.clear(); anchors.clear(); buildTick=Long.MIN_VALUE; builds=muzzleBuilds=0;
    }
    private static boolean ready() {
        var current = Minecraft.getInstance().level;
        if (current != level) { clear(); level=current; }
        return level != null;
    }
    private static Vec3 camera() { return Minecraft.getInstance().gameRenderer.mainCamera().position(); }

    private static @Nullable LivingEntity holder(ItemDisplayContext ctx) {
        var mc = Minecraft.getInstance();
        if (ctx == null || mc.player == null || mc.gui.screen() != null || IrisCompat.isRenderShadow() || ScopePipRenderer.isInsideScopeLevelRender()) return null;
        if (ctx.firstPerson()) return mc.options.getCameraType().isFirstPerson() && MuzzleFlashRender.isSelf ? mc.player : null;
        if (ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            if (GunItemRendererWrapper.IS_MAIN_HAND_SUBMIT && level.getEntity(NativeMuzzleEffects.currentOwner()) instanceof LivingEntity entity
                    && entity.distanceToSqr(camera()) < MUZZLE_DISTANCE_SQR) return entity;
        }
        return null;
    }
    /** Snapshot relative to the actual hand/world basis, not the crosshair or the projectile origin. */
    private static @Nullable Ray ray(Matrix4f locator, boolean first) {
        var pose = GunItemRendererWrapper.surfaceLocator(locator, first);
        var origin = camera().add(new Vec3(pose.getTranslation(new Vector3f())));
        var delta = new Vec3(pose.transformDirection(new Vector3f(0,0,-1)));
        return origin.isFinite() && delta.isFinite() && delta.lengthSqr() > 1e-10 ? new Ray(origin,delta) : null;
    }
    public static void muzzleLocator(ItemStack stack, ItemDisplayContext ctx, Matrix4f locator) {
        if (!RenderConfig.MUZZLE_SURFACE_LIGHT.get() || !ready()) return;
        var holder = holder(ctx);
        var gun = IGun.getIGunOrNull(stack);
        if (holder == null || gun == null || IGun.getIGunOrNull(holder.getMainHandItem()) == null) return;
        int id = holder.getId(); long frame = ScopePipRenderer.frameNumber();
        if (anchors.size() >= MAX_ANCHORS && !anchors.containsKey(id)) {
            anchors.values().removeIf(a -> frame-a.frame>2);
            if (anchors.size() >= MAX_ANCHORS && holder != Minecraft.getInstance().player) return;
            if (anchors.size() >= MAX_ANCHORS) anchors.remove(anchors.keySet().iterator().next());
        }
        var ray = ray(locator, ctx.firstPerson());
        if (ray!=null) anchors.put(id,new Anchor(gun.getGunId(stack),gun.getGunDisplayId(stack),ctx.firstPerson(),frame,ray));
    }
    public static void shot(LivingEntity shooter, Identifier gun, Identifier display, boolean first, float offsetBb, int light) {
        if (light<=0 || !RenderConfig.MUZZLE_SURFACE_LIGHT.get() || !ready() || shooter.level()!=level || shooter.distanceToSqr(camera())>MUZZLE_DISTANCE_SQR) return;
        Vec3 eye = shooter.getEyePosition();
        var a = anchors.get(shooter.getId());
        Vec3 forward = shooter.getViewVector(1).normalize();
        Vec3 pos = eye.add(forward.scale(.65)).add(0,-.12,0);
        if (a!=null && a.first==first && a.gun.equals(gun) && a.display.equals(display) && ScopePipRenderer.frameNumber()-a.frame<=2) {
            var actual = a.ray.origin.add(a.ray.delta.scale(offsetBb/16f));
            if (actual.isFinite() && actual.distanceToSqr(eye)<16) {
                pos=actual;
                var barrel=a.ray.delta.normalize();
                // Decorative/rotated locator axes are not necessarily a barrel direction.
                if (barrel.dot(forward)>.7) forward=barrel;
            }
        }
        muzzleEvents++;
        float power=Math.clamp(light,0,15)/15f;
        offer(new Pulse(pos,eye,forward.scale(SpillField.MUZZLE_BIAS),2.6+1.6*power,
                WARM,.55f*power,28,105,shooter.getId(),shooter==Minecraft.getInstance().player));
    }

    /** Vanilla's authoritative explosion packet carries center/radius but no source ID. Only
     * standard explosion visuals qualify (not arbitrary explosion-looking particle children). */
    public static void explosion(ClientboundExplodePacket packet) {
        if (!RenderConfig.EXPLOSION_SURFACE_LIGHT.get() || !ready()) return;
        var kind=packet.explosionParticle().getType();
        if (kind!=ParticleTypes.EXPLOSION && kind!=ParticleTypes.EXPLOSION_EMITTER) return;
        if (!(packet.radius()>0) || !Float.isFinite(packet.radius()) || !nearBlast(packet.center())) return;
        explosionEvents++;
        var pulse=new Pulse(packet.center(),null,Vec3.ZERO,Math.clamp(4+packet.radius()*1.5,6,SpillField.MAX_RADIUS),
                BLAST,.90f,80,320,-1,false);
        pulse.refreshCrater=packet.blockCount()>0;
        offer(pulse);
    }
    /** LR already sends one FLASH burst at the stun entity, before removal. Never use a blind
     * status on the camera, a trail particle, or all 12 individual sprites as light sources. */
    public static void stun(ClientboundLevelParticlesPacket packet) {
        if (!RenderConfig.EXPLOSION_SURFACE_LIGHT.get() || packet.particle().getType()!=ParticleTypes.FLASH
                || packet.count()!=StunGrenadeEntity.FLASH_PARTICLES || !ready()) return;
        var pos=new Vec3(packet.x(),packet.y(),packet.z());
        if (!nearBlast(pos) || level.getEntitiesOfClass(StunGrenadeEntity.class,new AABB(pos,pos).inflate(1.5)).isEmpty()) return;
        stunEvents++;
        offer(new Pulse(pos,null,Vec3.ZERO,6,WHITE,.76f,45,175,-1,false));
    }
    private static boolean nearBlast(Vec3 pos) { return pos.isFinite() && pos.distanceToSqr(camera())<BLAST_DISTANCE_SQR; }
    /** Drawing the same broad muzzle footprint twice both washes it out and shades the
     * screen twice. Only coalesce tightly aligned muzzles in the SAME empty cells. Separate
     * shooters, opposite directions and occluding walls still get independent projections. */
    private static boolean coincidentMuzzles(Pulse a,Pulse b) {
        if (a.eye==null || b.eye==null || a.pos.distanceToSqr(b.pos)>=1 || a.eye.distanceToSqr(b.eye)>=1
                || a.bias.dot(b.bias)<.966*SpillField.MUZZLE_BIAS*SpillField.MUZZLE_BIAS) return false;
        var source=BlockPos.containing(a.pos); var eye=BlockPos.containing(a.eye);
        return source.equals(BlockPos.containing(b.pos)) && eye.equals(BlockPos.containing(b.eye))
                && level.getBlockState(source).isAir() && level.getBlockState(eye).isAir();
    }
    private static void offer(Pulse pulse) {
        long now=GunTime.nowMillis();
        pulses.removeIf(p -> p.expired(now) || (p.eye!=null && pulse.eye!=null && p.owner==pulse.owner));
        if (pulse.eye!=null) for (var it=pulses.iterator();it.hasNext();) {
            var p=it.next();
            if (!coincidentMuzzles(p,pulse)) continue;
            coincident++;
            if (!pulse.local && (p.local || (p.patches==null ? p.strength : p.intensity(now))>=pulse.strength)) return;
            it.remove(); // the newer/brighter or first-person source owns this footprint
            break;
        }
        // Gunfire is frequent and its near-wall footprints can fill the screen. Limit it
        // separately so four automatic weapons cannot also starve a rarer detonation.
        int liveMuzzles = 0;
        for (var p : pulses) if (p.eye != null) liveMuzzles++;
        boolean muzzleFull = pulse.eye != null && liveMuzzles >= MAX_MUZZLES;
        if (muzzleFull || pulses.size()>=MAX_PULSES) {
            Pulse farthest=null; double distance=pulse.local ? -1 : pulse.pos.distanceToSqr(camera());
            for (var p:pulses) if ((!muzzleFull || p.eye!=null) && !p.local && p.pos.distanceToSqr(camera())>distance) { farthest=p; distance=p.pos.distanceToSqr(camera()); }
            if (farthest==null) return;
            pulses.remove(farthest);
        }
        pulses.add(pulse);
    }

    public static void submit(SubmitNodeCollector out, Vec3 camera) {
        if (pulses.isEmpty()) return;
        if (!ready() || ScopePipRenderer.isInsideScopeLevelRender() || IrisCompat.isRenderShadow()) return;
        long now=GunTime.nowMillis(), tick=level.getGameTime();
        if (tick!=buildTick) { buildTick=tick; builds=muzzleBuilds=0; }
        pulses.removeIf(p -> p.expired(now) || !(p.eye==null ? RenderConfig.EXPLOSION_SURFACE_LIGHT.get()
                : RenderConfig.MUZZLE_SURFACE_LIGHT.get() && !RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.get()));
        if (pulses.isEmpty()) return;
        var draws=new ArrayList<Draw>(MAX_PULSES);
        var mc=Minecraft.getInstance();
        var state=mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        // Terrain and emissive entities can have different subpixel jitter under Iris. A
        // .002-block normal offset disappears behind a jittered floor at grazing incidence.
        // Bound the clearance in BOTH screen pixels and blocks; never disable depth testing.
        double pixelSlope=IrisCompat.hasActiveIrisPack() ? 2.0/(Math.max(1,mc.getWindow().getHeight())*Math.max(.1,Math.abs(state.projectionMatrix.m11()))) : 0;
        for (var p:pulses) {
            boolean firstBuild=p.patches==null;
            if ((firstBuild || (p.refreshCrater && tick>p.builtTick)) && builds<MAX_PULSES && (p.eye==null || muzzleBuilds<MAX_MUZZLES)) {
                if (p.eye!=null) muzzleBuilds++;
                builds++; projections++;
                var origin=p.eye==null?p.pos:SurfaceProjection.visibleOrigin(level,p.eye,p.pos);
                p.patches=SurfaceProjection.scatter(level,origin,p.bias,p.range,camera,
                        p.eye==null ? SurfaceProjection.BLAST_RAYS : SurfaceProjection.MUZZLE_RAYS);
                p.builtTick=tick;
                if (firstBuild) p.start=now; else p.refreshCrater=false; // never restart the envelope
            }
            if (p.patches!=null && !p.patches.isEmpty()) {
                double far=p.pos.distanceTo(camera)+p.range/(p.eye==null ? 1 : 1-SpillField.MUZZLE_BIAS);
                double bias=.002+Math.min(.09,.85*far*pixelSlope);
                draws.add(new Draw(p.patches,p.rgb,p.intensity(now),bias));
            }
        }
        if (draws.isEmpty()) return;
        out.submitCustomGeometry(new PoseStack(),FlashMaterials.surface(),(pose,vertices) -> {
            for (var draw:draws) for (var patch:draw.patches) patch.draw(pose,vertices,camera,draw.rgb,draw.strength,draw.bias);
        });
    }
    private SurfaceLights() {}
}
