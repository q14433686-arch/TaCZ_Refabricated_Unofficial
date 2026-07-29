package com.tacz.guns.client.render.scope;

import com.tacz.guns.client.model.bedrock.BedrockPart;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * 一个瞄具模型里与「准星层」相关的节点集合。
 *
 * <p>在 {@code BedrockAttachmentModel} 构造时解析一次并缓存，
 * 之后每帧只读，避免重复遍历模型树。</p>
 *
 * <h2>为什么要单独拆出来</h2>
 * 准星的绘制策略（全息 / 蚀刻 / 混合）需要知道「这个瞄具到底有哪些准星节点」，
 * 而这个判定与具体怎么画是两件事 —— 拆开后 {@link IReticleRenderer}
 * 的各个实现只需读本对象，不必再碰模型树，第三方枪包也能复用同一份解析结果。
 */
public final class ScopeNodeSet {

    private static final ScopeNodeSet EMPTY =
            new ScopeNodeSet(Collections.emptyList(), Collections.emptyList());

    /** 蚀刻分划节点（{@code division} / {@code divisions} 等，<b>不</b>发光）。 */
    private final List<BedrockPart> etchedReticle;

    /** 发光准星节点（名字以 {@code _illuminated} 结尾，会被强制满亮度）。 */
    private final List<BedrockPart> illuminatedReticle;

    private final ReticleKind kind;

    /**
     * 该节点子树里是否<b>真的有几何</b>（自身或任一后代含至少一个 cube）。
     *
     * <h2>为什么不能只看「节点存不存在」</h2>
     * 实测默认枪包里存在<b>空的占位准星节点</b>：
     * <pre>
     * scope_contender（竞技者 4x）:
     *     division            4 cubes  &lt;- 全是 16x84 / 52x16 的【遮光板】，不是准星
     *     └─ dot_illuminated  0 cubes  &lt;- 空节点，没有任何几何
     * </pre>
     * 旧判据只看 {@code illuminatedReticle.isEmpty()}，于是认为它「有发光准星」，
     * {@link ReticleRendererRegistry} 就把它交给 {@link IlluminatedReticleRenderer}
     * （其 {@code matches()} 正是 {@code hasIlluminated()}）。
     * 那个策略<b>只画发光节点</b> —— 而这个节点是空的，结果一个像素都没画出来，
     * 表现为「竞技者 4x 镜内完全没有准星」（用户实测）。
     *
     * <p>更糟的是它同时<b>屏蔽了</b> {@link EtchedReticleRenderer}
     * （后者要求 {@code hasEtched() && !hasIlluminated()}），
     * 于是连 {@code division} 那条路也走不到。
     *
     * <p>改为按<b>实际几何</b>判定后：{@code dot_illuminated} 因无 cube 被判为
     * 「没有发光准星」，该镜自动落入 ETCHED 分支，由蚀刻策略绘制 {@code division}；
     * 而 {@code division} 里那几块遮光板的 XY 远在目镜投影之外
     * （X∈[-42,-26]∪[26,42]，目镜仅 X∈[-0.75,0.75]），
     * 会被反向裁剪整块丢弃，不会重演第 9 轮的糊屏。
     *
     * <p>同时这条判据对 {@code sight_t1}/{@code sight_t2} 是<b>安全</b>的：
     * 它们的 {@code division_illuminated} 自身也是 0 cube，
     * 但子节点 {@code bone} 里有 2 个 cube，递归统计后仍判定为「有发光准星」，
     * 行为不变。这正是必须<b>递归</b>而不能只看 {@code part.cubes} 的原因。
     */
    private static boolean hasGeometry(BedrockPart part) {
        if (part == null) {
            return false;
        }
        if (!part.cubes.isEmpty()) {
            return true;
        }
        for (BedrockPart child : part.children) {
            if (hasGeometry(child)) {
                return true;
            }
        }
        return false;
    }

    /** 过滤掉「空占位节点」，只留下真正带几何的。 */
    private static List<BedrockPart> withGeometry(List<BedrockPart> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        List<BedrockPart> out = new java.util.ArrayList<>(src.size());
        for (BedrockPart part : src) {
            if (hasGeometry(part)) {
                out.add(part);
            }
        }
        return out;
    }

    public ScopeNodeSet(List<BedrockPart> etchedReticle, List<BedrockPart> illuminatedReticle) {
        // 【按实际几何过滤】空占位节点一律不算数，详见 hasGeometry 的说明。
        this.etchedReticle = withGeometry(etchedReticle);
        this.illuminatedReticle = withGeometry(illuminatedReticle);
        boolean hasEtched = !this.etchedReticle.isEmpty();
        boolean hasIlluminated = !this.illuminatedReticle.isEmpty();
        if (hasEtched && hasIlluminated) {
            this.kind = ReticleKind.HYBRID;
        } else if (hasIlluminated) {
            this.kind = ReticleKind.HOLOGRAPHIC;
        } else if (hasEtched) {
            this.kind = ReticleKind.ETCHED;
        } else {
            this.kind = ReticleKind.NONE;
        }
    }

    public static ScopeNodeSet empty() {
        return EMPTY;
    }

    public List<BedrockPart> etchedReticle() {
        return etchedReticle;
    }

    public List<BedrockPart> illuminatedReticle() {
        return illuminatedReticle;
    }

    public ReticleKind kind() {
        return kind;
    }

    public boolean hasEtched() {
        return !etchedReticle.isEmpty();
    }

    public boolean hasIlluminated() {
        return !illuminatedReticle.isEmpty();
    }

    public boolean isEmpty() {
        return kind == ReticleKind.NONE;
    }

    /** 便于日志排查：返回形态与两类节点的数量。 */
    @Override
    public String toString() {
        return "ScopeNodeSet{" + kind + ", etched=" + etchedReticle.size()
                + ", illuminated=" + illuminatedReticle.size() + '}';
    }

    /** 取第一个发光节点，没有则 null（P1 阶段的常用快捷方式）。 */
    @Nullable
    public BedrockPart firstIlluminated() {
        return illuminatedReticle.isEmpty() ? null : illuminatedReticle.get(0);
    }
}
