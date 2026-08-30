package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-frame immutable scope geometry deferred from Iris {@code HAND_SOLID} to
 * {@code HAND_TRANSLUCENT}.
 *
 * <p>Iris（26.1 分支，1.11.x，本仓 26.1.2 的实机对照版本）只在 vanilla 检测到半透明手持物时
 * 才执行 translucent-hand pass；TACZ 枪械不是 BlockItem，因此随枪身一起提交的准星只能到达
 * {@code HAND_SOLID}，随后会被光影包的 composite/final pass 以水、雾、粒子覆盖。solid pass
 * 在这里冻结原始的准星/镜框模型快照；一个 Iris-only mixin 会强制开启一次较晚的手部 pass，
 * 在世界半透明绘制完成之后提交这些快照，让既有的手部收集器在它正常的 {@code endFrame()}
 * 边界执行真正的绘制。延后的 pipeline 在那里只写前景深度（世界半透明结束之后写才安全）。
 * 经过审计的 1.11.x 路径还会继续交给 {@link ScopeFinalOverlayState}——部分光影包会在更晚的
 * composite 里施加无视手部深度的屏幕空间雾。</p>
 *
 * <p>本类不持有任何可变的 {@code BedrockPart}、姿态或物品栈。入队的快照已经把 ADS、后坐力
 * 和视图摆动变换冻结进矩阵，因此移动它的提交时机不会改变准星的 3D 位置与动画。</p>
 */
public final class ScopeLateReticleState {
    /** Ordinary translucent hand geometry uses the default order; reticle must be later. */
    private static final int LATE_RETICLE_ORDER = 10_000;
    /** The physical ocular rim must be the last local layer and hide any edge spill. */
    private static final int LATE_OCULAR_RING_ORDER = 10_001;

    private static final List<ReticleDraw> PENDING_RETICLES = new ArrayList<>();
    private static final List<RingDraw> PENDING_RINGS = new ArrayList<>();
    private static boolean loggedLateQueue;
    private static boolean loggedLateSubmit;

    private ScopeLateReticleState() {
    }

    /** Clears stale work before extracting the next first-person gun solid pass. */
    public static void beginSolidSubmission() {
        PENDING_RETICLES.clear();
        PENDING_RINGS.clear();
    }

    /** @return whether Iris must run a translucent hand pass for this frame. */
    public static boolean hasPendingReticles() {
        return !PENDING_RETICLES.isEmpty();
    }

    /** @return current count, used by a caller to decide whether its ocular ring must be deferred too. */
    public static int pendingReticleCount() {
        return PENDING_RETICLES.size();
    }

    /** Queues an immutable, already filtered-at-write-time reticle snapshot. */
    static void queueReticle(BedrockRenderSnapshot snapshot, RenderType renderType) {
        if (!snapshot.isEmpty()) {
            PENDING_RETICLES.add(new ReticleDraw(snapshot, renderType));
            if (!loggedLateQueue) {
                loggedLateQueue = true;
                GunMod.LOGGER.info("[TACZ Scope] Queued reticle for Iris HAND_TRANSLUCENT.");
            }
        }
    }

    /** Queues the physical ocular rim after the associated reticle snapshots. */
    public static void queueOcularRing(BedrockRenderSnapshot snapshot, RenderType renderType) {
        if (!snapshot.isEmpty()) {
            PENDING_RINGS.add(new RingDraw(snapshot, renderType));
        }
    }

    /**
     * Called while Iris has selected {@code HAND_TRANSLUCENT}, before its normal hand extraction
     * reaches {@code endFrame()}. Submission remains delayed; the Iris collector still owns the
     * actual GPU draw and shader/FBO setup.
     */
    public static void submitPending(SubmitNodeCollector collector) {
        if (PENDING_RETICLES.isEmpty()) {
            return;
        }

        List<ReticleDraw> reticles = List.copyOf(PENDING_RETICLES);
        List<RingDraw> rings = List.copyOf(PENDING_RINGS);
        PENDING_RETICLES.clear();
        PENDING_RINGS.clear();

        OrderedSubmitNodeCollector reticleCollector = collector.order(LATE_RETICLE_ORDER);
        for (ReticleDraw draw : reticles) {
            submitReticleGeometry(reticleCollector, draw.snapshot(), draw.renderType());
        }

        OrderedSubmitNodeCollector ringCollector = collector.order(LATE_OCULAR_RING_ORDER);
        for (RingDraw draw : rings) {
            ringCollector.submitCustomGeometry(new PoseStack(), draw.renderType(),
                    (entryPose, consumer) -> draw.snapshot().write(consumer));
        }

        if (!loggedLateSubmit) {
            loggedLateSubmit = true;
            GunMod.LOGGER.info("[TACZ Scope] Deferred reticle and ocular rim to Iris HAND_TRANSLUCENT with late foreground depth.");
        }
    }

    /** Emits immediately on vanilla/ordinary Iris paths, or saves it for the Iris late hand pass. */
    static void submitReticle(IReticleRenderer.Context context,
                              BedrockRenderSnapshot snapshot,
                              RenderType renderType) {
        if (snapshot.isEmpty()) {
            return;
        }
        if (context.deferToIrisFinalOverlay()) {
            ScopeFinalOverlayState.queueReticle(snapshot, renderType);
            return;
        }
        if (context.deferToIrisTranslucent()) {
            queueReticle(snapshot, renderType);
            return;
        }
        submitReticleGeometry(context.collector(), snapshot, renderType);
    }

    private static void submitReticleGeometry(OrderedSubmitNodeCollector collector,
                                              BedrockRenderSnapshot snapshot,
                                              RenderType renderType) {
        // Snapshot matrices already include the original solid-pass model transform. Submit from
        // identity so neither the late hand pass nor its current PoseStack can apply it a second time.
        collector.submitCustomGeometry(new PoseStack(), renderType,
                (entryPose, consumer) -> snapshot.writeFiltered(consumer, ReticleMarkFilter::isThinMark));
    }

    private record ReticleDraw(BedrockRenderSnapshot snapshot, RenderType renderType) {
    }

    private record RingDraw(BedrockRenderSnapshot snapshot, RenderType renderType) {
    }
}
