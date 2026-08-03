package com.tacz.guns.client.gui.overlay;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.text.DecimalFormat;

/**
 * 过热机制的热度指示器。
 *
 * <h2>本轮重写：此前是自创 UI，与上游完全不同</h2>
 *
 * <p>旧实现把它画成了「屏幕右下角一条 104×5 的纯色矩形 + 描边」，
 * 既没有用上游的贴图，位置也完全不一样。而 {@code textures/hud/heat_base.png}
 * 与 {@code heat_bar.png} 两张贴图<b>一直躺在资源目录里没人引用</b>
 * （已核对：两张图与上游 byte-for-byte 一致，md5 相同）。
 * 这正是玩家反馈「过热机制的热度条不是 100% 还原」的原因 ——
 * 不是像素级偏差，是整个控件被换成了另一个东西。</p>
 *
 * <h2>与上游 1.21.1 的逐项对齐</h2>
 * <table border="1">
 *   <tr><th>项</th><th>上游</th><th>本实现</th></tr>
 *   <tr><td>位置</td><td><b>屏幕正中</b>（准星周围），非右下角</td><td>同</td></tr>
 *   <tr><td>底图</td><td>{@code heat_base.png} 128×128 画在 {@code (w/2-64, h/2-44)}</td><td>同</td></tr>
 *   <tr><td>热度条</td><td>{@code fill(w/2-30, h/2+30, +60*percent, h/2+34)}</td><td>同</td></tr>
 *   <tr><td>整体缩放</td><td>随热度在 0.75..0.875 之间平滑伸缩</td><td>同（含相同的迟滞逻辑）</td></tr>
 *   <tr><td>文字</td><td>{@code fontFilterFishy}，带阴影，居中于 {@code h/2+38}</td><td>同</td></tr>
 *   <tr><td>过热闪烁</td><td>底图整体染色 红/黄 每 10 tick 交替</td><td>同（改用 blit 的 tint 参数）</td></tr>
 *   <tr><td>文案</td><td>锁定时 {@code !OVERHEAT!}，否则 {@code 0.0%}</td><td>同</td></tr>
 * </table>
 *
 * <h2>26.2 的两处必要改写</h2>
 * <ul>
 *   <li>{@code RenderSystem.setShaderColor} 已移除 —— 上游靠它给底图整体染红/黄。
 *       26.2 的 {@code blit} 末位多了个 {@code tint} 参数（ARGB），语义等价且不依赖全局状态。</li>
 *   <li>{@code graphics.pose()} 从 {@code PoseStack} 变成 {@code Matrix3x2fStack}，
 *       {@code scale} 只接受两个分量。</li>
 * </ul>
 *
 * <p><b>tick 来源</b>：上游用 {@code mc.gui.getGuiTicks()}。注意 26.1.2 与该 API
 * 同名同签名（{@code Gui#getGuiTicks()} 公开，已对 26.1.2 jar 字节码确认）；
 * 「{@code Gui#hud} + {@code Hud#getGuiTicks()}」的拆分是<b>26.2 才有</b>的重构，
 * 26.2 补丁里的 {@code mc.gui.hud.getGuiTicks()} 写法在 26.1.2 编译不过，回本移植已改回。
 * 用 GUI tick 而不是 {@code player.tickCount}
 * 才能保证暂停时闪烁停下，与上游一致。</p>
 */
public class HeatBarOverlay {
    private static final Identifier HEAT_BASE =
            Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/hud/heat_base.png");
    private static final DecimalFormat HEAT_FORMAT_PERCENT = new DecimalFormat("0.0%");

    /** 随热度平滑伸缩的整体缩放，带迟滞。与上游同为 static，跨帧保留。 */
    private static float heatScale = 0.25f;

    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        if (!RenderConfig.GUN_HUD_ENABLE.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!(player instanceof IClientPlayerGunOperator)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        GunData gunData = TimelessAPI.getClientGunIndex(iGun.getGunId(stack))
                .map(ClientGunIndex::getGunData).orElse(null);
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
        if (gunData == null || display == null) {
            return;
        }
        GunHeatData heatData = gunData.getHeatData();
        if (heatData == null || !iGun.hasHeatData(stack)) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        float percent = iGun.getHeatAmount(stack) / heatData.getHeatMax();

        // 上游的缩放迟滞：升温时每帧 +0.05、降温时 -0.025，进入目标邻域后吸附。
        // 逐行照搬，包括那两个不对称的阈值 —— 它们决定了「快速升温、缓慢回落」的手感。
        float scaleValue = (percent / 8f) + 0.75f;
        if (heatScale < scaleValue) heatScale += 0.05f;
        if (heatScale > scaleValue) heatScale -= 0.025f;
        if (heatScale > scaleValue - 0.03f && heatScale < scaleValue + 0.055f) heatScale = scaleValue;

        boolean locked = iGun.isOverheatLocked(stack);
        int tickCount = mc.gui.getGuiTicks();

        Matrix3x2fStack poseStack = graphics.pose();
        poseStack.pushMatrix();
        poseStack.scale(heatScale, heatScale);
        renderOverheat(percent, graphics, (int) (width / heatScale), (int) (height / heatScale), locked, tickCount);
        poseStack.popMatrix();
    }

    public static void renderOverheat(float heatPercentage, GuiGraphicsExtractor graphics, int w, int h,
                                      boolean locked, int tickCount) {
        // 热度条本体：从中心偏左 30px 起，最长 60px
        int barColor = getHeatColor(heatPercentage, locked, tickCount);
        graphics.fill(w / 2 - 30, h / 2 + 30, w / 2 - 30 + (int) (heatPercentage * 60), h / 2 + 34, barColor);

        // 底图。上游用 RenderSystem.setShaderColor 在过热时整体染红/黄，
        // 26.2 无该 API，改用 blit 的 tint 参数（末位 ARGB），效果等价。
        int tint = 0xFFFFFFFF;
        if (locked) {
            tint = tickCount % 20 < 10 ? 0xFFFF1A1A : 0xFFFFFF1A;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, HEAT_BASE,
                w / 2 - 64, h / 2 - 44, 0.0F, 0.0F, 128, 128, 128, 128, tint);

        // 百分比文字。上游用 fontFilterFishy（会过滤不雅词的那套字形），带阴影。
        Font font = Minecraft.getInstance().fontFilterFishy;
        String percentString = locked ? "!OVERHEAT!" : HEAT_FORMAT_PERCENT.format(heatPercentage);
        int color = locked ? (tickCount % 20 < 10 ? 0xFFFF0000 : 0xFFFFFF00) : 0xFFFFFFFF;
        graphics.text(font, percentString, w / 2 - (font.width(percentString) / 2), h / 2 + 38, color, true);
    }

    public static int getHeatColor(float percent, boolean locked, int tickCount) {
        if (locked) {
            return tickCount % 20 < 10 ? 0x9FFF0000 : 0x9FFFFF00;
        }
        if (percent < 0.4) return 0x9FFFFFFF;
        int color;
        if (percent <= 0.65) {
            color = ARGB.srgbLerp(percent * 4 - 1.6f, 0x9FFFFFFF, 0x9FFFFF00);
        } else {
            color = ARGB.srgbLerp((percent - 0.65f) / 0.35f, 0x9FFFFF00, 0x9FFF0000);
        }
        return color;
    }
}
