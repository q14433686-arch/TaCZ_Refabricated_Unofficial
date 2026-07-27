package com.tacz.guns.client.render.scope;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@link IReticleRenderer} 的注册表与选择器。
 *
 * <h2>用途</h2>
 * 第三方枪包 / 附属模组可以在客户端初始化时注册自己的准星策略，
 * 通过 {@link IReticleRenderer#priority()} 覆盖内置实现：
 * <pre>{@code
 * ReticleRendererRegistry.register(new MyHoloReticleRenderer());  // priority() > 0
 * }</pre>
 *
 * <h2>选择规则</h2>
 * 按 {@code priority()} 从大到小遍历，返回第一个 {@code matches(nodes)} 为真的实现。
 * 内置实现优先级一律为 0，因此任何 {@code > 0} 的注册都会优先命中。
 */
public final class ReticleRendererRegistry {

    private static final List<IReticleRenderer> RENDERERS = new ArrayList<>();

    static {
        // 发光准星：覆盖 HOLOGRAPHIC 与 HYBRID（默认枪包 25 个瞄具）。
        register(IlluminatedReticleRenderer.INSTANCE);
        // 纯蚀刻分划：覆盖剩下 6 个只有 division、没有任何 *_illuminated 的瞄具
        // （春田 scope_1873_6x / 毛瑟 scope_98k / AUG 自带 scope_aug_default /
        //   scope_contender / scope_qmk152 / scope_retro_2x）。
        // 两者 priority 同为 0，靠 matches() 互斥：Illuminated 要求有发光节点，
        // Etched 要求「有蚀刻且无发光」，不会重叠。
        register(EtchedReticleRenderer.INSTANCE);
    }

    private ReticleRendererRegistry() {
    }

    /** 注册一个策略。线程不安全，仅应在客户端初始化阶段调用。 */
    public static synchronized void register(IReticleRenderer renderer) {
        if (renderer == null || RENDERERS.contains(renderer)) {
            return;
        }
        RENDERERS.add(renderer);
        RENDERERS.sort(Comparator.comparingInt(IReticleRenderer::priority).reversed());
    }

    /**
     * 为给定的节点集合挑选策略。
     *
     * @return 命中的策略；若没有任何策略适用则返回 {@code null}（调用方应跳过绘制）
     */
    @Nullable
    public static IReticleRenderer select(ScopeNodeSet nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        for (IReticleRenderer renderer : RENDERERS) {
            if (renderer.matches(nodes)) {
                return renderer;
            }
        }
        return null;
    }
}
