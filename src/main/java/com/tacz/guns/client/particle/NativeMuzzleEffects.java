package com.tacz.guns.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.client.model.GunModelConstant;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.particle.bedrock.particle.resource.ParticleDefinitionLoader;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import com.tacz.guns.client.model.IFunctionalSubmitter;
import com.tacz.guns.client.render.scope.ScopeBodyRenderTypes;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.time.GunTime;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;

/** Shot dispatcher: prefer the loaded Snowstorm definition; only the two shipped IDs have a native fallback.
 * Flash is anchored on every submit; smoke freezes the muzzle's world transform exactly once.
 * All mutable simulation runs on the client/render thread; queued geometry owns frozen matrices. */
public final class NativeMuzzleEffects {
    private static final RenderStateDataKey<Integer> OWNER = RenderStateDataKey.create(() -> "tacz muzzle owner");
    private static final Identifier FLASH = Identifier.parse("tacz:muzzle_flash");
    private static final Identifier SMOKE = Identifier.parse("tacz:muzzle_flash_smoke");
    private static final Identifier FLASH_TEXTURE = Identifier.parse("tacz:textures/particle/flash_strip.png");
    private static final Identifier SMOKE_TEXTURE = Identifier.parse("tacz:textures/particle/smoke_strip.png");
    private static final Map<Integer, Shot> SHOTS = new HashMap<>();
    private static final List<Puff> PUFFS = new ArrayList<>();
    private static final Set<Identifier> WARNED = new HashSet<>();
    private static int currentOwner = -1;
    private static long epoch = -1;
    private static Object level;
    private static long firstFlashSubmits, thirdFlashSubmits, smokeBursts;
    private static long runtimeFirstFlashes, runtimeThirdFlashes, runtimeSmokeBursts;

    private NativeMuzzleEffects() {}

    private static final class Shot {
        final MuzzleEffectParameters effect;
        final long born;
        final Identifier gun;
        final boolean firstPerson;
        final float offset;
        final float rotation = (float)(Math.random() * 120 - 60);
        final int frame = 4 + (int)(Math.random() * 14);
        long flashBorn = -1;
        boolean smoked;
        boolean customFlashed;
        Identifier displayId;
        Shot(MuzzleEffectParameters effect, Identifier gun, boolean firstPerson, float offset) {
            this.effect = effect; this.gun = gun; this.firstPerson = firstPerson; this.offset = offset;
            born = GunTime.nowMillis();
        }
    }
    private record Puff(Vec3 origin, Vec3 velocity, long born, float lifetime, float size, float rotation, int light, boolean firstPerson) {}
    public record Diagnostics(long firstFlashSubmits, long thirdFlashSubmits, long smokeBursts, int liveSmoke, int pendingShots, long runtimeFirstFlashes, long runtimeThirdFlashes, long runtimeSmokeBursts) {}
    public static Diagnostics diagnostics() { return new Diagnostics(firstFlashSubmits, thirdFlashSubmits, smokeBursts, PUFFS.size(), SHOTS.size(), runtimeFirstFlashes, runtimeThirdFlashes, runtimeSmokeBursts); }

