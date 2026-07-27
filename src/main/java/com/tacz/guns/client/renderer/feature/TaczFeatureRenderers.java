package com.tacz.guns.client.renderer.feature;

import com.tacz.guns.GunMod;
import net.fabricmc.fabric.api.client.rendering.v1.FeatureRendererRegistry;

/**
 * TACZ 26.2 自定义 FeatureRenderers 注册入口
 * <p>
 * 真实 API 来源: https://github.com/FabricMC/fabric-api/blob/26.2/fabric-rendering-v1/src/client/java/net/fabricmc/fabric/api/client/rendering/v1/FeatureRendererRegistry.java
 * <p>
 * 用法: 在 TACZ 的 {@code ClientModInitializer.onInitializeClient} 中调用 {@link #register()}
 * <pre>{@code
 * public class TacZClientInit implements ClientModInitializer {
 *     @Override
 *     public void onInitializeClient() {
 *         TaczFeatureRenderers.register();
 *     }
 * }
 * }</pre>
 */
public final class TaczFeatureRenderers {

    private TaczFeatureRenderers() {
    }

    /**
     * 注册所有 TACZ 自定义 FeatureRenderer
     * <p>
     * 当前注册:
     * <ul>
     *   <li>{@link GunModelFeatureRenderer} - 枪械 Bedrock 模型渲染</li>
     *   <li>未来: AttachmentModelFeatureRenderer, MuzzleFlashRenderer 等</li>
     * </ul>
     */
    public static void register() {
        GunMod.LOGGER.info("[TACZ] Registering FeatureRenderers for 26.2...");

        // 枪械模型
        FeatureRendererRegistry.register(
                GunModelFeatureRenderer.TYPE,
                GunModelFeatureRenderer::new
        );

        // 未来: 附件模型 (AttachmentModelFeatureRenderer)
        // FeatureRendererRegistry.register(
        //     AttachmentModelFeatureRenderer.TYPE,
        //     AttachmentModelFeatureRenderer::new
        // );

        GunMod.LOGGER.info("[TACZ] FeatureRenderers registered.");
    }
}
