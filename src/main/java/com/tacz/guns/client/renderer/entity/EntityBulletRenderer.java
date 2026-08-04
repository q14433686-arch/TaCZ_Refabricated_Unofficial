package com.tacz.guns.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class EntityBulletRenderer extends EntityRenderer<EntityKineticBullet, EntityBulletRenderer.BulletRenderState> {
    private static final Map<Integer, Integer> TRACER_DEBUG_LAST_TICK = new HashMap<>();
    private static long tracerDebugLastIntervalLogTime = 0L;

    public static class BulletRenderState extends EntityRenderState {
        public EntityKineticBullet bullet;
        public float partialTicks;
    }

    public EntityBulletRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    public BulletRenderState createRenderState() {
        return new BulletRenderState();
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.DEFAULT_BULLET_MODEL);
    }

    @Override
    public void extractRenderState(EntityKineticBullet bullet, BulletRenderState state, float partialTicks) {
        super.extractRenderState(bullet, state, partialTicks);
        state.bullet = bullet;
        state.partialTicks = partialTicks;
    }

    @Override
    public void submit(BulletRenderState state, PoseStack poseStack, SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {
        EntityKineticBullet bullet = state.bullet;
        if (bullet == null) return;
        float partialTicks = state.partialTicks;
        
        Identifier gunId = bullet.getGunId();
        Identifier gunDisplayId = bullet.getGunDisplayId();
        Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(gunDisplayId, gunId);
        if (display.isEmpty()) {
            return;
        }
        float @Nullable [] tracerColor = bullet.getTracerColorOverride().orElse(display.get().getTracerColor());
        Identifier ammoId = bullet.getAmmoId();
        TimelessAPI.getClientAmmoIndex(ammoId).ifPresent(ammoIndex -> {
            BedrockAmmoModel ammoEntityModel = ammoIndex.getAmmoEntityModel();
            Identifier textureLocation = ammoIndex.getAmmoEntityTextureLocation();
            if (ammoEntityModel != null && textureLocation != null) {
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, bullet.yRotO, bullet.getYRot()) - 180.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTicks, bullet.xRotO, bullet.getXRot())));
                poseStack.pushPose();
                poseStack.translate(0, 1.5, 0);
                poseStack.scale(-1, -1, 1);
                ammoEntityModel.submit(poseStack, ItemDisplayContext.GROUND, collector, getRenderType(textureLocation), state.lightCoords, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }

            // 曳光弹发光
            if (bullet.isTracerAmmo()) {
                float[] actualTracerColor = Objects.requireNonNullElse(tracerColor, ammoIndex.getTracerColor());
                renderTracerAmmo(bullet, actualTracerColor, partialTicks, poseStack, collector, state.lightCoords);
            }
        });
    }
    
    private RenderType getRenderType(Identifier textureLocation) {
        // 由于entityTranslucentCull不可用，使用entityTranslucent作为替代
        return RenderTypes.entityTranslucent(textureLocation);
    }

    public void renderTracerAmmo(EntityKineticBullet bullet, float[] tracerColor, float partialTicks,
                                 PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        getModel().ifPresent(model -> {
            Entity shooter = bullet.getOwner();
            if (shooter == null) {
                return;
            }
            boolean isFirstPerson = this.entityRenderDispatcher.options.getCameraType().isFirstPerson()
                    && shooter instanceof LocalPlayer;
            if (isFirstPerson && !RenderConfig.FIRST_PERSON_BULLET_TRACER_ENABLE.get()) {
                return;
            }

            Vec3 bulletPosition = bullet.getPosition(partialTicks);
            Vec3 eyePosition = shooter.getEyePosition(partialTicks);
            Vec3 deltaMovement = bullet.getDeltaMovement();
            double speed = deltaMovement.length();
            if (speed < 1.0E-6) {
                return;
            }

            double normalTrailLength = 0.85 * speed;
            Vec3 segmentDirection = deltaMovement.normalize();
            double trailLength = Math.min(normalTrailLength,
                    bulletPosition.distanceTo(eyePosition) * 0.8);
            Vec3 segmentTail = bulletPosition.subtract(segmentDirection.scale(trailLength));
            Vec3 muzzlePosition = null;

            if (isFirstPerson) {
                Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
                // The vanilla and Iris pipelines submit the hand at different points in the frame.
                // muzzleRenderOffset is deliberately kept in view space, so even when vanilla is
                // using the previous hand submission we rotate it by the *current* camera here.
                // Freezing a world-space point per bullet made that point visibly slide away from
                // the gun whenever the player turned without shaders.
                Vector3f viewOffset = new Vector3f(GunItemRendererWrapper.muzzleRenderOffset);
                Vector3f worldOffset = viewOffset.rotate(camera.rotation());
                muzzlePosition = camera.position().add(worldOffset.x(), worldOffset.y(), worldOffset.z());

                Vec3 muzzleToBullet = bulletPosition.subtract(muzzlePosition);
                double distanceFromMuzzle = muzzleToBullet.length();
                if (distanceFromMuzzle <= normalTrailLength && distanceFromMuzzle > 1.0E-6) {
                    // While the streak is still shorter than its normal length, keep its tail on the
                    // live muzzle. This is the same animated bone used by the muzzle flash.
                    segmentDirection = muzzleToBullet.scale(1.0 / distanceFromMuzzle);
                    trailLength = distanceFromMuzzle;
                    segmentTail = muzzlePosition;
                } else if (distanceFromMuzzle <= 1.0E-6) {
                    trailLength = 0.0;
                }
                // Once full length is reached, retain the velocity-based direction/tail calculated
                // above. The tracer is then detached and cannot be dragged by later camera motion.
            }

            if (trailLength < 1.0E-4) {
                return;
            }

            Vec3 segmentCenter = bulletPosition.add(segmentTail).scale(0.5);
            Vec3 relativeCenter = segmentCenter.subtract(bulletPosition);
            double horizontalLength = Math.sqrt(segmentDirection.x * segmentDirection.x
                    + segmentDirection.z * segmentDirection.z);
            float segmentYaw = (float) Math.toDegrees(Mth.atan2(segmentDirection.x, segmentDirection.z));
            float segmentPitch = (float) Math.toDegrees(Mth.atan2(segmentDirection.y, horizontalLength));

            poseStack.pushPose();
            poseStack.translate(relativeCenter.x, relativeCenter.y, relativeCenter.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(segmentYaw - 180.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(segmentPitch));
            if (!isFirstPerson) {
                // Preserve the original third-person separation from the physical bullet model.
                poseStack.translate(0, -0.2, 0);
            }

            float width = 0.005f * bullet.getTracerSizeOverride();
            double distanceToEye = bulletPosition.distanceTo(eyePosition);
            width *= (float) Math.max(1.0, distanceToEye / 3.5);
            poseStack.scale(width, width, (float) trailLength);

            debugTracer(bullet, isFirstPerson, partialTicks, bulletPosition, muzzlePosition,
                    segmentTail, segmentCenter, deltaMovement, trailLength, width);
            double bulletDistance = bulletPosition.distanceTo(shooter.getEyePosition());
            if (bullet.tickCount >= 5 || bulletDistance > 2) {
                RenderType type = RenderTypes.energySwirl(InternalAssetLoader.DEFAULT_BULLET_TEXTURE, 15, 15);
                model.submit(poseStack, ItemDisplayContext.NONE, collector, type, packedLight,
                        OverlayTexture.NO_OVERLAY, tracerColor[0], tracerColor[1], tracerColor[2], 1);
            }
            poseStack.popPose();
        });
    }

    private static boolean tracerDebugEnabled(EntityKineticBullet bullet) {
        try {
            if (RenderConfig.TRACER_DEBUG == null || !RenderConfig.TRACER_DEBUG.get()) {
                return false;
            }
            String filter = RenderConfig.TRACER_DEBUG_GUN == null ? "" : RenderConfig.TRACER_DEBUG_GUN.get();
            if (filter == null || filter.isBlank()) {
                return true;
            }
            filter = filter.trim();
            Identifier gunId = bullet.getGunId();
            return filter.equalsIgnoreCase(gunId.toString()) || filter.equalsIgnoreCase(gunId.getPath());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int tracerDebugIntervalMs() {
        try {
            return RenderConfig.TRACER_DEBUG_INTERVAL_MS == null ? 500 : RenderConfig.TRACER_DEBUG_INTERVAL_MS.get();
        } catch (Throwable ignored) {
            return 500;
        }
    }

    private static int tracerDebugFirstTicks() {
        try {
            return RenderConfig.TRACER_DEBUG_FIRST_TICKS == null ? 3 : RenderConfig.TRACER_DEBUG_FIRST_TICKS.get();
        } catch (Throwable ignored) {
            return 3;
        }
    }

    private static boolean shouldLogTracer(EntityKineticBullet bullet, boolean enabled) {
        if (!enabled) {
            return false;
        }
        int firstTicks = tracerDebugFirstTicks();
        if (bullet.tickCount <= firstTicks) {
            Integer lastTick = TRACER_DEBUG_LAST_TICK.put(bullet.getId(), bullet.tickCount);
            if (TRACER_DEBUG_LAST_TICK.size() > 1024) {
                TRACER_DEBUG_LAST_TICK.clear();
            }
            return lastTick == null || lastTick != bullet.tickCount;
        }
        long now = System.currentTimeMillis();
        if (now - tracerDebugLastIntervalLogTime >= tracerDebugIntervalMs()) {
            tracerDebugLastIntervalLogTime = now;
            return true;
        }
        return false;
    }

    private static void debugTracer(EntityKineticBullet bullet,
                                    boolean isFirstPerson,
                                    float partialTicks,
                                    Vec3 bulletPosition,
                                    @Nullable Vec3 muzzlePosition,
                                    Vec3 segmentTail,
                                    Vec3 segmentCenter,
                                    Vec3 deltaMovement,
                                    double trailLength,
                                    float width) {
        if (!shouldLogTracer(bullet, tracerDebugEnabled(bullet))) {
            return;
        }
        GunMod.LOGGER.info("[TACZ TracerDebug] bullet={} gun={} tick={} partial={} firstPerson={} "
                        + "shader={} bulletPos={} muzzle={} tail={} center={} delta={} trail={} width={}",
                bullet.getId(), bullet.getGunId(), bullet.tickCount, trim(partialTicks), isFirstPerson,
                IrisCompat.isUsingRenderPack(), vec(bulletPosition), vec(muzzlePosition),
                vec(segmentTail), vec(segmentCenter), vec(deltaMovement), trim(trailLength), trim(width));
    }

    private static String vec(@Nullable Vec3 vec) {
        if (vec == null) {
            return "null";
        }
        return "(" + trim(vec.x) + "," + trim(vec.y) + "," + trim(vec.z) + ")";
    }

    private static String trim(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    @Override
    protected int getBlockLightLevel(@NotNull EntityKineticBullet bullet, @NotNull BlockPos pos) {
        return 15;
    }

    @Override
    public boolean shouldRender(EntityKineticBullet bullet, Frustum camera, double pCamX, double pCamY, double pCamZ) {
        AABB aabb = bullet.getBoundingBox().inflate(0.5);
        if (aabb.hasNaN() || aabb.getSize() == 0) {
            aabb = new AABB(bullet.getX() - 2.0, bullet.getY() - 2.0, bullet.getZ() - 2.0, bullet.getX() + 2.0, bullet.getY() + 2.0, bullet.getZ() + 2.0);
        }
        return camera.isVisible(aabb);
    }
}
