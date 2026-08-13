package com.tacz.guns.client.gui.preview;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;

import javax.annotation.Nullable;

/**
 * 枪械工作台左侧「旋转预览模型」的 GUI 渲染状态。
 *
 * <h2>为什么必须走 PIP（picture-in-picture）</h2>
 *
 * <p>1.21.1 的实现（{@code GunSmithTableScreen#renderLeftModel}）直接操作
 * {@code RenderSystem.getModelViewStack()}：push 一层矩阵、translate 到面板左侧、
 * {@code scale(scale, scale, scale)} 放大到 70 倍、绕 X/Y 轴旋转，然后立即
 * {@code ItemRenderer#renderStatic(..., FIXED, ...)} 并 {@code bufferSource.endBatch()}。</p>
 *
 * <p>26.2 把 GUI 拆成了「extract（收集 render state）→ 统一绘制」两段：
 * {@code RenderSystem.getModelViewStack()} 在 extract 阶段改它没有任何意义，
 * {@code renderStatic} 与 {@code endBatch} 也都不存在了。移植期间这段被降级成了
 * 一句 {@code graphics.item(result, x, y)} —— 也就是<b>画一个 16×16 的普通物品图标</b>：
 * 既不旋转、也不受 {@code scale} 影响。这正是玩家反馈「工作台里的模型没法缩放」的原因：
 * {@code +} / {@code -} / {@code R} 三个按钮改的 {@code scale} 字段<b>从头到尾没有被读过</b>。</p>
 *
 * <p>26.2 里唯一能在 GUI 内做「带自定义投影/变换的 3D 绘制」的官方通道就是
 * {@code PictureInPictureRenderer}：vanilla 自己的实体预览（{@code GuiEntityRenderer}）、
 * 超框物品（{@code OversizedItemRenderer}）、告示牌/书本预览全走这条路。
 * 它会把内容渲染到一张离屏纹理上，再作为一次 blit 合回 GUI，
 * 天然解决了「GUI 是 2D 批处理、模型是 3D 提交」这对矛盾。</p>
 *
 * <h2>字段语义（对齐 {@code PictureInPictureRenderer#prepare} 的字节码）</h2>
 * <ul>
 *   <li>{@code x0/y0/x1/y1}：离屏区域在 GUI 坐标系里的矩形，纹理尺寸 =
 *       {@code (x1-x0) * guiScale × (y1-y0) * guiScale}；</li>
 *   <li>{@code scale()}：{@code prepare} 里做 {@code poseStack.scale(guiScale * scale)}，
 *       因此它的物理含义是「1 个模型单位 = 多少 GUI 像素」——
 *       与 1.21.1 那句 {@code posestack.scale(scale, scale, scale)} 完全等价，
 *       所以默认值同样取 70，{@code +/-} 按钮同样在 10..200 之间调整；</li>
 *   <li>{@code scissorArea}：只影响最后那次 blit 的裁剪，用来复刻上游
 *       {@code RenderSystem.enableScissor} 限定的面板可视框；</li>
 *   <li>{@code bounds}：{@code ScreenArea} 要求，供 GUI 分层排序使用，
 *       由 {@code PictureInPictureRenderState#getBounds} 计算（已含 scissor 求交）。</li>
 *   <li>{@code offsetX/offsetY}：模型原点相对<b>预览框中心</b>的偏移，单位为 GUI 像素。
 *       PIP 的 {@code prepare} 只能把原点放在「水平居中 + {@code getTranslateY} 指定的高度」，
 *       而上游的模型原点是 {@code (leftPos+68, topPos+58)}，与 128×99 预览框的中心
 *       {@code (leftPos+67, topPos+65.5)} 差了 {@code (+1, -7.5)}。
 *       不补这一步，枪会比上游偏低一点点 —— 数值虽小，但既然是「还原度」议题就一并对齐。</li>
 * </ul>
 */
public record GunPreviewRenderState(ItemStackRenderState item,
                                    int x0,
                                    int y0,
                                    int x1,
                                    int y1,
                                    float scale,
                                    float pitch,
                                    float yaw,
                                    float offsetX,
                                    float offsetY,
                                    @Nullable ScreenRectangle scissorArea,
                                    @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {

    public GunPreviewRenderState(ItemStackRenderState item,
                                 int x0, int y0, int x1, int y1,
                                 float scale, float pitch, float yaw,
                                 float offsetX, float offsetY,
                                 @Nullable ScreenRectangle scissorArea) {
        this(item, x0, y0, x1, y1, scale, pitch, yaw, offsetX, offsetY, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}
