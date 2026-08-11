package com.tacz.guns.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.entity.EntityKineticBullet;
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

import java.util.Objects;
import java.util.Optional;

public class EntityBulletRenderer extends EntityRenderer<EntityKineticBullet, EntityBulletRenderer.BulletRenderState> {

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
            Vector3f firstPersonVisualOffset = FirstPersonBulletRenderOffset.atRenderPosition(bullet, partialTicks);
            if (ammoEntityModel != null && textureLocation != null) {
                // 1.21.1 only offsets the tracer mesh. Slow rounds with an explicit entity model
                // (40 mm grenade/RPG rocket) therefore visibly leave the player's eye. Apply the
                // same client-only muzzle offset to the model while keeping server ballistics intact.
                // Push before every transform: upstream rotates the shared stack before push(),
                // leaking projectile rotation into the following tracer submission.
                poseStack.pushPose();
                if (firstPersonVisualOffset != null) {
                    poseStack.translate(firstPersonVisualOffset.x(), firstPersonVisualOffset.y(), firstPersonVisualOffset.z());
                }
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, bullet.yRotO, bullet.getYRot()) - 180.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTicks, bullet.xRotO, bullet.getXRot())));
                poseStack.translate(0, 1.5, 0);
                poseStack.scale(-1, -1, 1);
                ammoEntityModel.submit(poseStack, ItemDisplayContext.GROUND, collector, getRenderType(textureLocation), state.lightCoords, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }

            // 曳光弹发光
            if (bullet.isTracerAmmo()) {
                float[] actualTracerColor = Objects.requireNonNullElse(tracerColor, ammoIndex.getTracerColor());
                renderTracerAmmo(bullet, actualTracerColor, partialTicks, poseStack, collector,
                        state.lightCoords, firstPersonVisualOffset);
            }
        });
    }
    
    private RenderType getRenderType(Identifier textureLocation) {
        // 由于entityTranslucentCull不可用，使用entityTranslucent作为替代
        return RenderTypes.entityTranslucent(textureLocation);
    }

    public void renderTracerAmmo(EntityKineticBullet bullet, float[] tracerColor, float partialTicks,
                                 PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        renderTracerAmmo(bullet, tracerColor, partialTicks, poseStack, collector, packedLight,
                FirstPersonBulletRenderOffset.atRenderPosition(bullet, partialTicks));
    }

    private void renderTracerAmmo(EntityKineticBullet bullet, float[] tracerColor, float partialTicks,
                                  PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                  @Nullable Vector3f firstPersonVisualOffset) {
        getModel().ifPresent(model -> {
            Entity shooter = bullet.getOwner();
            if (shooter == null) {
                return;
            }
            boolean isFirstPerson = this.entityRenderDispatcher.options.getCameraType().isFirstPerson() && shooter instanceof LocalPlayer;
            if (isFirstPerson && !RenderConfig.FIRST_PERSON_BULLET_TRACER_ENABLE.get()) {
                return;
            }
            poseStack.pushPose();
            {
                float width = 0.005f;
                Vec3 bulletPosition = bullet.getPosition(partialTicks);
                double trailLength = 0.85 * bullet.getDeltaMovement().length();
                double disToEye = bulletPosition.distanceTo(shooter.getEyePosition(partialTicks));
                trailLength = Math.min(trailLength, disToEye * 0.8);

                if (isFirstPerson && firstPersonVisualOffset != null) {
                    // Entity submission is world-axis translated; the shared helper has already
                    // converted the launch-frame muzzle offset to world space and applied decay.
                    poseStack.translate(firstPersonVisualOffset.x(),
                            firstPersonVisualOffset.y(),
                            firstPersonVisualOffset.z());
                }
                // 说是 override 其实默认值是 1
                // 所以这里直接乘也没关系
                width *= bullet.getTracerSizeOverride();
                width *= (float) Math.max(1.0, disToEye / 3.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, bullet.yRotO, bullet.getYRot()) - 180.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTicks, bullet.xRotO, bullet.getXRot())));
                poseStack.translate(0, isFirstPerson ? 0 : -0.2, trailLength / 2.0);
                poseStack.scale(width, width, (float) trailLength);
                // 距离两格外才渲染，只在前 5 tick 判定
                double bulletDistance = bulletPosition.distanceTo(shooter.getEyePosition());
                if (bullet.tickCount >= 5 || bulletDistance > 2) {
                    // 上游使用 energySwirl 渲染曳光弹；26.2 的 RenderTypes 仍提供该封装
                    // （同类调用已在 MuzzleFlashRender 中使用）。entityTranslucent 会把这条
                    // 拉伸后的方体当普通半透明实体画，视觉上更像“歪掉的实体块”而不是曳光。
                    RenderType type = RenderTypes.energySwirl(InternalAssetLoader.DEFAULT_BULLET_TEXTURE, 15, 15);
                    model.submit(poseStack, ItemDisplayContext.NONE, collector, type, packedLight, OverlayTexture.NO_OVERLAY,
                            tracerColor[0], tracerColor[1], tracerColor[2], 1);
                }
            }
            poseStack.popPose();
        });
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
