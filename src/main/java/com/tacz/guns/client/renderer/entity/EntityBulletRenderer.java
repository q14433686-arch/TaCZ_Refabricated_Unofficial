package com.tacz.guns.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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

    public void renderTracerAmmo(EntityKineticBullet bullet, float[] tracerColor, float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
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

                if (isFirstPerson) {
                    // 【曳光弹位置修复 - 回退到上游原始逻辑】
                    // 上游 1.21.1 已逐行核对：
                    //   offset = muzzleRenderOffset（摄像机局部，未旋转）
                    //   首次渲染缓存 offset + camera X/Y
                    //   reducer = (50 - dis)/50 线性
                    //   Iris：旋转 poseStack -> 平移 -> 反旋转
                    //   非 Iris：直接平移（实体已在视图空间）
                    // 26.2 移植曾硬编码相机为 0，又尝试 camera.rotation() 预烘焙，仍漂移。
                    // 本轮回退到上游原始 YN/XN 方案，并保留 26.2 API：mainCamera()/xRot()/yRot()
                    Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
                    Vector3f offset = bullet.getFirstPersonRenderOffset();
                    if (offset == null) {
                        offset = new Vector3f(GunItemRendererWrapper.muzzleRenderOffset);
                        bullet.setCameraXRot(camera.xRot());
                        bullet.setCameraYRot(camera.yRot());
                        bullet.setFirstPersonRenderOffset(offset);
                    }
                    double offsetReducer = Math.max(0, (50.0 - disToEye)) / 50.0;

                    if (IrisCompat.isUsingRenderPack()) {
                        poseStack.mulPose(Axis.YN.rotationDegrees(bullet.getCameraYRot() + 180f));
                        poseStack.mulPose(Axis.XN.rotationDegrees(bullet.getCameraXRot()));
                    }
                    poseStack.translate(offset.x * offsetReducer, offset.y * offsetReducer, offset.z * offsetReducer);
                    if (IrisCompat.isUsingRenderPack()) {
                        poseStack.mulPose(Axis.XP.rotationDegrees(bullet.getCameraXRot()));
                        poseStack.mulPose(Axis.YP.rotationDegrees(bullet.getCameraYRot() + 180f));
                    }
                }
                width *= bullet.getTracerSizeOverride();
                width *= (float) Math.max(1.0, disToEye / 3.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, bullet.yRotO, bullet.getYRot()) - 180.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTicks, bullet.xRotO, bullet.getXRot())));
                poseStack.translate(0, isFirstPerson ? 0 : -0.2, trailLength / 2.0);
                poseStack.scale(width, width, (float) trailLength);
                double bulletDistance = bulletPosition.distanceTo(shooter.getEyePosition());
                if (bullet.tickCount >= 5 || bulletDistance > 2) {
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
