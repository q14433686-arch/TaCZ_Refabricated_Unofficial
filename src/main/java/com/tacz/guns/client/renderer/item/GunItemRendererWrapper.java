package com.tacz.guns.client.renderer.item;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.event.CameraSetupEvent;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.TransformScale;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.RenderDistance;
import com.tacz.guns.util.math.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static net.minecraft.world.item.ItemDisplayContext.*;

/**
 * 负责主要的枪械动画模型渲染。额外的效果见 {@link FirstPersonRenderGunEvent}
 */
public class GunItemRendererWrapper extends AnimateGeoItemRenderer<BedrockGunModel, GunAnimationStateContext> {
    private static final SlotModel SLOT_GUN_MODEL = new SlotModel();
    private static BedrockGunModel lastModel = null;
    public static final Vector3f muzzleRenderOffset = new Vector3f();

    /**
     * 「当前这次 THIRD_PERSON_*_HAND 提交对应的是<b>主手</b>」。
     *
     * <p>由 {@code ItemInHandLayerMixin#submitArmWithItem} 在 HEAD 置位、TAIL 清除。
     * 用于把「左手」与「副手」区分开 —— 左利手玩家的主手就是左手，
     * 不能像上游那样用 {@code arm == LEFT} 代替「副手」判定，否则他的主手枪不渲染。</p>
     *
     * <p>渲染线程单线程，且 {@code ItemStackRenderState#submit} 是同步直调
     * {@code SpecialModelRenderer#submit}（字节码确认），因此普通 static 字段即可，
     * 不需要 ThreadLocal，也不会跨帧残留。</p>
     */
    public static boolean IS_MAIN_HAND_SUBMIT = false;

    public static final Supplier<GunItemRendererWrapper> INSTANCE = Suppliers.memoize(GunItemRendererWrapper::new);

    /**
     * 【RecoilDebug】枪械第一人称路径最近一次「消费摄像机动画数据」的毫秒时间戳。
     * 供 {@code BedrockAnimatedModel#cleanCameraAnimationTransform} 核对调用者身份：
     * 正常语义下清理只应紧跟本路径发生（30ms 窗口内）。
     */
    private static volatile long recoilDebugLastWrapTouchMs = -1;

    public static boolean recoilDebugExpectedCleanOwner() {
        return System.currentTimeMillis() - recoilDebugLastWrapTouchMs < 30;
    }

    private static void recoilDebugTouchCleanMark() {
        recoilDebugLastWrapTouchMs = System.currentTimeMillis();
    }

    /**
     * 本次手部提交入口处的基座矩阵（渲染线程单线程使用；与 26.2 手部 pass 的
     * PoseStack 基座预乘对应，只在本帧 renderFirstPerson → cacheMuzzlePosition 之间有效，
     * 用完即弃，见 {@link #cacheMuzzlePosition}）。
     */
    private static final Matrix4f handBasePose = new Matrix4f();

    // 【第 30 轮（案例⑧ 追根）】入口基座的**原始快照**（锁定修复改写前的那一份）。
    // 斜向修复（d24e604/c975748）的全套代数契约——包括 v1 朝向指纹实测所证明的
    // 「写入槽位的带回矩阵 = 入口基座本身」——全部建立在入口基座上；第 28 轮的
    // 锁视角修复会把 handBasePose 改写为 (B·MV)⁻¹ 派生值，两者不再相同。
    // 凡契约消费者（约束三明治、枪口缓存归一化）一律用本字段，保证与修复前逐位一致。
    private static final Matrix4f handBasePoseEntry = new Matrix4f();

    /**
     * 供 {@link com.tacz.guns.client.event.FirstPersonRenderGunEvent#applyAnimationConstraintTransform}
     * 做「入口基座归一化」用的只读副本：第一人称手部 pass 进入 {@code renderFirstPerson} 时的
     * 基座 3x3 旋转（vanilla 26.2 ≈ R(相机四元数)，Iris 手部 pass ≈ 单位阵，正交旋转，逆=转置）。
     * 仅渲染线程使用。
     * 【第 30 轮修正】恢复为入口快照（锁视角修复前的契约值），不再跟随锁后改写。
     */
    public static void copyHandBaseRotation(org.joml.Matrix3f dst) {
        dst.set(handBasePoseEntry);
    }

    public GunItemRendererWrapper() {
        super();
    }

    @Override
    public GunAnimationStateContext initContext(ItemStack stack, Player player, float partialTick) {
        GunAnimationStateContext context = new GunAnimationStateContext();
        this.updateContext(context, stack, player, partialTick);
        return context;
    }

    @Override
    public void updateContext(GunAnimationStateContext context, ItemStack stack, Player player, float partialTick) {
        context.setPartialTicks(partialTick);
        context.setCurrentGunItem(stack);
    }

    @Override
    public void tryInit(ItemStack stack, Player player, float partialTick) {
        super.tryInit(stack, player, partialTick);
    }

    @Override
    public void tryExit(ItemStack stack, long putAwayTime) {
        var stateMachine = getStateMachine(stack);
        if (stateMachine == null) {
            return;
        }
        stateMachine.processContextIfExist(context -> {
            context.setPutAwayTime(putAwayTime / 1000F);
            context.setCurrentGunItem(stack);
        });
        if (stateMachine.isInitialized()) {
            stateMachine.trigger(GunAnimationConstant.INPUT_PUT_AWAY);
//            KeepingItemRenderer.getRenderer().keep(stack, putAwayTime);
            stateMachine.exit();
            stateMachine.setExitingTime(putAwayTime + 50);
        }
    }

    @Override
    public long getPutAwayTime(ItemStack stack) {
        if (stack.getItem() instanceof IGun iGun) {
            return TimelessAPI.getCommonGunIndex(iGun.getGunId(stack))
                    .map(index -> (long) (index.getGunData().getPutAwayTime() * 1000L))
                    .orElse(0L);
        }
        return 0;
    }

    @Nullable
    @Override
    public LuaAnimationStateMachine<GunAnimationStateContext> getStateMachine(ItemStack stack) {
        return TimelessAPI.getGunDisplay(stack).map(GunDisplayInstance::getAnimationStateMachine).orElse(null);
    }

    @Override
    public BedrockGunModel getModel(ItemStack stack) {
        return TimelessAPI.getGunDisplay(stack).map(GunDisplayInstance::getGunModel).orElse(null);
    }

    @Override
    public Identifier getTextureLocation(ItemStack stack) {
        return TimelessAPI.getGunDisplay(stack).map(GunDisplayInstance::getModelTexture).orElse(null);
    }

