package com.tacz.guns.client.gui.overlay;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * 击杀数提示。<b>已按上游 1.21.1 逐项对齐</b>。
 *
 * <h2>此前与上游的差异（用户实测对照图 57 / 01）</h2>
 * 旧实现只是「把功能做出来」，样式与上游完全不同：
 * <table border="1">
 *   <tr><th></th><th>旧实现</th><th>上游（现已对齐）</th></tr>
 *   <tr><td>文本</td><td>{@code × 1}</td><td>{@code ☠ x 01}（骷髅符号 + 个位补零）</td></tr>
 *   <tr><td>位置</td><td>屏幕正中偏下</td><td><b>右下角</b>，准星右侧</td></tr>
 *   <tr><td>缩放</td><td>无（原始字号）</td><td>{@code 0.5} 倍</td></tr>
 *   <tr><td>颜色</td><td>固定红 {@code 0xFF5555}</td><td>按连杀数做 <b>HSV 渐变</b>（黄→红）</td></tr>
 *   <tr><td>淡出</td><td>全程线性</td><td>前 2/3 全不透明，后 1/3 才淡出</td></tr>
 *   <tr><td>前置条件</td><td>仅看计数</td><td>还要求<b>主手持枪</b></td></tr>
 * </table>
 *
 * <h2>26.2 移植要点</h2>
 * <ul>
 *   <li>{@code PoseStack} → {@code Matrix3x2fStack}，{@code pushPose/popPose} →
 *       {@code pushMatrix/popMatrix}（与 {@code GunSmithTableScreen} 已验证的写法一致）；</li>
 *   <li>{@code RenderSystem.enableBlend()} 等已移除 —— 26.2 的 GUI 文本走
 *       {@code GuiRenderState}，混合由管线自带，不需要手动开关；</li>
 *   <li>颜色<b>必须带 alpha</b>：{@code GuiGraphicsExtractor#text} 的第一条指令就是
 *       {@code if (ARGB.alpha(color) == 0) return;}，上游的
 *       {@code Mth.hsvToRgb(...) + (alpha << 24)} 天然满足，这里原样保留。</li>
 * </ul>
 */
public class KillAmountOverlay {
    private static long killTimestamp = -1L;
    private static int killAmount = 0;

    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        if (!RenderConfig.KILL_AMOUNT_ENABLE.get()) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int timeout = (int) (RenderConfig.KILL_AMOUNT_DURATION_SECOND.get() * 1000);
        // 连杀数达到该值时颜色变到最红；上游取 30。
        float colorCount = 30;

        long remainTime = System.currentTimeMillis() - killTimestamp;
        if (remainTime > timeout) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!(player instanceof IClientPlayerGunOperator)) {
            return;
        }
        // 上游语义：只有主手持枪时才显示击杀提示。
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun)) {
            return;
        }

        String text;
        if (killAmount < 10) {
            text = "\u2620 x 0" + killAmount;
        } else {
            text = "\u2620 x " + killAmount;
        }
        int fontWith = mc.font.width(text);
        // 前 2/3 时间保持不透明，最后 1/3 才开始淡出。
        double fadeOutTime = timeout / 3.0 * 2;
        float hue = (1 - Math.min((killAmount / colorCount), 1)) * 0.15f;
        int alpha = 0xFF;
        if (remainTime > fadeOutTime) {
            alpha = 0xFF - (int) ((remainTime - fadeOutTime) / (timeout - fadeOutTime) * 0xF0);
        }
        int color = Mth.hsvToRgb(hue, 0.75f, 1) + (alpha << 24);

        Matrix3x2fStack poseStack = graphics.pose();
        poseStack.pushMatrix();
        {
            // 先缩放再用 2 倍坐标定位 —— 与上游逐字一致：
            // 缩放 0.5 后，屏幕像素 (x, y) 对应的绘制坐标是 (2x, 2y)。
            poseStack.scale(0.5f, 0.5f);
            graphics.text(mc.font, text, (int) (width - fontWith / 2.0f), (height - 45) * 2 - 1, color, false);
        }
        poseStack.popMatrix();
    }

    public static void markTimestamp() {
        int timeout = (int) (RenderConfig.KILL_AMOUNT_DURATION_SECOND.get() * 1000);
        if (System.currentTimeMillis() - killTimestamp > timeout) {
            killAmount = 0;
        }
        killTimestamp = System.currentTimeMillis();
        killAmount += 1;
    }
}