    public static void register() {
        GunFirePostEvent.EVENT.register(NativeMuzzleEffects::onShot);
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            synchronizeSession();
            var camera = context.levelState().cameraRenderState;
            submitSmoke(context.submitNodeCollector(), camera.pos);
        });
    }
    public static void extracted(Entity entity, EntityRenderState state) {
        if (state != null) state.setData(OWNER, entity.getId());
    }
    public static int beginEntity(EntityRenderState state) {
        int old = currentOwner;
        Integer id = state.getData(OWNER);
        currentOwner = id == null ? -1 : id;
        return old;
    }
    public static int currentOwner(){return currentOwner;}
    public static void endEntity(int old) { currentOwner = old; }

    private static void synchronizeSession() {
        var mc = Minecraft.getInstance();
        if (epoch != GunTime.epoch() || level != mc.level) {
            clear(); epoch = GunTime.epoch(); level = mc.level;
        }
        long now = GunTime.nowMillis();
        SHOTS.values().removeIf(s -> now < s.born || now - s.born > 150);
        PUFFS.removeIf(p -> now < p.born || (now - p.born) / 1000f >= p.lifetime);
    }
    public static void clear() {
        com.tacz.guns.client.particle.AnimationParticleDispatcher.clear();
        SHOTS.clear(); PUFFS.clear(); WARNED.clear(); currentOwner = -1; level = null; epoch = -1;
    }

    private static void onShot(GunFirePostEvent event) {
        if (!event.getLogicalSide().isClient()) return;
        synchronizeSession();
        var mc = Minecraft.getInstance();
        var shooter = event.getShooter();
        ItemStack stack = event.getGunItemStack();
        var gun = IGun.getIGunOrNull(stack);
        if (gun == null || mc.level == null) return;
        var display = TimelessAPI.getGunDisplay(stack).orElse(null);
        if (display == null || display.getMuzzleFlash() == null || !display.getMuzzleFlash().isRenew()) return;
        var part = MuzzleAttachment.of(stack);
        var effect = MuzzleEffectParameters.resolve(display.getMuzzleFlash(), part.data(), part.silenced());
        boolean first = shooter == mc.player && mc.options.getCameraType().isFirstPerson();
        if (SHOTS.size() >= 128 && !SHOTS.containsKey(shooter.getId())) return;
        Shot shot = new Shot(effect, gun.getGunId(stack), first, part.offsetBb());
        shot.displayId = gun.getGunDisplayId(stack);
        SHOTS.put(shooter.getId(), shot);
    }

    /** True means renew owns this locator, even when no flash is active. */
    public static boolean extract(ItemStack stack, IFunctionalSubmitter.ExtractionContext context) {
        var display = TimelessAPI.getGunDisplay(stack).orElse(null);
        if (display == null || display.getMuzzleFlash() == null || !display.getMuzzleFlash().isRenew()) return false;
        synchronizeSession();
        var mc = Minecraft.getInstance();
        boolean first = context.displayContext() != null && context.displayContext().firstPerson();
        int id = first && mc.player != null ? mc.player.getId() : currentOwner;
        // Never draw inventory, ground, display-rack or back-mounted guns as a shooter's muzzle.
        if (!first && (context.displayContext() == null || !GunItemRendererWrapper.IS_MAIN_HAND_SUBMIT)) return true;
        Shot shot = SHOTS.get(id);
        var gun = IGun.getIGunOrNull(stack);
        if (shot == null || gun == null || shot.firstPerson != first || !shot.gun.equals(gun.getGunId(stack))
                || !shot.displayId.equals(gun.getGunDisplayId(stack))) return true;
        long age = GunTime.nowMillis() - shot.born;
        var effect = shot.effect;
        PoseStack pose = copy(context.poseStack());
        pose.translate(0, 0, -shot.offset / 16f);
        if (!shot.smoked) {
            shot.smoked = true; // Includes disabled/missing effects: don't burst when options change later.
            Identifier definition = first ? effect.firstSmoke() : effect.thirdSmoke();
            if (effect.smokeEnabled() && RenderConfig.RENDER_MUZZLE_SMOKE.get()
                    && !customEffect(definition, shot, pose, id, true) && supported(definition, SMOKE) && effect.smokeSpeed() > 0) {
                emitSmoke(shot, worldPose(pose, first), context.light(), first ? GunItemRendererWrapper.muzzleWorldScale() : 1f);
            }
        }
        Identifier definition = first ? effect.firstFlash() : effect.thirdFlash();
        if (!effect.hasFlash() || definition == null || RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.get()) return true;
        if (ParticleDefinitionLoader.getInstance().getDefinition(definition) != null) {
            if (!shot.customFlashed) {
                shot.customFlashed = true;
                customEffect(definition, shot, pose, id, false);
            }
            return true;
        }
        // Starting the 20ms particle at the first locator submit guarantees the instant
        // emitter has one frame even when event handling and extraction straddle a slow frame.
        if (shot.flashBorn < 0) shot.flashBorn = GunTime.nowMillis();
        long flashAge = first ? GunTime.nowMillis() - shot.flashBorn : age;
        long lifetime = first ? 20 : 150;
        if (age < 0 || flashAge > lifetime || !supported(definition, FLASH)) return true;
        float size = effect.flashScale() * .45f;
        if (!Float.isFinite(size) || size > 32) return true;
        pose.rotate(com.mojang.math.Axis.ZP.rotationDegrees(shot.rotation));
        boolean clipped = first && ScopeBodyRenderTypes.maskReadyForViewmodel(true);
        RenderType type = clipped ? ScopeBodyRenderTypes.flashSwirlClipped(FLASH_TEXTURE) : RenderTypes.energySwirl(FLASH_TEXTURE, 0, 0);
        float u = shot.frame / 18f;
        context.add(out -> quad(out, pose, type, size, size, u, u + 1f/18, 0xffffffff, 15728880));
        if (first) firstFlashSubmits++; else thirdFlashSubmits++;
        return true;
    }

    /** Native shipped profiles are reusable from animation keyframes without a synthetic gun shot. */
    public static boolean submitAnimationProfile(Identifier id, PoseStack pose, Matrix4f world, boolean first, int light, SubmitNodeCollector collector) {
        synchronizeSession();
        if (id.equals(SMOKE)) {
            if (RenderConfig.RENDER_MUZZLE_SMOKE.get()) {
                var parameters = new MuzzleEffectParameters(null, SMOKE, null, SMOKE, 0, 0, true, 1, 1, 15, null);
                emitSmoke(new Shot(parameters, id, first, 0), world, light, first ? GunItemRendererWrapper.muzzleWorldScale() : 1);
            }
            return true;
        }
        if (id.equals(FLASH)) {
            if (!RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.get()) {
                var type = first && ScopeBodyRenderTypes.maskReadyForViewmodel(true)
                        ? ScopeBodyRenderTypes.flashSwirlClipped(FLASH_TEXTURE) : RenderTypes.energySwirl(FLASH_TEXTURE, 0, 0);
                quad(collector, copy(pose), type, .45f, .45f, 4f/18, 5f/18, 0xffffffff, 15728880);
            }
            return true;
        }
        return false;
    }

    private static Matrix4f worldPose(PoseStack pose, boolean first) {
        if (first) return GunItemRendererWrapper.muzzleWorldPose(pose.last().pose());
        var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        return new Matrix4f(pose.last().pose()).translateLocal((float)camera.x, (float)camera.y, (float)camera.z);
    }

    private static boolean customEffect(Identifier id, Shot shot, PoseStack pose, int owner, boolean smoke) {
        var definition = ParticleDefinitionLoader.getInstance().getDefinition(id);
        if (definition == null) return false;
        var mc = Minecraft.getInstance();
        boolean first = shot.firstPerson;
        Matrix4f world = worldPose(pose, first);
        var runtime = WorldEmitterManager.getInstance();
        // The locator is spatial only. Multiplying it by a visual parameter changes offsets
        // and velocities, is lost on refresh, and never supplies v.muzzle_scale to the pack.
        var emitter = runtime.addEmitter(mc.level, world, Vec3.ZERO, definition, "", mc.level.getEntity(owner),
                smoke ? shot.effect::bindSmoke : shot.effect::bindFlash);
        if (emitter != null) {
            // World smoke (including custom sustained emitters) stays at the firing position.
            if (first || !smoke) runtime.bindAnimated(emitter, owner, shot.gun, GunModelConstant.MUZZLE_FLASH_ORIGIN_NODE,
                    first, pose.last().pose(), world, -shot.offset / 16f);
            emitter.setFollowAnchor(!first && !smoke);
            if (smoke) runtimeSmokeBursts++;
            else if (first) runtimeFirstFlashes++;
            else runtimeThirdFlashes++;
        }
        return true;
    }

    private static boolean supported(Identifier actual, Identifier profile) {
        if (actual == null) return false;
        if (actual.equals(profile)) return true;
        if (WARNED.add(actual)) GunMod.LOGGER.warn("Native muzzle profile {} is unsupported (expected {}); not substituting another effect", actual, profile);
        return false;
    }
    private static void emitSmoke(Shot shot, Matrix4f pose, int light, float worldScale) {
        var effect = shot.effect;
        float count = effect.smokeCount() == 0 ? 15 : effect.smokeCount();
        float size = effect.smokeSize() == 0 ? 1 : effect.smokeSize();
        // A zero attachment multiplier suppresses emission rather than reactivating definition defaults.
        if (size <= 0 || count <= 0 || !Float.isFinite(size) || !Float.isFinite(count)) return;
        int n = Math.min(64, (int)(count * (.25 + Math.random() * .75)));
        for (int i = 0; i < n && PUFFS.size() < 512; i++) {
            Vector3f direction = new Vector3f((float)Math.random()-.5f, (float)Math.random()-.5f, (float)Math.random()-.5f);
            var config = effect.smokeDirection();
            if (config != null) {
                float[] d = config.directions()[i % config.directions().length];
                if (config.angleMode()) {
                    Vector3f axis = new Vector3f(d[0], d[1], d[2]);
                    Vector3f tangent = Math.abs(axis.y) < .99f ? new Vector3f(0,1,0).cross(axis).normalize()
                            : new Vector3f(1,0,0).cross(axis).normalize();
                    Vector3f bitangent = new Vector3f(axis).cross(tangent).normalize();
                    double cos = 1 - Math.random() * (1-Math.cos(Math.toRadians(config.spread())));
                    double sin = Math.sqrt(Math.max(0, 1-cos*cos)), angle = Math.random()*2*Math.PI;
                    direction.set(axis).mul((float)cos).fma((float)(sin*Math.cos(angle)), tangent).fma((float)(sin*Math.sin(angle)), bitangent);
                } else direction.mul(2 * config.spread()).add(d[0], d[1], d[2]);
            }
            if (direction.lengthSquared() < .00001f) direction.set(0, 0, -1);
            direction.normalize();
            var origin = pose.transformPosition(new Vector3f(direction).mul(.12f).add(0,0,.12f));
            var velocity = pose.transformDirection(direction).normalize().mul(6 * effect.smokeSpeed() * worldScale);
            PUFFS.add(new Puff(new Vec3(origin.x,origin.y,origin.z), new Vec3(velocity.x,velocity.y,velocity.z),
                    GunTime.nowMillis(), .12f + (float)Math.random() * .13f,
                    size * worldScale * (.8f + (float)Math.random()*.4f), (float)Math.random()*360, light, shot.firstPerson));
        }
        smokeBursts++;
    }
    private static void submitSmoke(SubmitNodeCollector collector, Vec3 camera) {
        if (!RenderConfig.RENDER_MUZZLE_SMOKE.get()) return;
        long now = GunTime.nowMillis();
        var rotation = Minecraft.getInstance().gameRenderer.mainCamera().rotation();
        for (Puff puff : PUFFS) {
            float t = (now-puff.born)/1000f, life = t/puff.lifetime;
            if (life < 0 || life >= 1) continue;
            double distance = (1 - Math.exp(-1.5*t))/1.5;
            Vec3 pos = puff.origin.add(puff.velocity.scale(distance)).subtract(camera);
            PoseStack pose = new PoseStack();
            pose.translate(pos.x,pos.y,pos.z);
            pose.rotate(rotation);
            pose.rotate(com.mojang.math.Axis.ZP.rotationDegrees(puff.rotation));
            float size = puff.size * (.1f + .22f*life);
            int frame = Math.min(5, (int)(life*6));
            int alpha = (int)gradient(life, 128, 117, 96, 0);
            int grey = (int)gradient(life, 140, 128, 64, 0);
            int color = alpha<<24 | grey<<16 | grey<<8 | grey;
            var type=puff.firstPerson && com.tacz.guns.client.render.scope.ScopeParticleClipping.ready(false)
                    ? ScopeBodyRenderTypes.flashSwirlClipped(SMOKE_TEXTURE) : RenderTypes.energySwirl(SMOKE_TEXTURE, 0, 0);
            quad(collector, pose, type, size, size, frame/6f, (frame+1)/6f, color, puff.light);
        }
    }
    private static float gradient(float age, float a, float b, float c, float d) {
        if (age < .3f) return a + (b-a)*(age/.3f);
        if (age < .6f) return b + (c-b)*((age-.3f)/.3f);
        return c + (d-c)*Math.min(1,(age-.6f)/.3f);
    }
    public static int flashLight(ItemStack stack, int light) {
        var mc = Minecraft.getInstance();
        Shot shot = mc.player == null ? null : SHOTS.get(mc.player.getId());
        if (shot == null || !shot.firstPerson || !shot.effect.hasFlash() || shot.effect.firstFlash() == null
                || !GunTime.isWithin(shot.born, 50) || RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.get()) return light;
        var gun = IGun.getIGunOrNull(stack);
        if (gun == null || !shot.gun.equals(gun.getGunId(stack)) || !shot.displayId.equals(gun.getGunDisplayId(stack))) return light;
        return withFlashLight(light, shot.effect.flashLight());
    }

    static int withFlashLight(int packed, int desired) {
        int block = (packed >> 4) & 15;
        int raised = Math.max(block, Math.min(desired, Math.min(12, block + 10)));
        return (packed & ~0xf0) | raised << 4;
    }
    private static PoseStack copy(PoseStack original) {
        PoseStack p = new PoseStack(); p.last().pose().set(original.last().pose()); p.last().normal().set(original.last().normal()); return p;
    }
    private static void quad(SubmitNodeCollector out, PoseStack pose, RenderType type, float w, float h, float u0, float u1, int color, int light) {
        out.submitCustomGeometry(pose, type, (p,v) -> {
            vertex(p,v,-w,-h,u0,1,color,light); vertex(p,v,w,-h,u1,1,color,light);
            vertex(p,v,w,h,u1,0,color,light); vertex(p,v,-w,h,u0,0,color,light);
        });
    }
    private static void vertex(PoseStack.Pose p, VertexConsumer v, float x, float y, float u, float uvY, int color, int light) {
        v.addVertex(p.pose(),x,y,0).setColor(color).setUv(u,uvY).setOverlay(0).setLight(light).setNormal(p,0,0,1);
    }
}
