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
