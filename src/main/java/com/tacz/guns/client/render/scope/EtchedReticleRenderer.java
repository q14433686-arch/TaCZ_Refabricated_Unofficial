package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 蚀刻分划准星（不发光）。
 *
 * <h2>它补的是哪个洞</h2>
 * 此前只注册了 {@link IlluminatedReticleRenderer}，而它的 {@code matches()}
 * 要求<b>存在发光节点</b>。默认枪包里有 6 个瞄具是<b>纯蚀刻</b>的
 * —— 只有 {@code division}、没有任何 {@code *_illuminated}：
 * <pre>
 * scope_1873_6x（春田）  scope_98k（毛瑟）   scope_aug_default（AUG 自带）
 * scope_contender        scope_qmk152        scope_retro_2x
 * </pre>
 * 它们因此一个策略都匹配不上，镜内完全没有准星 —— 正是用户实测到的问题。
 * {@code ReticleRendererRegistry} 里那句「P2 将补 EtchedReticleRenderer」
 * 就是指本类，一直没写。
 *
 * <h2>为什么现在才敢画 division</h2>
 * {@code division} 里混着<b>遮光板</b>：不是准星，而是几块用来挡住镜外视野的大面。
 * 实测尺寸相当夸张（{@code scope_qmk152} 单块面积达 6486、
 * {@code scope_1873_6x} 有 96×34 的），第 9 轮无差别绘制过一次，
 * 结果是一大块黑色糊住屏幕，第 10 轮撤销。
 *
 * <p>上游能直接整根画是因为它有 stencil 兜底
 * （{@code renderDivisionOnly: stencilFunc(GL_EQUAL, i+1)} 把一切裁在目镜圆内）。
 * <b>现在我们也有等价物了</b> —— 反向裁剪的 RenderType。
 * 已逐个核对：这些遮光板的 XY 范围<b>全部落在目镜投影之外</b>
 * （如 {@code scope_retro_2x} 目镜 X∈[-0.75,0.75]，而遮光板 X∈[-32,-8]），
 * 因此会被反向裁剪整块丢弃，不会重演第 9 轮的糊屏。
 *
 * <p>换句话说：这个策略<b>依赖掩码才成立</b>。若掩码不可用（配置关闭等），
 * 调用方传进来的就是未裁剪的 RenderType，此时遮光板会露出来 ——
 * 所以 {@link #submitReticle} 里加了兜底，掩码没生效时不画。
 */
public final class EtchedReticleRenderer implements IReticleRenderer {

    public static final EtchedReticleRenderer INSTANCE = new EtchedReticleRenderer();

    /**
     * 开始显现的开镜进度。与 {@link IlluminatedReticleRenderer} 保持一致，
     * 让两类准星的出现时机统一。
     */
    private static final float FADE_IN_START = 0.35f;

    private EtchedReticleRenderer() {
    }

    @Override
    public boolean matches(ScopeNodeSet nodes) {
        // 只接纯蚀刻镜。带发光节点的交给 IlluminatedReticleRenderer ——
        // 它的 priority 与本类相同，但注册更早，会先命中。
        return nodes.hasEtched() && !nodes.hasIlluminated();
    }

    @Override
    public void submitReticle(Context ctx, ScopeNodeSet nodes) {
        if (!ctx.maskActive()) {
            // 【安全兜底】掩码没生效时绝不绘制。
            // division 里的遮光板尺寸极大（scope_qmk152 单块面积 6486），
            // 没有反向裁剪就会整块糊在屏幕上 —— 第 9 轮踩过、第 10 轮撤销过。
            // 宁可这几个瞄具暂时没准星（= 修复前的现状），也不能糊屏。
            return;
        }
        float progress = ctx.aimingProgress();
        if (progress <= FADE_IN_START) {
            return;
        }
        float alpha = (progress - FADE_IN_START) / (1.0f - FADE_IN_START);
        alpha = Math.min(1.0f, Math.max(0.0f, alpha));

        for (BedrockPart part : nodes.etchedReticle()) {
            submitOne(ctx, part, alpha);
        }
    }

    /**
     * 与 {@code IlluminatedReticleRenderer#submitOne} 同构。
     *
     * <p>唯一区别：不依赖 {@code part.illuminated}，蚀刻分划用继承光照，
     * 因此在暗处会跟着变暗 —— 这符合「刻在玻璃上的线」的物理直觉。
     */
    private void submitOne(Context ctx, BedrockPart part, float alpha) {
        PoseStack poseStack = ctx.poseStack();

        // captureSubtree 要求 rootPose 已套用本节点及其父级链的全部变换，
        // 因此自底向上收集祖先链，再自顶向下套用。
        Deque<BedrockPart> chain = new ArrayDeque<>();
        for (BedrockPart p = part; p != null; p = p.getParent()) {
            chain.push(p);
        }

        // division 在构造函数里被 setHidden(true)（那是为了不让它走主渲染列表），
        // 而快照遍历器遇 visible=false 会直接 return。这里临时打开整条链，
        // 画完在 finally 里逐一还原 —— BedrockPart 跨帧共享，不还原会污染别处。
        List<BedrockPart> touched = new ArrayList<>();
        List<Boolean> saved = new ArrayList<>();
        for (BedrockPart p : chain) {
            touched.add(p);
            saved.add(p.visible);
            p.visible = true;
        }

        poseStack.pushPose();
        BedrockRenderSnapshot snapshot;
        try {
            for (BedrockPart p : chain) {
                p.translateAndRotateAndScale(poseStack);
            }
            snapshot = BedrockRenderSnapshot.captureSubtree(
                    part, poseStack, ctx.displayContext(),
                    ctx.light(), ctx.overlay(),
                    1.0f, 1.0f, 1.0f, alpha);
        } finally {
            poseStack.popPose();
            for (int i = 0; i < touched.size(); i++) {
                touched.get(i).visible = saved.get(i);
            }
        }

        if (!snapshot.isEmpty()) {
            // 快照矩阵已含完整入参 pose，必须从单位矩阵提交，否则根变换叠加两次。
            PoseStack identity = new PoseStack();
            ctx.collector().submitCustomGeometry(
                    identity, ctx.baseRenderType(),
                    (entryPose, consumer) -> snapshot.write(consumer));
        }
    }
}
