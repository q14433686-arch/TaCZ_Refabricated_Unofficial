package com.tacz.guns.client.gui.preview;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 枪械工作台左侧预览模型的 PIP 渲染器。见 {@link GunPreviewRenderState} 的类注释了解为何走 PIP。
 *
 * <h2>26.1.2 回移植适配说明（与 26.2 版本的差异）</h2>
 *
 * <p>26.2 的基类签名是
 * {@code renderToTexture(PictureInPictureRenderState, PoseStack, SubmitNodeCollector)}，
 * collector 由框架传入；26.1.2 的基类签名只有两参
 * {@code renderToTexture(PictureInPictureRenderState, PoseStack)}（字节码确认），
 * 且基类构造器要求 {@link MultiBufferSource.BufferSource}，由 Fabric
 * {@code PictureInPictureRendererRegistry.Context#bufferSource()} 提供。</p>
 *
 * <p>因此物品渲染状态的提交方式按 vanilla 26.1.2 同版本的
 * {@code OversizedItemRenderer#renderToTexture} 照抄：
 * 从 {@code GameRenderer#getFeatureRenderDispatcher().getSubmitNodeStorage()}
 * 取 {@code SubmitNodeStorage}（其实现 {@code SubmitNodeCollector}）作为提交目标，
 * 提交后立即 {@code renderAllFeatures()} 把队列画进当前绑定的 PIP 离屏纹理。
 * 26.1.2 的 {@code ItemStackRenderState#submit(PoseStack, SubmitNodeCollector, int, int, int)}
 * 签名与 26.2 一致（均已对 minecraft-merged-0d09a28b48-26.1.2.jar 逐符号验证）。</p>
 *
 * <p>另一个改名点：26.2 的 {@code gameRenderer.lighting()} 在 26.1.2 叫
 * {@code GameRenderer#getLighting()}，返回同一个 {@link Lighting} 实例。</p>
 *
 * <h2>变换链与 1.21.1 的逐项对应</h2>
 *
 * <p>{@code PictureInPictureRenderer#prepare}（字节码确认）在调用本方法之前已经做了：</p>
 * <pre>
 * poseStack.translate(width / 2.0F, getTranslateY(height, guiScale), 0.0F);
 * float s = guiScale * state.scale();
 * poseStack.scale(s, s, -s);
 * </pre>
 * 也就是说进入 {@link #renderToTexture} 时，原点已经在离屏纹理的
 * 「水平居中 + {@link #getTranslateY} 指定的纵向位置」，且 1 单位 = {@code scale} 个 GUI 像素。
 *
 * <p>因此这里只需补上剩下三步，与上游一一对应：</p>
 * <table border="1">
 *   <tr><th>1.21.1</th><th>这里</th></tr>
 *   <tr><td>{@code posestack.scale(1.0F, -1.0F, 1.0F)}</td>
 *       <td>{@code scale(1, -1, -1)}（外层已带一个 -Z，两者相乘等于上游的 +Z；
 *           与 vanilla {@code OversizedItemRenderer} 的写法一致）</td></tr>
 *   <tr><td>{@code Axis.XP.rotationDegrees(rotPitch)}</td><td>同</td></tr>
 *   <tr><td>{@code Axis.YP.rotationDegrees(rot)}</td><td>同</td></tr>
 * </table>
 *
 * <p>光照用 {@code ITEMS_3D}：上游是 {@code Lighting.setupFor3DItems()}，本版的等价物。
 * 亮度用满亮 {@code 15728880}，对应上游 {@code renderStatic(..., 0xf000f0, ...)}。</p>
 *
 * <p><b>不覆写 {@code textureIsReadyToBlit}</b>：基类默认返回 false，即每帧重画。
 * 预览模型一直在自转，缓存纹理反而会把它冻住。</p>
 */
public class GunPreviewRenderer extends PictureInPictureRenderer<GunPreviewRenderState> {

    /**
     * 26.1.2 的基类只有 {@code PictureInPictureRenderer(MultiBufferSource.BufferSource)}
     * 这一个构造器（protected，字节码确认），bufferSource 由 Fabric 注册上下文提供。
     */
    public GunPreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<GunPreviewRenderState> getRenderStateClass() {
        return GunPreviewRenderState.class;
    }

    @Override
    protected void renderToTexture(GunPreviewRenderState state, PoseStack poseStack) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
        // 把原点从「预览框中心」移到上游的模型原点。
        // 此刻 1 单位 = state.scale() 个 GUI 像素（prepare 里 scale(guiScale * scale)），
        // 且 y 轴方向仍与 GUI 一致（向下为正），所以直接按 GUI 像素除以 scale 即可。
        // 必须在下面的翻转之前做，否则 y 的符号会反。
        poseStack.translate(state.offsetX() / state.scale(), state.offsetY() / state.scale(), 0.0F);
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch()));
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw()));
        // 与 vanilla 26.1.2 的 OversizedItemRenderer#renderToTexture 同一套提交路径：
        // FeatureRenderDispatcher 的 SubmitNodeStorage 就是本帧 GUI 阶段的 SubmitNodeCollector。
        state.item().submit(poseStack,
                mc.gameRenderer.getFeatureRenderDispatcher().getSubmitNodeStorage(),
                15728880, OverlayTexture.NO_OVERLAY, 0);
        mc.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
    }

    /**
     * 基类默认返回 {@code height}（即把原点放在纹理底边，供实体预览「站在地面上」用）。
     * 枪械预览要的是<b>居中</b>，与上游把模型画在预览框中心一致。
     */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "tacz_gun_preview";
    }
}
