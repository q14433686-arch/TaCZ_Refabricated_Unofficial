package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * 瞄具「目镜掩码」的离屏渲染目标。
 *
 * <h2>它要解决什么</h2>
 * 上游 1.21.1 用 stencil 做「镜身只在目镜圆<b>之外</b>绘制」：
 * <pre>
 * renderOcularStencil(...)              // 用 ocular 几何本身写模板值
 *     colorMask(false,false,false,false);  //   只写模板、不写颜色
 * scope_body: stencilFunc(GL_EQUAL, 0)  // 只在目镜【没盖到】的地方画镜身
 * </pre>
 * 注意裁剪区域是<b>目镜几何的屏幕投影</b>，不是某个几何圆
 * （r46 就是把这里搞错了：画了个固定在屏幕中心的圆，导致擦掉整个瞄具主体）。
 *
 * <p>26.2 的渲染抽象层（含 Vulkan 后端）完全没有 stencil，
 * 等价做法是把 {@code ocular} 单独渲染到一张离屏纹理当作掩码，
 * 再让镜身的 shader 采样它决定 discard。本类负责那张纹理的生命周期。</p>
 *
 * <h2>进度</h2>
 * <ul>
 *   <li><b>Step 1（已 PASS）</b>：建 target、清成纯红、blit 到屏幕角落。
 *       证明了「离屏 target 能建、能清、纹理能被采样回屏幕」。
 *       那次的清屏方法已随本轮删除 —— 目标达成即退场，不留死代码。</li>
 *   <li><b>Step 2-probe（已 PASS）</b>：在<b>阶段边界</b>开一个指向本 target 的
 *       空 render pass 并清成纯绿，验证「跨 OutputTarget 的 pass 切换在阶段边界
 *       是否安全」—— 这是 r51 撞上 {@code VK_ERROR_DEVICE_LOST} 之后
 *       唯一没被单独验过的假设。实测通过，探针已删除。</li>
 *   <li><b>Step 2（当前）</b>：由 {@link ScopeMaskRenderer} 在同一时机
 *       把当帧所有目镜几何画进本 target。预览块里应出现
 *       <b>随枪移动的白色形状</b> —— 这一步验证整个方案的核心假设：
 *       <b>裁剪区域 = 目镜几何的屏幕投影</b>。</li>
 * </ul>
 *
 * <p>为什么要拆得这么碎：沙盒里既无法编译也无法看画面，掩码类 bug 通常表现为
 * 「全黑」或「全没」，很难区分是哪一环坏的。每一步都设计成<b>肉眼可判定</b>，
 * 后续步骤才有可信的基准。</p>
 */
@Environment(EnvType.CLIENT)
public final class ScopeMaskTarget {

    /** 掩码分辨率相对主帧缓冲的缩放。1.0 = 等分辨率。 */
    private static final float SCALE = 1.0f;

    @Nullable
    private static TextureTarget target;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    /** 一旦出过错就永久停用，避免每帧刷屏或反复抛异常。 */
    private static boolean failed = false;

    private ScopeMaskTarget() {
    }

    /**
     * 取（必要时创建/重建）掩码 target。
     *
     * <p>窗口尺寸变化时会重建 —— {@code RenderTarget#resize} 存在，
     * 但重建更简单且这里每帧只比较两个 int，开销可忽略。</p>
     *
     * @return 可用的 target；失败或尺寸非法时返回 {@code null}
     */
    @Nullable
    public static TextureTarget getOrCreate() {
        if (failed) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        // 注意用帧缓冲的物理像素尺寸，不是 GUI 缩放后的尺寸。
        int w = Math.max(1, (int) (mc.getWindow().getWidth() * SCALE));
        int h = Math.max(1, (int) (mc.getWindow().getHeight() * SCALE));
        try {
            if (target == null || w != lastWidth || h != lastHeight) {
                if (target != null) {
                    target.destroyBuffers();
                }
                // useDepth=false：掩码只关心「这个像素有没有被目镜盖到」，
                // 不需要深度。少一张深度纹理也省显存。
                target = new TextureTarget("tacz_scope_mask", w, h, false, GpuFormat.RGBA8_UNORM);
                lastWidth = w;
                lastHeight = h;
            }
            return target;
        } catch (Exception e) {
            failed = true;
            GunMod.LOGGER.error("[TACZ Scope] Failed to create scope mask target; feature disabled.", e);
            close();
            return null;
        }
    }

    /** 当前 target 是否可用（供调试叠加层判断要不要画）。 */
    public static boolean isAvailable() {
        return !failed && target != null;
    }

    @Nullable
    public static RenderTarget current() {
        return target;
    }

    /** 资源释放。目前没有接到任何生命周期回调上，留作后续 Step 使用。 */
    public static void close() {
        if (target != null) {
            try {
                target.destroyBuffers();
            } catch (Exception ignored) {
                // 关闭失败没有补救手段，忽略即可
            }
            target = null;
        }
        lastWidth = -1;
        lastHeight = -1;
    }
}
