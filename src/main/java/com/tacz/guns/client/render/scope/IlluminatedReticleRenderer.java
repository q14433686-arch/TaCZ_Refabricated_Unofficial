package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * P1 策略：只绘制<b>发光</b>准星（{@code *_illuminated} 节点）。
 *
 * <p>覆盖 {@link ReticleKind#HOLOGRAPHIC} 与 {@link ReticleKind#HYBRID} 两种形态
 * —— 也就是默认枪包 33 个瞄具里的 31 个。纯蚀刻镜（{@code scope_98k}、
 * {@code scope_retro_2x}）由后续 P2 的蚀刻策略接手。</p>
 *
 * <h2>发光层也不是免检区：vudu 把遮光板塞进了 {@code division_illuminated}</h2>
 * {@code division} 节点里混着<b>遮光板</b>：例如 {@code scope_1873_6x} 的
 * {@code division} 有 10 个 cube，其中两块是 32×32 的大面
 * （{@code origin=[-14.0625,-37.1875,-111] size=[32,32,0]}）。
 * 上游靠 stencil 把它们裁在圆外，我们没有 stencil，无差别绘制就会复现
 * <b>第 9 轮那块糊屏的黑方块</b>（第 10 轮撤销过一次）。
 *
 * <p>早年假定「{@code *_illuminated} 全是小几何（红点、细线），不可能是遮光板」，
 * 被 {@code scope_vudu} 实测推翻：它的 {@code division_illuminated} 共 6 个 cube，
 * 其中 5 块是 {@code [50,50,0] / [50,100,0]×2 / [100,250,0]×2} 的整版遮光面，只有
 * 第 6 块 {@code [0.25,0.25,0]} 才是真正的准星点。因此这里与蚀刻路径共用
 * {@link ReticleMarkFilter} 的同一把尺寸尺，逐 cube 过滤后再提交。</p>
 *
 * <h2>关于「视差」：r44 已移除自造的近似</h2>
 * 早前这里有一个 {@code applyParallax()}，按开镜进度把准星沿镜轴前推 0.75 单位，
 * 意图模拟全息镜「准星浮在无穷远」的手感。<b>该逻辑已删除</b>，原因：
 * <ul>
 *   <li><b>上游没有任何对应物。</b>对 1.21.1 上游全仓 grep
 *       {@code collimat} / {@code parallax} / {@code billboard} <b>零命中</b>；
 *       准星几何是刚性挂在枪体上的，从未做过位置补偿。</li>
 *   <li>玩家观察到的「准星随视角移动」是<b>真实透视的天然副产品</b>
 *       —— {@code division} 本就位于物镜前方很远处
 *       （实测 {@code scope_acog_ta31} 的 {@code division_illuminated} 在 z=-99.875），
 *       视角一动，远处的它与近处镜框自然产生相对位移，不需要额外补偿。</li>
 * </ul>
 * 保留这段说明，是为了避免后来者再次「发明」同类几何近似。
 */
public final class IlluminatedReticleRenderer implements IReticleRenderer {

    public static final IlluminatedReticleRenderer INSTANCE = new IlluminatedReticleRenderer();

    /**
     * 准星淡入的起始开镜进度。低于该值完全不画 ——
     * 不开镜时红点不该亮在屏幕上（现实里也看不见，因为眼睛不在光轴上）。
     */
    private static final float FADE_IN_START = 0.35f;

    /**
     * 视差前推的最大距离（模型空间单位，1 单位 = 1/16 格）。
     *
     * <p>取值说明：默认枪包里 {@code division_illuminated} 的 z 普遍在
     * -45 ~ -100 之间（例：{@code sight_exp3} 为 -45，{@code scope_acog_ta31} 为 -99.875），
     * 相对镜身只有几个单位的浮动。这里取 0.75 是一个<b>保守</b>的量：
     * 足以产生「准星浮在镜片前方」的分离感，又不会大到穿模。</p>
     */

    private IlluminatedReticleRenderer() {
    }

    @Override
    public boolean matches(ScopeNodeSet nodes) {
        // 只要有发光节点就归本策略（HOLOGRAPHIC 与 HYBRID 都走这里）。
        return nodes.hasIlluminated();
    }

    @Override
    public void submitReticle(Context ctx, ScopeNodeSet nodes) {
        float progress = ctx.aimingProgress();
        if (progress <= FADE_IN_START) {
            return;
        }
        // 线性淡入：FADE_IN_START -> 1.0 映射到 alpha 0 -> 1。
        // 上游是 stencil 硬切（要么全有要么全无），这里做平滑过渡，观感更顺。
        float alpha = (progress - FADE_IN_START) / (1.0f - FADE_IN_START);
        alpha = Math.min(1.0f, Math.max(0.0f, alpha));

        List<BedrockPart> reticles = nodes.illuminatedReticle();
        for (BedrockPart part : reticles) {
            submitOne(ctx, part, alpha);
        }
    }

    private void submitOne(Context ctx, BedrockPart part, float alpha) {
        PoseStack poseStack = ctx.poseStack();

        // 这些节点是【跨帧共享】的：它们的 visible 在别处（构造函数把父级 division
        // 隐藏了）可能是 false，而快照遍历器遇到 visible=false 会直接 return。
        // 因此必须临时打开、画完还原 —— 第 4 轮就吃过"共享状态不还原"的亏。
        // captureSubtree 要求 rootPose 【已经】套用了本节点及其父级链的全部变换
        // （它只在递归子节点时才 translateAndRotateAndScale）。
        // 因此这里必须自底向上收集祖先链，再自顶向下套用 —— 与 BedrockModel#getPath 同构。
        // 漏掉这一步会让准星画在瞄具原点而不是目镜位置。
        Deque<BedrockPart> chain = new ArrayDeque<>();
        for (BedrockPart p = part; p != null; p = p.getParent()) {
            chain.push(p);
        }

        // 沿途的祖先可能是隐藏的（例如 division_illuminated 的父级 division 在构造函数里
        // 被 setHidden(true)），而快照遍历器遇到 visible=false 会直接 return。
        // 这里把整条链临时置为可见，画完在 finally 里逐一还原。
        // 注意：只改 visible 标志，不改任何几何 —— 祖先自身的 cubes 不会被画出来，
        // 因为 captureSubtree 只从 part 这个根开始采集。
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
                    part,
                    poseStack,
                    ctx.displayContext(),
                    // 光照参数在快照内部会被 part.illuminated 覆写为满亮度(15728880)，
                    // 这里传入继承光照即可，不必手动写死。
                    ctx.light(),
                    ctx.overlay(),
                    1.0f, 1.0f, 1.0f, alpha);
        } finally {
            poseStack.popPose();
            for (int i = 0; i < touched.size(); i++) {
                touched.get(i).visible = saved.get(i);
            }
        }

        // 与 BedrockModel#submit 保持同一套提交惯例：快照里的矩阵已含完整入参 pose，
        // 因此必须从【单位矩阵】提交，否则根变换会被叠加两次。Iris solid pass 下仅
        // 冻结它，ScopeLateReticleState 会在较晚的 HAND_TRANSLUCENT pass 提交。
        ScopeLateReticleState.submitReticle(ctx, snapshot, ctx.illuminatedRenderType());
    }
}
