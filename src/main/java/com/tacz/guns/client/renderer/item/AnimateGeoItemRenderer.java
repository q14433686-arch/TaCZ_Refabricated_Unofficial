package com.tacz.guns.client.renderer.item;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.IFPAnimationInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.IFPGeoItemRenderer;
import com.maydaymemory.mae.basic.DummyPose;
import com.maydaymemory.mae.basic.Pose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.item.IAnimationItem;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.animation.statemachine.ItemAnimationStateContext;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.util.math.MathUtil;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

/**
 * 抽象的基岩版动画物品模型BEWLR，包含一些默认实现
 *
 * @param <M>   基岩版模型
 * @param <CTX> 动画状态机上下文
 */
public abstract class AnimateGeoItemRenderer<M extends BedrockAnimatedModel, CTX extends ItemAnimationStateContext>
        implements IFPGeoItemRenderer, BuiltinItemRendererRegistry.DynamicItemRenderer {
    @Nullable
    protected LuaAnimationStateMachine<CTX> stateMachine;
    protected M model;

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, SubmitNodeCollector collector, int light, int overlay) {
        // 第一人称<b>不</b>在这里处理。
        //
        // 该方法由 ItemModel(tacz:dynamic_item) 的 SpecialModelRenderer 调用，此时 vanilla 的
        // ItemInHandRenderer#submitArmWithItem 已经施加了 applyItemArmTransform（±0.56/-0.52/-0.72）、
        // 挥动动画和装备抬手动画，PoseStack 不再是上游 1.21.1 所预期的干净矩阵 ——
        // 会导致枪相对摄像机位置/缩放错误，且移动时与 TACZ 动画叠加产生抖动。
        //
        // 正确入口是 ItemInHandRendererMixin#tacz$submitArmWithAnimatedItem：它包裹
        // submitHandsWithItems -> submitArmWithItem 的调用并跳过后者，语义与
        // SimpleBedrockModel 的 RenderHandEvent 取消点一致。详见该 mixin 注释。
        //
        // 这里仍需处理 firstPerson 分支的兜底：正常情况下走不到（mixin 已 cancel），
        // 但如果 mixin 因故未生效，直接 return 也比画在错误位置好 —— 至少不会出现"双份枪"。
        if (mode.firstPerson()) {
            return;
        }
        this.renderByItem(stack, mode, matrices, collector, light, overlay);
    }

    public Identifier textureLocation;

    public AnimateGeoItemRenderer() {
    }

    public void setModel(M model) {
        this.model = model;
    }

    public M getModel(ItemStack stack) {
        return model;
    }

    @Nullable
    public LuaAnimationStateMachine<CTX> getStateMachine(ItemStack stack) {
        return stateMachine;
    }

    public Identifier getTextureLocation(ItemStack stack) {
        return textureLocation;
    }

    public RenderType getRenderType(ItemStack stack) {
        return RenderTypes.entityCutout(getTextureLocation(stack));
    }

    public boolean needReInit(ItemStack stack) {
        var stateMachine = getStateMachine(stack);
        if (stateMachine == null) {
            return false;
        }
        return !stateMachine.isInitialized() && stateMachine.getExitingTime() < System.currentTimeMillis();
    }

    public abstract CTX initContext(ItemStack stack, Player player, float partialTick);

    public abstract void updateContext(CTX context, ItemStack stack, Player player, float partialTick);

    /**
     * 计算并返回切出动画的时长，单位ms
     *
     * @return 保持时间
     */
    public long getPutAwayTime(ItemStack stack) {
        return 0;
    }

    /**
     * 尝试初始化状态机并触发切入信号
     */
    public void tryInit(ItemStack stack, Player player, float partialTick) {
        var stateMachine = getStateMachine(stack);
        if (stateMachine == null) {
            return;
        }
        if (stateMachine.isInitialized()) {
            stateMachine.exit();
        }

        stateMachine.setContext(initContext(stack, player, partialTick));
        stateMachine.initialize();

        stateMachine.trigger(GunAnimationConstant.INPUT_DRAW);
    }

    /**
     * 尝试退出状态机并触发切出信号
     */
    public void tryExit(ItemStack stack, long putAwayTime) {
        var stateMachine = getStateMachine(stack);
        if (stateMachine == null) {
            return;
        }
        stateMachine.processContextIfExist(context -> {
            context.setPutAwayTime(putAwayTime / 1000F);
        });
        if (stateMachine.isInitialized()) {
            stateMachine.trigger(GunAnimationConstant.INPUT_PUT_AWAY);
            
//            KeepingItemRenderer.getRenderer().keep(stack, putAwayTime);
            stateMachine.exit();
            // 需要设置的比动画稍长些，避免意外的重初始化（可能是丢精度了）
            // 延后一tick应该基本没有感知）
            stateMachine.setExitingTime(putAwayTime + 50);
        }
    }

    /**
     * 尝试触发状态机转移
     *
     * @param input 输入信号
     */
    public void triggerAnimation(ItemStack stack, String input) {
        var stateMachine = getStateMachine(stack);
        if (stateMachine == null) {
            return;
        }
        stateMachine.trigger(input);
    }

    /**
     * 更新状态机但是不进行模型写入，用于播放音效
     */
    public void visualUpdate(ItemStack stack) {
        var stateMachine = getStateMachine(stack);
        if (stateMachine == null) {
            return;
        }
        stateMachine.visualUpdate();
    }

    /**
     * 应用状态机的世界摄像机动画，暂时只用于玩家
     */
    public void applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles event, ItemStack stack, LocalPlayer player) {
        this.applyLevelCameraAnimation(event, stack, 1);
    }

    public void applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles event, ItemStack stack, float multiplier) {
        var model = getModel(stack);
        if (model == null) {
            return;
        }
        Quaternionf q = MathUtil.multiplyQuaternion(model.getCameraAnimationObject().rotationQuaternion, multiplier);
        double yaw = Math.asin(2 * (q.w() * q.y() - q.x() * q.z()));
        double pitch = Math.atan2(2 * (q.w() * q.x() + q.y() * q.z()), 1 - 2 * (q.x() * q.x() + q.y() * q.y()));
        double roll = Math.atan2(2 * (q.w() * q.z() + q.x() * q.y()), 1 - 2 * (q.y() * q.y() + q.z() * q.z()));
        yaw = Math.toDegrees(yaw);
        pitch = Math.toDegrees(pitch);
        roll = Math.toDegrees(roll);
        float inYaw = event.getYaw();
        float inPitch = event.getPitch();
        float inRoll = event.getRoll();
        event.setYaw((float) yaw + inYaw);
        event.setPitch((float) pitch + inPitch);
        event.setRoll((float) roll + inRoll);
        if (com.tacz.guns.config.client.RenderConfig.RECOIL_DEBUG.get()) {
            debugRecoilLevelCam(q, multiplier, (float) yaw, (float) pitch, (float) roll, inYaw, inPitch, inRoll);
        }
    }

    /**
     * 【RecoilDebug 探针 · 世界相机动画】开火动画驱动世界摄像机的逐帧取证。
     *
     * <p>已知疑点（待日志裁决）：此处把视空间动画四元数按 ZYX 顺序分解出
     * (yaw,pitch,roll) 后**加在世界系欧拉角**上（yaw 叠在最外层世界 Y 轴），
     * 而正确的选择子复合应对应 YXZ 内旋序。静态推导该错配的误差只随俯仰角耦合、
     * 与朝向无关，与用户「对角朝向固定偏」症状指纹不符——日志将双向验证。</p>
     */
    private static void debugRecoilLevelCam(Quaternionf q, float mult, float dYaw, float dPitch, float dRoll,
                                            float inYaw, float inPitch, float inRoll) {
        try {
            double angleDeg = Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(q.w()))));
            if (angleDeg < 0.05) {
                return;
            }
            LocalPlayer player = Minecraft.getInstance().player;
            float facingY = player == null ? Float.NaN : Mth.wrapDegrees(player.getYRot());
            float facingX = player == null ? Float.NaN : player.getXRot();
            com.tacz.guns.GunMod.LOGGER.info(
                    "[TACZ RecoilDebug] levelCam ms={} q=({},{},{},{}) mult={} ang={} eulerD=({},{},{}) in=({},{},{}) facing=({},{}) shader={} irisHand={}",
                    System.currentTimeMillis(),
                    f(q.x()), f(q.y()), f(q.z()), f(q.w()), f(mult), f(angleDeg),
                    f(dYaw), f(dPitch), f(dRoll),
                    f(inYaw), f(inPitch), f(inRoll),
                    f(facingX), f(facingY),
                    com.tacz.guns.compat.iris.IrisCompat.isUsingRenderPack(),
                    com.tacz.guns.compat.iris.IrisCompat.isHandRendererActive());
        } catch (Throwable ignored) {
        }
    }

    /**
     * 【RecoilDebug 探针 · 手持相机动画】记录动画四元数左乘/右乘到手部基座之前的
     * 完整状态：四元数、乘子、以及<b>叠加前</b>的手部基座矩阵（3x3 旋转 + 平移）。
     *
     * <p>若手部基座在 vanilla 管线混入非纯旋转内容（缩放/切变），
     * 视空间后乘假设即被破坏，误差随朝向出场——对角朝向的固定侧偏正属此类指纹；
     * Iris 手部 pass 基座≈单位阵时则天然豁免，与「开光影正常」的目击吻合。</p>
     */
    protected static void debugRecoilItemCam(Quaternionf q, float mult, PoseStack poseStack) {
        try {
            double angleDeg = Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(q.w()))));
            if (angleDeg < 0.05) {
                return;
            }
            Matrix4f b = poseStack.last().pose();
            LocalPlayer player = Minecraft.getInstance().player;
            float facingY = player == null ? Float.NaN : Mth.wrapDegrees(player.getYRot());
            float facingX = player == null ? Float.NaN : player.getXRot();
            com.tacz.guns.GunMod.LOGGER.info(
                    "[TACZ RecoilDebug] itemCam ms={} q=({},{},{},{}) mult={} ang={} base3x3=[{},{},{}; {},{},{}; {},{},{}] baseT=({},{},{}) facing=({},{}) shader={} irisHand={}",
                    System.currentTimeMillis(),
                    f(q.x()), f(q.y()), f(q.z()), f(q.w()), f(mult), f(angleDeg),
                    f(b.m00()), f(b.m01()), f(b.m02()),
                    f(b.m10()), f(b.m11()), f(b.m12()),
                    f(b.m20()), f(b.m21()), f(b.m22()),
                    f(b.m30()), f(b.m31()), f(b.m32()),
                    f(facingX), f(facingY),
                    com.tacz.guns.compat.iris.IrisCompat.isUsingRenderPack(),
                    com.tacz.guns.compat.iris.IrisCompat.isHandRendererActive());
        } catch (Throwable ignored) {
        }
    }

    private static String f(double v) {
        return String.format(java.util.Locale.ROOT, "%+.5f", v);
    }

    /**
     * 应用状态机的手持物品摄像机动画，暂时只用于玩家
     */
    /**
     * 开镜晃动缩放系数：腰射恒为 1，随开镜进度插值到 {@code AimingSwayIntensity}。
     *
     * <p>按开镜进度插值而不是「开镜就切换」，是为了避免抬镜那一瞬间晃动幅度突然跳一下 ——
     * 那种跳变比晃动本身更容易被察觉。
     *
     * <p>整体包 try/catch：本方法在每帧的第一人称渲染路径上，
     * 任何异常（配置尚未加载、玩家状态异常）都不该把持枪渲染带崩，
     * 兜底返回 1 = 原有手感。
     *
     * @return 缩放系数；{@code 1} 表示与改动前完全一致
     */
    protected static float aimingSwayScale(LocalPlayer player, float partialTick) {
        try {
            if (com.tacz.guns.config.client.RenderConfig.AIMING_SWAY_INTENSITY == null) {
                return 1.0F;
            }
            float intensity = com.tacz.guns.config.client.RenderConfig.AIMING_SWAY_INTENSITY.get().floatValue();
            if (intensity == 1.0F) {
                // 常见情形直接短路，省掉一次开镜进度查询。
                return 1.0F;
            }
            float aimingProgress = Mth.clamp(
                    com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(player)
                            .getClientAimingProgress(partialTick), 0.0F, 1.0F);
            return Mth.lerp(aimingProgress, 1.0F, intensity);
        } catch (Throwable ignored) {
            return 1.0F;
        }
    }

    public void applyItemInHandCameraAnimation(BeforeRenderHandEvent event, ItemStack stack, LocalPlayer player) {
        applyItemInHandCameraAnimation(event, stack, 1);
    }

    public void applyItemInHandCameraAnimation(BeforeRenderHandEvent event, ItemStack stack, float multiplier) {
        var model = getModel(stack);
        if (model == null) {
            return;
        }
        Quaternionf quaternion = MathUtil.multiplyQuaternion(model.getCameraAnimationObject().rotationQuaternion, multiplier);
        PoseStack poseStack = event.getPoseStack();
        if (com.tacz.guns.config.client.RenderConfig.RECOIL_DEBUG.get()) {
            debugRecoilItemCam(quaternion, multiplier, poseStack);
        }
        poseStack.mulPose(quaternion);
    }

    /**
     * 执行额外的变换
     */
    public void doExtraTransforms(PoseStack poseStack, M model, ItemStack stack) {
        applyFirstPersonPositioningTransform(poseStack, model, stack);
    }

    /**
     * 渲染第一人称。26.2 入口：客户端 ItemModel(tacz:dynamic_item) -> TaczDynamicItemModel 的
     * SpecialModelRenderer -> AnimateGeoItemRenderer#render 的 mode.firstPerson() 分支。
     */
    public void renderFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, SubmitNodeCollector collector,
                                  int light, float partialTick) {
        M model = getModel(stack);
        if (model != null) {
            poseStack.pushPose();
            // 【持枪晃动 sway】枪跟不上视角转动的那一份滞后：
            // getViewXRot 是当前朝向，xBob/yBob 是 vanilla 维护的平滑滞后值，两者之差
            // 就是「刚才甩了多少」。甩得越快差值越大，枪甩动、镜像也跟着晃。
            float xRotOffset = Mth.lerp(partialTick, player.xBobO, player.xBob);
            float yRotOffset = Mth.lerp(partialTick, player.yBobO, player.yBob);
            float xRot = player.getViewXRot(partialTick) - xRotOffset;
            float yRot = player.getViewYRot(partialTick) - yRotOffset;
            // 开镜时把晃动按配置缩放：开镜进度 0 → 恒为 1（腰射手感一点不变），
            // 满开镜 → 取到 AimingSwayIntensity。默认 1.5 = 比原来更明显一些。
            //
            // 为什么值得单独放大：开镜后视野被瞄具收窄（PIP 更是把镜内又放大了 Z 倍），
            // 同样的角度抖动在镜内被放大成同样倍数的位移 —— 现实里高倍镜正是这样「越放大越难稳住」。
            // 原实现对开镜与否一视同仁，镜内反而显得过于稳定。
            // Keep the viewmodel sway identical to upstream.  In particular, do not
            // scale it by aiming progress: the vanilla bob is already magnified by
            // the ADS projection and applying a second ADS multiplier makes pitch and
            // roll visibly larger than upstream.
            poseStack.mulPose(Axis.XP.rotationDegrees(xRot * -0.1F));
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot * -0.1F));
            BedrockPart rootNode = model.getRootNode();
            if (rootNode != null) {
                // tanh 饱和限幅保持在缩放【之前】：它的作用是防止快速转身时枪飞出画面，
                // 那道保护必须先生效，缩放只放大限幅后的结果。
                xRot = (float) Math.tanh(xRot / 25) * 25;
                yRot = (float) Math.tanh(yRot / 25) * 25;
                rootNode.offsetX += yRot * 0.1F / 16F / 3F;
                rootNode.offsetY += -xRot * 0.1F / 16F / 3F;
                rootNode.additionalQuaternion.mul(Axis.XP.rotationDegrees(xRot * 0.05F));
                rootNode.additionalQuaternion.mul(Axis.YP.rotationDegrees(yRot * 0.05F));
            }

            // 从渲染原点 (0, 24, 0) 移动到模型原点 (0, 0, 0)
            poseStack.translate(0, 1.5f, 0);
            // 基岩版模型是上下颠倒的，需要翻转过来。
            poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
            doExtraTransforms(poseStack, model, stack);

            var stateMachine = getStateMachine(stack);
            if (stateMachine != null) {
                stateMachine.processContextIfExist(context -> {
                    updateContext(context, stack, player, partialTick);
                });
                stateMachine.update();
            }

            model.submit(poseStack, ctx, collector, getRenderType(stack), light, OverlayTexture.NO_OVERLAY);

            // 渲染结束后清除动画变换
            model.cleanAnimationTransform();
            poseStack.popPose();
        }
    }

    @ParametersAreNonnullByDefault
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, SubmitNodeCollector collector,
                             int light, int overlay) {
        if (ctx.firstPerson()) return;
        M model = getModel(stack);
        if (model != null) {
            poseStack.pushPose();
            // 从渲染原点 (0, 24, 0) 移动到模型原点 (0, 0, 0)
            poseStack.translate(0.5, 1.5f, 0.5);
            // 基岩版模型是上下颠倒的，需要翻转过来。
            poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
            model.submit(poseStack, ctx, collector, RenderTypes.entityCutout(
                    getTextureLocation(stack)
            ), light, overlay);
            poseStack.popPose();
        }
    }

    /**
     * 获取摄像机定位组的反相矩阵
     */
    @Nonnull
    public static Matrix4f getPositioningNodeInverse(List<BedrockPart> nodePath) {
        Matrix4f matrix4f = new Matrix4f();
        matrix4f.identity();
        if (nodePath != null) {
            for (int i = nodePath.size() - 1; i >= 0; i--) {
                BedrockPart part = nodePath.get(i);
                // 计算反向的旋转
                matrix4f.rotate(Axis.XN.rotation(part.xRot));
                matrix4f.rotate(Axis.YN.rotation(part.yRot));
                matrix4f.rotate(Axis.ZN.rotation(part.zRot));
                // 计算反向的位移
                if (part.getParent() != null) {
                    matrix4f.translate(-part.x / 16.0F, -part.y / 16.0F, -part.z / 16.0F);
                } else {
                    matrix4f.translate(-part.x / 16.0F, (1.5F - part.y / 16.0F), -part.z / 16.0F);
                }
            }
        }
        return matrix4f;
    }

    public static void applyFirstPersonPositioningTransform(PoseStack poseStack, BedrockAnimatedModel model, ItemStack stack) {
        Matrix4f transformMatrix = new Matrix4f();
        transformMatrix.identity();
        // 应用瞄准定位
        List<BedrockPart> idleNodePath = model.getIdleSightPath();

        Matrix4f idleViewMatrix = getPositioningNodeInverse(idleNodePath);

        // 应用瞄准变换
        MathUtil.applyMatrixLerp(transformMatrix, idleViewMatrix, transformMatrix, 1);

        // 应用变换到 PoseStack
        poseStack.translate(0, 1.5f, 0);
        poseStack.mulPose(transformMatrix);
        poseStack.translate(0, -1.5f, 0);
    }

    @Override
    public long getPutAwayDuration(ItemStack stack) {
        return this.getPutAwayTime(stack);
    }

    @Nullable
    @Override
    public IFPAnimationInstance createAnimationInstance(ItemStack stack, Entity entity) {
        return new IFPAnimationInstance() {
            private boolean drawn = false;
            private ItemStack lastItem = stack;

            @Override
            public ItemStack currentItem() {
                return lastItem;
            }

            @Override
            public Pose getPose() {
                return DummyPose.INSTANCE;
            }

            @Override
            public void tick(float v) {

            }

            @Override
            public @NotNull Quaternionf getCameraRotation() {
                return new Quaternionf();
            }

            @Override
            public void setCameraRotation(@NotNull Quaternionf quaternionf) {

            }

            @Override
            public Pose getCachedPose() {
                return DummyPose.INSTANCE;
            }

            @Override
            public void updateItem(ItemStack itemStack) {
                lastItem = itemStack;
            }

            @Override
            public void triggerDraw() {
                if (drawn) return;
                drawn = true;
                tryInit(lastItem, Minecraft.getInstance().player, 0);
                if (Minecraft.getInstance().player == null) return;
                TimelessAPI.getGunDisplay(lastItem).ifPresent(display -> {
                    SoundPlayManager.stopPlayGunSound();
                    SoundPlayManager.playDrawSound(Minecraft.getInstance().player, display);
                });
            }

            @Override
            public void triggerPutAway() {
                tryExit(lastItem, getPutAwayTime(lastItem));
                if (Minecraft.getInstance().player == null) return;
                TimelessAPI.getGunDisplay(lastItem).ifPresent(display -> {
                    SoundPlayManager.stopPlayGunSound();
                    SoundPlayManager.playPutAwaySound(Minecraft.getInstance().player, display);
                });
            }
        };
    }

    @Override
    public boolean isSameItem(ItemStack oldStack, ItemStack newStack) {
        if (oldStack.getItem() instanceof IAnimationItem item) {
            return item.isSame(oldStack, newStack);
        }
        return ItemStack.matches(oldStack, newStack);
    }

    @Override
    public boolean blockOffhandRender() {
        return true;
    }
}
