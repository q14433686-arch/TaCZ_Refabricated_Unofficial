package com.tacz.guns.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.event.RenderItemInHandBobEvent;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.math.Easing;
import com.tacz.guns.util.math.MathUtil;
import com.tacz.guns.util.math.PerlinNoise;
import com.tacz.guns.util.math.SecondOrderDynamics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 负责第一人称的枪械模型额外效果的渲染。其他部分参见 {@link GunItemRendererWrapper}
 */
@Environment(EnvType.CLIENT)
public class FirstPersonRenderGunEvent {
    // 用于生成瞄准动作的运动曲线，使动作看起来更平滑
    private static final SecondOrderDynamics AIMING_DYNAMICS = new SecondOrderDynamics(1.2f, 1.2f, 0.5f, 0);
    private static SecondOrderDynamics SWITCH_VIEW_DYNAMICS;
    // 用于打开改装界面时枪械运动的平滑
    private static final SecondOrderDynamics REFIT_OPENING_DYNAMICS = new SecondOrderDynamics(1f, 1.2f, 0.5f, 0);
    // 用于跳跃延滞动画的平滑
    private static final SecondOrderDynamics JUMPING_DYNAMICS = new SecondOrderDynamics(0.28f, 1f, 0.65f, 0);
    private static final float JUMPING_Y_SWAY = -2f;
    private static final float JUMPING_SWAY_TIME = 0.3f;
    private static final float LANDING_SWAY_TIME = 0.15f;
    // 用于枪械后座的程序动画
    private static final PerlinNoise SHOOT_X_SWAY_NOISE = new PerlinNoise(-0.2f, 0.2f, 400);
    private static final PerlinNoise SHOOT_Y_ROTATION_NOISE = new PerlinNoise(-0.0136f, 0.0136f, 100);
    private static final float SHOOT_Y_SWAY = -0.1f;
    private static final float SHOOT_ANIMATION_TIME = 0.3f;

    private static float jumpingSwayProgress = 0;
    private static boolean lastOnGround = false;
    private static long jumpingTimeStamp = -1;
    private static long shootTimeStamp = -1;
    private static Matrix4f oldAimingViewMatrix;
    private static float oldViewIndex;
    private static int currentViewIndex = -1;

    /**
     * 当主手拿着枪械物品的时候，取消应用在它上面的 viewBobbing，以便应用自定义的跑步/走路动画。
     */
    public static void cancelItemInHandViewBobbing(RenderItemInHandBobEvent.BobView event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ItemStack itemStack = KeepingItemRenderer.getRenderer().getCurrentItem();
        if (IGun.getIGunOrNull(itemStack) != null) {
            event.setCanceled(true);
        }
    }

    public static void onGunFire(GunFireEvent event) {
        if (event.getLogicalSide().isClient()) {
            LivingEntity shooter = event.getShooter();
            LocalPlayer player = Minecraft.getInstance().player;
            if (!shooter.equals(player)) {
                return;
            }
            ItemStack mainHandItem = player.getMainHandItem();
            IGun iGun = IGun.getIGunOrNull(mainHandItem);
            if (iGun == null) {
                return;
            }
            TimelessAPI.getClientGunIndex(iGun.getGunId(mainHandItem)).ifPresent(gunIndex -> {
                // 记录开火时间戳，用于后坐力程序动画
                shootTimeStamp = System.currentTimeMillis();
                // 记录枪口火焰数据
                MuzzleFlashRender.onShoot();
            });
        }
    }

    private static boolean bulletFromPlayer(Entity entity) {
        if (entity instanceof EntityKineticBullet entityBullet) {
            return entityBullet.getOwner() instanceof LocalPlayer;
        }
        return false;
    }

