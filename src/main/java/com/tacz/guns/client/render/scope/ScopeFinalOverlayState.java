package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws frozen scope reticle/rim geometry after Iris has completed every composite and final pass.
 *
 * <p>Some shader packs, including Complementary Reimagined, apply a screen-space fog pass after
 * {@code HAND_TRANSLUCENT} and sample a pre-hand or otherwise immutable world depth snapshot. A
 * late hand depth write cannot affect that input. This state therefore keeps the original 3D
 * snapshots and the exact hand projection/model-view transform, but delays their actual vanilla
 * pipeline draw until {@code IrisRenderingPipeline#finalizeLevelRendering()} has returned.</p>
 *
 * <p>This is not a HUD crosshair: vertices remain the original Bedrock model geometry with ADS,
 * recoil and view-bob already frozen into their snapshots. Only the final color submission moves
 * past shader-pack post-processing.</p>
 */
public final class ScopeFinalOverlayState {
    private static final int FINAL_RETICLE_ORDER = 20_000;
    private static final int FINAL_OCULAR_RING_ORDER = 20_001;

    private static final List<ReticleDraw> PENDING_RETICLES = new ArrayList<>();
    private static final List<RingDraw> PENDING_RINGS = new ArrayList<>();
    private static @Nullable HandTransform handTransform;
    private static @Nullable RenderBuffers renderBuffers;
    private static @Nullable SubmitNodeStorage submitNodes;
    private static @Nullable FeatureRenderDispatcher featureDispatcher;
    private static boolean loggedQueued;
    private static boolean loggedRendered;
    private static boolean loggedFailure;

    private ScopeFinalOverlayState() {
    }

    /** Clears a deferred frame that could not reach Iris' finalization hook. */
    public static void beginSolidSubmission() {
        PENDING_RETICLES.clear();
        PENDING_RINGS.clear();
        handTransform = null;
    }

    public static int pendingReticleCount() {
        return PENDING_RETICLES.size();
    }

    public static boolean hasPendingReticles() {
        return !PENDING_RETICLES.isEmpty();
    }

    static void queueReticle(BedrockRenderSnapshot snapshot, RenderType renderType) {
        if (snapshot.isEmpty()) {
            return;
        }
        captureHandTransform();
        if (handTransform == null) {
            return;
        }
        PENDING_RETICLES.add(new ReticleDraw(snapshot, renderType));
        if (!loggedQueued) {
            loggedQueued = true;
            GunMod.LOGGER.info("[TACZ Scope] Queued reticle for Iris post-composite overlay.");
        }
    }

    public static void queueOcularRing(BedrockRenderSnapshot snapshot, RenderType renderType) {
        if (!snapshot.isEmpty()) {
            PENDING_RINGS.add(new RingDraw(snapshot, renderType));
        }
    }

    /** Called by the Iris-only final-pipeline mixin after shader-pack final compositing. */
    public static void renderAfterFinalComposite() {
        if (PENDING_RETICLES.isEmpty() || handTransform == null) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        if (!ensureDispatcher(minecraft)) {
            return;
        }

        List<ReticleDraw> reticles = List.copyOf(PENDING_RETICLES);
        List<RingDraw> rings = List.copyOf(PENDING_RINGS);
        HandTransform transform = handTransform;
        PENDING_RETICLES.clear();
        PENDING_RINGS.clear();
        handTransform = null;

        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        var previousColorTarget = RenderSystem.outputColorTextureOverride;
        var previousDepthTarget = RenderSystem.outputDepthTextureOverride;

        modelView.pushMatrix();
        modelView.set(transform.modelView());
        RenderSystem.setProjectionMatrix(transform.projection(), transform.projectionType());
        RenderSystem.outputColorTextureOverride = minecraft.getMainRenderTarget().getColorTextureView();
        RenderSystem.outputDepthTextureOverride = minecraft.getMainRenderTarget().getDepthTextureView();
        try {
            OrderedSubmitNodeCollector reticleCollector = submitNodes.order(FINAL_RETICLE_ORDER);
            for (ReticleDraw draw : reticles) {
                reticleCollector.submitCustomGeometry(new PoseStack(), draw.renderType(),
                        (entryPose, consumer) -> draw.snapshot().writeFiltered(consumer, ReticleMarkFilter::isThinMark));
            }

            OrderedSubmitNodeCollector ringCollector = submitNodes.order(FINAL_OCULAR_RING_ORDER);
            for (RingDraw draw : rings) {
                ringCollector.submitCustomGeometry(new PoseStack(), draw.renderType(),
                        (entryPose, consumer) -> draw.snapshot().write(consumer));
            }

            featureDispatcher.renderAllFeatures();
            renderBuffers.bufferSource().endBatch();
            if (!loggedRendered) {
                loggedRendered = true;
                GunMod.LOGGER.info("[TACZ Scope] Rendered reticle and ocular rim after Iris final composite.");
            }
        } catch (RuntimeException e) {
            // Optional Iris integration must not turn a shader-pack edge case into a client crash.
            if (!loggedFailure) {
                loggedFailure = true;
                GunMod.LOGGER.warn("[TACZ Scope] Post-composite reticle overlay failed; skipping this frame.", e);
            }
        } finally {
            try {
                submitNodes.endFrame();
            } finally {
                RenderSystem.outputColorTextureOverride = previousColorTarget;
                RenderSystem.outputDepthTextureOverride = previousDepthTarget;
                modelView.popMatrix();
                RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            }
        }
    }

    private static void captureHandTransform() {
        if (handTransform != null) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        handTransform = new HandTransform(
                new Matrix4f(RenderSystem.getModelViewMatrix()),
                RenderSystem.getProjectionMatrixBuffer(),
                RenderSystem.getProjectionType()
        );
    }

    private static boolean ensureDispatcher(Minecraft minecraft) {
        if (renderBuffers != null && submitNodes != null && featureDispatcher != null) {
            return true;
        }
        try {
            RenderBuffers buffers = new RenderBuffers(Runtime.getRuntime().availableProcessors());
            SubmitNodeStorage nodes = new SubmitNodeStorage();
            FeatureRenderDispatcher dispatcher = new FeatureRenderDispatcher(
                    nodes,
                    minecraft.getBlockRenderer(),
                    buffers.bufferSource(),
                    minecraft.getAtlasManager(),
                    buffers.outlineBufferSource(),
                    buffers.crumblingBufferSource(),
                    minecraft.font
            );
            renderBuffers = buffers;
            submitNodes = nodes;
            featureDispatcher = dispatcher;
            return true;
        } catch (RuntimeException e) {
            GunMod.LOGGER.warn("[TACZ Scope] Cannot initialize post-composite reticle renderer; falling back next frame.", e);
            return false;
        }
    }

    private record HandTransform(Matrix4f modelView,
                                 GpuBufferSlice projection,
                                 ProjectionType projectionType) {
        private HandTransform {
            modelView = new Matrix4f(modelView);
        }
    }

    private record ReticleDraw(BedrockRenderSnapshot snapshot, RenderType renderType) {
    }

    private record RingDraw(BedrockRenderSnapshot snapshot, RenderType renderType) {
    }
}
