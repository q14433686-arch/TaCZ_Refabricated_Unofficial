package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.LaserColorUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class BeamRenderer {
    public static final Identifier LASER_BEAM_TEXTURE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/entity/beam.png");
    private static final LaserConfig DEFAULT_LASER_CONFIG = new LaserConfig();

    /**
     * 26.2 迁移: 使用 RenderTypes.entityTranslucentEmissive 替代自定义 RenderStateShard 组合。
     * 旧的 additive blend 效果由 entityTranslucentEmissive 内置管线提供。
     */
    public static RenderType getLaserBeam() {
        return RenderTypes.entityTranslucentEmissive(LASER_BEAM_TEXTURE);
    }

    public static RenderType getLaserBeamEntity() {
        return RenderTypes.entityTranslucentEmissive(LASER_BEAM_TEXTURE);
    }

    public static void renderLaserBeam(ItemStack stack, PoseStack poseStack, ItemDisplayContext transformType, @Nonnull List<BedrockPart> path) {
        renderLaserBeam(stack, poseStack, transformType, path, null);
    }

    public static void renderLaserBeam(ItemStack stack, PoseStack poseStack, ItemDisplayContext transformType, @Nonnull List<BedrockPart> path, @Nullable SubmitNodeCollector collector) {
        if (stack == null || !transformType.firstPerson() && !(transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
            return;
        }

        if (ARCompat.shouldAccelerate() && renderLaserBeamAccelerated(stack, poseStack, transformType, path, collector)) {
            return;
        }

        // The 26.1.2 renderer is collector-only. Every built-in gun and attachment submission
        // supplies a collector; the nullable legacy overload is retained solely for binary/source
        // compatibility with old callers and cannot emit delayed geometry by itself.
        if (collector == null) {
            return;
        }

        poseStack.pushPose();
        {
            for (int i = 0; i < path.size(); ++i) {
                path.get(i).translateAndRotateAndScale(poseStack);
            }

            LaserConfig laserConfig = getLaserConfig(stack);

            int color = LaserColorUtil.getLaserColor(stack, laserConfig);
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            float z = transformType.firstPerson() ? -laserConfig.getLength() : -laserConfig.getLengthThird();
            float width = transformType.firstPerson() ? laserConfig.getWidth() : laserConfig.getWidthThird();
            boolean fadeOut = RenderConfig.ENABLE_LASER_FADE_OUT.get();

            collector.submitCustomGeometry(poseStack, getLaserBeam(), (pose, consumer) -> {
                stringVertex(z, width, consumer, pose, r, g, b, fadeOut);
            });
        }
        poseStack.popPose();
    }

    public static boolean renderLaserBeamAccelerated(ItemStack stack, PoseStack poseStack, ItemDisplayContext transformType, @Nonnull List<BedrockPart> path, @Nullable SubmitNodeCollector collector) {
        // Accelerated Rendering has no 26.1.2 build/API for Feature Rendering. The ordinary
        // collector path above is complete and remains the authoritative rendering path.
        if (!ARCompat.shouldAccelerate()) {
            return false;
        }
        return false;
    }

    private static LaserConfig getLaserConfig(ItemStack stack) {
        if (stack == null) {
            return DEFAULT_LASER_CONFIG;
        }

        if (stack.getItem() instanceof IAttachment iAttachment) {
            return TimelessAPI.getClientAttachmentIndex(iAttachment.getAttachmentId(stack))
                    .map(ClientAttachmentIndex::getLaserConfig)
                    .orElse(DEFAULT_LASER_CONFIG);
        }

        if (stack.getItem() instanceof IGun) {
            return TimelessAPI.getGunDisplay(stack)
                    .map(GunDisplayInstance::getLaserConfig)
                    .orElse(DEFAULT_LASER_CONFIG);
        }

        return DEFAULT_LASER_CONFIG;
    }

    private static void stringVertex(float z, float width, VertexConsumer pConsumer, PoseStack.Pose pPose, int r, int g, int b, boolean fadeOut) {
        float halfWidth = width / 2;
        int endAlpha = fadeOut ? 0 : 255;
        int light = 15728880;
        int overlay = OverlayTexture.NO_OVERLAY;
        // 26.2: addVertex(pose, x,y,z).setColor().setUv().setOverlay().setLight().setNormal() - must complete all vertex elements
        pConsumer.addVertex(pPose.pose(), -halfWidth, -halfWidth, 0).setColor(r, g, b, 255).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);
        pConsumer.addVertex(pPose.pose(), -halfWidth, halfWidth, 0).setColor(r, g, b, 255).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);
        pConsumer.addVertex(pPose.pose(), -halfWidth, halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);
        pConsumer.addVertex(pPose.pose(), -halfWidth, -halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);

        pConsumer.addVertex(pPose.pose(), -halfWidth, halfWidth, 0).setColor(r, g, b, 255).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);
        pConsumer.addVertex(pPose.pose(), halfWidth, halfWidth, 0).setColor(r, g, b, 255).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);
        pConsumer.addVertex(pPose.pose(), halfWidth, halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);
        pConsumer.addVertex(pPose.pose(), -halfWidth, halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);

        pConsumer.addVertex(pPose.pose(), halfWidth, halfWidth, 0).setColor(r, g, b, 255).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);
        pConsumer.addVertex(pPose.pose(), halfWidth, -halfWidth, 0).setColor(r, g, b, 255).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);
        pConsumer.addVertex(pPose.pose(), halfWidth, -halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);
        pConsumer.addVertex(pPose.pose(), halfWidth, halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);

        pConsumer.addVertex(pPose.pose(), halfWidth, -halfWidth, 0).setColor(r, g, b, 255).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);
        pConsumer.addVertex(pPose.pose(), -halfWidth, -halfWidth, 0).setColor(r, g, b, 255).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);
        pConsumer.addVertex(pPose.pose(), -halfWidth, -halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);
        pConsumer.addVertex(pPose.pose(), halfWidth, -halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(pPose, 0, 0, 1);
    }
}
