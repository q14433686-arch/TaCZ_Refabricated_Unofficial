package com.tacz.guns.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.compat.iris.IrisCompat;
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
                    // 第一人称渲染自己的曳光弹的时候需要应用偏移（偏移量 = 枪口相对摄像机的位置）
                    //
                    // 【第 9 轮修复】曳光弹不从枪口射出、而是固定从某个位置射出。
                    //
                    // 移植版这里有两处偏差：
                    //   1) 摄像机旋转被硬编码成 0（原注释写"无法获取相机旋转，暂时设置默认值"）。
                    //      于是下面的"旋转 -> 平移 -> 反旋转"退化成在<b>未旋转坐标系</b>里做平移，
                    //      muzzleRenderOffset 被当成世界轴偏移 —— 无论朝哪个方向开枪，
                    //      曳光弹起点都固定在同一处。
                    //   2) 上游那对"旋转/反旋转"<b>只在 Iris 光影启用时</b>才需要
                    //      （1.21.1+ 的渲染坐标空间已不需要手动转换，但 Iris 仍是老样子）。
                    //      移植版无条件执行，即使拿到正确角度也会引入多余变换。
                    //
                    // 摄像机可直接从 Minecraft.gameRenderer.getMainCamera() 取得（与上游一致）。
                    // 26.2: GameRenderer#getMainCamera() -> mainCamera()，Camera#getXRot/getYRot -> xRot()/yRot()
                    Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
                    if (bullet.getFirstPersonRenderOffset() == null) {
                        // 只记录开火瞬间的摄像机朝向，之后整条弹道都沿用，避免转视角时曳光弹跟着甩。
                        // 注意：不要把 muzzleRenderOffset 也永久缓存到 bullet 上。这个 offset 来自当前帧
                        // 第一人称枪口视觉位置；ADS 横移时它会被 walk_aiming / 约束推到一侧。
                        // 若在开火瞬间把它锁死，就会出现“停止移动后偏移不再变大、但也不会回正”的现象。
                        bullet.setCameraXRot(camera.xRot());
                        bullet.setCameraYRot(camera.yRot());
                        bullet.setFirstPersonRenderOffset(new Vector3f());
                    }
                    Vector3f offset = new Vector3f(GunItemRendererWrapper.muzzleRenderOffset);
                    // 按照距离快速削减第一人称枪口视觉偏移，避免远处曳光仍被大幅拉向开火瞬间的枪口侧。
                    // 旧曲线是 50 格线性衰减，ADS 横移时偏移量过高、持续太久；这里改为 12 格二次衰减
                    // 并整体压到 65%，只在枪口附近保留“从枪口出来”的视觉感，随后快速贴回真实弹道。
                    double offsetT = Math.max(0, (12.0 - disToEye)) / 12.0;
                    double offsetReducer = offsetT * offsetT * 0.65;
                    // 摄像机旋转（仅 Iris 需要，见上）
                    // 上游用的是 IrisCompat.isPackInUseQuick()；本移植版对应的方法名为 isUsingRenderPack()，
                    // 内部同样是反射查询 IrisApi#isShaderPackInUse，无硬依赖。
                    boolean needCameraSpaceFix = IrisCompat.isUsingRenderPack();
                    if (needCameraSpaceFix) {
                        poseStack.mulPose(Axis.YN.rotationDegrees(bullet.getCameraYRot() + 180f));
                        poseStack.mulPose(Axis.XN.rotationDegrees(bullet.getCameraXRot()));
                    }
                    // 应用偏移
                    poseStack.translate(offset.x * offsetReducer, offset.y * offsetReducer, offset.z * offsetReducer);
                    // 逆转摄像机旋转
                    if (needCameraSpaceFix) {
                        poseStack.mulPose(Axis.XP.rotationDegrees(bullet.getCameraXRot()));
                        poseStack.mulPose(Axis.YP.rotationDegrees(bullet.getCameraYRot() + 180f));
                    }
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
                    // 由于 energySwirl 不可用，使用 entityTranslucent 替代
                    RenderType type = RenderTypes.entityTranslucent(InternalAssetLoader.DEFAULT_BULLET_TEXTURE);
                    model.submit(poseStack, ItemDisplayContext.NONE, collector, type, packedLight, OverlayTexture.NO_OVERLAY,
                            tracerColor[0], tracerColor[1], tracerColor[2], 1);
                }
            }
            poseStack.popPose();
        });
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
