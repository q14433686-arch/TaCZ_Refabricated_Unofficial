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
import org.joml.Matrix4f;
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
                Vec3 eyePosition = shooter.getEyePosition(partialTicks);
                Vec3 deltaMovement = bullet.getDeltaMovement();
                double rawTrailLength = 0.85 * deltaMovement.length();
                double disToEye = bulletPosition.distanceTo(eyePosition);
                double trailLength = Math.min(rawTrailLength, disToEye * 0.8);
                boolean debug = tracerDebugEnabled(bullet);
                boolean offsetInitialized = false;
                double offsetReducer = 0.0;
                Vector3f globalMuzzleOffset = new Vector3f(GunItemRendererWrapper.muzzleRenderOffset);
                Vector3f firstPersonOffsetBefore = bullet.getFirstPersonRenderOffset() == null
                        ? null
                        : new Vector3f(bullet.getFirstPersonRenderOffset());
                Vector3f firstPersonOffsetAfter = firstPersonOffsetBefore == null ? null : new Vector3f(firstPersonOffsetBefore);
                // 视图空间偏移经相机旋转后的世界轴向量，用于验证「起点是否真的落在枪口」。
                Vector3f firstPersonWorldOffset = null;
                Matrix4f poseBeforeOffset = new Matrix4f(poseStack.last().pose());
                Matrix4f poseAfterOffset = null;
                Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();

                if (isFirstPerson) {
                    // 【第 26 轮定稿：第一人称曳光弹起点锁定枪口】
                    //
                    // 【空间契约（26.2 字节码逐项核实）】
                    // 1. 实体提交侧（本方法收到的 poseStack）：LevelRenderer#render 先把
                    //    cameraState.viewRotationMatrix（= R(camera.rotation().conjugate())，world→view）
                    //    push 进 RenderSystem modelViewStack，实体只做
                    //    translate(entityPos - cameraPos)，全程不乘旋转
                    //    —— 即本方法里 poseStack 的平移/新增平移都是【世界轴】，
                    //    相机旋转由后续绘制时统一施加。（第 25 轮实测 poseBefore ≡ bulletPos - eye 亦证此。）
                    // 2. 手部提交侧（muzzleRenderOffset 的采集点）：
                    //    26.2 的 GameRenderer#renderItemInHand 开头
                    //      poseStack.mulPose(new Matrix4f(viewRotationMatrix).invert())  // 基座 R(q)，view→world
                    //      modelViewStack.mul(viewRotationMatrix)                        // W2V，绘制时抵消
                    //    因此手部 pose 里读到的 m30/31/32 是【世界轴】的 R(q)·v，不是 1.21.1 的视图空间 v！
                    //    第 25 轮曾据 submitHandsWithItems「只有两条 0.1 系数 bob mulPose」断言
                    //    「poseStack 停留在视图空间」—— 那只看了 ItemInHandRenderer 内部，
                    //    漏看了 renderItemInHand 给进来的基座，结论不成立。
                    //    GunItemRendererWrapper#cacheMuzzlePosition 现在用【手部提交入口的基座矩阵 B】
                    //    的转置把采集位移解算回纯视图空间（B 与枪口矩阵共享同一条基座/bob 前缀，
                    //    Bᵀ·(m − B.t) 恒等于 1.21.1 语义的 v）——不依赖任何渲染管线内部状态，
                    //    vanilla（B≈R(q)）、Iris 手部 pass（B≈I）等一切基座取值下同时正确。
                    //    曾尝试改用 RenderSystem modelView 还原，但 26.2 绘制矩阵经由
                    //    SubmitNodeCollector/DynamicTransforms 下发，RenderSystem modelView 栈
                    //    仅为兼容保留、内容不可作为信源，实测无效，已弃用。
                    //
                    // 【症状对应】旧链路下采集到 R(q)·v 又被这里 rotate 一次（R(q)²·v），
                    // 枪口起点按【二倍朝向角】偏转：面南/面北几乎无感，面向东南/西南/东北/西北时
                    // 整串曳光弹起点被甩到枪口一侧 —— 反馈里「后坐力方向固定向左/右偏」即此；
                    // 光影开启时手部 pass 不经过 26.2 的基座预乘，链路本就自洽，故一切正常。
                    //
                    // camera.rotation() 是【视图→世界】：Camera#setRotation 用
                    // rotationYXZ(PI - yRot*DEG, -xRot*DEG, 0) 构造，随后
                    // FORWARDS(0,0,-1).rotate(rotation) 得到世界前向。
                    // 正是这里需要的方向，<b>直接 rotate，不要 conjugate</b>。
                    //
                    // 枪口采集与子弹提交不在同一次渲染段（世界 pass 先于手部 pass），
                    // muzzleRenderOffset 是上一帧手部 pass 的留存值；用【当帧相机】旋转之，
                    // 转头时仅残留一帧量级的视差，与上游设计一致。
                    Vector3f offset = bullet.getFirstPersonRenderOffset();
                    if (offset == null) {
                        offset = new Vector3f(globalMuzzleOffset);
                        bullet.setCameraXRot(camera.xRot());
                        bullet.setCameraYRot(camera.yRot());
                        bullet.setFirstPersonRenderOffset(offset);
                        offsetInitialized = true;
                    }
                    firstPersonOffsetAfter = new Vector3f(offset);
                    offsetReducer = Math.max(0, (50.0 - disToEye)) / 50.0;

                    // 视图空间 -> 世界空间：camera.rotation() 即视图→世界，直接 rotate。
                    Vector3f worldOffset = new Vector3f(globalMuzzleOffset).rotate(camera.rotation());
                    firstPersonWorldOffset = new Vector3f(worldOffset);
                    poseStack.translate(worldOffset.x() * offsetReducer,
                            worldOffset.y() * offsetReducer,
                            worldOffset.z() * offsetReducer);
                    poseAfterOffset = new Matrix4f(poseStack.last().pose());
                }
                width *= bullet.getTracerSizeOverride();
                width *= (float) Math.max(1.0, disToEye / 3.5);
                // 曳光弹轨迹条带的朝向来源是【实体 rot 字段】而非速度向量；
                // 提起为本地变量并写进诊断日志，用于核对「条带朝向 == 速度反向」。
                float bulletLerpYRot = Mth.lerp(partialTicks, bullet.yRotO, bullet.getYRot());
                float bulletLerpXRot = Mth.lerp(partialTicks, bullet.xRotO, bullet.getXRot());
                poseStack.mulPose(Axis.YP.rotationDegrees(bulletLerpYRot - 180.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(bulletLerpXRot));
                poseStack.translate(0, isFirstPerson ? 0 : -0.2, trailLength / 2.0);
                poseStack.scale(width, width, (float) trailLength);
                double bulletDistance = bulletPosition.distanceTo(shooter.getEyePosition());
                debugTracer(bullet, isFirstPerson, partialTicks, bulletPosition, eyePosition, deltaMovement,
                        rawTrailLength, trailLength, disToEye, bulletDistance, width, offsetReducer,
                        globalMuzzleOffset, firstPersonOffsetBefore, firstPersonOffsetAfter,
                        firstPersonWorldOffset,
                        offsetInitialized, camera, bulletLerpYRot, bulletLerpXRot,
                        poseBeforeOffset, poseAfterOffset,
                        new Matrix4f(poseStack.last().pose()), debug);
                if (bullet.tickCount >= 5 || bulletDistance > 2) {
                    RenderType type = RenderTypes.energySwirl(InternalAssetLoader.DEFAULT_BULLET_TEXTURE, 15, 15);
                    model.submit(poseStack, ItemDisplayContext.NONE, collector, type, packedLight, OverlayTexture.NO_OVERLAY,
                            tracerColor[0], tracerColor[1], tracerColor[2], 1);
                }
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
                                    Vec3 eyePosition,
                                    Vec3 deltaMovement,
                                    double rawTrailLength,
                                    double trailLength,
                                    double disToEye,
                                    double bulletDistance,
                                    float width,
                                    double offsetReducer,
                                    Vector3f globalMuzzleOffset,
                                    @Nullable Vector3f firstPersonOffsetBefore,
                                    @Nullable Vector3f firstPersonOffsetAfter,
                                    @Nullable Vector3f firstPersonWorldOffset,
                                    boolean offsetInitialized,
                                    Camera camera,
                                    float bulletLerpYRot,
                                    float bulletLerpXRot,
                                    Matrix4f poseBeforeOffset,
                                    @Nullable Matrix4f poseAfterOffset,
                                    Matrix4f finalPose,
                                    boolean enabled) {
        if (!shouldLogTracer(bullet, enabled)) {
            return;
        }
        GunMod.LOGGER.info("[TACZ TracerDebug] bullet={} gun={} display={} ammo={} tick={} partial={} firstPerson={} shader={} irisHand={} tracer={} camera=({},{}) cachedCamera=({},{}) bulletRot=({},{}) offsetInit={} offsetReducer={} bulletPos={} eye={} delta={} disToEye={} bulletDistance={} rawTrail={} trail={} width={} globalMuzzle={} fpOffsetBefore={} fpOffsetAfter={} fpWorldOffset={} poseBefore={} poseAfterOffset={} finalPose={}",
                bullet.getId(),
                bullet.getGunId(),
                bullet.getGunDisplayId(),
                bullet.getAmmoId(),
                bullet.tickCount,
                trim(partialTicks),
                isFirstPerson,
                IrisCompat.isUsingRenderPack(),
                IrisCompat.isHandRendererActive(),
                bullet.isTracerAmmo(),
                trim(camera.xRot()), trim(camera.yRot()),
                trim(bullet.getCameraXRot()), trim(bullet.getCameraYRot()),
                trim(bulletLerpYRot), trim(bulletLerpXRot),
                offsetInitialized,
                trim(offsetReducer),
                vec(bulletPosition),
                vec(eyePosition),
                vec(deltaMovement),
                trim(disToEye),
                trim(bulletDistance),
                trim(rawTrailLength),
                trim(trailLength),
                trim(width),
                vec(globalMuzzleOffset),
                vec(firstPersonOffsetBefore),
                vec(firstPersonOffsetAfter),
                vec(firstPersonWorldOffset),
                translation(poseBeforeOffset),
                translation(poseAfterOffset),
                translation(finalPose));
    }

    private static String vec(@Nullable Vec3 vec) {
        if (vec == null) {
            return "null";
        }
        return "(" + trim(vec.x) + "," + trim(vec.y) + "," + trim(vec.z) + ")";
    }

    private static String vec(@Nullable Vector3f vec) {
        if (vec == null) {
            return "null";
        }
        return "(" + trim(vec.x()) + "," + trim(vec.y()) + "," + trim(vec.z()) + ")";
    }

    private static String translation(@Nullable Matrix4f matrix) {
        if (matrix == null) {
            return "null";
        }
        return "(" + trim(matrix.m30()) + "," + trim(matrix.m31()) + "," + trim(matrix.m32()) + ")";
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
