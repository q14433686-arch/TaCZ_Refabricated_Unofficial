package com.tacz.guns.client.gui.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;

/**
 * 从一张整图里按 UV 取三态（普通 / 悬停 / 禁用）的贴图按钮。
 *
 * <h2>为什么需要这个类</h2>
 *
 * <p>上游 1.21.1 的枪械工作台一共有 <b>9 个</b>贴图按钮，全部走它自带的
 * {@code cn.sh1rocu.tacz.util.forge.ImageButton}，UV 直接指向
 * {@code textures/gui/gun_smith_table.png} 里画好的按钮图案：</p>
 *
 * <table border="1">
 *   <tr><th>按钮</th><th>位置</th><th>尺寸</th><th>UV</th></tr>
 *   <tr><td>制造</td><td>{@code +289,+162}</td><td>48×18</td><td>138,164</td></tr>
 *   <tr><td>URL</td><td>{@code +112,+164}</td><td>18×18</td><td>149,211</td></tr>
 *   <tr><td>配方上翻</td><td>{@code +143,+56}</td><td>96×6</td><td>40,166</td></tr>
 *   <tr><td>配方下翻</td><td>{@code +143,+171}</td><td>96×6</td><td>40,186</td></tr>
 *   <tr><td>分类左翻</td><td>{@code +136,+4}</td><td>18×20</td><td>0,162</td></tr>
 *   <tr><td>分类右翻</td><td>{@code +327,+4}</td><td>18×20</td><td>20,162</td></tr>
 *   <tr><td>放大 / 缩小 / 复位</td><td>{@code +5/+17/+29,+5}</td><td>10×10</td><td>188/200/212,173</td></tr>
 * </table>
 *
 * <p>移植时这 9 个全被替换成了原版灰底 {@code Button}，标签是
 * {@code ^ v &lt; &gt; + - R URL} 这样的 ASCII 字符。功能能用，但外观与上游完全不同 ——
 * 这正是玩家说的「合成台的那些按钮不是 100% 还原」。
 * 贴图本身<b>一直都在</b>（{@code gun_smith_table.png} 与上游 md5 相同），只是没人去采样。</p>
 *
 * <h2>三态 UV 约定（与上游 {@code ImageButton#renderTexture} 一致）</h2>
 * <pre>
 * v            普通
 * v + yDiffTex 悬停/聚焦
 * v + yDiffTex*2 禁用
 * </pre>
 * 贴图总尺寸固定 256×256 —— 已核实 {@code gun_smith_table.png} 确为 256×256。
 * （移植期曾把这里写成 512 导致 UV 水平减半、整个面板错位，见 GunSmithTableScreen 注释。）
 *
 * <h2>26.2 改写点</h2>
 * <ul>
 *   <li>{@code renderWidget(GuiGraphics,...)} → {@code renderContents(GuiGraphics,...)}；</li>
 *   <li>{@code blit(texture,x,y,u,v,w,h,texW,texH)} 的参数顺序变了，
 *       26.2 首参是 {@code RenderPipeline}，且 u/v 为 float；</li>
 *   <li>{@code RenderSystem.enableDepthTest()} 已无必要 —— GUI 管线自带深度状态。</li>
 * </ul>
 */
public class TaczImageButton extends Button {
    private final int xTexStart;
    private final int yTexStart;
    private final int yDiffTex;
    private final Identifier texture;

    public TaczImageButton(int x, int y, int width, int height,
                           int xTexStart, int yTexStart, int yDiffTex,
                           Identifier texture, Button.OnPress onPress) {
        super(x, y, width, height, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
        this.xTexStart = xTexStart;
        this.yTexStart = yTexStart;
        this.yDiffTex = yDiffTex;
        this.texture = texture;
    }

    @Override
    protected void renderContents(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int v = this.yTexStart;
        if (!this.isActive()) {
            v = this.yTexStart + this.yDiffTex * 2;
        } else if (this.isHoveredOrFocused()) {
            v = this.yTexStart + this.yDiffTex;
        }
        gui.blit(RenderPipelines.GUI_TEXTURED, this.texture,
                this.getX(), this.getY(), (float) this.xTexStart, (float) v,
                this.width, this.height, 256, 256);
    }
}
