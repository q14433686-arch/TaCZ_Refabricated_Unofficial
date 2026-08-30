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
 * {@link #suppressesWorldFovZoom()} is consulted by {@code CameraSetupEvent#applyScopeMagnification}
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
     * Whether {@code CameraSetupEvent#applyScopeMagnification} should leave the world FOV alone.
     *
     * <p>True only while PIP is neither disabled nor failed and the held gun is a real magnifying
     * scope. It is a query, never a cached flag, so a mid-session failure automatically returns
     * the player to the existing whole-screen FOV zoom on the very next frame.</p>
     */
    public static boolean suppressesWorldFovZoom() {
        return isEnabled() && currentZoom() > 1;
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
        if (main == null || main.getColorTextureView() == null) {
            sceneCaptured = false;
            return;
        }
        int width = main.getColorTextureView().getWidth(0);
        int height = main.getColorTextureView().getHeight(0);
        if (width <= 0 || height <= 0) {
            sceneCaptured = false;
            return;
        }
        try {
            SceneColorTarget target = sceneTarget(width, height);
            if (target == null || !target.copyFromCurrentDrawFramebuffer()) {
                sceneCaptured = false;
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

    /** Ensures a suitably-sized scene target exists (called on capture; composite reads it back). */
    private static SceneColorTarget sceneTarget(int width, int height) {
        if (failed) {
            return null;
        }
        if (SceneColorTarget.instance == null || SceneColorTarget.instance.width() != width
                || SceneColorTarget.instance.height() != height) {
            SceneColorTarget.close();
            int w = Math.max(1, width);
            int h = Math.max(1, height);
            SceneColorTarget.instance = new SceneColorTarget(w, h);
            if (failed) {
                SceneColorTarget.close();
                return null;
            }
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
     * A real off-screen RGBA copy of the pre-hand world color, with a depth-less FBO used only as
     * the blit destination. Its GlTexture wrapper is how the RenderPass can bind it as a sampler.
     */
    private static final class SceneColorTarget {
        private static SceneColorTarget instance;
        private final int framebuffer;
        private final int texture;
        private final int width;
        private final int height;
        // Wrapper types grow a strong reference back to the raw GL ids so they stay valid.
        private final ImportedSceneTexture wrappedTexture;
        private final ImportedSceneTextureView wrappedView;

        SceneColorTarget(int width, int height) {
            this.width = width;
            this.height = height;
            this.framebuffer = GL30.glGenFramebuffers();
            this.texture = GL11.glGenTextures();
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA8,
                    width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                    (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);

            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
            GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, this.texture, 0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                failed = true;
            }

            this.wrappedTexture = new ImportedSceneTexture(this.texture, this.width, this.height, "tacz_scope_pip_scene");
            this.wrappedView = new ImportedSceneTextureView(this.wrappedTexture);
        }

        int width() {
            return this.width;
        }

        int height() {
            return this.height;
        }

        int textureId() {
            return this.texture;
        }

        ImportedSceneTextureView view() {
            return this.wrappedView;
        }

        boolean copyFromCurrentDrawFramebuffer() {
            if (failed) {
                return false;
            }
            int sourceFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            if (sourceFbo == 0) {
                return false;
            }
            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.framebuffer);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(
                    0, 0, width, height,
                    0, 0, width, height,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_NEAREST);
            int error = GL11.glGetError();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            return error == GL11.GL_NO_ERROR;
        }

        static void close() {
            if (instance != null) {
                int tex = instance.texture;
                int fbo = instance.framebuffer;
                if (GL11.glIsTexture(tex)) {
                    GL11.glDeleteTextures(tex);
                }
                if (GL30.glIsFramebuffer(fbo)) {
                    GL30.glDeleteFramebuffers(fbo);
                }
                instance = null;
            }
        }
    }

    /** Wraps the raw scene GL texture so {@code RenderPass} can bind it as a sampler. */
    private static final class ImportedSceneTexture extends GlTexture {
        ImportedSceneTexture(int glId, int width, int height, String label) {
            super(GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT
                    | GpuTexture.USAGE_COPY_DST, label, TextureFormat.RGBA8,
                    width, height, 1, 1, glId);
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
