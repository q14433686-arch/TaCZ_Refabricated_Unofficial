package com.tacz.guns.client.event;

import com.tacz.guns.util.time.GunTime;
import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import cn.sh1rocu.tacz.api.event.ComputeFovModifierEvent;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.api.modifier.ParameterizedCachePair;
import com.tacz.guns.client.render.scope.ScopePipRenderState;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.AdsFov;
import com.tacz.guns.util.math.Easing;
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

import java.util.Optional;

@Environment(EnvType.CLIENT)
public class CameraSetupEvent {
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
            // Gate on the same interpolated progress the fallback below uses (frame partial-tick),
            // so a boundary frame cannot "fall through" with a non-1x aim value. A fixed 0/1 tick
            // value is one frame out of phase at one side of the transition and still caused the
            // exit POV jump.
            if (ScopePipRenderState.suppressesWorldFovZoom((float) event.getPartialTick())) {
                // Step 3 (real PIP): the lens owns the scope zoom, but part of it may be assigned to
                // the world by ScopePipWorldZoomShare. Apply exactly that share using the same
                // progress formula as the non-PIP fallback below, and keep WORLD_FOV_DYNAMICS tracking
                // it (sprint's moving base FOV must not snap on exit). With the default share=0 this
                // reduces to "keep the live base FOV" — the previous suppression behavior.
                float aimingProgress;
                if (livingEntity instanceof LocalPlayer localPlayer) {
                    IClientPlayerGunOperator gunOperator = IClientPlayerGunOperator.fromLocalPlayer(localPlayer);
                    aimingProgress = gunOperator.getClientAimingProgress((float) event.getPartialTick());
                } else {
                    IGunOperator gunOperator = IGunOperator.fromLivingEntity(livingEntity);
                    aimingProgress = gunOperator.getSynAimingProgress();
                }
                float worldZoom = ScopePipRenderState.worldZoomAtProgress(aimingProgress);
                float baseFov = (float) event.getFOV();
                float fov = WORLD_FOV_DYNAMICS.update((float) MathUtil.magnificationToFov(worldZoom, baseFov));
                event.setFOV(fov);
                return;
            }
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
            var display = TimelessAPI.getGunDisplay(stack);
            float base = display.map(GunDisplayInstance::getModelFov).orElse((float) event.getFOV());
            Identifier resolved = scopeItemId;
            var fovs = TimelessAPI.getClientAttachmentVariantIndex(resolved, scopeTag).map(index -> index.getViewsFov())
                    .or(() -> TimelessAPI.getClientAttachmentIndex(resolved).map(index -> index.getViewsFov()));
            float modified = fovs.map(fov -> fov[Math.floorMod(zoomNumber, fov.length)])
                    .orElseGet(() -> display.map(GunDisplayInstance::getZoomModelFov).orElse(base));
            AdsFov.Channel curve = display.map(GunDisplayInstance::getAdsFov).map(AdsFov::getModel).orElse(null);
            float progress = fovProgress(livingEntity, (float) event.getPartialTick(), curve);
            event.setFOV(applyFovCurve(ITEM_MODEL_FOV_DYNAMICS, Mth.lerp(progress, base, modified), curve));
        }
    }

    private static float fovProgress(LivingEntity entity, float partialTick, AdsFov.Channel curve) {
        float progress = entity instanceof LocalPlayer player
                ? IClientPlayerGunOperator.fromLocalPlayer(player).getClientAimingProgress(partialTick)
                : IGunOperator.fromLivingEntity(entity).getSynAimingProgress();
        return curve == null ? (float) Easing.easeInExpo(progress) : curve.evaluate(progress);
    }

    private static float applyFovCurve(SecondOrderDynamics smoother, float target, AdsFov.Channel curve) {
        // Even in direct mode advance the smoother so switching guns doesn't restore a stale FOV.
        float smoothed = smoother.update(target);
        return curve == null || curve.isSmooth() ? smoothed : target;
    }

    // Kept callable for 26.3 integrations. Our own listener only runs after all pre callbacks voted.
    public static void initialCameraRecoil(GunFireEvent event) {
        if (!event.isCanceled() && event.getLogicalSide().isClient()) initialCameraRecoil(event.getShooter());
    }

    public static void initialCameraRecoilPost(GunFirePostEvent event) {
        if (event.getLogicalSide().isClient()) initialCameraRecoil(event.getShooter());
    }

    private static void initialCameraRecoil(LivingEntity shooter) {
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
        shootTimeStamp = GunTime.nowMillis();
        xRotO = 0;
        yRotO = 0;
    }

    public static void applyCameraRecoil(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        long timeTotal = GunTime.nowMillis() - shootTimeStamp;
        if (pitchSplineFunction != null && pitchSplineFunction.isValidPoint(timeTotal)) {
            double value = pitchSplineFunction.value(timeTotal);
            player.setXRot(player.getXRot() - (float) (value - xRotO));
            xRotO = value;
        }
        if (yawSplineFunction != null && yawSplineFunction.isValidPoint(timeTotal)) {
            double value = yawSplineFunction.value(timeTotal);
            player.setYRot(player.getYRot() - (float) (value - yRotO));
            yRotO = value;
        }
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

    public static void resetTimestamp() {
        shootTimeStamp = -1;
        pitchSplineFunction = null;
        yawSplineFunction = null;
        xRotO = 0;
        yRotO = 0;
    }
}
