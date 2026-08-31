package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.model.IFunctionalSubmitter;
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
 * Draws frozen scope reticle/rim geometry after the last thing that could cover it.
 *
 * <p>Two call sites share this path:</p>
 * <ul>
 *   <li>Iris post-composite: some shader packs, including Complementary Reimagined, apply a
 *       screen-space fog pass after {@code HAND_TRANSLUCENT} and sample a pre-hand or otherwise
 *       immutable world depth snapshot; a late hand depth write cannot affect that input. This
 *       state keeps the original 3D snapshots and the exact hand projection/model-view transform,
 *       but delays their actual vanilla pipeline draw until
 *       {@code IrisRenderingPipeline#finalizeLevelRendering()} has returned.</li>
 *   <li>Vanilla PIP lens: {@code GameRenderer} calls {@link #renderAfterFinalComposite()} right
 *       after the Step-3 composite, restoring physical lens order (picture -> reticle/crosshair ->
 *       ocular shade) without moving the composite into the middle of the hand batch.</li>
 * </ul>
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
    /**
     * Scope-model text (MK5/MK5HD ammo counter etc.) deferred together with the reticle/rim.
     * The text itself is a vanilla font submission task, not TACZ custom geometry, so the
     * shader-pack depth whitelist does not apply to it; it only needs to be moved past the
     * post passes so neither the PIP lens picture nor a pack's composite can cover it.
     */
    private static final List<IFunctionalSubmitter.SubmitTask> PENDING_TEXT = new ArrayList<>();
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
        PENDING_TEXT.clear();
        handTransform = null;
    }

    public static int pendingReticleCount() {
        return PENDING_RETICLES.size();
    }

    public static boolean hasPendingReticles() {
        return !PENDING_RETICLES.isEmpty();
    }

    /** @return whether anything (reticle or bare physical rim) waits to be drawn after the final cover. */
    public static boolean hasPendingOverlay() {
        return !PENDING_RETICLES.isEmpty() || !PENDING_RINGS.isEmpty() || !PENDING_TEXT.isEmpty();
    }

    /**
     * Queues one scope-model functional task (in practice a {@code TextShowRender} ammo-counter
     * font submission) for the post-composite flush, mirroring {@link #queueOcularRing}.
     *
     * <p>Physical order after the flush is picture -&gt; text -&gt; reticle -&gt; ocular shade, matching
     * what the immediate (non-deferred) hand pass gets by submitting at the collector's default
     * order between depth cleanup and the reticle. The hand transform is captured here so a
     * text-only queue can still flush.</p>
     */
    public static void queueFunctionalTask(IFunctionalSubmitter.SubmitTask task) {
        captureHandTransform();
        if (handTransform == null) {
            return;
        }
        PENDING_TEXT.add(task);
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
            GunMod.LOGGER.info("[TACZ Scope] Queued reticle for post-composite overlay (Iris or PIP lens).");
        }
    }

    public static void queueOcularRing(BedrockRenderSnapshot snapshot, RenderType renderType) {
        if (!snapshot.isEmpty()) {
            // Normally a ring is queued only after a reticle captured the hand transform, but the
            // PIP lens can also defer a bare rim (scope with shade and no visible reticle, or a
            // reticle filtered out during fade-in). Capture here so a ring-only queue can flush too.
            captureHandTransform();
            if (handTransform == null) {
                return;
            }
            PENDING_RINGS.add(new RingDraw(snapshot, renderType));
        }
    }

    /**
     * Draws deferred reticle/rim geometry after the last thing that could cover it.
     *
     * <p>Two call sites share this path:</p>
     * <ul>
     *   <li>Iris: called by the final-pipeline mixin after all shader-pack composite/final passes.</li>
     *   <li>Vanilla PIP: called by {@code GameRenderer} right after the Step-3 lens composite, so the
     *       crosshair and shade sit above the magnified picture instead of being covered by it.</li>
     * </ul>
     */
    public static void renderAfterFinalComposite() {
        if ((PENDING_RETICLES.isEmpty() && PENDING_RINGS.isEmpty() && PENDING_TEXT.isEmpty())
                || handTransform == null) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        if (!ensureDispatcher(minecraft)) {
            return;
        }

        List<ReticleDraw> reticles = List.copyOf(PENDING_RETICLES);
        List<RingDraw> rings = List.copyOf(PENDING_RINGS);
        List<IFunctionalSubmitter.SubmitTask> texts = List.copyOf(PENDING_TEXT);
        HandTransform transform = handTransform;
        PENDING_RETICLES.clear();
        PENDING_RINGS.clear();
        PENDING_TEXT.clear();
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
            // Text first, and submitted straight through the storage itself: SubmitNodeStorage is the
            // frame's SubmitNodeCollector (same fact GunPreviewRenderer relies on), whereas the
            // OrderedSubmitNodeCollector returned by order(int) cannot be handed to a task. Default
            // order bucket therefore lands below FINAL_RETICLE_ORDER - picture -> text -> reticle -> shade.
            for (IFunctionalSubmitter.SubmitTask task : texts) {
                task.submit(submitNodes);
            }

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
                GunMod.LOGGER.info("[TACZ Scope] Rendered deferred reticle, ocular rim and scope text after the final cover ({} reticles, {} rims, {} texts).",
                        reticles.size(), rings.size(), texts.size());
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