    public static void applyFirstPersonGunTransform(LocalPlayer player, ItemStack gunItemStack, PoseStack poseStack, BedrockGunModel model, float partialTicks) {
        // 配合运动曲线，计算改装枪口的打开进度
        float refitScreenOpeningProgress = REFIT_OPENING_DYNAMICS.update(RefitTransform.getOpeningProgress());
        // 配合运动曲线，计算瞄准进度
        float aimingProgress = AIMING_DYNAMICS.update(IClientPlayerGunOperator.fromLocalPlayer(player).getClientAimingProgress(partialTicks));
        // 应用枪械动态，如后坐力、持枪跳跃等
        applyGunMovements(model, aimingProgress, partialTicks);
        // 应用各种摄像机定位组的变换（默认持枪、瞄准、改装界面等）
        applyFirstPersonPositioningTransform(poseStack, model, gunItemStack, aimingProgress, refitScreenOpeningProgress);
        // 【案例⑧ 探针 · 第三轮】分段点 P2：定位 lerp 之后、约束写入之前。
        // ADS 辑拿显示 chainP1(基座段) 逐位干净而 gunRoot(链末端) 随朝向漂移 0.02~0.05，
        // 本探针把「定位段 / 约束段」的注入归属当场劈开。
        GunItemRendererWrapper.debugCase08ChainP2(poseStack);
        // 应用动画约束变换
        applyAnimationConstraintTransform(poseStack, model, aimingProgress * (1 - refitScreenOpeningProgress));
    }

    private static void applyGunMovements(BedrockGunModel model, float aimingProgress, float partialTicks) {
        applyShootSwayAndRotation(model, aimingProgress);
        applyJumpingSway(model, partialTicks);
    }

