package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

/**
 * 「镜内画中画」用的离屏纹理 —— 本帧世界画面的一份<b>拷贝</b>。
 *
 * <h2>它为什么只是一份拷贝</h2>
 * 镜内画面 = 同一台摄像机、同一个朝向、只是 FOV 更窄的那一张图。
 * 而透视投影下「压窄 FOV」在屏幕空间就是<b>绕光轴的等比放大</b>：
 * <pre>
 * 窄FOV下某点的 NDC = 宽FOV下同一点的 NDC × Z          （Z = 倍率）
 *   ⇒ 反过来采样：wideUV = center + (narrowUV − center) / Z
 * </pre>
 * 这个关系是<b>恒等的</b>，不含任何近似 —— 于是根本不需要把世界再画一遍，
 * 把已经画好的世界拷出来、按上式重采样即可。
 *
 * <h2>为什么不是「再渲染一遍世界」</h2>
 * 第一版确实那么做（重定向 {@code GameRenderer#mainRenderTarget()} 后再跑一次
 * {@code LevelRenderer#render}）。它在纯原版渲染下成立，但**与 Sodium 不兼容**：
 * Sodium 完全接管地形渲染，不走
 * {@code ChunkSectionLayerGroup#outputTarget() → gameRenderer.mainRenderTarget()}
 * 这条路，于是重定向对地形无效。实测症状正是：
 * 镜内只有原版路径的实体/粒子/天空被放大、地形没有（「画面糊在一起」），
 * 同时一帧内两次驱动 {@code LevelRenderer#render} 打乱了 Sodium 与
 * ImmediatelyFast 的逐帧状态，导致<b>镜外</b>的实体也一并消失。
 *
 * <p>拷贝方案没有这个问题：它只读<b>最终的颜色缓冲</b>，
 * 至于那些像素是 Sodium 画的还是原版画的，完全无所谓。
 *
 * <h2>尺寸与格式：与主帧缓冲严格一致</h2>
 * 拷贝是逐像素的 {@code copyTextureToTexture}，源和目标必须同格式；
 * 尺寸也取满，<b>不做任何降采样</b> —— 镜内画面本来就要放大一个中心裁切区，
 * 源头再降分辨率只会雪上加霜，而一次全屏拷贝的开销与「多渲一遍世界」不在一个量级，
 * 没有省的必要。
 *
 * <p>不需要深度附件：这里存的是一张已完成的二维图像，不参与任何深度测试。
 */
@Environment(EnvType.CLIENT)
public final class ScopePipTarget {

    @Nullable
    private static TextureTarget target;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    @Nullable
    private static GpuFormat lastFormat = null;
    private static boolean lastUseDepth = false;

    /** 一旦出过错就永久停用，避免每帧刷屏或反复抛异常。 */
    private static boolean failed = false;

    /**
     * 重建代数：每次真正 new 一个 target 就 +1。
     *
     * <p>给「隔帧渲染」（ScopePipRerenderInterval）用的：复用上一帧的镜内画面
     * 前提是那张画面还躺在<b>同一个</b>纹理里 —— 窗口缩放/格式变化触发重建后，
     * 新纹理里是未定义内容，绝不能当「上一帧」端出去。比较代数比比较引用可靠：
     * 引用相等无法区分「同一个对象」与「销毁后恰好复用了同地址」这种理论坑，
     * 而代数单调递增，语义就是「第几次分配」。
     */
    private static int generation = 0;

    private ScopePipTarget() {
    }

    /**
     * 取（必要时创建/重建）镜内画面的离屏纹理。
     *
     * @param width  主帧缓冲宽度（物理像素）
     * @param height 主帧缓冲高度（物理像素）
     * @param format 主帧缓冲颜色纹理的格式；拷贝要求两端一致，所以由调用方传进来
     * @return 可用的 target；失败时返回 {@code null}（调用方据此退回不做 PIP）
     */
    @Nullable
    public static TextureTarget getOrCreate(int width, int height, GpuFormat format, boolean needsDepth) {
        if (failed) {
            return null;
        }
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        try {
            if (target == null || w != lastWidth || h != lastHeight || format != lastFormat
                    || needsDepth != lastUseDepth) {
                if (target != null) {
                    target.destroyBuffers();
                    target = null;
                }
                // 重投影模式存的是一张已完成的二维图像，不需要深度；
                // 二次渲染模式跑的是完整的世界渲染，没有深度附件就没有遮挡关系。
                target = new TextureTarget("tacz_scope_pip", w, h, needsDepth, format);
                generation++;
                lastWidth = w;
                lastHeight = h;
                lastFormat = format;
                lastUseDepth = needsDepth;
                GunMod.LOGGER.info("[TACZ Scope] Scope PIP target allocated at {}x{} ({}, depth={}).",
                        w, h, format, needsDepth);
            }
            return target;
        } catch (Exception e) {
            failed = true;
            GunMod.LOGGER.error("[TACZ Scope] Failed to allocate the scope PIP scene copy; PIP disabled.", e);
            close();
            return null;
        }
    }

    @Nullable
    public static TextureTarget current() {
        return failed ? null : target;
    }

    /** 当前 target 是第几次分配出来的。target 为 null 时也照常返回，调用方自行搭配 current() 判空。 */
    public static int generation() {
        return generation;
    }

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
        lastFormat = null;
        lastUseDepth = false;
    }
}
