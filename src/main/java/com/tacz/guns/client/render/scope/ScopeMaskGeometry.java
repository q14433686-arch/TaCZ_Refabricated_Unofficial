package com.tacz.guns.client.render.scope;

import com.tacz.guns.client.model.bedrock.BedrockCube;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 当帧待写入掩码的目镜几何清单。
 *
 * <h2>为什么需要一个「收集器」而不是直接画</h2>
 * 26.2 的绘制是<b>两阶段</b>的：模型代码在 {@code submit} 里只做「提交」，
 * 真正的绘制发生在稍后的 {@code FeatureRenderDispatcher#renderAllFeatures}。
 * 而我们的掩码 pass 必须开在<b>阶段边界</b>（这是 r51 撞设备丢失后
 * 唯一被证实安全的时机，已由上一轮的空 pass 探针实测确认）。
 *
 * <p>两者时机不同，中间就需要一个存放处：
 * {@code BedrockAttachmentModel#submit} 往这里<b>登记</b>目镜几何，
 * 阶段边界的掩码 pass 再<b>取走并画掉</b>。
 *
 * <p>这也顺带满足了 vanilla 的用法约束 ——「<b>成批地</b>、在阶段边界切 target」。
 * 一帧里可能有多个瞄具（主手/副手、组合镜两组目镜），全部攒齐后一次画完，
 * 只开一个 pass。r51 正是因为每个瞄具各自触发一次 target 切换才崩的。
 *
 * <h2>坐标空间</h2>
 * 登记进来的矩阵是<b>已经乘好的完整模型矩阵</b>（含 PoseStack 根变换与整条父级链），
 * 与 {@code BedrockRenderSnapshot.DrawCommand#pose} 同一空间。
 * 顶点写入时只做 {@code pos/16 → mul(matrix)}，与
 * {@code BedrockCubeBox#compile} 的算法逐行一致，避免两条路径产生偏差。
 *
 * <h2>生命周期</h2>
 * 每帧 {@code clear()} 一次。**必须无条件清空**，哪怕掩码没画成 ——
 * 否则不开镜时会残留上一帧的几何，越积越多。
 */
@Environment(EnvType.CLIENT)
public final class ScopeMaskGeometry {

    /**
     * 一批待写入掩码的立方体，连同它们共用的模型矩阵。
     *
     * @param pose  完整模型矩阵（已含根变换与父级链）
     * @param cubes 该矩阵下的立方体
     */
    public record Entry(Matrix4f pose, List<BedrockCube> cubes) {
        public Entry {
            // 防御性拷贝：BedrockPart 跨帧共享且会被动画改写，
            // 而本清单要活到阶段边界才消费，中途被改会画错位置。
            pose = new Matrix4f(pose);
            cubes = List.copyOf(cubes);
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    /**
     * 本帧的掩码是否<b>也</b>用来裁剪枪身 / 非瞄具配件 / 枪口火光。
     *
     * <p>为什么需要它（案例⑨ 第四轮）：掩码现在有<b>两个独立消费者</b> ——
     * ①准星约束（低倍 sight 与高倍筒镜都要）、②镜外视模裁剪（只有高倍筒镜通道要）。
     * 低倍 sight 通道会生成 <b>reticle-only</b> 掩码，而上游 {@code renderSight}
     * 不裁镜身/枪身/配件/火光；若调用点只看「本帧有没有几何」，就会拿着这张
     * 只为准星准备的掩码去把枪身在镜片投影内啃出一个洞。</p>
     *
     * <p>与 {@link #ENTRIES} 同生命周期：{@link #clear()} 里一起复位，避免跨帧泄漏。</p>
     */
    private static boolean viewmodelClipEnabled;

    private ScopeMaskGeometry() {
    }

    public static void add(Matrix4f pose, List<BedrockCube> cubes) {
        if (cubes.isEmpty()) {
            return;
        }
        ENTRIES.add(new Entry(pose, cubes));
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static boolean isEmpty() {
        return ENTRIES.isEmpty();
    }

    /**
     * 打开「本次视模提交内，掩码也用于裁剪枪身/配件/火光」（OR 语义：
     * 一帧内可能有多个瞄具提交，任何一个要求裁剪就算数）。
     *
     * <p>调用点是 {@code BedrockAttachmentModel#submit}，且只在<b>镜身也走裁剪</b>
     * （高倍筒镜通道）时调用。</p>
     */
    public static void enableViewmodelClip() {
        viewmodelClipEnabled = true;
    }

    public static boolean isViewmodelClipEnabled() {
        return viewmodelClipEnabled;
    }

    public static void clear() {
        ENTRIES.clear();
        viewmodelClipEnabled = false;
    }
}
