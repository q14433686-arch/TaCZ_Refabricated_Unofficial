package com.tacz.guns.client.event;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import cn.sh1rocu.tacz.api.event.ComputeFovModifierEvent;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.api.modifier.ParameterizedCachePair;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.RecoilModifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.math.MathUtil;
import com.tacz.guns.util.math.SecondOrderDynamics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.util.Locale;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class CameraSetupEvent {
    /**
     * 【RecoilDebug 探针】开火时对 pitch/yaw 两条后坐力样条做定点包络采样（毫秒）。
     * 采样值 = 该发子弹的随机抽取 × 配件/瞄准修正，跨多发求均值即可分辨
     * 「yaw 样条本身系统性偏置」与「渲染层朝向耦合」（前者此处即现身，后者这里必然干净）。
     */
    private static final double[] RECOIL_DEBUG_SAMPLE_MS = {0, 40, 80, 120, 160, 240, 320, 480};
    /**
     * 用于平滑 FOV 变化
     */
    public static final SecondOrderDynamics WORLD_FOV_DYNAMICS = new SecondOrderDynamics(0.5f, 1.2f, 0.5f, 0);
    public static final SecondOrderDynamics ITEM_MODEL_FOV_DYNAMICS = new SecondOrderDynamics(0.5f, 1.2f, 0.5f, 0);
    private static PolynomialSplineFunction pitchSplineFunction;
    private static PolynomialSplineFunction yawSplineFunction;
    private static long shootTimeStamp = -1L;
    private static double xRotO = 0;
    private static double yRotO = 0;

    public static void applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles event) {
        if (!Minecraft.getInstance().options.bobView().get()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = KeepingItemRenderer.getRenderer().getCurrentItem();
        // 尝试调用物品的自定义相机动画
        if (BuiltinItemRendererRegistry.INSTANCE.get(stack.getItem()) instanceof AnimateGeoItemRenderer<?, ?> renderer) {
            renderer.applyLevelCameraAnimation(event, stack, player);
        }

    }

    public static void applyItemInHandCameraAnimation(BeforeRenderHandEvent event) {
        if (!Minecraft.getInstance().options.bobView().get()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = KeepingItemRenderer.getRenderer().getCurrentItem();
        // 尝试调用物品的自定义相机动画
        if (BuiltinItemRendererRegistry.INSTANCE.get(stack.getItem()) instanceof AnimateGeoItemRenderer<?, ?> renderer) {
            renderer.applyItemInHandCameraAnimation(event, stack, player);
        }
    }

    public static void applyScopeMagnification(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) {
            return; // 只修改世界渲染的 fov，因此如果是手部渲染 fov 事件，则返回
        }
        Entity entity = event.getCamera().entity();
        if (entity instanceof LivingEntity livingEntity) {
            ItemStack stack = KeepingItemRenderer.getRenderer().getCurrentItem();
            if (!(stack.getItem() instanceof IGun iGun)) {
                float fov = WORLD_FOV_DYNAMICS.update((float) event.getFOV());
                event.setFOV(fov);
                return;
            }
            float zoom = iGun.getAimingZoom(stack);
            if (livingEntity instanceof LocalPlayer localPlayer) {
                IClientPlayerGunOperator gunOperator = IClientPlayerGunOperator.fromLocalPlayer(localPlayer);
                float aimingProgress = gunOperator.getClientAimingProgress((float) event.getPartialTick());
                float fov = WORLD_FOV_DYNAMICS.update((float) MathUtil.magnificationToFov(1 + (zoom - 1) * aimingProgress, event.getFOV()));
                event.setFOV(fov);
            } else {
                IGunOperator gunOperator = IGunOperator.fromLivingEntity(livingEntity);
                float aimingProgress = gunOperator.getSynAimingProgress();
                float fov = WORLD_FOV_DYNAMICS.update((float) MathUtil.magnificationToFov(1 + (zoom - 1) * aimingProgress, event.getFOV()));
                event.setFOV(fov);
            }
        }
    }

    public static void applyGunModelFovModifying(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) {
            return; // 只修改手部物品的 fov，因此如果是世界渲染 fov 事件，则返回
        }
        Entity entity = event.getCamera().entity();
        if (entity instanceof LivingEntity livingEntity) {
            ItemStack stack = KeepingItemRenderer.getRenderer().getCurrentItem();
            if (!(stack.getItem() instanceof IGun iGun)) {
                float fov = ITEM_MODEL_FOV_DYNAMICS.update((float) event.getFOV());
                event.setFOV(fov);
                return;
            }
            Identifier scopeItemId = iGun.getAttachmentId(stack, AttachmentType.SCOPE);
            if (scopeItemId.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
                scopeItemId = iGun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
            }
            CompoundTag scopeTag = iGun.getAttachmentTag(stack, AttachmentType.SCOPE);
            int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
            // 尝试使用配件fov修改，若无则尝试使用枪械本身fov修改，否则维持不变
            float modifiedFov = TimelessAPI.getClientAttachmentIndex(scopeItemId)
                    .map(index -> {
                        float[] viewsFov = index.getViewsFov();
                        return viewsFov[zoomNumber % viewsFov.length];
                    })
                    .orElse(
                            TimelessAPI.getGunDisplay(stack)
                                    .map(GunDisplayInstance::getZoomModelFov)
                                    .orElse((float) event.getFOV())
                    );
            if (livingEntity instanceof LocalPlayer localPlayer) {
                IClientPlayerGunOperator gunOperator = IClientPlayerGunOperator.fromLocalPlayer(localPlayer);
                float aimingProgress = gunOperator.getClientAimingProgress((float) event.getPartialTick());
                float fov = ITEM_MODEL_FOV_DYNAMICS.update(Mth.lerp(aimingProgress, (float) event.getFOV(), modifiedFov));
                event.setFOV(fov);
            } else {
                IGunOperator gunOperator = IGunOperator.fromLivingEntity(livingEntity);
                float aimingProgress = gunOperator.getSynAimingProgress();
                float fov = ITEM_MODEL_FOV_DYNAMICS.update(Mth.lerp(aimingProgress, (float) event.getFOV(), modifiedFov));
                event.setFOV(fov);
            }
        }
    }

    public static void initialCameraRecoil(GunFireEvent event) {
        if (event.getLogicalSide().isClient()) {
            LivingEntity shooter = event.getShooter();
            LocalPlayer player = Minecraft.getInstance().player;
            if (!shooter.equals(player)) {
                return;
            }
            ItemStack mainHandItem = player.getMainHandItem();
            if (!(mainHandItem.getItem() instanceof IGun iGun)) {
                return;
            }
            AttachmentCacheProperty cacheProperty = IGunOperator.fromLivingEntity(player).getCacheProperty();
            if (cacheProperty == null) {
                return;
            }
            Identifier gunId = iGun.getGunId(mainHandItem);
            Optional<ClientGunIndex> gunIndexOptional = TimelessAPI.getClientGunIndex(gunId);
            if (gunIndexOptional.isEmpty()) {
                return;
            }
            ClientGunIndex gunIndex = gunIndexOptional.get();
            GunData gunData = gunIndex.getGunData();
            // 获取所有配件对摄像机后坐力的修改
            ParameterizedCachePair<Float, Float> attachmentRecoilModifier = cacheProperty.getCache(RecoilModifier.ID);
            IClientPlayerGunOperator clientPlayerGunOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
            float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            float aimingProgress = clientPlayerGunOperator.getClientAimingProgress(partialTicks);
            float zoom = iGun.getAimingZoom(mainHandItem);
            float aimingRecoilModifier = 1 - aimingProgress + aimingProgress / (float) Math.min(Math.sqrt(zoom), 1.5);
            // 如果是趴下，那么后坐力按 data 设计减少（默认为降低一半）
            if (!player.isSwimming() && player.getPose() == Pose.SWIMMING) {
                aimingRecoilModifier = aimingRecoilModifier * gunData.getCrawlRecoilMultiplier();
            }
            float pitchMod = (float) attachmentRecoilModifier.left().eval(aimingRecoilModifier);
            float yawMod = (float) attachmentRecoilModifier.right().eval(aimingRecoilModifier);
            pitchSplineFunction = gunData.getRecoil().genPitchSplineFunction(pitchMod);
            yawSplineFunction = gunData.getRecoil().genYawSplineFunction(yawMod);
            shootTimeStamp = System.currentTimeMillis();
            xRotO = 0;
            yRotO = 0;
            if (RenderConfig.RECOIL_DEBUG.get()) {
                debugLogRecoilFire(player, aimingRecoilModifier, pitchMod, yawMod, pitchSplineFunction, yawSplineFunction);
            }
        }
    }

    public static void applyCameraRecoil(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        long timeTotal = System.currentTimeMillis() - shootTimeStamp;
        double dPitch = 0;
        double dYaw = 0;
        if (pitchSplineFunction != null && pitchSplineFunction.isValidPoint(timeTotal)) {
            double value = pitchSplineFunction.value(timeTotal);
            dPitch = value - xRotO;
            player.setXRot(player.getXRot() - (float) dPitch);
            xRotO = value;
        }
        if (yawSplineFunction != null && yawSplineFunction.isValidPoint(timeTotal)) {
            double value = yawSplineFunction.value(timeTotal);
            dYaw = value - yRotO;
            player.setYRot(player.getYRot() - (float) dYaw);
            yRotO = value;
        }
        // 【RecoilDebug 探针】后坐力对玩家视角的逐帧增量。
        // 叠加对象是玩家自身 xRot/yRot（自身坐标系，与朝向无关），
        // 故本通道理论上不可能产生随朝向的固定偏置——若下轮日志在
        // 对角朝向上量出系统性 dYaw 偏移，说明还有第三处在写玩家旋转。
        if (RenderConfig.RECOIL_DEBUG.get() && (dPitch != 0 || dYaw != 0)) {
            GunMod.LOGGER.info(
                    "[TACZ RecoilDebug] apply t={} dPitch={} dYaw={} player=({},{}) camEvent=({},{}) shader={} irisHand={}",
                    timeTotal, fmt(dPitch), fmt(dYaw),
                    fmt(player.getXRot()), fmt(Mth.wrapDegrees(player.getYRot())),
                    fmt(event.getYaw()), fmt(event.getPitch()),
                    com.tacz.guns.compat.iris.IrisCompat.isUsingRenderPack(),
                    com.tacz.guns.compat.iris.IrisCompat.isHandRendererActive());
        }
    }

    /**
     * 【RecoilDebug 探针】开火瞬间记录两条后坐力样条在固定时刻的包络值，
     * 连同配件修正、瞄准进度修正与玩家朝向。多发射击后按朝向分桶对比
     * yawEnv 均值，直接判定「yaw 后坐力本身是否带系统性偏置」。
     */
    private static void debugLogRecoilFire(LocalPlayer player, float aimingRecoilModifier, float pitchMod, float yawMod,
                                           @javax.annotation.Nullable PolynomialSplineFunction pitchSpline,
                                           @javax.annotation.Nullable PolynomialSplineFunction yawSpline) {
        try {
            GunMod.LOGGER.info(
                    "[TACZ RecoilDebug] fire facing=({},{}) aimMod={} pmod={} ymod={} pitchEnv=[{}] yawEnv=[{}] shader={} irisHand={}",
                    fmt(player.getXRot()), fmt(Mth.wrapDegrees(player.getYRot())),
                    fmt(aimingRecoilModifier), fmt(pitchMod), fmt(yawMod),
                    sampleSpline(pitchSpline), sampleSpline(yawSpline),
                    com.tacz.guns.compat.iris.IrisCompat.isUsingRenderPack(),
                    com.tacz.guns.compat.iris.IrisCompat.isHandRendererActive());
        } catch (Throwable ignored) {
        }
    }

    private static String sampleSpline(@javax.annotation.Nullable PolynomialSplineFunction spline) {
        if (spline == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < RECOIL_DEBUG_SAMPLE_MS.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            double t = RECOIL_DEBUG_SAMPLE_MS[i];
            sb.append(fmt(spline.isValidPoint(t) ? spline.value(t) : Double.NaN));
        }
        return sb.toString();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%+.4f", v);
    }

    public static void onComputeMovementFov(ComputeFovModifierEvent event) {
        if (!RenderConfig.DISABLE_MOVEMENT_ATTRIBUTE_FOV.get()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        float f = 1.0f;
        if (player.getMainHandItem().getItem() instanceof AbstractGunItem) {
            if (player.getAbilities().flying) {
                f *= 1.1F;
            }
            event.setNewFovModifier(player.isSprinting() ? 1.15f * f : f);
        }
    }
}
