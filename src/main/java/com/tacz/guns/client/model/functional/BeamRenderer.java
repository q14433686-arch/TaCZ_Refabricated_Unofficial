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
    /** LaserDebug 探针的节流时间戳（见 renderLaserBeam 内的注释）。 */
    private static long LAST_LASER_DEBUG_LOG = 0L;

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

        // 26.2: MultiBufferSource 已移除，通过 SubmitNodeCollector.submitCustomGeometry 提交自定义几何
        if (collector == null) {
            // 【2026-08-11 核实】此分支目前不可达：现存两个调用方
            // （BedrockGunModel / BedrockAttachmentModel）都传了 collector；
            // collector==null 只会来自 4 参便捷重载，而它没有调用方。
            // 语义：无 collector 即不画（26.2 下没有可退回的即时渲染通道）。
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

            // 【LaserDebug · 第 28 轮探针】NVIDIA+Iris 下改激光颜色无效一案的数据侧取证。
            // 记录本次提交写入顶点色的 RGB、颜色来源（玩家自定义 / 默认配置）、
            // 渲染上下文与光影状态：若改色后日志的 RGB 跟着变而画面不变，
            // 问题在 GL/Iris 管线侧（顶点色被吞）；若日志也不变，问题在数据侧。
            if (RenderConfig.LASER_DEBUG.get()
                    && System.currentTimeMillis() - LAST_LASER_DEBUG_LOG > 1000) {
                LAST_LASER_DEBUG_LOG = System.currentTimeMillis();
                boolean custom = false;
                if (stack.getItem() instanceof com.tacz.guns.api.item.IAttachment) {
                    custom = ((com.tacz.guns.api.item.IAttachment) stack.getItem()).hasCustomLaserColor(stack);
                } else if (stack.getItem() instanceof com.tacz.guns.api.item.IGun) {
                    custom = ((com.tacz.guns.api.item.IGun) stack.getItem()).hasCustomLaserColor(stack);
                }
                GunMod.LOGGER.info("[TACZ LaserDebug] beam submit color=#{} custom={} ctx={} irisPack={} irisHand={}",
                        String.format("%06X", color & 0xFFFFFF), custom, transformType,
                        com.tacz.guns.compat.iris.IrisCompat.isUsingRenderPack(),
                        com.tacz.guns.compat.iris.IrisCompat.isHandRendererActive());
            }

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
        // 26.2: ARCompat 加速渲染路径暂时禁用（Accelerated Rendering 无 26.2 构建）
        if (!ARCompat.shouldAccelerate()) {
            return false;
        }
        // TODO 26.2: 待 Accelerated Rendering 移植后恢复加速路径
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
