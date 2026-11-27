package com.tacz.guns.client.model.functional;

import com.tacz.guns.util.time.GunTime;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.particle.MuzzleAttachment;
import com.tacz.guns.client.particle.NativeMuzzleEffects;
import com.tacz.guns.client.render.FlashMaterials;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalSubmitter;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.render.scope.ScopeRenderTypes;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.item.ItemStack;

public class MuzzleFlashRender implements IFunctionalSubmitter {
    private static final SlotModel MUZZLE_FLASH_MODEL = new SlotModel(true);
    /**
     * 50ms 显示时间
     */
    private static final long TIME_RANGE = 50;
    public static boolean isSelf = false;
    private static long shootTimeStamp = -1;
    private static boolean muzzleFlashStartMark = false;
    private static float muzzleFlashRandomRotate = 0;

    private final BedrockGunModel bedrockGunModel;

    public MuzzleFlashRender(BedrockGunModel bedrockGunModel) {
        this.bedrockGunModel = bedrockGunModel;
    }

    public static void onShoot() {
        // 记录开火时间戳
        shootTimeStamp = GunTime.nowMillis();
        // 记录枪口火焰启动标记
        muzzleFlashStartMark = true;
        // 随机给予枪口火焰的旋转
        muzzleFlashRandomRotate = (float) (Math.random() * 360);
    }

    @Override
    public void extract(ExtractionContext context) {
        if (IrisCompat.isRenderShadow()) return;
        ItemStack currentGunItem = bedrockGunModel.getCurrentGunItem();
        if (RenderConfig.MUZZLE_SURFACE_LIGHT.get()) com.tacz.guns.client.render.light.SurfaceLights.muzzleLocator(
                currentGunItem, context.displayContext(), context.poseStack().last().pose());
        // Renew owns two independent layers. Disabling the flash must not suppress the smoke.
        if (NativeMuzzleEffects.extract(currentGunItem, context)) return;
        if (!isSelf || RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.get()) return;
        // 曾在此调 IrisCompat.assignCommonEntityPipelinesToHandIfNeeded()（vanilla 管线归 HAND）。
        // 在 Iris 26.2 上那是 no-op（管线已预注册、assignPipeline 抛 already assigned），
        // 只留下日志噪音；已移除，见 IrisCompat#assignCommonEntityPipelinesToHandIfNeeded。
        long time = GunTime.nowMillis() - shootTimeStamp;
        if (time < 0 || time > TIME_RANGE) {
            return;
        }

        GunDisplayInstance display = TimelessAPI.getGunDisplay(currentGunItem).orElse(null);
        if (display == null || display.getMuzzleFlash() == null) {
            return;
        }

        // Legacy uses the silence flag, not renew's fractional flash_hider.
        if (MuzzleAttachment.of(currentGunItem).silenced()) return;

        MuzzleFlash muzzleFlash = display.getMuzzleFlash();
        float scale = 0.5f * muzzleFlash.getScale();
        float scaleTime = TIME_RANGE / 2.0f;
        if (time < scaleTime) {
            scale *= time / scaleTime;
        }
        float frozenScale = scale;
        float frozenRotation = muzzleFlashRandomRotate;
        PoseStack frozenPose = context.poseStack();
        int light = context.light();
        int overlay = context.overlay();
        muzzleFlashStartMark = false;

        // The depth aperture is restored before ordinary translucent FX, so both muzzle-flash
        // layers would otherwise reappear inside the scope. Select an aperture-aware type only
        // when this same first-person gun submission actually queued an ocular sequence AND the
        // scope is at/above ScopePipMinMagnification (default 4x): low-power optics have no
        // magnified lens picture to make room for, so punching the flash out there only reads
        // as a hole in the flame. At draw time ScopeDepthCopyState validates both depth copies
        // and fails open to normal rendering.
        boolean clipToScopeExterior = context.displayContext() != null
                && context.displayContext().firstPerson()
                && ScopeRenderTypes.shouldClipViewmodel();
        RenderType backgroundType = FlashMaterials.legacyStrip(muzzleFlash.getTexture(), clipToScopeExterior);
        RenderType glowType = FlashMaterials.swirl(muzzleFlash.getTexture(), clipToScopeExterior, clipToScopeExterior ? 0 : 1);

        context.add(collector -> {
            PoseStack backgroundPose = new PoseStack();
            backgroundPose.last().pose().set(frozenPose.last().pose());
            backgroundPose.last().normal().set(frozenPose.last().normal());
            backgroundPose.scale(frozenScale, frozenScale, frozenScale);
            backgroundPose.mulPose(Axis.ZP.rotationDegrees(frozenRotation));
            backgroundPose.translate(0, -1, 0);
            collector.submitCustomGeometry(backgroundPose, backgroundType,
                    (pose, buffer) -> MUZZLE_FLASH_MODEL.renderToBuffer(
                            backgroundPose, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F));

            PoseStack glowPose = new PoseStack();
            glowPose.last().pose().set(frozenPose.last().pose());
            glowPose.last().normal().set(frozenPose.last().normal());
            glowPose.scale(frozenScale / 2, frozenScale / 2, frozenScale / 2);
            glowPose.mulPose(Axis.ZP.rotationDegrees(frozenRotation));
            glowPose.translate(0, -0.9, 0);
            collector.submitCustomGeometry(glowPose, glowType,
                    (pose, buffer) -> MUZZLE_FLASH_MODEL.renderToBuffer(
                            glowPose, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F));
        });
    }

    public static void resetTimestamps() {
        shootTimeStamp = -1;
        muzzleFlashStartMark = false;
        isSelf = false;
    }
}
