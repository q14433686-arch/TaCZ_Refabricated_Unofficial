package me.xjqsh.lrtactical.client.overlay;

import me.xjqsh.lrtactical.init.ModEffects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 致盲遮罩 —— 被闪光弹闪到时往整个屏幕糊一层白。
 *
 * <p>这是 {@code ModEffects.BLIND} 的<b>实际效果所在</b>：
 * 那个 {@code MobEffect} 本身是空壳，不画这层就等于没有闪光弹。
 *
 * <h2>26.2 移植要点</h2>
 * <ol>
 *   <li><b>颜色必须带 alpha</b>。26.2 会在 alpha=0 时<b>整段短路不画</b>
 *       （PORTING_NOTES 第 1 节：本项目单一原因造成最多 bug 的一条）。
 *       这里的 alpha 本来就是动态计算的，但仍要确保它<b>永不为 0</b> ——
 *       否则不是「淡出」而是「突然消失」。</li>
 *   <li>渲染入口是 {@code HudElementRegistry.addLast} +
 *       {@code GuiGraphicsExtractor}（1.21.1 是 {@code GuiGraphics}），
 *       照抄本仓库 {@code GunHudOverlay} 等 5 个 overlay 的既有写法。</li>
 *   <li>上游还手工调了 {@code RenderSystem.disableDepthTest/enableBlend} 等状态。
 *       26.2 的 {@code fill} 走的是 {@code RenderPipeline} 体系，
 *       混合与深度由管线自己管，<b>手动改全局状态既无必要也有风险</b>，故不移植。</li>
 * </ol>
 *
 * <h2>为什么是白色而不是黑色</h2>
 * 闪光弹的观感是「被强光晃到睁不开眼」，白色更贴近。
 * 上游有个 {@code ClientConfig.BLACK_FLASH} 开关可切黑色；
 * 配置层尚未移植，此处固定白色（与上游默认值一致）。
 */
@Environment(EnvType.CLIENT)
public final class BlindnessOverlay {
    /** 超过这个剩余时长就是全不透明；之后开始线性淡出。 */
    private static final float FADE_START_TICKS = 100f;
    /** 最低 alpha —— 绝不能到 0，见类注释第 1 点。 */
    private static final int MIN_ALPHA = 1;

    private BlindnessOverlay() {
    }

    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null) {
            return;
        }
        MobEffectInstance effect = player.getEffect(ModEffects.BLIND);
        if (effect == null) {
            return;
        }

        int remaining = effect.getDuration();
        int alpha = remaining > FADE_START_TICKS
                ? 255
                : (int) (remaining / FADE_START_TICKS * 255f);
        // 【关键】alpha 为 0 时 26.2 会整段跳过绘制，表现为「突然消失」而非淡出。
        // 夹到至少 1，让最后一帧仍然是渐变的一部分。
        alpha = Math.max(MIN_ALPHA, Math.min(255, alpha));

        int color = (alpha << 24) | 0xFFFFFF;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color);
    }
}
