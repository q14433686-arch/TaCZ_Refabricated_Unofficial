package com.tacz.guns.client.gui.overlay;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.tacz.guns.client.render.scope.ScopeMaskTarget;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 【调试用】把瞄具掩码贴图画到屏幕左上角。
 *
 * <p>由 {@code RenderConfig.SCOPE_MASK_DEBUG} 控制，<b>默认关闭</b>。
 * 它的唯一目的是让「离屏渲染链路是否通」变成一个<b>肉眼可判定</b>的问题 ——
 * 沙盒里既不能编译也不能看画面，如果一次性把掩码接进裁剪逻辑，
 * 出问题时只会看到「全黑」或「全没」，无法定位是哪一环坏的。</p>
 *
 * <h2>各步骤下应该看到什么</h2>
 * <ul>
 *   <li><b>Step 1</b>：左上角一个<b>纯红方块</b>
 *       → 证明 target 能建、能清、纹理能采样回屏幕；</li>
 *   <li><b>Step 2</b>：方块里出现一个<b>随枪移动的白色形状</b>
 *       → 证明「目镜投影」这一核心假设成立（关键分水岭）；</li>
 *   <li>Step 3 之后本叠加层就只剩排查价值，可以关掉。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class ScopeMaskDebugOverlay {

    /** 预览方块的边长（GUI 像素）。 */
    private static final int PREVIEW_SIZE = 96;
    /** 距屏幕边缘的留白。 */
    private static final int MARGIN = 8;

    private ScopeMaskDebugOverlay() {
    }

    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        if (!RenderConfig.SCOPE_MASK_DEBUG.get()) {
            return;
        }
        // 【Step 2-probe】这里【不再】清屏。
        //
        // Step 1 曾在此处 clearToDebugRed()，那是为了验证「target 能建、能清、
        // 能采样回屏幕」——该目标已达成（你实测 PASS），使命结束。
        //
        // 现在由 ScopeMaskRenderer 在【阶段边界】绘制真正的目镜掩码，
        // 这里只负责把结果显示出来。判据：
        //   白色形状且【随枪移动】 → 核心假设成立（裁剪区域=目镜屏幕投影）
        //   纯黑                   → 没有目镜几何被登记，或绘制失败（查日志）
        //   形状不动/位置错         → 矩阵空间搞错了，需要回头查 pose 链

        RenderTarget target = ScopeMaskTarget.current();
        if (target == null || !ScopeMaskTarget.isAvailable()) {
            return;
        }
        try {
            // 【坐标约定】blit 与 outline 的后两个 int 语义【不一样】，这是个真容易踩的坑：
            //
            //   blit(view, sampler, x0, y0, x1, y1, u0,u1,v0,v1)
            //       后两个是【右下角坐标】。字节码链路：
            //       blit -> innerBlit(4 个 int 原样透传)
            //            -> new BlitRenderState(..., x0, y0, x1, y1, ...)
            //            -> buildVertices: (x0,y0) (x0,y1) (x1,y1) (x1,y0)
            //
            //   outline(x, y, w, h, color)
            //       后两个是【宽高】。字节码展开为四条 fill：
            //           fill(x,     y,     x+w,   y+1  )   上
            //           fill(x,     y+h-1, x+w,   y+h  )   下
            //           fill(x,     y+1,   x+1,   y+h-1)   左
            //           fill(x+w-1, y+1,   x+w,   y+h-1)   右
            //
            // 之前两边都传了 (MARGIN, MARGIN, PREVIEW_SIZE, PREVIEW_SIZE)，于是：
            //   白框覆盖 [8, 8+96] = [8, 104]
            //   贴图只覆盖 [8, 96]          ← 被当成了右下角坐标
            // 差出的正好是一个 MARGIN(8px)，表现为「绿色没填满白框，右下各缺一条」。
            // Step 1 的红色方块其实也有同样的缺口，只是当时没深究。
            final int x0 = MARGIN;
            final int y0 = MARGIN;
            final int x1 = MARGIN + PREVIEW_SIZE;
            final int y1 = MARGIN + PREVIEW_SIZE;

            // 用 NEAREST：掩码是二值数据，线性过滤会在边缘造成中间值，
            // 调试时反而看不清真实的覆盖范围。
            graphics.blit(
                    target.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
                    x0, y0, x1, y1,
                    // UV：u 全图 0..1，【v 要翻转】传 1..0。
                    //
                    // 离屏 RenderTarget 的纹理原点在【左下】，而 GUI 坐标系原点在【左上】，
                    // 两者 Y 方向相反。直接传 v0=0,v1=1 预览就会上下颠倒
                    // —— 实测组合镜 scope_mk5hd 的小红点(模型 Y=[4.75,5.53]，实际在上)
                    // 显示在了下方，大筒镜(Y=[1.06,3.06]，实际在下)显示在上方。
                    //
                    // vanilla 自己 blit 离屏 target 时就是这么传的
                    // （DebugScreenOverlay#extractRenderState 偏移 1054-1058：
                    //   fconst_0, fconst_1, fconst_1, fconst_0 -> u0=0,u1=1,v0=1,v1=0）。
                    //
                    // 【重要】这是【预览显示】的修正，掩码纹理本身的内容是正确的。
                    // Step 3 让镜身采样掩码时用的是 gl_FragCoord 而非这里的 UV，
                    // 不受本行影响 —— 不要因为看到这行就去把掩码几何也翻一次。
                    0.0f, 1.0f, 1.0f, 0.0f);
            // 白框画在贴图【外侧一圈】，这样它只做边界指示、不遮住任何一个掩码像素
            // —— 否则最外圈的掩码内容会被白线盖掉，看不出真实覆盖范围。
            graphics.outline(x0 - 1, y0 - 1, PREVIEW_SIZE + 2, PREVIEW_SIZE + 2, 0xFFFFFFFF);
        } catch (Exception ignored) {
            // 调试叠加层绝不能把游戏带崩；失败就当没画。
        }
    }
}
