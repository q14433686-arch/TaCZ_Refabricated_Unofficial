package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.util.OptionalInt;

/**
 * Step 3 of the depth-based scope PIP: the first real lens picture.
 *
 * <p>Step 2 only painted the aperture solid magenta; this class replaces that with the actual
 * pre-hand world. The three building blocks are the ones Step 2 already proved on a real
 * machine:</p>
 *
 * <ol>
 *   <li>Capture the main target color <b>before</b> {@code renderItemInHand} draws the gun/hand,
 *       into a private off-screen color copy ({@link SceneColorTarget}).</li>
 *   <li>At the hand-pass RETURN, run a full-screen {@code RenderPass} that samples the captured
 *       scene inside the ocular aperture only (the exact {@code ad < wd - eps} criterion).</li>
 *   <li>Sample the scene at {@code center + (uv - center) / Z}, i.e. the screen-space
 *       re-projection that is mathematically identical to narrowing the FOV by {@code Z}.</li>
 * </ol>
 *
 * <h2>What this step does NOT do yet</h2>
 * <ul>
 *   <li><b>No aim-progress ramp yet.</b> The magnification is the steady-state scope zoom
 *       ({@code IGun#getAimingZoom}); it is correct only at full ADS. The
 *       {@code 1 + (Z - 1) * progress} ramping is a later step.</li>
 *   <li><b>Vanilla / no shader pack only.</b> Iris is skipped exactly like Step 2 (the Iris
 *       depthtex2/final-composite wiring is still a later step).</li>
 *   <li><b>No config key.</b> It is a JVM property, {@code -Dtacz.scope.pip.enable=true}, for the
 *       same reason Step 2 is. Config alignment is a later step.</li>
 *   <li><b>No Catmull-Rom / sharpening.</b> Nearest/linear hardware sampling only for now.</li>
 * </ul>
 *
 * <h2>Whole-screen FOV zoom suppression</h2>
 * {@link #suppressesWorldFovZoom(float)} is consulted by {@code CameraSetupEvent#applyScopeMagnification}
 * so the world outside the lens stays at 1× while the lens shows the {@code Z}-magnified scene.
 * If PIP fails or is disabled this returns false and the existing whole-screen FOV zoom resumes.
 */
