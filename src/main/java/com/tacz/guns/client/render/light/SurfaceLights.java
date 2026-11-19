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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
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
    private static final int MAX_PULSES = 4, MAX_BEAMS = 4, MAX_ANCHORS = 16;
    private static final double NEAR_SQR = 24*24;
    private static final int WARM = 0xffbd85, BLAST = 0xffcc90, WHITE = 0xf4f5ff;
    private static final List<Pulse> pulses = new ArrayList<>(MAX_PULSES);
    private static final Map<Integer, Anchor> anchors = new HashMap<>();
    private static final Map<Integer, Beam> beams = new HashMap<>();
    private static @Nullable ClientLevel level;
    private static long buildTick, laserFrame;
    private static int builds;
    private static List<Draw> dots = List.of();
    private static long muzzleEvents, explosionEvents, stunEvents, projections;
    public record Diagnostics(int active, int beamHits, long muzzles, long explosions, long stuns, long projections) {}
    public static Diagnostics diagnostics() {
        return new Diagnostics(pulses.size(), dots.size(), muzzleEvents, explosionEvents, stunEvents, projections);
    }

    private record Ray(Vec3 origin, Vec3 delta) {}
    private record Anchor(Identifier gun, Identifier display, boolean first, long frame, Ray ray) {}
    private record Beam(long frame, LivingEntity holder, boolean first, ItemStack gun, Ray ray, int color, boolean fade) {}
    private record Draw(List<SurfaceProjection.Patch> patches, int rgb, float strength) {}
    private static final class Pulse {
        final Vec3 pos, eye;
        final double range;
        final int rgb, duration, owner;
        final float strength;
        final boolean local;
        final long born;
        List<SurfaceProjection.Patch> patches;
        long start;
        Pulse(Vec3 pos, @Nullable Vec3 eye, double range, int rgb, float strength, int duration, int owner, boolean local) {
            this.pos=pos; this.eye=eye; this.range=range; this.rgb=rgb; this.strength=strength;
            this.duration=duration; this.owner=owner; this.local=local; born=GunTime.nowMillis();
        }
        boolean expired(long now) { return now < born || (patches == null ? now-born>200 : now-start>=duration); }
    }

    public static void clear() {
        level=null; pulses.clear(); anchors.clear(); beams.clear(); dots=List.of(); laserFrame=buildTick=Long.MIN_VALUE; builds=0;
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
                    && entity.distanceToSqr(camera()) < NEAR_SQR) return entity;
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
        if (light<=0 || !RenderConfig.MUZZLE_SURFACE_LIGHT.get() || !ready() || shooter.level()!=level || shooter.distanceToSqr(camera())>NEAR_SQR) return;
        Vec3 eye = shooter.getEyePosition();
        var a = anchors.get(shooter.getId());
        Vec3 pos = eye.add(shooter.getViewVector(1).scale(.65)).add(0,-.12,0);
        if (a!=null && a.first==first && a.gun.equals(gun) && a.display.equals(display) && ScopePipRenderer.frameNumber()-a.frame<=2) {
            var actual = a.ray.origin.add(a.ray.delta.scale(offsetBb/16f));
            if (actual.isFinite() && actual.distanceToSqr(eye)<16) pos=actual;
        }
        muzzleEvents++;
        offer(new Pulse(pos,eye,1+2.25*light/15.,WARM,.48f*light/15,50,shooter.getId(),shooter==Minecraft.getInstance().player));
    }
    public static void laserLocator(ItemDisplayContext ctx, Matrix4f locator, float length, float width, int color, boolean fade) {
        if (!RenderConfig.LASER_IMPACT_SPOT.get() || !(length>0 && width>0) || !Float.isFinite(length+width) || !ready()) return;
        var holder = holder(ctx);
        if (holder == null || IGun.getIGunOrNull(holder.getMainHandItem()) == null) return;
        int id = holder.getId(); long frame=ScopePipRenderer.frameNumber();
        if (beams.size()>=MAX_BEAMS && !beams.containsKey(id)) {
            beams.values().removeIf(b -> frame-b.frame>1);
            if (beams.size()>=MAX_BEAMS) {
                Beam farthest=null; double distance=holder==Minecraft.getInstance().player ? -1 : holder.distanceToSqr(camera());
                for (Beam b:beams.values()) if (b.holder != Minecraft.getInstance().player && b.holder.distanceToSqr(camera())>distance) {
                    farthest=b; distance=b.holder.distanceToSqr(camera());
                }
                if (farthest==null) return;
                beams.remove(farthest.holder.getId());
            }
        }
        var ray=ray(locator,ctx.firstPerson());
        if (ray!=null && ray.origin.distanceToSqr(holder.getEyePosition())<16) {
            beams.put(id,new Beam(frame,holder,ctx.firstPerson(),holder.getMainHandItem(),new Ray(ray.origin,ray.delta.scale(length)),color,fade));
        }
    }

    /** Vanilla's authoritative explosion packet carries center/radius but no source ID. Only
     * standard explosion visuals qualify (not arbitrary explosion-looking particle children). */
    public static void explosion(ClientboundExplodePacket packet) {
        if (!RenderConfig.EXPLOSION_SURFACE_LIGHT.get() || !ready()) return;
        var kind=packet.explosionParticle().getType();
        if (kind!=ParticleTypes.EXPLOSION && kind!=ParticleTypes.EXPLOSION_EMITTER) return;
        if (!(packet.radius()>0) || !Float.isFinite(packet.radius()) || !near(packet.center())) return;
        explosionEvents++;
        offer(new Pulse(packet.center(),null,Math.clamp(packet.radius()*1.5,2,6),BLAST,.65f,130,-1,false));
    }
    /** LR already sends one FLASH burst at the stun entity, before removal. Never use a blind
     * status on the camera, a trail particle, or all 12 individual sprites as light sources. */
    public static void stun(ClientboundLevelParticlesPacket packet) {
        if (!RenderConfig.EXPLOSION_SURFACE_LIGHT.get() || packet.particle().getType()!=ParticleTypes.FLASH
                || packet.count()!=StunGrenadeEntity.FLASH_PARTICLES || !ready()) return;
        var pos=new Vec3(packet.x(),packet.y(),packet.z());
        if (!near(pos) || level.getEntitiesOfClass(StunGrenadeEntity.class,new AABB(pos,pos).inflate(1.5)).isEmpty()) return;
        stunEvents++;
        offer(new Pulse(pos,null,4,WHITE,.6f,100,-1,false));
    }
    private static boolean near(Vec3 pos) { return pos.isFinite() && pos.distanceToSqr(camera())<NEAR_SQR; }
    private static void offer(Pulse pulse) {
        long now=GunTime.nowMillis();
        pulses.removeIf(p -> p.expired(now) || (p.owner>=0 && p.owner==pulse.owner));
        if (pulses.size()>=MAX_PULSES) {
            Pulse farthest=null; double distance=pulse.local ? -1 : pulse.pos.distanceToSqr(camera());
            for (var p:pulses) if (!p.local && p.pos.distanceToSqr(camera())>distance) { farthest=p; distance=p.pos.distanceToSqr(camera()); }
            if (farthest==null) return;
            pulses.remove(farthest);
        }
        pulses.add(pulse);
    }

    public static void submit(SubmitNodeCollector out, Vec3 camera) {
        if (pulses.isEmpty() && beams.isEmpty()) return;
        if (!ready() || ScopePipRenderer.isInsideScopeLevelRender() || IrisCompat.isRenderShadow()) return;
        long now=GunTime.nowMillis(), tick=level.getGameTime(), frame=ScopePipRenderer.frameNumber();
        if (tick!=buildTick) { buildTick=tick; builds=0; }
        pulses.removeIf(p -> p.expired(now) || !(p.eye==null ? RenderConfig.EXPLOSION_SURFACE_LIGHT.get()
                : RenderConfig.MUZZLE_SURFACE_LIGHT.get() && !RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.get()));
        var draws=new ArrayList<Draw>(MAX_PULSES+MAX_BEAMS);
        for (var p:pulses) {
            if (p.patches==null) {
                if (builds>=MAX_PULSES) continue;
                builds++; projections++;
                var origin=p.eye==null?p.pos:SurfaceProjection.visibleOrigin(level,p.eye,p.pos);
                p.patches=SurfaceProjection.scatter(level,origin,p.range); p.start=now;
            }
            if (!p.patches.isEmpty()) draws.add(new Draw(p.patches,p.rgb,p.strength*(1-(float)(now-p.start)/p.duration)));
        }
        if (!RenderConfig.LASER_IMPACT_SPOT.get()) { beams.clear(); dots=List.of(); }
        else if (laserFrame!=frame) {
            laserFrame=frame;
            beams.values().removeIf(b -> frame-b.frame>1 || b.holder.isRemoved() || b.holder.getMainHandItem()!=b.gun
                    || (b.holder==Minecraft.getInstance().player && b.first!=Minecraft.getInstance().options.getCameraType().isFirstPerson()));
            var hits=new ArrayList<Draw>(beams.size());
            for (var b:beams.values()) {
                double length=b.ray.delta.length();
                if (!(length>0) || !Double.isFinite(length)) continue;
                var end=b.ray.origin.add(b.ray.delta.scale(Math.min(1,32/length)));
                var hit=SurfaceProjection.clip(level,b.holder.getEyePosition(),b.ray.origin);
                if (hit.getType()==HitResult.Type.MISS) hit=SurfaceProjection.clip(level,b.ray.origin,end);
                if (hit.getType()!=HitResult.Type.BLOCK) continue;
                float attenuation=b.fade?(float)Math.max(0,1-b.ray.origin.distanceTo(hit.getLocation())/length):1;
                var patch=SurfaceProjection.laser(level,hit,.065,.95f*attenuation);
                if (patch!=null) hits.add(new Draw(List.of(patch),b.color,1));
            }
            dots=List.copyOf(hits);
        }
        draws.addAll(dots);
        if (draws.isEmpty()) return;
        out.submitCustomGeometry(new PoseStack(),FlashMaterials.surface(),(pose,vertices) -> {
            for (var draw:draws) for (var patch:draw.patches) patch.draw(pose,vertices,camera,draw.rgb,draw.strength);
        });
    }
    private SurfaceLights() {}
}
