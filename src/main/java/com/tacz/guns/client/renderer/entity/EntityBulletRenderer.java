package com.tacz.guns.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.screen.RefitTransform;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
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
            if (isFirstPerson) {
                if (!RenderConfig.FIRST_PERSON_BULLET_TRACER_ENABLE.get()) {
                    return;
                }
                // 【本轮】第一人称曳光改由手部 pass 提交（GunItemRendererWrapper#renderFirstPerson
                // 在缓存当帧枪口视图偏移后调用 submitFirstPersonTracers），起点 = 当帧枪口，
                // 与枪械同投影、同 pass，彻底消除「实体 pass 先于手部 pass」造成的跨帧滞后。
                //
                // 本帧手部 pass 会画到这颗子弹（手里有枪、有枪口锚点、且子弹在曳光射程内）时，
                // 这里必须跳过，否则同一颗子弹的拖尾会被画两遍；其余情况
                // （收枪完成/切到非枪械/子弹飞出射程/枪械没有枪口节点）退回本路径的旧锚点逻辑兜底，
                // 保证曳光不会凭空消失。
                if (isGunRenderedInHandPass() && hasMuzzleAnchor() && withinHandPassRange(bullet)) {
                    return;
                }
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
                    // 【第 25 轮修复：第一人称曳光弹起点不在枪口，随朝向呈东南西北规律漂移】
                    //
                    // <h2>结论先行：muzzleRenderOffset 是【视图空间】的，必须旋转到世界再平移</h2>
                    //
                    // 上一轮（第 24 轮）断定它「已经是世界向量、直接相加即可」，
                    // <b>那个结论是错的</b>。本轮用同一份 latest.log 重新统计将其推翻。
                    //
                    // <h3>证据一：globalMuzzle 在 307° 偏航跨度上几乎是常量</h3>
                    // 86 个 TracerDebug 样本，yaw 覆盖 13 个 15° 桶、跨度 307°。
                    // 若它是世界向量，x/z 应当随朝向在 ±1.8 之间整周摆动；实测却是：
                    //   gx ∈ [+0.064, +0.195]   gz ∈ [-1.948, -1.727]
                    //   mean (+0.162, -0.188, -1.835)   std (0.019, 0.026, 0.043)
                    // 按 45° 分八桶后各桶均值几乎重合（gz 全部落在 -1.77 ~ -1.89）。
                    // 这是一个稳定的<b>视图空间</b>常量：「正前方约 1.84 格、略偏右下」，
                    // 与枪口的实际位置吻合。
                    //
                    // <h3>证据二：与 yaw 的相关性接近于零</h3>
                    //   corr(gx, sin yaw) = +0.27     corr(gz, cos yaw) = -0.20
                    // 若是世界向量，这两个值应接近 ±1.0。
                    // （第 24 轮引用的 -0.95/+0.97 来自更早的日志，那时旧代码正在对
                    //   offset 做旋转，测到的是「旋转之后」的量；用它推断「旋转之前」
                    //   的空间归属，把因果搞反了。）
                    //
                    // <h3>证据三：采集端链路里根本没有相机旋转</h3>
                    // muzzleRenderOffset 取自 renderFirstPerson 的 poseStack，其上游是
                    // ItemInHandRenderer#submitHandsWithItems。对该方法反汇编，从入口到
                    // submitArmWithItem 之间【只有两条 mulPose】：
                    //   mulPose(XP, (getViewXRot - xBob) * 0.1)
                    //   mulPose(YP, (getViewYRot - yBob) * 0.1)
                    // 系数是 <b>0.1</b> —— 这是视角延滞(bob)，只有真实视角的十分之一，
                    // <b>不是</b>把手部变换到世界空间的相机旋转。而
                    // GunItemRendererWrapper#renderFirstPerson 开头正是用
                    //   mulPose(XP, xRot * -0.1) / mulPose(YP, yRot * -0.1)
                    // 把这两条<b>逆转抵消</b>。故 poseStack 始终停留在【视图空间】。
                    //
                    // <h2>为什么症状呈「东南西北」规律</h2>
                    // 把视图空间向量（前方为 -Z）当成世界向量直接平移，它就恒定指向
                    // 世界 -Z（正北）。于是：
                    //   面北 → 世界 -Z 与视图前方同向，起点看着还在前方
                    //   面南 → 世界 -Z 成了身后，起点跑到视野后方
                    //   面东 → 世界 -Z 在左手边，起点偏左
                    //   面西 → 世界 -Z 在右手边，起点偏右
                    // 与实测的四方位描述逐条对应。俯仰同理：gy 恒为世界竖直分量，
                    // 抬头/低头时起点固定上/下偏，并与偏航叠加成「上偏左」「下偏右」等组合。
                    // 这也解释了「方向没错、只有起点错」—— 错的仅仅是这一次平移。
                    //
                    // <h2>为什么实体 poseStack 是世界轴（决定了修法是 rotate 而非 conjugate）</h2>
                    // 同一份日志验证：poseBefore ≡ bulletPos - eye，86 样本中 81 个逐轴
                    // 误差 < 0.0001。（5 个离群全是子弹已飞远的样本，差值模长约 2.0 格，
                    // 来自 getEyePosition(partialTicks) 与相机插值不在同一帧，与空间归属无关。）
                    // 字节码亦确认：LevelRenderer#submitFeatures 用 new PoseStack()（单位阵），
                    // submitEntities 只做 translate(entity.pos - camera.pos)，
                    // EntityRenderDispatcher#submit 只 pushPose/translate，全程无 mulPose。
                    //
                    // <h2>修法</h2>
                    // camera.rotation() 是【视图→世界】：Camera#setRotation 用
                    // rotationYXZ(PI - yRot*DEG, -xRot*DEG, 0) 构造，随后
                    // FORWARDS(0,0,-1).rotate(rotation) 得到世界前向。
                    // 正是这里需要的方向，<b>直接 rotate，不要 conjugate</b>。
                    //
                    // 另：改用【当帧实时】的 globalMuzzle 而非首帧缓存值。缓存值在玩家转头后
                    // 会失效（日志中 bullet=395 转 99.7° 时缓存值与实时值相差 1.83 格），
                    // 那是「起点随转头相对运动」的来源。枪械模型渲染与本次实体提交在同一帧、
                    // 同一相机下完成，实时取用即可始终贴合枪口。缓存值仅留作调试对照
                    // （fpOffsetBefore/After），不再参与定位。
                    //
                    // FOV 因子（tan(itemFov/2)/tan(levelFov/2)，在 cacheMuzzlePosition 里施加于 z）
                    // <b>必须保留</b>：applyScopeMagnification 与 applyGunModelFovModifying
                    // 驱动两套独立的 FOV dynamics，开镜时二者分离，去掉会导致开镜后起点前后错位。
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
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, bullet.yRotO, bullet.getYRot()) - 180.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTicks, bullet.xRotO, bullet.getXRot())));
                poseStack.translate(0, isFirstPerson ? 0 : -0.2, trailLength / 2.0);
                poseStack.scale(width, width, (float) trailLength);
                double bulletDistance = bulletPosition.distanceTo(shooter.getEyePosition());
                debugTracer(bullet, isFirstPerson, partialTicks, bulletPosition, eyePosition, deltaMovement,
                        rawTrailLength, trailLength, disToEye, bulletDistance, width, offsetReducer,
                        globalMuzzleOffset, firstPersonOffsetBefore, firstPersonOffsetAfter,
                        firstPersonWorldOffset,
                        offsetInitialized, camera, poseBeforeOffset, poseAfterOffset,
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

    /** 手部 pass 曳光覆盖的子弹距离（超出后由实体 pass 的旧锚点路径兜底）。 */
    private static final double HAND_PASS_RANGE = 256.0;
    /** 手部 pass 曳光拖尾长度上限，防止远距离子弹在屏幕上拉出贯穿全屏的长线。 */
    private static final double HAND_PASS_MAX_TRAIL = 32.0;

    /**
     * 第一人称曳光的手部 pass 提交入口（由 {@code GunItemRendererWrapper#renderFirstPerson}
     * 在缓存当帧枪口视图偏移之后调用）。
     *
     * <h2>为什么要把第一人称曳光挪到手部 pass</h2>
     * 实体（Level）pass 每帧先于手部（Hand）pass 执行，旧实现里曳光渲染时读到的
     * {@code muzzleRenderOffset} 是<b>上一帧</b>手部 pass 缓存的枪口偏移 —— 转头、开镜过渡、
     * 后坐动画、跳跃摆动期间枪口视图偏移每帧都在变，于是起点相对枪口漂移（1 帧滞后）。
     *
     * <p>这里改为在<b>手部 pass 内</b>、拿到<b>当帧</b> {@code muzzleRenderOffsetView}
     * （视图空间、未乘 FOV 因子）后提交：起点 = 当帧枪口，与枪械同投影、同 pass，
     * 不存在跨帧滞后，开镜/后坐/摆动期间拖尾都死死钉在枪口上。
     * 渲染流程与 {@code ShellRender} 一致（手部 pass 内提交模型），已被多轮实测验证。</p>
     *
     * <p>旧路径（{@link #renderTracerAmmo}）对第一人称子弹只保留兜底职责：手部 pass 不渲染
     * 本帧枪械（收枪/切到非枪械）或子弹超出 {@link #HAND_PASS_RANGE} 时才由它接管。</p>
     */
    public static void submitFirstPersonTracers(SubmitNodeCollector collector, int packedLight) {
        if (!RenderConfig.FIRST_PERSON_BULLET_TRACER_ENABLE.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (mc.options == null || mc.options.getCameraType() == null || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        // 改装界面里枪被摆到改装姿态，枪口偏移不是射击姿态，这期间不提交曳光。
        if (RefitTransform.getOpeningProgress() != 0) {
            return;
        }
        getModel().ifPresent(model -> {
            Camera camera = mc.gameRenderer.mainCamera();
            float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            // 26.2 的 Camera 不再暴露 getPosition()（位置已移入 CameraRenderState）；
            // 第一人称下相机位置 == 玩家眼睛位置，用 getEyePosition 获取（本文件既有同款调用，可编译）。
            Vec3 camPos = mc.player.getEyePosition(partialTicks);
            // 世界 -> 视图：把相机旋转取逆，再对（世界-相机）向量旋转。
            Quaternionf invRot = new Quaternionf(camera.rotation()).conjugate();
            Vector3f muzzleView = new Vector3f(GunItemRendererWrapper.muzzleRenderOffsetView);
            if (muzzleView.lengthSquared() < 1e-8f) {
                // 模型没有枪口节点（muzzleFlashPosPath 为 null）时偏移恒为 0，无从锚定，跳过。
                return;
            }
            AABB range = new AABB(camPos.x - HAND_PASS_RANGE, camPos.y - HAND_PASS_RANGE, camPos.z - HAND_PASS_RANGE,
                    camPos.x + HAND_PASS_RANGE, camPos.y + HAND_PASS_RANGE, camPos.z + HAND_PASS_RANGE);
            for (EntityKineticBullet bullet : mc.level.getEntitiesOfClass(EntityKineticBullet.class, range)) {
                if (!(bullet.getOwner() instanceof LocalPlayer)) {
                    continue;
                }
                if (!bullet.isTracerAmmo()) {
                    continue;
                }
                submitFirstPersonTracer(model, bullet, collector, packedLight, camPos, invRot, muzzleView, partialTicks);
            }
        });
    }

    private static void submitFirstPersonTracer(BedrockModel model, EntityKineticBullet bullet, SubmitNodeCollector collector,
                                                int packedLight, Vec3 camPos, Quaternionf invRot, Vector3f muzzleView,
                                                float partialTicks) {
        Vec3 bulletPos = bullet.getPosition(partialTicks);
        Vec3 delta = bullet.getDeltaMovement();
        Vector3f viewBullet = new Vector3f((float) (bulletPos.x - camPos.x), (float) (bulletPos.y - camPos.y),
                (float) (bulletPos.z - camPos.z)).rotate(invRot);
        Vector3f viewDelta = new Vector3f((float) delta.x, (float) delta.y, (float) delta.z).rotate(invRot);
        double rawTrailLength = 0.85 * delta.length();
        double disToEye = viewBullet.length();
        // 起点固定在当帧枪口（muzzleView），拖尾沿子弹速度方向向前延伸：
        // 长度随子弹距离增长（disToEye*0.8），近处保底 1.5 格（刚出膛也有可见拖尾），
        // 并有上限，避免远距离在屏幕上拉出贯穿全屏的长线。
        double trailLength = Math.min(rawTrailLength, Math.max(disToEye * 0.8, 1.5));
        trailLength = Math.min(trailLength, HAND_PASS_MAX_TRAIL);
        if (trailLength <= 0.05 || viewDelta.lengthSquared() < 1e-6) {
            return;
        }
        // 与实体路径同一套朝向约定：yaw = atan2(x, z)，pitch = atan2(y, 水平距离)，旋转 YP(yaw-180)+XP(pitch)。
        // 只是这里用的是【视图空间】速度向量 —— 坐标系换到视图后公式不变。
        float yaw = (float) Math.toDegrees(Math.atan2(viewDelta.x, viewDelta.z));
        double horizontalDistance = Math.hypot(viewDelta.x, viewDelta.y);
        float pitch = (float) Math.toDegrees(Math.atan2(viewDelta.y, horizontalDistance));
        float width = 0.005f * bullet.getTracerSizeOverride();
        width *= (float) Math.max(1.0, disToEye / 3.5);
        // 解析曳光颜色：与实体路径同一套回退链（override -> gun display -> ammo）。
        float[] tracerColor = bullet.getTracerColorOverride().orElse(null);
        if (tracerColor == null) {
            tracerColor = TimelessAPI.getGunDisplay(bullet.getGunDisplayId(), bullet.getGunId())
                    .map(GunDisplayInstance::getTracerColor).orElse(null);
        }
        if (tracerColor == null) {
            tracerColor = TimelessAPI.getClientAmmoIndex(bullet.getAmmoId())
                    .map(ammoIndex -> ammoIndex.getTracerColor()).orElse(null);
        }
        if (tracerColor == null) {
            tracerColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        }
        PoseStack pose = new PoseStack();
        pose.translate(muzzleView.x(), muzzleView.y(), muzzleView.z());
        pose.mulPose(Axis.YP.rotationDegrees(yaw - 180.0F));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.translate(0, 0, (float) (trailLength / 2.0));
        pose.scale(width, width, (float) trailLength);
        if (IrisCompat.isHandRendererActive()) {
            // 与抛壳/枪口火光同一套光影兼容：把能量漩涡管线显式归到手部 program，
            // 否则在 Iris hand pass 中可能不渲染或坐标系错乱。
            IrisCompat.assignCommonEntityPipelinesToHandIfNeeded();
        }
        RenderType type = RenderTypes.energySwirl(InternalAssetLoader.DEFAULT_BULLET_TEXTURE, 15, 15);
        model.submit(pose, ItemDisplayContext.NONE, collector, type, packedLight, OverlayTexture.NO_OVERLAY,
                tracerColor[0], tracerColor[1], tracerColor[2], 1);
        debugFirstPersonTracer(bullet, partialTicks, camPos, muzzleView, viewBullet, viewDelta, trailLength, width, yaw, pitch, tracerColor);
    }

    /** 当前枪械是否有可用的枪口锚点（muzzleFlashPosPath 存在时偏移非零）。 */
    private static boolean hasMuzzleAnchor() {
        return GunItemRendererWrapper.muzzleRenderOffsetView.lengthSquared() >= 1e-8f;
    }

    /** 本帧手部 pass 是否渲染枪械（第一人称 + 主手/延留物品是枪且模型已加载）。 */
    private static boolean isGunRenderedInHandPass() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.getCameraType() == null || !mc.options.getCameraType().isFirstPerson()) {
            return false;
        }
        if (mc.player == null) {
            return false;
        }
        ItemStack current = KeepingItemRenderer.getRenderer().getCurrentItem();
        if (current == null || current.isEmpty()) {
            return false;
        }
        return IGun.getIGunOrNull(current) != null;
    }

    /** 子弹是否在 {@link #HAND_PASS_RANGE} 内（超出则由实体 pass 的旧锚点路径兜底）。 */
    private static boolean withinHandPassRange(EntityKineticBullet bullet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        // 与 submitFirstPersonTracers 同一约定：第一人称下相机位置 == 玩家眼睛位置。
        Vec3 camPos = mc.player.getEyePosition();
        return bullet.distanceToSqr(camPos.x, camPos.y, camPos.z) <= HAND_PASS_RANGE * HAND_PASS_RANGE;
    }

    private static void debugFirstPersonTracer(EntityKineticBullet bullet, float partialTicks, Vec3 camPos,
                                               Vector3f muzzleView, Vector3f viewBullet, Vector3f viewDelta,
                                               double trailLength, float width, float yaw, float pitch,
                                               float[] tracerColor) {
        if (!shouldLogTracer(bullet, tracerDebugEnabled(bullet))) {
            return;
        }
        GunMod.LOGGER.info("[TACZ TracerDebug-FP] bullet={} gun={} tick={} partial={} shader={} irisHand={} camera=({},{}) camPos={} muzzleView={} viewBullet={} viewDelta={} disToEye={} trail={} width={} yaw={} pitch={} color=({},{},{},{})",
                bullet.getId(),
                bullet.getGunId(),
                bullet.tickCount,
                trim(partialTicks),
                IrisCompat.isUsingRenderPack(),
                IrisCompat.isHandRendererActive(),
                trim(Minecraft.getInstance().gameRenderer.mainCamera().xRot()),
                trim(Minecraft.getInstance().gameRenderer.mainCamera().yRot()),
                vec(camPos),
                vec(muzzleView),
                vec(viewBullet),
                vec(viewDelta),
                trim(viewBullet.length()),
                trim(trailLength),
                trim(width),
                trim(yaw),
                trim(pitch),
                trim(tracerColor[0]), trim(tracerColor[1]), trim(tracerColor[2]), trim(tracerColor.length > 3 ? tracerColor[3] : 1));
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
                                    Matrix4f poseBeforeOffset,
                                    @Nullable Matrix4f poseAfterOffset,
                                    Matrix4f finalPose,
                                    boolean enabled) {
        if (!shouldLogTracer(bullet, enabled)) {
            return;
        }
        GunMod.LOGGER.info("[TACZ TracerDebug] bullet={} gun={} display={} ammo={} tick={} partial={} firstPerson={} shader={} irisHand={} tracer={} camera=({},{}) cachedCamera=({},{}) offsetInit={} offsetReducer={} bulletPos={} eye={} delta={} disToEye={} bulletDistance={} rawTrail={} trail={} width={} globalMuzzle={} fpOffsetBefore={} fpOffsetAfter={} fpWorldOffset={} poseBefore={} poseAfterOffset={} finalPose={}",
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
