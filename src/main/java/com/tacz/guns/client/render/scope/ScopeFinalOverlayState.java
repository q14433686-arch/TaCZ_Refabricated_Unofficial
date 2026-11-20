package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
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
import java.util.function.Consumer;

/**
 * Redraws deferred optical foreground (reticle, text, ocular ring) after Iris shader-pack scene composition.
 * Adapted for Minecraft 26.1.2 (8-arg FeatureRenderDispatcher, Minecraft#getMainRenderTarget, RenderSystem#getModelViewMatrix).
 */
public final class ScopeFinalOverlayState {

    private static final int FINAL_OCULAR_RING_ORDER = 20_001;

    private static final List<RingDraw> PENDING_RETICLES = new ArrayList<>();
    private static final List<Consumer<OrderedSubmitNodeCollector>> PENDING_TEXT = new ArrayList<>();
    private static final List<RingDraw> PENDING_RINGS = new ArrayList<>();

    @Nullable
    private static HandTransform handTransform;
    @Nullable
    private static RenderBuffers renderBuffers;
    @Nullable
    private static SubmitNodeStorage submitNodes;
    @Nullable
    private static FeatureRenderDispatcher featureDispatcher;

    private static boolean replaying;
    private static boolean loggedRendered;
    private static boolean loggedFailure;

    private ScopeFinalOverlayState() {
    }

    public static boolean isReplaying() {
        return replaying;
    }

    public static void queueReticle(BedrockRenderSnapshot snapshot, RenderType type) {
        if (!snapshot.isEmpty()) {
            PENDING_RETICLES.add(new RingDraw(snapshot, type));
        }
    }

    public static void queueText(Consumer<OrderedSubmitNodeCollector> task) {
        PENDING_TEXT.add(task);
    }

    private static boolean empty() {
        return PENDING_RINGS.isEmpty() && PENDING_RETICLES.isEmpty() && PENDING_TEXT.isEmpty();
    }

    public static void beginFrame() {
        PENDING_RINGS.clear();
        PENDING_RETICLES.clear();
        PENDING_TEXT.clear();
        handTransform = null;
    }

    public static void queueOcularRing(BedrockRenderSnapshot snapshot, RenderType renderType) {
        if (!snapshot.isEmpty()) {
            PENDING_RINGS.add(new RingDraw(snapshot, renderType));
        }
    }

    public static void capturePhaseBoundaryTransform() {
        if (handTransform != null || empty()) {
            return;
        }
        if (!ScopeMaskRenderer.isInHandPass()) {
            return;
        }
        handTransform = new HandTransform(
                new Matrix4f(RenderSystem.getModelViewMatrix()),
                RenderSystem.getProjectionMatrixBuffer(),
                RenderSystem.getProjectionType());
    }

    private static boolean ensureDispatcher(Minecraft minecraft) {
        if (featureDispatcher != null && renderBuffers != null && submitNodes != null) {
            return true;
        }
        try {
            renderBuffers = new RenderBuffers(1);
            submitNodes = new SubmitNodeStorage();
            featureDispatcher = new FeatureRenderDispatcher(
                    submitNodes,
                    minecraft.getModelManager(),
                    renderBuffers.bufferSource(),
                    minecraft.getAtlasManager(),
                    renderBuffers.outlineBufferSource(),
                    renderBuffers.crumblingBufferSource(),
                    minecraft.font,
                    minecraft.gameRenderer.getGameRenderState()
            );
            return true;
        } catch (RuntimeException e) {
            if (!loggedFailure) {
                loggedFailure = true;
                GunMod.LOGGER.warn("[TACZ Scope] Failed to construct post-composite FeatureRenderDispatcher on 26.1.2.", e);
            }
            return false;
        }
    }

    public static void renderAfterLevelComposite() {
        if (empty()) {
            return;
        }
        List<RingDraw> reticles = List.copyOf(PENDING_RETICLES);
        List<Consumer<OrderedSubmitNodeCollector>> text = List.copyOf(PENDING_TEXT);
        List<RingDraw> rings = List.copyOf(PENDING_RINGS);
        HandTransform transform = handTransform;
        PENDING_RINGS.clear();
        PENDING_RETICLES.clear();
        PENDING_TEXT.clear();
        handTransform = null;
        if (transform == null || ScopePipRenderer.opaqueCameraMedium()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main == null || !ensureDispatcher(minecraft)) {
            return;
        }

        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        GpuTextureView previousColorOverride = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepthOverride = RenderSystem.outputDepthTextureOverride;

        modelView.pushMatrix();
        modelView.set(transform.modelView());
        RenderSystem.setProjectionMatrix(transform.projection(), transform.projectionType());
        RenderSystem.outputColorTextureOverride = main.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = main.getDepthTextureView();
        replaying = true;
        try {
            SubmitNodeStorage storage = submitNodes;
            storage.clear();
            OrderedSubmitNodeCollector displayCollector = storage.order(FINAL_OCULAR_RING_ORDER - 1);
            var projection = ScopeProjection.current();
            var clip = projection == null ? null : new Matrix4f(projection).mul(transform.modelView());
            for (RingDraw draw : reticles) {
                boolean coverage = ScopeBodyRenderTypes.usesReticleCoverage(draw.renderType());
                displayCollector.submitCustomGeometry(new PoseStack(), draw.renderType(), (pose, consumer) -> {
                    if (coverage) {
                        draw.snapshot().writeFilteredReticle(consumer, clip, main.width, main.height);
                    } else {
                        draw.snapshot().write(consumer);
                    }
                });
            }
            for (var task : text) {
                task.accept(displayCollector);
            }
            OrderedSubmitNodeCollector ringCollector = storage.order(FINAL_OCULAR_RING_ORDER);
            for (RingDraw draw : rings) {
                ringCollector.submitCustomGeometry(new PoseStack(), draw.renderType(),
                        (entryPose, consumer) -> draw.snapshot().write(consumer));
            }
            featureDispatcher.renderAllFeatures();
            renderBuffers.bufferSource().endBatch();
            if (!loggedRendered) {
                loggedRendered = true;
                GunMod.LOGGER.info("[TACZ Scope] Redrew optical foreground after shader-pack scene.");
            }
        } catch (RuntimeException e) {
            if (!loggedFailure) {
                loggedFailure = true;
                GunMod.LOGGER.warn("[TACZ Scope] Post-composite ocular ring redraw failed; skipping.", e);
            }
        } finally {
            replaying = false;
            try {
                if (submitNodes != null) {
                    submitNodes.endFrame();
                }
            } finally {
                RenderSystem.outputColorTextureOverride = previousColorOverride;
                RenderSystem.outputDepthTextureOverride = previousDepthOverride;
                modelView.popMatrix();
                RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            }
        }
    }

    private record HandTransform(Matrix4f modelView,
                                 GpuBufferSlice projection,
                                 ProjectionType projectionType) {
    }

    private record RingDraw(BedrockRenderSnapshot snapshot, RenderType renderType) {
    }
}
