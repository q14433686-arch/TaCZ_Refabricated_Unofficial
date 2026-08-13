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
 * <p>Iris 1.10.7 only invokes its translucent-hand pass when vanilla detects a translucent held
 * block. TACZ guns are not block items, so a scope reticle submitted with the gun body can only
 * reach {@code HAND_SOLID}; shader packs may subsequently composite water, fog and particles over
 * it. The solid pass freezes the original reticle/ring model snapshots here. An Iris-only mixin
 * then forces one late hand pass, submits these snapshots after world translucency, and lets the
 * existing hand collector perform the real draw at its normal {@code endBatch()} boundary. R9 uses
 * dedicated late pipelines there to write foreground depth only after world translucency is done.
 * R11 routes the audited Iris 1.10.7 path onward to {@link ScopeFinalOverlayState}, because some
 * shader packs still apply fog in a later composite that ignores mutable hand depth.</p>
 *
 * <p>This class never keeps mutable {@code BedrockPart}s, poses or item stacks. A queued snapshot
 * has already captured ADS, recoil and view-bob transforms, so moving its submission does not
 * change the reticle's 3D placement or animation.</p>
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
     * reaches {@code endBatch()}. Submission remains delayed; the Iris collector still owns the
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
