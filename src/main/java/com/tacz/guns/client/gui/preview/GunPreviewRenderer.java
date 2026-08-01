package com.tacz.guns.client.gui.preview;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 枪械工作台左侧预览模型的 PIP 渲染器。见 {@link GunPreviewRenderState} 的类注释了解为何走 PIP。
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
 * <p>光照用 {@code ITEMS_3D}：上游是 {@code Lighting.setupFor3DItems()}，26.2 的等价物。
 * 亮度用满亮 {@code 15728880}，对应上游 {@code renderStatic(..., 0xf000f0, ...)}。</p>
 *
 * <p><b>不覆写 {@code textureIsReadyToBlit}</b>：基类默认返回 false，即每帧重画。
 * 预览模型一直在自转，缓存纹理反而会把它冻住。</p>
 */
public class GunPreviewRenderer extends PictureInPictureRenderer<GunPreviewRenderState> {

    @Override
    public Class<GunPreviewRenderState> getRenderStateClass() {
        return GunPreviewRenderState.class;
    }

    @Override
    protected void renderToTexture(GunPreviewRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        // 把原点从「预览框中心」移到上游的模型原点。
        // 此刻 1 单位 = state.scale() 个 GUI 像素（prepare 里 scale(guiScale * scale)），
        // 且 y 轴方向仍与 GUI 一致（向下为正），所以直接按 GUI 像素除以 scale 即可。
        // 必须在下面的翻转之前做，否则 y 的符号会反。
        poseStack.translate(state.offsetX() / state.scale(), state.offsetY() / state.scale(), 0.0F);
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch()));
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw()));
        state.item().submit(poseStack, collector, 15728880, OverlayTexture.NO_OVERLAY, 0);
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
