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
 * Draws frozen scope reticle/rim geometry after Iris has completed every composite and final pass.
 *
 * <p>部分光影包（含 Complementary Reimagined）会在 {@code HAND_TRANSLUCENT} 之后再执行一次
 * 屏幕空间雾 pass，且采样的是早于手部（或其他不可变）的世界深度快照——延后的手部深度写入
 * 影响不到它的输入。因此这里保留原始 3D 快照与精确的手部投影/模型视图变换，把真正的
 * vanilla 管线绘制推迟到 {@code IrisRenderingPipeline#finalizeLevelRendering()} 返回之后。</p>
 *
 * <p>这不是 HUD 准星：顶点依旧是原始的基岩模型几何，ADS、后坐力与视图摆动已冻结在快照里。
 * 只有最终的颜色提交越过光影包后处理。</p>
 *
 * <p>26.1.2 适配（相对 1.21.11 的差异，均已对官方未混淆 jar 逐符号核对）：
 * {@code FeatureRenderDispatcher} 的构造器是八参
 * {@code (SubmitNodeStorage, ModelManager, BufferSource, AtlasManager, OutlineBufferSource,
 * BufferSource, Font, GameRenderState)}——第二参是 {@code ModelManager}（1.21.11 为
 * {@code BlockRenderer}），并多出末尾的 {@code GameRenderState}；
 * {@code RenderBuffers} 在 {@code net.minecraft.client.renderer}（两版相同）；
 * {@code RenderSystem.getModelViewMatrix()} 在 26.1.2 仍是这个名字（26.2 才改名为
 * {@code getModelViewMatrixCopy()}）。</p>
 */
public final class ScopeFinalOverlayState {
    private static final int FINAL_RETICLE_ORDER = 20_000;
    private static final int FINAL_OCULAR_RING_ORDER = 20_001;

    private static final List<ReticleDraw> PENDING_RETICLES = new ArrayList<>();
    private static final List<RingDraw> PENDING_RINGS = new ArrayList<>();
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
     * submit) for the post-composite flush, mirroring {@link #queueOcularRing}. Scope text rides
     * the same deferral as the reticle/rim so the PIP lens picture (drawn at the hand-pass end)
     * or a shader pack's post passes cannot cover it: physical order becomes picture -> text ->
     * reticle -> shade. The hand transform is captured here so a text-only queue can flush too.
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
            // Scope text first: the tasks are submitted straight through the storage itself
            // (26.1.2's SubmitNodeStorage IS a SubmitNodeCollector; the OrderedSubmitNodeCollector
            // returned by order(int) is NOT one, so a task.submit(collector.order(...)) does not
            // even type-check). This lands text in the storage's default order bucket, i.e.
            // before reticle (20_000) / rim (20_001) - picture -> text -> reticle -> shade.
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
                GunMod.LOGGER.info("[TACZ Scope] Rendered deferred reticle and ocular rim after the final cover ({} reticles, {} rims, {} texts).",
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
            // 26.1.2 的八参构造器（字节码核对）：第二参 ModelManager、末尾多一个 GameRenderState。
            FeatureRenderDispatcher dispatcher = new FeatureRenderDispatcher(
                    nodes,
                    minecraft.getModelManager(),
                    buffers.bufferSource(),
                    minecraft.getAtlasManager(),
                    buffers.outlineBufferSource(),
                    buffers.crumblingBufferSource(),
                    minecraft.font,
                    minecraft.gameRenderer.getGameRenderState()
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