public final class ScopePipRenderState {
    public static final String ENABLE_PROPERTY = "tacz.scope.pip.enable";
    private static final String SCENE_SAMPLER_UNIFORM = "tacz_SceneColorSampler";
    private static final float MIN_AIMING_PROGRESS = 0.05f;

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(ENABLE_PROPERTY, "false"));

    private static RenderPipeline pipeline;
    private static int builtZoom = -1;
    private static boolean failed;
    private static boolean sceneCaptured;
    private static boolean loggedCapture;
    private static boolean loggedCaptureFailure;
    private static boolean loggedComposite;

    // Borrowed depth copies (same wrap-first approach as ScopePipDepthDebug). The depth textures
    // are owned by ScopeDepthCopyState and must never be released by this class.
    private static ImportedDepthTexture worldTexture;
    private static ImportedDepthTexture apertureTexture;
    private static ImportedDepthTextureView worldView;
    private static ImportedDepthTextureView apertureView;
    private static int worldTextureId;
    private static int apertureTextureId;

    private ScopePipRenderState() {
    }

    /** Read once at class-load so the build exception never escapes hot paths. */
    public static boolean isEnabled() {
        return ENABLED && !failed;
    }

    /**
     * One-line explanation for diagnostics when Step 3 did not paint: which JVM property was seen at
     * class-load and whether the runtime marked the pipeline failed.
     */
    public static String enablePropertySummary() {
        return "(enable=" + System.getProperty(ENABLE_PROPERTY, "<unset>")
                + ", classLoadedEnabled=" + ENABLED
                + ", failed=" + failed + ")";
    }

    /**
     * Whether {@code CameraSetupEvent#applyScopeMagnification} should leave the world FOV alone.
     *
     * <p>True while PIP is neither disabled nor failed, the held gun is a real magnifying scope and
     * the player is entering/holding ADS. This is deliberately a <b>stable per-frame query</b> based
     * on the client aim state, <b>not</b> on {@link #sceneCaptured}: that flag is written mid-frame
     * at the hand-pass HEAD, so gating the FOV on it made the world POV jump while the player was
     * entering/leaving ADS. The whole-screen zoom must be removed for the whole transition, not only
     * on frames where the lens capture happened to be written before the FOV was computed.</p>
     *
     * <p>{@code partialTicks} must be the <b>same</b> frame partial-tick that
     * {@code CameraSetupEvent#applyScopeMagnification} uses for its own fallback zoom, because the
     * suppression gate is literally asking "would this frame apply a non-1x whole-screen zoom?".
     * A fixed tick value does not answer that: {@code partialTicks=1} reads the current tick, which
     * reaches 0 one tick before the interpolated value on the exit boundary, so the gate dropped one
     * frame early and let a residual zoom pulse through (the remaining exit POV jump).</p>
     */
    public static boolean suppressesWorldFovZoom(float partialTicks) {
        // The Iris check is a stable per-session fact, not a mid-frame capture outcome: when a shader
        // pack is active the lens is deliberately not drawn, so the old whole-screen FOV zoom must
        // stay on rather than leaving the world at 1x with no PIP picture.
        return isEnabled() && !IrisCompat.isUsingRenderPack() && currentZoom() > 1 && isAimingStarted(partialTicks);
    }

    /**
     * Whether the ordered scope reticle and physical ocular rim must be drawn after the PIP lens
     * composite. When the real PIP lens is active it owns the aperture pixels at the hand-pass end,
     * so the normal solid-pass reticle/rim would already be under it. Deferring those two overlays
     * to {@link ScopeFinalOverlayState} restores the physical lens order (crosshair and shade on top
     * of the picture) without moving the composite into the middle of the hand batch.
     */
    public static boolean shouldDeferReticleOverlay() {
        return isEnabled() && !IrisCompat.isUsingRenderPack() && sceneCaptured;
    }

    /** The steady-state scope zoom for the local player, or 1 when there is no scope. */
    public static float currentZoom() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 1.0f;
        }
        ItemStack stack = KeepingItemRenderer.getRenderer().getCurrentItem();
        if (!(stack.getItem() instanceof IGun iGun)) {
            return 1.0f;
        }
        float zoom = iGun.getAimingZoom(stack);
        return zoom > 1.0f ? zoom : 1.0f;
    }

    /**
     * Stable per-frame check: has ADS begun at all (used by the FOV suppression gate).
     *
     * <p>This must use the <b>same {@code partialTicks}</b> that {@code CameraSetupEvent}'s fallback
     * zoom uses. If the gate reads a fixed tick value ({@code 0} = previous tick, {@code 1} =
     * current tick) it answers "is the player aiming on that tick", not "would this frame try to
     * apply a whole-screen zoom?". On the entering boundary the interpolated value can be > 0 while
     * the previous tick is still 0; on the exit boundary the current tick is already 0 while the
     * interpolated value is still > 0. Either mismatch leaked one frame of the old whole-screen zoom
     * — the previous-tick gate on entry, the current-tick gate on exit. The interpolated progress is
     * exactly the factor the fallback zoom uses, so gating on "interpolated progress > 0" keeps the
     * world POV at 1x for the whole transition and drops to the fallback only when it would be a
     * 1x no-op.</p>
     */
    private static boolean isAimingStarted(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        ItemStack stack = KeepingItemRenderer.getRenderer().getCurrentItem();
        if (!(stack.getItem() instanceof IGun)) {
            return false;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(mc.player);
        if (operator == null) {
            IGunOperator entityOperator = IGunOperator.fromLivingEntity(mc.player);
            return entityOperator != null && entityOperator.getSynAimingProgress() > 0.0f;
        }
        return operator.getClientAimingProgress(partialTicks) > 0.0f;
    }

    private static boolean isAiming(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        ItemStack stack = KeepingItemRenderer.getRenderer().getCurrentItem();
        if (!(stack.getItem() instanceof IGun)) {
            return false;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(mc.player);
        if (operator == null) {
            IGunOperator entityOperator = IGunOperator.fromLivingEntity(mc.player);
            return entityOperator != null && entityOperator.getSynAimingProgress() > MIN_AIMING_PROGRESS;
        }
        return operator.getClientAimingProgress(0.0f) > MIN_AIMING_PROGRESS;
    }

    /**
     * Captures the fully-rendered world color before the hand/gun is drawn.
     *
     * <p>Called from {@code GameRenderer#renderItemInHand} HEAD. At that point the main target
     * already contains the whole world (the same target that Step 2 verified is readable), and the
     * gun has not been rasterized into it yet, so this copy is exactly the clean lens source.</p>
     */
    public static void captureScene(Minecraft mc) {
        if (!isEnabled() || failed || mc == null) {
            sceneCaptured = false;
            return;
        }
        if (IrisCompat.isUsingRenderPack()) {
            sceneCaptured = false;
            return;
        }
        if (!isAiming(mc)) {
            sceneCaptured = false;
            return;
        }
        var main = mc.getMainRenderTarget();
        if (main == null || main.getColorTexture() == null) {
            sceneCaptured = false;
            return;
        }
        GpuTexture source = main.getColorTexture();
        if (source.isClosed()) {
            sceneCaptured = false;
            return;
        }
        int width = source.getWidth(0);
        int height = source.getHeight(0);
        if (width <= 0 || height <= 0) {
            sceneCaptured = false;
            return;
        }
        try {
            SceneColorTarget target = sceneTarget(width, height, source.getFormat());
            if (target == null || !target.copyFrom(source)) {
                sceneCaptured = false;
                logCaptureFailure();
                return;
            }
            if (!loggedCapture) {
                loggedCapture = true;
                GunMod.LOGGER.info(
                        "[TACZ Scope] Step3 captured a {}x{} clean pre-hand world for {}x PIP.",
                        target.width(), target.height(), (int) currentZoom());
            }
            sceneCaptured = true;
        } catch (Exception e) {
            failed = true;
            sceneCaptured = false;
            GunMod.LOGGER.error(
                    "[TACZ Scope] Step3 scene capture failed; PIP disabled, "
                            + "falling back to whole-screen FOV zoom.", e);
        }
    }

    /** Composites the captured scene into the lens after the hand pass has finished. */
    public static void compositeAfterHand(Minecraft mc) {
        if (!isEnabled() || failed || !sceneCaptured || mc == null) {
            return;
        }
        if (IrisCompat.isUsingRenderPack()) {
            return;
        }
        ScopeDepthCopyState.DepthHandle world = ScopeDepthCopyState.worldDepthTarget();
        ScopeDepthCopyState.DepthHandle aperture = ScopeDepthCopyState.apertureDepthTarget();
        if (!world.available() || !aperture.available()
                || world.textureId() == 0 || aperture.textureId() == 0) {
            return;
        }
        var main = mc.getMainRenderTarget();
        if (main == null || main.getColorTextureView() == null) {
            return;
        }
        try {
            SceneColorTarget scene = sceneTarget();
            ImportedDepthTextureView worldBinding = worldView(world);
            // scene is only read for the blit-free composite; its size is already the main target's.
            ImportedDepthTextureView apertureBinding = apertureView(aperture);
            if (scene == null || worldBinding == null || apertureBinding == null) {
                return;
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "tacz_scope_pip_composite",
                    main.getColorTextureView(),
                    OptionalInt.empty())) {
                pass.setPipeline(pipeline());
                pass.bindTexture(SCENE_SAMPLER_UNIFORM,
                        scene.view(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.bindTexture(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM,
                        worldBinding,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.bindTexture(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM,
                        apertureBinding,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(0, 3);
            }
            if (!loggedComposite) {
                loggedComposite = true;
                GunMod.LOGGER.info(
                        "[TACZ Scope] Step3 composite painted the {}x lens (scene tex={}, world tex={}, aperture tex={}).",
                        (int) currentZoom(), scene.textureId(), world.textureId(), aperture.textureId());
            }
        } catch (Exception e) {
            failed = true;
            sceneCaptured = false;
            GunMod.LOGGER.error(
                    "[TACZ Scope] Step3 composite failed; PIP disabled, "
                            + "falling back to whole-screen FOV zoom.", e);
        }
    }

    private static void logCaptureFailure() {
        if (!loggedCaptureFailure) {
            loggedCaptureFailure = true;
            GunMod.LOGGER.warn(
                    "[TACZ Scope] Step3 could not capture a clean pre-hand world this frame; "
                            + "PIP is not painting. Falling back to whole-screen FOV zoom.");
        }
    }

    private static RenderPipeline pipeline() {
        int zoom = Math.max(1, Math.round(currentZoom()));
        if (pipeline == null || builtZoom != zoom) {
            RenderPipeline source = RenderPipelines.ENTITY_OUTLINE_BLIT;
            pipeline = RenderPipelines.register(
                    RenderPipeline.builder()
                            .withLocation(Identifier.fromNamespaceAndPath(
                                    GunMod.MOD_ID, "pipeline/scope_pip_composite"))
                            .withVertexShader(Identifier.fromNamespaceAndPath(
                                    "minecraft", "core/screenquad"))
                            .withFragmentShader(Identifier.fromNamespaceAndPath(
                                    GunMod.MOD_ID, "core/scope_pip"))
                            .withShaderDefine("TACZ_PIP_ZOOM", (float) zoom)
                            .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
                            .withSampler(SCENE_SAMPLER_UNIFORM)
                            .withSampler(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM)
                            .withSampler(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM)
                            .withCull(false)
                            .withoutBlend()
                            .withColorWrite(true)
                            // No depth in/out: this is a pure screen-space overwrite inside the lens.
                            .build());
            builtZoom = zoom;
        }
        return pipeline;
    }

    private static ImportedDepthTextureView worldView(ScopeDepthCopyState.DepthHandle handle) {
        if (worldTexture == null || worldTextureId != handle.textureId()) {
            worldTexture = new ImportedDepthTexture(handle, "tacz_scope_pip_world_depth");
            worldTextureId = handle.textureId();
            worldView = new ImportedDepthTextureView(worldTexture);
        }
        return worldView;
    }

    private static ImportedDepthTextureView apertureView(ScopeDepthCopyState.DepthHandle handle) {
        if (apertureTexture == null || apertureTextureId != handle.textureId()) {
            apertureTexture = new ImportedDepthTexture(handle, "tacz_scope_pip_aperture_depth");
            apertureTextureId = handle.textureId();
            apertureView = new ImportedDepthTextureView(apertureTexture);
        }
        return apertureView;
    }

    /** Allocates (and remembers) the reusable scene color copy sized to the main color target. */
    private static SceneColorTarget sceneTarget() {
        if (failed || SceneColorTarget.instance == null) {
            return null;
        }
        return SceneColorTarget.instance;
    }

    /**
     * Ensures a suitably-sized scene target exists (called on capture; composite reads it back).
     * The format must match the source's {@code TextureFormat} because
     * {@code CommandEncoder#copyTextureToTexture} checks src/dst format equality.
     */
    private static SceneColorTarget sceneTarget(int width, int height, TextureFormat format) {
        if (failed) {
            return null;
        }
        if (SceneColorTarget.instance == null || SceneColorTarget.instance.width() != width
                || SceneColorTarget.instance.height() != height
                || SceneColorTarget.instance.format() != format) {
            SceneColorTarget.close();
            int w = Math.max(1, width);
            int h = Math.max(1, height);
            SceneColorTarget instance = new SceneColorTarget(w, h, format);
            if (!instance.usable()) {
                instance.close();
                SceneColorTarget.instance = null;
                return null;
            }
            SceneColorTarget.instance = instance;
        }
        return SceneColorTarget.instance;
    }

    /**
     * A {@link GlTexture} that borrows an existing private depth texture. Never frees it.
     */
    private static final class ImportedDepthTexture extends GlTexture {
        ImportedDepthTexture(ScopeDepthCopyState.DepthHandle handle, String label) {
            super(GpuTexture.USAGE_TEXTURE_BINDING, label, TextureFormat.DEPTH32,
                    Math.max(1, handle.width()), Math.max(1, handle.height()), 1, 1,
                    handle.textureId());
        }

        @Override
        public void close() {
            // ScopeDepthCopyState owns this texture; nothing here may release it.
        }
    }

    /**
     * A depth view that never closes, so the pass cannot decrement/free the private copy.
     */
    private static final class ImportedDepthTextureView extends GlTextureView {
        ImportedDepthTextureView(GlTexture texture) {
            super(texture, 0, 1);
        }

        @Override
        public void close() {
        }
    }

    /**
     * A real off-screen color copy of the pre-hand world. Uses the same no-FBO
     * {@code CommandEncoder#copyTextureToTexture} approach that the 26.2 {@code ScopePipRenderer}
     * already uses, so the capture never depends on which FBO is bound at hand-pass start.
     */
    private static final class SceneColorTarget {
        private static SceneColorTarget instance;
        private final int texture;
        private final int width;
        private final int height;
        private final TextureFormat format;
        // Wrapper types keep a strong reference back to the raw GL id so it stays valid.
        private final ImportedSceneTexture wrappedTexture;
        private final ImportedSceneTextureView wrappedView;

        private final boolean usableFormat;

        SceneColorTarget(int width, int height, TextureFormat format) {
            this.width = width;
            this.height = height;
            this.format = format;
            int internalFormat = glInternalFormat(format);
            this.usableFormat = internalFormat != 0;
            this.texture = GL11.glGenTextures();
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
            if (internalFormat != 0) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat,
                        width, height, 0, glExternalFormat(format), glType(format),
                        (java.nio.ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            this.wrappedTexture = new ImportedSceneTexture(this.texture, this.width, this.height,
                    this.format, "tacz_scope_pip_scene");
            this.wrappedView = new ImportedSceneTextureView(this.wrappedTexture);
        }

        boolean usable() {
            return usableFormat && !failed;
        }

        int width() {
            return this.width;
        }

        int height() {
            return this.height;
        }

        TextureFormat format() {
            return this.format;
        }

        int textureId() {
            return this.texture;
        }

        ImportedSceneTextureView view() {
            return this.wrappedView;
        }

        boolean copyFrom(GpuTexture source) {
            if (failed || source == null || source.isClosed()) {
                return false;
            }
            clearGlErrors();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            // (source, target, mipLevel, dstX, dstY, srcX, srcY, width, height)
            encoder.copyTextureToTexture(
                    source, this.wrappedTexture, 0,
                    0, 0, 0, 0, width, height);
            return GL11.glGetError() == GL11.GL_NO_ERROR;
        }

        static void close() {
            if (instance != null) {
                int tex = instance.texture;
                if (GL11.glIsTexture(tex)) {
                    GL11.glDeleteTextures(tex);
                }
                instance = null;
            }
        }

        static int glInternalFormat(TextureFormat format) {
            if (format == TextureFormat.RGBA8) {
                return GL30.GL_RGBA8;
            }
            if (format == TextureFormat.RED8) {
                return GL30.GL_R8;
            }
            return 0;
        }

        static int glExternalFormat(TextureFormat format) {
            if (format == TextureFormat.RED8) {
                return GL11.GL_RED;
            }
            return GL11.GL_RGBA;
        }

        static int glType(TextureFormat format) {
            return GL11.GL_UNSIGNED_BYTE;
        }

        static void clearGlErrors() {
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
                // drain stale errors so the copy result is attributable
            }
        }
    }

    /** Wraps the raw scene GL texture so {@code RenderPass} can bind it as a sampler. */
    private static final class ImportedSceneTexture extends GlTexture {
        ImportedSceneTexture(int glId, int width, int height, TextureFormat format, String label) {
            super(GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                    label, format, width, height, 1, 1, glId);
        }

        @Override
        public void close() {
            // SceneColorTarget owns this texture and frees it only on resize/shutdown.
        }
    }

    private static final class ImportedSceneTextureView extends GlTextureView {
        ImportedSceneTextureView(GlTexture texture) {
            super(texture, 0, 1);
        }

        @Override
        public void close() {
        }
    }
}