    @Override
    public void applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles event, ItemStack stack, LocalPlayer player) {
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        Optional.ofNullable(getModel(stack)).ifPresent(model -> {
            if (lastModel != model) {
                // 切换枪械模型的时候清理一下摄像机动画数据，以避免上一次播放到一半的摄像机动画影响观感。
                model.cleanCameraAnimationTransform();
                lastModel = model;
            }
            IClientPlayerGunOperator clientPlayerGunOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
            float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float aimingProgress = clientPlayerGunOperator.getClientAimingProgress(partialTicks);
            float zoom = iGun.getAimingZoom(stack);
            float multiplier = 1 - aimingProgress + aimingProgress / (float) Math.sqrt(zoom);
            // 【RecoilDebug 隔离】第 27.4 轮：运行时旁路摄像机动画的世界相机叠加消费
            if (RenderConfig.DEBUG_DISABLE_CAMERA_ANIM == null || !RenderConfig.DEBUG_DISABLE_CAMERA_ANIM.get()) {
                this.applyLevelCameraAnimation(event, stack, multiplier);
            }
        });
    }

    @Override
    public void applyItemInHandCameraAnimation(BeforeRenderHandEvent event, ItemStack stack, LocalPlayer player) {
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        Optional.ofNullable(getModel(stack)).ifPresent(model -> {
            PoseStack poseStack = event.getPoseStack();
            IClientPlayerGunOperator clientPlayerGunOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
            float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float aimingProgress = clientPlayerGunOperator.getClientAimingProgress(partialTicks);
            float zoom = iGun.getAimingZoom(stack);
            float multiplier = 1 - aimingProgress + aimingProgress / (float) Math.sqrt(zoom);
            Quaternionf quaternion = MathUtil.multiplyQuaternion(model.getCameraAnimationObject().rotationQuaternion, multiplier);
            // 【RecoilDebug 探针】枪械走本重载而非基类 applyItemInHandCameraAnimation，
            // 探针挂在这里（叠加前基座采样），与基类探针共用同一输出通道。
            if (RenderConfig.RECOIL_DEBUG.get()) {
                debugRecoilItemCam(quaternion, multiplier, poseStack);
            }
            // 【RecoilDebug 隔离】第 27.4 轮：运行时旁路摄像机动画的手部消费（旋转不叠加，但数据照常清理，避免残留）
            if (RenderConfig.DEBUG_DISABLE_CAMERA_ANIM == null || !RenderConfig.DEBUG_DISABLE_CAMERA_ANIM.get()) {
                poseStack.mulPose(quaternion);
            }
            recoilDebugTouchCleanMark();
            // 截至目前，摄像机动画数据已消费完毕。是否有更好的清理动画数据的方法？
            model.cleanCameraAnimationTransform();
        });
    }

    @Override
    public void renderFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, SubmitNodeCollector collector,
                                  int light, float partialTick) {
        if (!(stack.getItem() instanceof IGun)) {
            return;
        }

        TimelessAPI.getGunDisplay(stack).ifPresent(display -> {
            BedrockGunModel gunModel = display.getGunModel();
            var animationStateMachine = display.getAnimationStateMachine();
            if (gunModel == null) {
                return;
            }

            // 在渲染之前，先更新动画，让动画数据写入模型
            if (animationStateMachine != null) {
                animationStateMachine.processContextIfExist(context -> {
                    updateContext(context, stack, player, partialTick);
                });
                animationStateMachine.update();
            }

            poseStack.pushPose();
            // 【关键基座采样：26.2 手部 pose 并不从单位阵开始】
            // vanilla 26.2：GameRenderer#renderItemInHand 先给手部 PoseStack 预乘
            // invert(viewRotationMatrix)（= R(camera.rotation())，view→world），又经
            // bobHurt/bobView 与 submitHandsWithItems 的两条 0.1 系数 bob ——
            // 这些都会同时出现在「此刻的基座」和「稍后读取的枪口矩阵」里，
            // 因此先把入口矩阵原样记下，供 cacheMuzzlePosition 做转置归一化。
            // Iris 手部 pass 基座≈单位阵，转置归一化天然 no-op。
            handBasePose.set(poseStack.last().pose());
            // 入口原始快照（第 30 轮）：在锁视角修复可能改写基座之前留存，
            // 下游「斜向修复契约」消费者（约束三明治/枪口归一化）只认这一份。
            handBasePoseEntry.set(poseStack.last().pose());
            // 【案例⑧主修复 · 第 28 轮：开镜换弹/开火时整枪随朝向平移 + 后坐过分下压】
            //
            // 取证链（v3 探针 + 20:25 受控 ADS 辑拿，全部逐帧毫秒对齐）：
            // ① R = modelView × 手部基座(3x3) 在开镜状态下 ≡ 摄像机动画的**全量**旋转
            //    （lockAng 拟合 k=1.000 vs itemCam 角度，corr=0.998，全程逐帧成立）；
            // ② R 的旋转轴是**世界系固定轴**（N/S、N/E、N/W 同相位配对后世界系夹角
            //    中位 6.5°，与 N/N' 同朝向噪声底 6.5° 一致；视图系夹角则达 86°~162°）；
            // ③ 腰射同协议下 R 残差只有约一半、且用户无感——开镜（aiming→1、zoom
            //    放大画面）让同一根残差越过可见阈值；
            // ④ 即：26.2 的手部渲染链里，摄像机动画经 CameraMixin 欧拉叠加进
            //    camera.quaternion 后随世界视图矩阵进入了 modelView，而手部基座
            //    （BeforeRenderHandEvent 的 mulPose 消费）并没有收到与之匹配的世界系分量
            //    ——R≠I 的部分就是把整枪图像绕一根世界系轴刚性旋转；玩家转身时该轴在
            //    屏幕上的投影方向跟着转 ⇒「手臂+枪整体随朝向平移」；轴带俯仰分量
            //    ⇒ 部分朝向屏幕上呈现为「整体向下压」。
            // ⑤ Iris 手部 pass 基座≈单位阵、其 modelView 与基座天然互逆 ⇒ R≡I ⇒
            //    「开光影全部正常」的实测与本案同构 —— Iris 观感即为本案的正确参照系。
            //
            // 修复手法（强制锁视角）：捕获基座后把当前栈左乘 C=(B·MV)⁻¹：
            //   之后屏幕上 = MV·(C·B·X·p) = MV·(B·MV)⁻¹·B·X·p = X·p
            // 即图像恒等于「基座归一化后的 authored 局部内容」，与 Iris/上游观感逐位一致，
            // 与朝向完全解耦。一次捕获期 3 次矩阵乘法，开销可忽略。
            // 实验开关：RenderConfig.HAND_VIEW_LOCK_FIX（经在体否决后默认 false；
            // 仅保留用于复现实验，详见 RenderConfig）。
            // Iris 手部渲染激活时本 fix 恒为恒等矩阵，保持豁免、行为零变化。
            if (RenderConfig.HAND_VIEW_LOCK_FIX != null && RenderConfig.HAND_VIEW_LOCK_FIX.get()
                    && !IrisCompat.isHandRendererActive()) {
                try {
                    org.joml.Matrix4f mvNow = com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrixCopy();
                    // 【案例⑧ · 第 29 轮取证】v5 受控辑拿：此处的 modelView 顶部读取在约 39% 帧上
                    // 携带 0.9933~1.0068 的均匀缩放，而旋转分部与同帧稍后两次读取逐位一致
                    // （离线验证 B=s·MVᵀ、MV·B 非对角元恒 0、colNormDev=0.0067、F 出现 s² 对角）。
                    // 先落 lockCap 全量矩阵供离线指认该缩放矩阵的真实身份。
                    debugRecoilLockCap(mvNow);
                    // 【第 29 轮并行开关】剔除 mvNow 3x3 的列缩放：防止缩放经 (B·MV)⁻¹ 烙进
                    // 基座与整条姿态链（旋转分部不动，故绘制观感要么完全一致、要么只差 ≤0.7%
                    // 的均匀缩放——远低于可见阈值；并行开关可单独回退本步）。
                    if (RenderConfig.HAND_VIEW_LOCK_NORMALIZE != null
                            && RenderConfig.HAND_VIEW_LOCK_NORMALIZE.get()) {
                        float n0 = (float) Math.sqrt((double) (mvNow.m00() * mvNow.m00() + mvNow.m10() * mvNow.m10() + mvNow.m20() * mvNow.m20()));
                        float n1 = (float) Math.sqrt((double) (mvNow.m01() * mvNow.m01() + mvNow.m11() * mvNow.m11() + mvNow.m21() * mvNow.m21()));
                        float n2 = (float) Math.sqrt((double) (mvNow.m02() * mvNow.m02() + mvNow.m12() * mvNow.m12() + mvNow.m22() * mvNow.m22()));
                        if (n0 > 1.0e-8f && n1 > 1.0e-8f && n2 > 1.0e-8f) {
                            float i0 = 1.0f / n0, i1 = 1.0f / n1, i2 = 1.0f / n2;
                            mvNow.m00(mvNow.m00() * i0); mvNow.m10(mvNow.m10() * i0); mvNow.m20(mvNow.m20() * i0);
                            mvNow.m01(mvNow.m01() * i1); mvNow.m11(mvNow.m11() * i1); mvNow.m21(mvNow.m21() * i1);
                            mvNow.m02(mvNow.m02() * i2); mvNow.m12(mvNow.m12() * i2); mvNow.m22(mvNow.m22() * i2);
                        }
                    }
                    org.joml.Matrix4f lockC = new org.joml.Matrix4f(handBasePose).mul(mvNow);
                    if (Math.abs(lockC.determinant()) > 1.0e-8) {
                        lockC.invert();
                        poseStack.last().pose().set(lockC.mul(poseStack.last().pose()));
                        // 基座对象随之更新：下游所有 B′ 归一化探针/约束写入/枪口缓存
                        // 一律以校正后的基座为准，探针读数即修复后的在体事实。
                        handBasePose.set(poseStack.last().pose());
                    }
                } catch (Throwable ignored) {
                }
            }
            // 逆转原版施加在手上的延滞效果，改为写入模型动画数据中
            //
            // 【这里是枪械实际走的那一份】与父类 AnimateGeoItemRenderer#renderFirstPerson
            // 里那段是同一套公式的两份拷贝。改晃动手感时<b>两处必须同时改</b>，
            // 否则只有一半路径生效，表现为「有的枪改了有的没改」。
            // 缩放系数由父类的 aimingSwayScale 统一提供，避免两边算法漂移。
            float xRotOffset = Mth.lerp(partialTick, player.xBobO, player.xBob);
            float yRotOffset = Mth.lerp(partialTick, player.yBobO, player.yBob);
            float xRot = player.getViewXRot(partialTick) - xRotOffset;
            float yRot = player.getViewYRot(partialTick) - yRotOffset;
            float swayScale = aimingSwayScale(player, partialTick);
            poseStack.mulPose(Axis.XP.rotationDegrees(xRot * -0.1F * swayScale));
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot * -0.1F * swayScale));
            BedrockPart rootNode = gunModel.getRootNode();
            if (rootNode != null) {
                // tanh 饱和限幅保持在缩放【之前】：它防的是快速转身时枪飞出画面。
                xRot = (float) Math.tanh(xRot / 25) * 25 * swayScale;
                yRot = (float) Math.tanh(yRot / 25) * 25 * swayScale;
                rootNode.offsetX += yRot * 0.1F / 16F / 3F;
                rootNode.offsetY += -xRot * 0.1F / 16F / 3F;
                rootNode.additionalQuaternion.mul(Axis.XP.rotationDegrees(xRot * 0.05F));
                rootNode.additionalQuaternion.mul(Axis.YP.rotationDegrees(yRot * 0.05F));
            }
            // 从渲染原点 (0, 24, 0) 移动到模型原点 (0, 0, 0)
            poseStack.translate(0, 1.5f, 0);
            // 基岩版模型是上下颠倒的，需要翻转过来。
            poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
            // 【案例⑧ 探针】分段点 P1：基座之后、定位/约束之前的逐帧链位姿
            debugCase08ChainP1(poseStack.last().pose());
            // 应用持枪姿态变换，如第一人称摄像机定位
            FirstPersonRenderGunEvent.applyFirstPersonGunTransform(player, stack, poseStack, gunModel, partialTick);

            // 开启第一人称弹壳和火焰渲染
            MuzzleFlashRender.isSelf = true;
            ShellRender.isSelf = true;
            // 如果正在打开改装界面，则取消手臂渲染
            boolean renderHand = gunModel.getRenderHand();
            if (RefitTransform.getOpeningProgress() != 0) {
                gunModel.setRenderHand(false);
            }
            // 调用枪械模型渲染
            RenderType renderType = display.enablesTransparency()
                    ? RenderTypes.entityTranslucent(display.getModelTexture())
                    : RenderTypes.entityCutout(display.getModelTexture());
            // 【RecoilDebug 探针】第 27.3 轮：枪击瞬间枪模网格在屏幕上确证整体右偏（SW/NE 斜向），
            // 但入口基座/摄像机动画四元数/相机后坐包络全部干净——故把「枪根原点」在定位链施加
            // 之后的视图坐标也逐帧记下：若 viewRoot 与 viewMuzzle 同偏同量，偏转来自定位/延滞链；
            // 若 viewRoot 不偏而 viewMuzzle 偏，则来自骨骼动画层（枪口骨骼路径）。
            debugRecoilGunRoot(poseStack.last().pose());
            gunModel.submit(poseStack, stack, ctx, collector, renderType,
                    display.getModelTexture(), light, OverlayTexture.NO_OVERLAY);
            // 缓存枪口位置，为第一人称曳光弹渲染作准备
            cacheMuzzlePosition(poseStack, gunModel);
            // 【RecoilDebug 探针】第 27.5 轮：机瞄骨骼链视图坐标逐帧取证。
            // 已证根↔枪口链在开枪斜向帧冻结（±0.001），但用户目击「机瞄框偏离屏幕中线」——
            // 瞄具位于另一条骨骼链，若唯独它摆动，则动画写入层（AnimationRunner 通道）
            // 存在朝向污染（与当年摄像机动画通道同族）。
            debugRecoilSightPos(poseStack, gunModel);
            // 恢复手臂渲染
            gunModel.setRenderHand(renderHand);
            // 渲染完成后，将动画数据从模型中清除，不对其他视角下的模型渲染产生影响
            poseStack.popPose();
            gunModel.cleanAnimationTransform();
            // 关闭第一人称弹壳和火焰渲染
            MuzzleFlashRender.isSelf = false;
            ShellRender.isSelf = false;
        });
    }

    private static void cacheMuzzlePosition(PoseStack poseStack, BedrockGunModel gunModel) {
        if (gunModel.getMuzzleFlashPosPath() != null) {
            // 计算出枪口相对于摄像机中心的坐标
            poseStack.pushPose();
            for (BedrockPart bedrockPart : gunModel.getMuzzleFlashPosPath()) {
                bedrockPart.translateAndRotateAndScale(poseStack);
            }
            Matrix4f pose = poseStack.last().pose();
            double itemRenderFov = CameraSetupEvent.ITEM_MODEL_FOV_DYNAMICS.get();
            double levelRenderFov = CameraSetupEvent.WORLD_FOV_DYNAMICS.get();
            poseStack.popPose();
            // 【第 26 轮修复：第一人称曳光弹起点不锁枪口 / 开火弹道视觉随朝向向一侧固定拉偏】
            //
            // muzzleRenderOffset 的上游不变量是【视图空间（摄像机局部）坐标】，
            // EntityBulletRenderer 里再 camera.rotation() 转到世界轴平移。
            // 但 26.2 原生手部 pass 进入渲染时 PoseStack 不再是身份阵 —— 26.2 字节码确认
            // GameRenderer#renderItemInHand(cameraState, tickDelta, viewRotationMatrix) 开头:
            //   poseStack.mulPose(new Matrix4f(viewRotationMatrix).invert());   // base = R(q)，view→world
            //   RenderSystem.getModelViewStack().pushMatrix().mul(viewRotationMatrix); // modelView = W2V，绘制时抵消
            // （viewRotationMatrix = Camera#getViewRotationMatrix: R(camera.rotation().conjugate())）
            //
            // 因此 vanilla 26.2 下 pose.last().pose().m30/31/32 读到的不是 v，而是 R(q)·v（世界轴位移），
            // 直接缓存后，EntityBulletRenderer 侧再 rotate(camera.rotation()) 一次就成了 R(q)²·v ——
            // 枪口偏移被【二倍朝向角】旋转：面南/面北方向几乎看不出，斜向（东南/西南/东北/西北）
            // 时整串曳光弹起点被甩到枪口的一侧，看上去就像「后坐力固定向左/右偏」。
            // （这正是第 25 轮日志里 globalMuzzle 随 yaw 在 ±1.8 间按正弦摆动的来源。）
            //
            // 修法（采集端归一化 · 自校正版）：不再依赖 RenderSystem modelView 的假定内容
            // （实测该值在 26.2 vanilla 手部 pass 内是不受控的 —— 26.2 的绘制矩阵经由
            // SubmitNodeCollector/DynamicTransforms 下发，RenderSystem modelView 仅为
            // 兼容保留，上一轮用它做还原的尝试实测无效），而是直接转置【入口基座矩阵 B】：
            //   B 与枪口矩阵共享同一条变换前缀（基座预乘 + bob + 伤害后仰），
            //   Bᵀ · (m30..m32 - B.m30..m32) 恒等于 1.21.1 语义下的视图空间枪口偏移 v，
            //   与基座具体是什么无关 —— vanilla（B≈R(q)）、Iris 手部 pass（B≈I）
            //   以及其他任何 shader 管线均同时正确，无需分支。
            // B 的 3x3 是正交旋转（手部 pass 里不会出现非均匀缩放），故逆 = 转置。
            float mx0 = pose.m30();
            float my0 = pose.m31();
            float mz0 = pose.m32();
            // 【第 30 轮】归一化基座恢复为入口原始快照 handBasePoseEntry（锁视角修复
            // 不再改写下游契约；与第 26 轮实测定案的版本逐位一致）。
            float dx = mx0 - handBasePoseEntry.m30();
            float dy = my0 - handBasePoseEntry.m31();
            float dz = mz0 - handBasePoseEntry.m32();
            float viewX = handBasePoseEntry.m00() * dx + handBasePoseEntry.m01() * dy + handBasePoseEntry.m02() * dz;
            float viewY = handBasePoseEntry.m10() * dx + handBasePoseEntry.m11() * dy + handBasePoseEntry.m12() * dz;
            float viewZ = handBasePoseEntry.m20() * dx + handBasePoseEntry.m21() * dy + handBasePoseEntry.m22() * dz;
            // FOV 比值换算作用于【视图空间】的深度 z（开镜时手部 FOV 与世界 FOV 分离的补偿）。
            // 旧代码乘在 pose.m32() 上 —— 那在 vanilla 26.2 下是世界轴 Z（正北方向），与视深无关。
            double fovScale = Math.tan(itemRenderFov / 2 * Math.PI / 180) / Math.tan(levelRenderFov / 2 * Math.PI / 180);
            muzzleRenderOffset.set(viewX, viewY, (float) (viewZ * fovScale));
            debugMuzzleSpace(mx0, my0, mz0, viewX, viewY, viewZ, fovScale);
        }
    }

    // —— 枪口空间诊断（配合 RenderConfig.TRACER_DEBUG / RECOIL_DEBUG；后者逐帧，仅供对照实证）——
    private static long debugMuzzleSpaceLastLog = 0L;

    private static void debugMuzzleSpace(float rawX, float rawY, float rawZ,
                                         float viewX, float viewY, float viewZ, double fovScale) {
        try {
            boolean perFrame = RenderConfig.RECOIL_DEBUG != null && RenderConfig.RECOIL_DEBUG.get();
            if (RenderConfig.TRACER_DEBUG == null || !RenderConfig.TRACER_DEBUG.get()) {
                if (!perFrame) {
                    return;
                }
            }
            long now = System.currentTimeMillis();
            if (!perFrame) {
                if (now - debugMuzzleSpaceLastLog < 1000) {
                    return;
                }
                debugMuzzleSpaceLastLog = now;
            }
            net.minecraft.client.Camera cam = Minecraft.getInstance().gameRenderer.mainCamera();
            Matrix4f modelViewAtHand = RenderSystem.getModelViewMatrixCopy();
            GunMod.LOGGER.info(
                    "[TACZ MuzzleSpace] camera=({},{}) irisHand={} baseT=({},{},{}) rawMuzzle=({},{},{}) viewMuzzle=({},{},{}) fovScale={} modelViewRot=[{},{},{}; {},{},{}; {},{},{}] modelViewT=({},{},{})",
                    trim2(cam.xRot()), trim2(cam.yRot()), IrisCompat.isHandRendererActive(),
                    trim2(handBasePose.m30()), trim2(handBasePose.m31()), trim2(handBasePose.m32()),
                    trim2(rawX), trim2(rawY), trim2(rawZ),
                    trim2(viewX), trim2(viewY), trim2(viewZ), trim2(fovScale),
                    trim2(modelViewAtHand.m00()), trim2(modelViewAtHand.m01()), trim2(modelViewAtHand.m02()),
                    trim2(modelViewAtHand.m10()), trim2(modelViewAtHand.m11()), trim2(modelViewAtHand.m12()),
                    trim2(modelViewAtHand.m20()), trim2(modelViewAtHand.m21()), trim2(modelViewAtHand.m22()),
                    trim2(modelViewAtHand.m30()), trim2(modelViewAtHand.m31()), trim2(modelViewAtHand.m32()));
        } catch (Throwable ignored) {
        }
    }

    private static String trim2(double v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }

    private static long lockCapFrameCount = 0L;
    private static long lockCapLastLogMs = 0L;

    /**
     * 【案例⑧ · 第 29 轮取证探针】把锁视角修复首次读取的 modelView（mvNow）逐帧落档：
     * 三列模长（检出 0.9933~1.0068 缩放突发的直接证据）+ 完整 3x3 + 平移列 + 朝向/iris 态。
     * 平移列用于离线指认该矩阵属于哪个渲染 pass（手部括弧理论平移≈0；GUI/世界则不然）。
     * 缩放异常帧（最大列模偏差 &gt; 0.002）全量落（50ms 节流）；正常帧每 200 帧心跳一次。
     */
    private static void debugRecoilLockCap(org.joml.Matrix4f mvNow) {
        try {
            if (RenderConfig.RECOIL_DEBUG == null || !RenderConfig.RECOIL_DEBUG.get()) {
                return;
            }
            float n0 = (float) Math.sqrt((double) (mvNow.m00() * mvNow.m00() + mvNow.m10() * mvNow.m10() + mvNow.m20() * mvNow.m20()));
            float n1 = (float) Math.sqrt((double) (mvNow.m01() * mvNow.m01() + mvNow.m11() * mvNow.m11() + mvNow.m21() * mvNow.m21()));
            float n2 = (float) Math.sqrt((double) (mvNow.m02() * mvNow.m02() + mvNow.m12() * mvNow.m12() + mvNow.m22() * mvNow.m22()));
            float dev = Math.max(Math.abs(n0 - 1), Math.max(Math.abs(n1 - 1), Math.abs(n2 - 1)));
            lockCapFrameCount++;
            boolean anomaly = dev > 0.002f;
            if (!anomaly && (lockCapFrameCount % 200L) != 0L) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lockCapLastLogMs < 50L) {
                return;
            }
            lockCapLastLogMs = now;
            net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
            float fy = player == null ? Float.NaN : net.minecraft.util.Mth.wrapDegrees(player.getYRot());
            float fx = player == null ? Float.NaN : player.getXRot();
            GunMod.LOGGER.info(
                    "[TACZ RecoilDebug] lockCap ms={} colN=({},{},{}) mv=[{},{},{} {},{},{} {},{},{}] mvT=({},{},{}) facing=({},{}) irisHand={} anomaly={}",
                    now,
                    trim2(n0), trim2(n1), trim2(n2),
                    trim2(mvNow.m00()), trim2(mvNow.m01()), trim2(mvNow.m02()),
                    trim2(mvNow.m10()), trim2(mvNow.m11()), trim2(mvNow.m12()),
                    trim2(mvNow.m20()), trim2(mvNow.m21()), trim2(mvNow.m22()),
                    trim2(mvNow.m30()), trim2(mvNow.m31()), trim2(mvNow.m32()),
                    trim2(fx), trim2(fy),
                    IrisCompat.isHandRendererActive(), anomaly);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 【RecoilDebug 探针】把机瞄定位组骨骼链（iron sight path，未装瞄具时的瞄具所在链）
     * 的末端原点换算到视图空间并逐帧打印，口径与 cacheMuzzlePosition 完全一致的 Bᵀ 转置归一化，
     * 可直接与 viewRoot / viewMuzzle 三分量互扣。装在枪身上的任何一条骨骼链的漂移由此现形。
     */
    private static void debugRecoilSightPos(PoseStack poseStack, BedrockGunModel gunModel) {
        try {
            if (RenderConfig.RECOIL_DEBUG == null || !RenderConfig.RECOIL_DEBUG.get()) {
                return;
            }
            List<BedrockPart> path = gunModel.getIronSightPath();
            if (path == null || path.isEmpty()) {
                return;
            }
            logSightPathPose(poseStack, path, "sight");
            List<BedrockPart> scopePath = gunModel.getScopePosPath();
            if (scopePath != null && !scopePath.isEmpty()) {
                logSightPathPose(poseStack, scopePath, "scope");
            }
        } catch (Throwable ignored) {
        }
    }

    private static void logSightPathPose(PoseStack poseStack, List<BedrockPart> path, String tag) {
        try {
            poseStack.pushPose();
            for (BedrockPart part : path) {
                part.translateAndRotateAndScale(poseStack);
            }
            Matrix4f pose = poseStack.last().pose();
            poseStack.popPose();
            float dx = pose.m30() - handBasePose.m30();
            float dy = pose.m31() - handBasePose.m31();
            float dz = pose.m32() - handBasePose.m32();
            float viewX = handBasePose.m00() * dx + handBasePose.m01() * dy + handBasePose.m02() * dz;
            float viewY = handBasePose.m10() * dx + handBasePose.m11() * dy + handBasePose.m12() * dz;
            float viewZ = handBasePose.m20() * dx + handBasePose.m21() * dy + handBasePose.m22() * dz;
            net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
            float fy = player == null ? Float.NaN : net.minecraft.util.Mth.wrapDegrees(player.getYRot());
            float fx = player == null ? Float.NaN : player.getXRot();
            GunMod.LOGGER.info(
                    "[TACZ RecoilDebug] {}Pos view=({},{},{}) raw=({},{},{}) facing=({},{}) shader={} irisHand={}",
                    tag,
                    trim2(viewX), trim2(viewY), trim2(viewZ),
                    trim2(pose.m30()), trim2(pose.m31()), trim2(pose.m32()),
                    trim2(fx), trim2(fy),
                    IrisCompat.isUsingRenderPack(), IrisCompat.isHandRendererActive());
        } catch (Throwable ignored) {
        }
    }

    /**
     * 【RecoilDebug 探针】逐帧打印「枪根原点」在世界轴（raw）与视图空间（viewRoot）的坐标，
     * 以及此刻矩阵 3x3 的列正交偏差（捕捉非均匀缩放/剪切），随帧附带 facing/shader。
     * 视图空间换算与 cacheMuzzlePosition 同一转置归一化（Bᵀ·(t−B.t)），保证与 viewMuzzle 可直接相减对比。
     */
    private static void debugRecoilGunRoot(Matrix4f pose) {
        try {
            if (RenderConfig.RECOIL_DEBUG == null || !RenderConfig.RECOIL_DEBUG.get()) {
                return;
            }
            float dx = pose.m30() - handBasePose.m30();
            float dy = pose.m31() - handBasePose.m31();
            float dz = pose.m32() - handBasePose.m32();
            float viewX = handBasePose.m00() * dx + handBasePose.m01() * dy + handBasePose.m02() * dz;
            float viewY = handBasePose.m10() * dx + handBasePose.m11() * dy + handBasePose.m12() * dz;
            float viewZ = handBasePose.m20() * dx + handBasePose.m21() * dy + handBasePose.m22() * dz;
            double c0 = Math.sqrt(pose.m00() * pose.m00() + pose.m10() * pose.m10() + pose.m20() * pose.m20());
            double c1 = Math.sqrt(pose.m01() * pose.m01() + pose.m11() * pose.m11() + pose.m21() * pose.m21());
            double c2 = Math.sqrt(pose.m02() * pose.m02() + pose.m12() * pose.m12() + pose.m22() * pose.m22());
            double dev = Math.max(Math.abs(c0 - 1), Math.max(Math.abs(c1 - 1), Math.abs(c2 - 1)));
            net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
            float fy = player == null ? Float.NaN : net.minecraft.util.Mth.wrapDegrees(player.getYRot());
            float fx = player == null ? Float.NaN : player.getXRot();
            // 【案例⑧ · 第三轮】锁残差 R = modelView × handBasePose(3x3) 的轴角分解。
            // 19:25 辑拿：ADS 下 R 恒为 ~1.4-2.1° 的成建制旋转、轴随朝向翻转（世界系固定轴），
            // hip-S 桶 R=I —— 破锁仅存在于开镜态。此项给出相位精确(与 gunRoot 同 ms)的 R(t)，
            // 供离线把「整枪图像刚性旋转」按方位逐相位核对用户指纹。
            double lockAng = 0, lockAxX = 0, lockAxY = 0, lockAxZ = 1;
            // 【案例⑧ · 第 29 轮】此处读到的 modelView 也与 lockCap 对照全量落档：
            // 两次读取若列模长不一致，即为「同一调用内顶部内容被改写」的在体铁证。
            double mv00 = 0, mv01 = 0, mv02 = 0, mv10 = 0, mv11 = 0, mv12 = 0, mv20 = 0, mv21 = 0, mv22 = 0;
            double mvN0 = -1, mvN1 = -1, mvN2 = -1;
            try {
                org.joml.Matrix4f mv4 = com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrixCopy();
                mv00 = mv4.m00(); mv01 = mv4.m01(); mv02 = mv4.m02();
                mv10 = mv4.m10(); mv11 = mv4.m11(); mv12 = mv4.m12();
                mv20 = mv4.m20(); mv21 = mv4.m21(); mv22 = mv4.m22();
                mvN0 = Math.sqrt(mv00 * mv00 + mv10 * mv10 + mv20 * mv20);
                mvN1 = Math.sqrt(mv01 * mv01 + mv11 * mv11 + mv21 * mv21);
                mvN2 = Math.sqrt(mv02 * mv02 + mv12 * mv12 + mv22 * mv22);
                double r00 = mv4.m00() * handBasePose.m00() + mv4.m01() * handBasePose.m10() + mv4.m02() * handBasePose.m20();
                double r01 = mv4.m00() * handBasePose.m01() + mv4.m01() * handBasePose.m11() + mv4.m02() * handBasePose.m21();
                double r02 = mv4.m00() * handBasePose.m02() + mv4.m01() * handBasePose.m12() + mv4.m02() * handBasePose.m22();
                double r10 = mv4.m10() * handBasePose.m00() + mv4.m11() * handBasePose.m10() + mv4.m12() * handBasePose.m20();
                double r11 = mv4.m10() * handBasePose.m01() + mv4.m11() * handBasePose.m11() + mv4.m12() * handBasePose.m21();
                double r12 = mv4.m10() * handBasePose.m02() + mv4.m11() * handBasePose.m12() + mv4.m12() * handBasePose.m22();
                double r20 = mv4.m20() * handBasePose.m00() + mv4.m21() * handBasePose.m10() + mv4.m22() * handBasePose.m20();
                double r21 = mv4.m20() * handBasePose.m01() + mv4.m21() * handBasePose.m11() + mv4.m22() * handBasePose.m21();
                double r22 = mv4.m20() * handBasePose.m02() + mv4.m21() * handBasePose.m12() + mv4.m22() * handBasePose.m22();
                double tr = r00 + r11 + r22;
                lockAng = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (tr - 1) / 2))));
                double ax = r21 - r12, ay = r02 - r20, az = r10 - r01;
                double an = Math.sqrt(ax * ax + ay * ay + az * az);
                if (an > 1e-9) {
                    lockAxX = ax / an; lockAxY = ay / an; lockAxZ = az / an;
                }
            } catch (Throwable ignored2) {
            }
            GunMod.LOGGER.info(
                    "[TACZ RecoilDebug] gunRoot ms={} viewRoot=({},{},{}) raw=({},{},{}) colNormDev={} shear=m01/m10 {}/{} m02/m20 {}/{} facing=({},{}) shader={} irisHand={} lockAng={} lockAxis=({},{},{}) mvColN=({},{},{}) mv=[{},{},{} {},{},{} {},{},{}]",
                    System.currentTimeMillis(),
                    trim2(viewX), trim2(viewY), trim2(viewZ),
                    trim2(pose.m30()), trim2(pose.m31()), trim2(pose.m32()),
                    String.format(java.util.Locale.ROOT, "%.6f", dev),
                    trim2(pose.m01()), trim2(pose.m10()), trim2(pose.m02()), trim2(pose.m20()),
                    trim2(fx), trim2(fy),
                    IrisCompat.isUsingRenderPack(), IrisCompat.isHandRendererActive(),
                    trim2(lockAng), trim2(lockAxX), trim2(lockAxY), trim2(lockAxZ),
                    trim2(mvN0), trim2(mvN1), trim2(mvN2),
                    trim2(mv00), trim2(mv01), trim2(mv02),
                    trim2(mv10), trim2(mv11), trim2(mv12),
                    trim2(mv20), trim2(mv21), trim2(mv22));
        } catch (Throwable ignored) {
        }
    }

    /**
     * 【案例⑧ 探针】链上分段取证：在「基座捕获之后、applyFirstPersonGunTransform 之前」
     * （即 lag 逆转 + Z180 翻转完成、定位/约束尚未施加的分段点）逐帧落 B′ 归一化的视图坐标。
     * 与 gunRoot（链末端）成对比较：若本点已随朝向漂移，泄漏在基座/lag/摄像机动画段；
     * 若本点稳定而 gunRoot 漂移，泄漏在定位/约束写入段。
     */
    private static void debugCase08ChainP1(Matrix4f pose) {
        try {
            if (RenderConfig.RECOIL_DEBUG == null || !RenderConfig.RECOIL_DEBUG.get()) {
                return;
            }
            float dx = pose.m30() - handBasePose.m30();
            float dy = pose.m31() - handBasePose.m31();
            float dz = pose.m32() - handBasePose.m32();
            float viewX = handBasePose.m00() * dx + handBasePose.m01() * dy + handBasePose.m02() * dz;
            float viewY = handBasePose.m10() * dx + handBasePose.m11() * dy + handBasePose.m12() * dz;
            float viewZ = handBasePose.m20() * dx + handBasePose.m21() * dy + handBasePose.m22() * dz;
            net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
            float fy = player == null ? Float.NaN : net.minecraft.util.Mth.wrapDegrees(player.getYRot());
            float fx = player == null ? Float.NaN : player.getXRot();
            GunMod.LOGGER.info(
                    "[TACZ Case08] chainP1 ms={} view=({},{},{}) facing=({},{})",
                    System.currentTimeMillis(),
                    trim2(viewX), trim2(viewY), trim2(viewZ),
                    trim2(fx), trim2(fy));
        } catch (Throwable ignored) {
        }
    }

    /**
     * 【案例⑧ 探针 · 第三轮新增】链上中段取证点 P2：「瞄准定位 lerp 之后、动画约束写入之前」。
     * 19:25 ADS 辑拿证实：chainP1 四朝向逐位一致而 gunRoot 漂移 0.02~0.05，
     * 且 F=BᵀP（局部链 3x3）呈 {N,S}/{E,W}/{UP,DN} 三块结构（桶间矩阵范数差 ~2.6）。
     * 本探针把 B′ 归一化平移 与 B′ᵀ·P 局部 3x3 同时落日志：
     * 漂移若在此点已出现 → 定位段（applyFirstPersonPositioningTransform）携带朝向内容；
     * 若此点干净而 gunRoot 漂移 → 只剩约束写入段。
     */
    public static void debugCase08ChainP2(PoseStack poseStack) {
        try {
            if (RenderConfig.RECOIL_DEBUG == null || !RenderConfig.RECOIL_DEBUG.get()) {
                return;
            }
            Matrix4f pose = poseStack.last().pose();
            float dx = pose.m30() - handBasePose.m30();
            float dy = pose.m31() - handBasePose.m31();
            float dz = pose.m32() - handBasePose.m32();
            float viewX = handBasePose.m00() * dx + handBasePose.m01() * dy + handBasePose.m02() * dz;
            float viewY = handBasePose.m10() * dx + handBasePose.m11() * dy + handBasePose.m12() * dz;
            float viewZ = handBasePose.m20() * dx + handBasePose.m21() * dy + handBasePose.m22() * dz;
            // B′ᵀ·P（局部链 3x3；行主序。B′ 为捕获基座，名义上列正交 ⇒ 转置≈逆）
            float f00 = handBasePose.m00() * pose.m00() + handBasePose.m10() * pose.m10() + handBasePose.m20() * pose.m20();
            float f01 = handBasePose.m00() * pose.m01() + handBasePose.m10() * pose.m11() + handBasePose.m20() * pose.m21();
            float f02 = handBasePose.m00() * pose.m02() + handBasePose.m10() * pose.m12() + handBasePose.m20() * pose.m22();
            float f10 = handBasePose.m01() * pose.m00() + handBasePose.m11() * pose.m10() + handBasePose.m21() * pose.m20();
            float f11 = handBasePose.m01() * pose.m01() + handBasePose.m11() * pose.m11() + handBasePose.m21() * pose.m21();
            float f12 = handBasePose.m01() * pose.m02() + handBasePose.m11() * pose.m12() + handBasePose.m21() * pose.m22();
            float f20 = handBasePose.m02() * pose.m00() + handBasePose.m12() * pose.m10() + handBasePose.m22() * pose.m20();
            float f21 = handBasePose.m02() * pose.m01() + handBasePose.m12() * pose.m11() + handBasePose.m22() * pose.m21();
            float f22 = handBasePose.m02() * pose.m02() + handBasePose.m12() * pose.m12() + handBasePose.m22() * pose.m22();
            net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
            float fy = player == null ? Float.NaN : net.minecraft.util.Mth.wrapDegrees(player.getYRot());
            float fx = player == null ? Float.NaN : player.getXRot();
            GunMod.LOGGER.info(
                    "[TACZ Case08] chainP2 ms={} view=({},{},{}) F=[{},{},{} {},{},{} {},{},{}] facing=({},{})",
                    System.currentTimeMillis(),
                    trim2(viewX), trim2(viewY), trim2(viewZ),
                    trim2(f00), trim2(f01), trim2(f02), trim2(f10), trim2(f11), trim2(f12), trim2(f20), trim2(f21), trim2(f22),
                    trim2(fx), trim2(fy));
        } catch (Throwable ignored) {
        }
    }


    @Override
    public void renderByItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext transformType, @Nonnull PoseStack poseStack, @Nonnull SubmitNodeCollector collector,
                             int pPackedLight, int pPackedOverlay) {
        if (!(stack.getItem() instanceof IGun)) {
            return;
        }
        poseStack.pushPose();
        TimelessAPI.getGunDisplay(stack).ifPresentOrElse(gunIndex -> {
            // 第一人称就不渲染了，交给别的地方
            if (transformType == FIRST_PERSON_LEFT_HAND || transformType == FIRST_PERSON_RIGHT_HAND) {
                return;
            }
            // 第三人称「副手」不渲染 —— 副手枪改由 HumanoidOffhandRender 以背挂姿态绘制。
            //
            // 【本轮修复：左利手玩家第三人称看不到主手枪】
            //
            // 上游 1.21.1 这里写的是「transformType == THIRD_PERSON_LEFT_HAND 就 return」，
            // 配合它的 mixin「arm == LEFT 就 cancel」，两处都<b>把「左手」等同于「副手」</b>。
            // 对左利手玩家（getMainArm() == LEFT）这个等式不成立：他的主手就是左手，
            // 于是主手那把枪要么被 mixin 取消、要么走到这里被 return —— 两条路都画不出来。
            // 这是上游就有的缺陷，不是移植引入的。
            //
            // 26.2 的 ArmedEntityRenderState 明确带了 mainArm 字段（字节码确认），
            // 因此可以严格按「是不是副手」判定，而不是按「是不是左手」。
            // ItemInHandLayerMixin 在放行主手那一侧时会置位 IS_MAIN_HAND_SUBMIT，
            // 这里据此区分「左手＝主手」与「左手＝副手」两种情况。
            //
            // 该标志的读写严格同步：ItemStackRenderState#submit 内部是<b>直接</b>调用
            // SpecialModelRenderer#submit（字节码确认，无延迟队列），
            // 也就是本方法就在 mixin 的 HEAD/TAIL 之间执行，不存在跨帧残留。
            if (transformType == THIRD_PERSON_LEFT_HAND && !IS_MAIN_HAND_SUBMIT) {
                return;
            }
            // GUI 特殊渲染
            if (transformType == GUI) {
                renderSlotTexture(poseStack, collector, pPackedLight, pPackedOverlay, gunIndex.getSlotTexture());
                return;
            }
            // 剩下的渲染
            BedrockGunModel gunModel;
            Identifier gunTexture;
            Pair<BedrockGunModel, Identifier> lodModel = gunIndex.getLodModel();
            if (lodModel == null || RenderDistance.inRenderHighPolyModelDistance(poseStack)) {
                gunModel = gunIndex.getGunModel();
                gunTexture = gunIndex.getModelTexture();
            } else {
                gunModel = lodModel.getLeft();
                gunTexture = lodModel.getRight();
            }
            if (gunModel == null) {
                renderSlotTexture(poseStack, collector, pPackedLight, pPackedOverlay, gunIndex.getSlotTexture());
                return;
            }
            // 移动到模型原点
            poseStack.translate(0.5, 2, 0.5);
            // 反转模型
            poseStack.scale(-1, -1, 1);
            // 应用定位组的变换（位移和旋转，不包括缩放）
            applyPositioningTransform(transformType, gunIndex.getTransform().getScale(), gunModel, poseStack);
            // 应用 display 数据中的缩放
            applyScaleTransform(transformType, gunIndex.getTransform().getScale(), poseStack);
            // 渲染枪械模型
            RenderType renderType = RenderTypes.entityCutout(gunTexture);
            gunModel.submit(poseStack, stack, transformType, collector, renderType,
                    gunTexture, pPackedLight, pPackedOverlay);
        }, () -> {
            // 没有这个 gunID，渲染个错误材质提醒别人
            renderSlotTexture(poseStack, collector, pPackedLight, pPackedOverlay, MissingTextureAtlasSprite.getLocation());
        });
        poseStack.popPose();
    }

    private static void renderSlotTexture(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, int packedOverlay, Identifier texture) {
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Axis.ZN.rotationDegrees(180));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture), (pose, buffer) -> {
            // 26.2: 必须使用回调参数 pose（= 提交那一刻 poseStack.last().copy() 的快照），
            // 而不是外层 poseStack —— 回调执行时它早已被 popPose/复用，
            // 结果就是图标被画到错误位置（物品栏一片空白）。
            PoseStack tacz$snapshotPose = new PoseStack();
            tacz$snapshotPose.last().pose().set(pose.pose());
            tacz$snapshotPose.last().normal().set(pose.normal());
            SLOT_GUN_MODEL.renderToBuffer(tacz$snapshotPose, buffer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        });
    }

    private static void applyPositioningTransform(ItemDisplayContext transformType, TransformScale scale, BedrockGunModel model,
                                                  PoseStack poseStack) {
        switch (transformType) {
            case FIXED -> applyPositioningNodeTransform(model.getFixedOriginPath(), poseStack, scale.getFixed());
            case GROUND -> applyPositioningNodeTransform(model.getGroundOriginPath(), poseStack, scale.getGround());
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND ->
                    applyPositioningNodeTransform(model.getThirdPersonHandOriginPath(), poseStack, scale.getThirdPerson());
        }
    }

    private static void applyScaleTransform(ItemDisplayContext transformType, TransformScale scale, PoseStack poseStack) {
        if (scale == null) {
            return;
        }
        Vector3f vector3f = null;
        switch (transformType) {
            case FIXED -> vector3f = scale.getFixed();
            case GROUND -> vector3f = scale.getGround();
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> vector3f = scale.getThirdPerson();
        }
        if (vector3f != null) {
            poseStack.translate(0, 1.5, 0);
            poseStack.scale(vector3f.x(), vector3f.y(), vector3f.z());
            poseStack.translate(0, -1.5, 0);
        }
    }

    private static void applyPositioningNodeTransform(List<BedrockPart> nodePath, PoseStack poseStack, Vector3f scale) {
        if (nodePath == null) {
            return;
        }
        if (scale == null) {
            scale = new Vector3f(1, 1, 1);
        }
        // 应用定位组的反向位移、旋转，使定位组的位置就是渲染中心
        poseStack.translate(0, 1.5, 0);
        for (int i = nodePath.size() - 1; i >= 0; i--) {
            BedrockPart t = nodePath.get(i);
            poseStack.mulPose(Axis.XN.rotation(t.xRot));
            poseStack.mulPose(Axis.YN.rotation(t.yRot));
            poseStack.mulPose(Axis.ZN.rotation(t.zRot));
            if (t.getParent() != null) {
                poseStack.translate(-t.x * scale.x() / 16.0F, -t.y * scale.y() / 16.0F, -t.z * scale.z() / 16.0F);
            } else {
                poseStack.translate(-t.x * scale.x() / 16.0F, (1.5F - t.y / 16.0F) * scale.y(), -t.z * scale.z() / 16.0F);
            }
        }
        poseStack.translate(0, -1.5, 0);
    }
}