    /**
     * 应用瞄具摄像机定位组、机瞄摄像机定位组和 Idle 摄像机定位组的变换。会在几个摄像机定位之间插值。
     */
    private static void applyFirstPersonPositioningTransform(PoseStack poseStack, BedrockGunModel model, ItemStack stack, float aimingProgress, float refitScreenOpeningProgress) {
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) {
            return;
        }
        Matrix4f transformMatrix = new Matrix4f();
        transformMatrix.identity();
        // 应用瞄准定位
        List<BedrockPart> idleNodePath = model.getIdleSightPath();
        List<BedrockPart> aimingNodePath = null;
        Identifier scopeId = iGun.getAttachmentId(stack, AttachmentType.SCOPE);
        if (scopeId.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            scopeId = iGun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
        }
        CompoundTag scopeTag = iGun.getAttachmentTag(stack, AttachmentType.SCOPE);
        int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
        int viewIndex = 1;
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            // 未安装瞄具，使用机瞄定位组
            aimingNodePath = model.getIronSightPath();
        } else {
            // 安装瞄具，组合瞄具定位组和瞄具视野定位组
            List<BedrockPart> scopeNodePath = model.getScopePosPath();
            if (scopeNodePath != null) {
                aimingNodePath = new ArrayList<>(scopeNodePath);
                Optional<ClientAttachmentIndex> indexOptional = TimelessAPI.getClientAttachmentIndex(scopeId);
                if (indexOptional.isPresent()) {
                    BedrockAttachmentModel attachmentModel = indexOptional.get().getAttachmentModel();
                    int[] views = indexOptional.get().getViews();
                    viewIndex = views[zoomNumber % views.length] - 1;
                    if (attachmentModel != null) {
                        // 【第 35 轮】把"当前用的是哪一组镜"告诉模型，供组合镜过滤准星。
                        // views[] 里 1 = 红点分系统、2 = 筒镜分系统
                        // （scope_hamr_display.json: zoom[3.25, 1.25] / views[2, 1]）。
                        // 这里是全流程唯一同时知道 zoomNumber 与 views[] 的地方。
                        attachmentModel.setActiveViewGroup(views[zoomNumber % views.length]);
                        List<BedrockPart> scopeViewPath = attachmentModel.getScopeViewPath(currentViewIndex == -1 ? viewIndex : currentViewIndex);
                        if (scopeViewPath != null) {
                            aimingNodePath.addAll(scopeViewPath);
                        }
                    }
                }
            }
        }
        Matrix4f aimingViewMatrix = getPositioningNodeInverse(aimingNodePath);
        // 执行两个 scope view 之间的插值
        if (currentViewIndex == -1) {
            currentViewIndex = viewIndex;
            oldViewIndex = viewIndex;
            oldAimingViewMatrix = aimingViewMatrix;
            SWITCH_VIEW_DYNAMICS = new SecondOrderDynamics(0.35f, 1.2f, 0.3f, viewIndex);
        }
        float view_interpret = SWITCH_VIEW_DYNAMICS.update(viewIndex);
        float span = currentViewIndex - oldViewIndex;
        float switchingProgress = Math.abs(span) < 0.05 ? 1 : (view_interpret - oldViewIndex) / span;
        MathUtil.applyMatrixLerp(aimingViewMatrix, oldAimingViewMatrix, aimingViewMatrix, 1 - switchingProgress);
        if (currentViewIndex != viewIndex) {
            oldAimingViewMatrix = aimingViewMatrix;
            oldViewIndex = view_interpret;
            currentViewIndex = viewIndex;
        }
        // 应用瞄准变换
        MathUtil.applyMatrixLerp(transformMatrix, getPositioningNodeInverse(idleNodePath), transformMatrix, (1 - refitScreenOpeningProgress));
        MathUtil.applyMatrixLerp(transformMatrix, aimingViewMatrix, transformMatrix, (1 - refitScreenOpeningProgress) * aimingProgress);
        // 应用改装界面开启时的定位
        float refitTransformProgress = (float) Easing.easeOutCubic(RefitTransform.getTransformProgress());
        AttachmentType oldType = RefitTransform.getOldTransformType();
        AttachmentType currentType = RefitTransform.getCurrentTransformType();
        List<BedrockPart> fromNode = model.getRefitAttachmentViewPath(oldType);
        List<BedrockPart> toNode = model.getRefitAttachmentViewPath(currentType);
        MathUtil.applyMatrixLerp(transformMatrix, getPositioningNodeInverse(fromNode), transformMatrix, refitScreenOpeningProgress);
        MathUtil.applyMatrixLerp(transformMatrix, getPositioningNodeInverse(toNode), transformMatrix, refitScreenOpeningProgress * refitTransformProgress);
        // 应用变换到 PoseStack
        poseStack.translate(0, 1.5f, 0);
        poseStack.mulPose(transformMatrix);
        poseStack.translate(0, -1.5f, 0);
    }

    /**
     * 获取摄像机定位组的反相矩阵
     */
    @Nonnull
    private static Matrix4f getPositioningNodeInverse(List<BedrockPart> nodePath) {
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

    private static void applyShootSwayAndRotation(BedrockGunModel model, float aimingProgress) {
        BedrockPart rootNode = model.getRootNode();
        if (rootNode != null) {
            float progress = 1 - (System.currentTimeMillis() - shootTimeStamp) / (SHOOT_ANIMATION_TIME * 1000);
            if (progress < 0) {
                progress = 0;
            }
            progress = (float) Easing.easeOutCubic(progress);
            rootNode.offsetX += SHOOT_X_SWAY_NOISE.getValue() / 16 * progress * (1 - aimingProgress);
            // 基岩版模型 y 轴上下颠倒，sway 值取相反数
            rootNode.offsetY += -SHOOT_Y_SWAY / 16 * progress * (1 - aimingProgress);
            rootNode.additionalQuaternion.mul(Axis.YP.rotation(SHOOT_Y_ROTATION_NOISE.getValue() * progress));
        }
    }

    private static void applyJumpingSway(BedrockGunModel model, float partialTicks) {
        if (jumpingTimeStamp == -1) {
            jumpingTimeStamp = System.currentTimeMillis();
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            double posY = Mth.lerp(partialTicks, Minecraft.getInstance().player.yOld, Minecraft.getInstance().player.getY());
            // 防御：partialTicks 为 0 时避免除零产生 NaN
            float velocityY = partialTicks > 0.0f
                    ? (float) (posY - Minecraft.getInstance().player.yOld) / partialTicks
                    : 0.0f;
            if (player.onGround()) {
                if (!lastOnGround) {
                    jumpingSwayProgress = velocityY / -0.1f;
                    if (jumpingSwayProgress > 1) {
                        jumpingSwayProgress = 1;
                    }
                    lastOnGround = true;
                } else {
                    jumpingSwayProgress -= (System.currentTimeMillis() - jumpingTimeStamp) / (LANDING_SWAY_TIME * 1000);
                    if (jumpingSwayProgress < 0) {
                        jumpingSwayProgress = 0;
                    }
                }
            } else {
                if (lastOnGround) {
                    // 0.42 是玩家自然起跳的速度
                    jumpingSwayProgress = velocityY / 0.42f;
                    if (jumpingSwayProgress > 1) {
                        jumpingSwayProgress = 1;
                    }
                    lastOnGround = false;
                } else {
                    jumpingSwayProgress -= (System.currentTimeMillis() - jumpingTimeStamp) / (JUMPING_SWAY_TIME * 1000);
                    if (jumpingSwayProgress < 0) {
                        jumpingSwayProgress = 0;
                    }
                }
            }
        }
        jumpingTimeStamp = System.currentTimeMillis();
        float ySway = JUMPING_DYNAMICS.update(JUMPING_Y_SWAY * jumpingSwayProgress);
        BedrockPart rootNode = model.getRootNode();
        if (rootNode != null) {
            // 基岩版模型 y 轴上下颠倒，sway 值取相反数
            rootNode.offsetY += -ySway / 16;
        }
    }

    /**
     * 获取动画约束点的变换数据。
     *
     * @param originTranslation   用于输出约束点的原坐标
     * @param animatedTranslation 用于输出约束点经过动画变换之后的坐标
     * @param rotation            用于输出约束点的旋转
     */
    private static void getAnimationConstraintTransform(List<BedrockPart> nodePath, @Nonnull Vector3f originTranslation, @Nonnull Vector3f animatedTranslation, @Nonnull Vector3f rotation) {
        if (nodePath == null) {
            return;
        }
        // 约束点动画变换矩阵
        Matrix4f animeMatrix = new Matrix4f();
        // 约束点初始变换矩阵
        Matrix4f originMatrix = new Matrix4f();
        animeMatrix.identity();
        originMatrix.identity();
        BedrockPart constrainNode = nodePath.get(nodePath.size() - 1);
        for (BedrockPart part : nodePath) {
            // 乘动画位移
            if (part != constrainNode) {
                animeMatrix.translate(part.offsetX, part.offsetY, part.offsetZ);
            }
            // 乘组位移
            if (part.getParent() != null) {
                animeMatrix.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
            } else {
                animeMatrix.translate(part.x / 16.0F, (part.y / 16.0F - 1.5F), part.z / 16.0F);
            }
            // 乘动画旋转
            if (part != constrainNode) {
                animeMatrix.rotate(part.additionalQuaternion);
            }
            // 乘组旋转
            animeMatrix.rotate(Axis.ZP.rotation(part.zRot));
            animeMatrix.rotate(Axis.YP.rotation(part.yRot));
            animeMatrix.rotate(Axis.XP.rotation(part.xRot));

            // 乘组位移
            if (part.getParent() != null) {
                originMatrix.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
            } else {
                originMatrix.translate(part.x / 16.0F, (part.y / 16.0F - 1.5F), part.z / 16.0F);
            }
            // 乘组旋转
            originMatrix.rotate(Axis.ZP.rotation(part.zRot));
            originMatrix.rotate(Axis.YP.rotation(part.yRot));
            originMatrix.rotate(Axis.XP.rotation(part.xRot));

        }
        // 把变换数据写入输出
        animeMatrix.getTranslation(animatedTranslation);
        originMatrix.getTranslation(originTranslation);
        Vector3f animatedRotation = MathUtil.getEulerAngles(animeMatrix);
        Vector3f originRotation = MathUtil.getEulerAngles(originMatrix);
        animatedRotation.sub(originRotation);
        rotation.set(animatedRotation.x(), animatedRotation.y(), animatedRotation.z());
    }

    /**
     * 应用动画约束变换。
     *
     * @param weight 控制约束变换的权重，用于插值。
     */
    public static void applyAnimationConstraintTransform(PoseStack poseStack, BedrockGunModel gunModel, float weight) {
        List<BedrockPart> nodePath = gunModel.getConstraintPath();
        if (nodePath == null) {
            return;
        }
        if (gunModel.getConstraintObject() == null) {
            return;
        }
        // 获取动画约束点的变换信息
        Vector3f originTranslation = new Vector3f();
        Vector3f animatedTranslation = new Vector3f();
        Vector3f rotation = new Vector3f();
        Vector3f translationICA = gunModel.getConstraintObject().translationConstraint;
        Vector3f rotationICA = gunModel.getConstraintObject().rotationConstraint;
        getAnimationConstraintTransform(nodePath, originTranslation, animatedTranslation, rotation);
        // 配合约束系数，计算约束位移需要的反向位移
        Vector3f inverseTranslation = new Vector3f(originTranslation);
        inverseTranslation.sub(animatedTranslation);
        // 【案例⑧探针 · 采样点1】骨骼链差值（authored 帧），仅在探针开启时分配
        Vector3f case08Delta = case08DebugOn() ? new Vector3f(inverseTranslation) : null;
        inverseTranslation.mulDirection(poseStack.last().pose());
        // 【案例⑧探针 · 采样点2】经 mulDirection 进入写入帧的向量 v0
        Vector3f case08V0 = case08Delta != null ? new Vector3f(inverseTranslation) : null;
        // 【第 31 轮】mode 2（当前姿态帧共轭）用：与 mulDirection 同一时刻的姿态旋转 P_pre。
        org.joml.Matrix3f case08PoseFrameR = new org.joml.Matrix3f(poseStack.last().pose());
        // 【26.2 修复·终版：ADS 开枪/换弹时枪身随朝向整体偏移——
        //  原始症状：斜向（东南/西南/东北/西北）固定向一侧横移（东南/西北偏左、东北/西南偏右），
        //  正方向与腰射完全正常，开启 Iris 光影（手部 pass 无基座预乘）时一切正常】
        //
        // 坐标系结构（经两轮朝向指纹实测锁定）：
        // 26.2 vanilla 手部 pass 在 poseStack 进入物品渲染前就预乘了基座 B=R(q)
        // （view→world 相机基座，含朝向 q；Iris 手部 pass 不预乘，B≈I）。
        // 而 1.21.1 里该栈从单位阵开始。关键实测事实：本函数写入的 m30..m32 槽位，
        // 其上方链在提交（ViewSnapshot→几何）时还会再左乘一次 B —— 即
        //   最终视图位移 = B · v_written
        // 上游 1.21.1 正确观感（authored）是：最终视图位移 = diag(c) · F · Δ
        // （F=ZP 翻转等骨骼链内变换，Δ=约束骨骼位移差，c=(ICA_x−1, ICA_y−1, 1−ICA_z)）。
        //
        // ——老 bug 的成因——：
        // mulDirection(pose) 把 Δ 旋进「写入帧」：v0 = B·F·Δ；逐轴乘系数后 v = diag(c)·B·F·Δ；
        // 提交时再被 B 带一次：最终 = B·diag(c)·B ·(FΔ)。注意是 B·diag·B 而非共轭
        // （Y 轴旋转让 x/z 之一带负号）：非对角元 = (cx+cz)/2 · sin2θ —— 正方向归零，
        // 斜向出现按象限对反号的纯横向泄漏，正是最初目击的全部特征。
        //
        // ——上一版修复为何反而更糟（26.2 复测确诊）——：
        // 上一版做了 v = B·diag·Bᵀ·v0（共轭方向写反），净效果 = B²·diag·F·Δ = R(2θ)·authored：
        // 正南/正北（2θ=0/360°）恰好恒等 → 正常；正东/正西（2θ=±180°）x/z 符号翻转
        // → 后坐力"向后怼"、换弹跑到右后方；斜向（2θ=±90°）x/z 互换 → 纯平移。
        // 与用户复测报告逐条吻合，此指纹是本坐标系模型的决定性证据。
        //
        // ——正确修复——：
        // 需要在写入前把向量变到「逆基座」帧：v = Bᵀ·diag(c)·Bᵀ·v0 = Bᵀ·diag(c)·F·Δ，
        // 提交时被 B 带回：B·v = diag(c)·F·Δ = authored，全朝向与 1.21.1 完全一致；
        // Iris 下 B≈I，两步均恒等、行为不变。AK47 shoot 的 constraint 位移动画
        // [0.15, 0.05, 0.4] 给出系数 ≈(−0.85, −0.95, +0.6)，强各向异性，
        // 故无修复时斜向横移可达 0.1~0.2 视图单位（~2-4°），肉眼显著；换弹动画同样
        // 驱动 constraint 骨骼，故同路径一并修复。
        // 【第 31 轮（案例⑧ 定案）约束位移写入的三档形态 —— 用户在场 A/B 实测裁决：
        //   mode 0 = plain：diag·v0 直写（修复前原版）。用户实测：当前「整体随朝向转」
        //            病根完全消失；代价 = 四方向斜向后坐力侧漏回来了（8/10 的原案）。
        //   mode 1 = Bᵀ·diag·Bᵀ 三明治（8/10 终版存档）。用户实测：正朝向整枪随朝向转、
        //            竖直「跑后方」、后坐过压 —— 即本案全部症状 ⇒ 三明治是该病灶注入源。
        //   mode 2 = 姿态帧共轭 v = P_post·diag·P_preᵀ·v0。【默认】。推导依据：
        //            ① plain（mode 0）在全部朝向/竖直的观感都正确 ⇒ 槽位带回乘子 X 满足
        //               X·(姿态链旋转) = I（至多差一个产生斜向 sin2φ 泄漏的小残差）；
        //            ② upstream/authored 要求最终视图位移 = diag(c)·F_pre·Δ；
        //            ③ 唯一两条同时成立且不读任何外部矩阵（B/modelView 都被实测证伪过）
        //               的写法 = 用当前姿态自身做共轭，各向异性系数被 Fold 进姿态帧内部，
        //               与朝向/基座结构性解耦；斜向泄漏同型消除。
        // Iris 手部 pass 基座≈I 时三档逐位等价 ⇒ 恒按 mode 0 执行（保持参照零介入）。
        // 【第 32 轮修正 · 档位判定唯一信源化】
        // 第 31 轮的兼容映射「老布尔 ConstraintBaseCompensate=false 强制落 mode 0」
        // 在现场被证明是配置陷阱：用户在第 30 轮 A/B 时把那枚布尔留在 false，
        // 随后显式把 ConstraintCompensateMode 设为 2，布尔却静默否决了它——
        // 用户当轮回报「整体不转 / 斜向漏 / 跟手自然」三项正是 mode 0 plain 的
        // 已知指纹（斜向漏 = 本案最原始病灶复现），即 mode 2 从未真正运行。
        // 判定从此只认 ConstraintCompensateMode 一个信源；老布尔保留注册
        // （旧配置文件不出错），但代码中不再读取。
        int case08Mode;
        if (com.tacz.guns.config.client.RenderConfig.CONSTRAINT_COMPENSATE_MODE != null) {
            case08Mode = com.tacz.guns.config.client.RenderConfig.CONSTRAINT_COMPENSATE_MODE.get();
        } else {
            case08Mode = 2;
        }
        if (case08Mode < 0 || case08Mode > 2) {
            case08Mode = 2; // 文件被手改越界时回落默认档，绝不落到未定义形态
        }
        int case08EffMode = com.tacz.guns.compat.iris.IrisCompat.isHandRendererActive() ? 0 : case08Mode;
        // 【第 32 轮】生效档一次性播报：上一轮的静默降级让「这一局跑的到底是哪档」
        // 完全不可感知、只能猜；每进程首帧约束写入时落一行日志，供不共享日志时自查。
        if (!case08ModeAnnounced) {
            case08ModeAnnounced = true;
            com.tacz.guns.GunMod.LOGGER.info(
                    "[TACZ Case08] ConstraintCompensateMode effective={} (config={}, irisHandActive={})",
                    case08EffMode, case08Mode,
                    com.tacz.guns.compat.iris.IrisCompat.isHandRendererActive());
        }
        org.joml.Matrix3f baseR = new Matrix3f();
        GunItemRendererWrapper.copyHandBaseRotation(baseR);
        if (case08EffMode == 1) {
            inverseTranslation.mulTranspose(baseR);  // Bᵀ·v0：写入帧 → 逆基座（authored）帧
        } else if (case08EffMode == 2) {
            inverseTranslation.mulTranspose(case08PoseFrameR);  // P_preᵀ·v0 = F_pre·Δ（姿态自身逆帧）
        }
        inverseTranslation.mul(translationICA.x() - 1, translationICA.y() - 1, 1 - translationICA.z()); // 基岩版模型的旋转导致 xy 轴要反过来
        if (case08EffMode == 1) {
            inverseTranslation.mulTranspose(baseR);  // 终版三明治右半（存档形态，勿作默认）
        }
        // mode 2 的右半（P_post·…）必须等下面的约束旋转块执行完、拿到终态 3x3 后再补乘，
        // 见下方写入前的 mul(new Matrix3f(poseMatrix))。
        // 计算约束旋转需要的反向旋转。因需要插值，获取的是欧拉角
        Vector3f inverseRotation = new Vector3f(rotation);
        inverseRotation.mul(rotationICA.x() - 1, rotationICA.y() - 1, rotationICA.z() - 1);
        // 约束旋转
        poseStack.translate(animatedTranslation.x(), animatedTranslation.y() + 1.5f, animatedTranslation.z());
        poseStack.mulPose(Axis.XP.rotation(inverseRotation.x() * weight));
        poseStack.mulPose(Axis.YP.rotation(inverseRotation.y() * weight));
        poseStack.mulPose(Axis.ZP.rotation(inverseRotation.z() * weight));
        poseStack.translate(-animatedTranslation.x(), -animatedTranslation.y() - 1.5f, -animatedTranslation.z());
        // 约束位移
        Matrix4f poseMatrix = poseStack.last().pose();
        if (case08EffMode == 2) {
            // P_post·diag·P_preᵀ·v0 的右半：约束旋转块执行完之后的终态姿态帧。
            inverseTranslation.mul(new org.joml.Matrix3f(poseMatrix));
        }
        poseMatrix.m30(poseMatrix.m30() - inverseTranslation.x() * weight);
        poseMatrix.m31(poseMatrix.m31() - inverseTranslation.y() * weight);
        poseMatrix.m32(poseMatrix.m32() + inverseTranslation.z() * weight);
        // 【案例⑧探针 · 采样点3】最终写入向量 v3 与全部基座数据落日志
        case08DebugConstraint(case08Delta, case08V0, inverseTranslation, baseR, poseMatrix, weight);
    }

    // ============================ 案例⑧ 取证探针（2026-08-11） ============================
    //
    // 症状（用户六朝向实测）：换弹时「手臂+枪体」作为整体的平移方向随玩家朝向旋转
    // （北→偏左、南→~正常或偏下、西→偏右、东→偏左、仰视→左上+后方、俯视→后方）；
    // 后坐力除正北外均「过分向下压」、正南最重；**开 Iris 光影时全部正常**。
    //
    // 静态审计已排除（数学或字节码级）：相机后坐力通道（setXRot/setYRot 增量，与朝向无关）、
    // 普通骨骼动画（模型空间撰写，天然锁视角）、渲染全程的右乘局部复合（26.2 vanilla 手部链
    // 经官方 jar 字节码核对：pose 预乘 invert(viewRotation)、modelView mul viewRotation，
    // 提交时 C·base=I，静帧下任何纯右乘变换不可能产生朝向相关平移）。
    // 唯一帧敏感操作 = 本函数的 m30..m32 绝对槽位写入（不能右乘复合，必须显式做基座归一化）。
    // 但现状代码（两次 mulTranspose + diag）经数值拟合**注定在 N/S 双干净、E/W 同号**，
    // 与用户指纹（N 偏、S 净、E/W 异号）不符 —— 说明还有第三处未被静态建模覆盖的写入/错位。
    //
    // 本探针把整条链的三个采样点 + 两张基座矩阵一起落日志，六朝向各打一发连点+一次换弹后，
    // 离线直接算出真实残差旋转的轴与角，一次性锁定错误因子，杜绝再靠脑推改矩阵。
    private static long case08LastLogMs = 0L;

    // 【第 32 轮】生效档一次性播报的去重门闩（每 JVM 进程只打一行，见上方写入路径）
    private static boolean case08ModeAnnounced = false;

    private static boolean case08DebugOn() {
        return com.tacz.guns.config.client.RenderConfig.RECOIL_DEBUG != null
                && com.tacz.guns.config.client.RenderConfig.RECOIL_DEBUG.get();
    }

    private static String c8(double v) {
        return String.format(java.util.Locale.ROOT, "%+.4f", v);
    }

    private static void case08DebugConstraint(Vector3f delta, Vector3f v0, Vector3f v3,
                                              Matrix3f baseR, Matrix4f poseNow, float weight) {
        try {
            if (delta == null) {
                return;
            }
            if (delta.lengthSquared() < 1.0e-6 && v3.lengthSquared() < 1.0e-6) {
                return; // 静帧不打，避免刷屏
            }
            long now = System.currentTimeMillis();
            if (now - case08LastLogMs < 150L) {
                return;
            }
            case08LastLogMs = now;
            LocalPlayer player = Minecraft.getInstance().player;
            float fy = player == null ? Float.NaN : Mth.wrapDegrees(player.getYRot());
            float fx = player == null ? Float.NaN : player.getXRot();
            Matrix4f mv = com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrixCopy();
            com.tacz.guns.GunMod.LOGGER.info(
                    "[TACZ Case08] ms={} facing=({},{}) w={} d=({},{},{}) v0=({},{},{}) v3=({},{},{}) "
                            + "B=[{},{},{} {},{},{} {},{},{}] P=[{},{},{} {},{},{} {},{},{}] Pt=({},{},{}) "
                            + "MV=[{},{},{} {},{},{} {},{},{}] irisHand={} pack={}",
                    now,
                    c8(fx), c8(fy), c8(weight),
                    c8(delta.x()), c8(delta.y()), c8(delta.z()),
                    c8(v0.x()), c8(v0.y()), c8(v0.z()),
                    c8(v3.x()), c8(v3.y()), c8(v3.z()),
                    c8(baseR.m00()), c8(baseR.m01()), c8(baseR.m02()),
                    c8(baseR.m10()), c8(baseR.m11()), c8(baseR.m12()),
                    c8(baseR.m20()), c8(baseR.m21()), c8(baseR.m22()),
                    c8(poseNow.m00()), c8(poseNow.m01()), c8(poseNow.m02()),
                    c8(poseNow.m10()), c8(poseNow.m11()), c8(poseNow.m12()),
                    c8(poseNow.m20()), c8(poseNow.m21()), c8(poseNow.m22()),
                    c8(poseNow.m30()), c8(poseNow.m31()), c8(poseNow.m32()),
                    c8(mv.m00()), c8(mv.m01()), c8(mv.m02()),
                    c8(mv.m10()), c8(mv.m11()), c8(mv.m12()),
                    c8(mv.m20()), c8(mv.m21()), c8(mv.m22()),
                    com.tacz.guns.compat.iris.IrisCompat.isHandRendererActive(),
                    com.tacz.guns.compat.iris.IrisCompat.isUsingRenderPack());
        } catch (Throwable ignored) {
        }
    }
}