package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalSubmitter;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.render.scope.ScopeRenderTypes;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import it.unimi.dsi.fastutil.Pair;
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
        shootTimeStamp = System.currentTimeMillis();
        // 记录枪口火焰启动标记
        muzzleFlashStartMark = true;
        // 随机给予枪口火焰的旋转
        muzzleFlashRandomRotate = (float) (Math.random() * 360);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void extract(ExtractionContext context) {
        if (IrisCompat.isRenderShadow() || !isSelf) {
            return;
        }
        long time = System.currentTimeMillis() - shootTimeStamp;
        if (time < 0 || time > TIME_RANGE) {
            return;
        }

        ItemStack currentGunItem = bedrockGunModel.getCurrentGunItem();
        GunDisplayInstance display = TimelessAPI.getGunDisplay(currentGunItem).orElse(null);
        if (display == null || display.getMuzzleFlash() == null) {
            return;
        }

        ItemStack muzzleAttachment = bedrockGunModel.getCurrentAttachmentItem().get(AttachmentType.MUZZLE);
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(muzzleAttachment);
        if (iAttachment != null) {
            var index = TimelessAPI.getCommonAttachmentIndex(iAttachment.getAttachmentId(muzzleAttachment)).orElse(null);
            if (index != null) {
                var modifier = index.getData().getModifier();
                if (modifier.containsKey(SilenceModifier.ID)
                        && modifier.get(SilenceModifier.ID).getValue() instanceof Pair<?, ?> pair
                        && ((Pair<Integer, Boolean>) pair).right()) {
                    return;
                }
            }
        }

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
        // when this same first-person gun submission actually queued an ocular sequence. At draw
        // time ScopeDepthCopyState validates both depth copies and fails open to normal rendering.
        boolean clipToScopeExterior = context.displayContext() != null
                && context.displayContext().firstPerson()
                && ScopeRenderTypes.hasScheduledViewmodelAperture();
        RenderType backgroundType = clipToScopeExterior
                ? ScopeRenderTypes.flashTranslucentClipped(muzzleFlash.getTexture())
                : RenderTypes.entityTranslucent(muzzleFlash.getTexture());
        RenderType glowType = clipToScopeExterior
                ? ScopeRenderTypes.flashSwirlClipped(muzzleFlash.getTexture())
                : RenderTypes.energySwirl(muzzleFlash.getTexture(), 1, 1);

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

}
