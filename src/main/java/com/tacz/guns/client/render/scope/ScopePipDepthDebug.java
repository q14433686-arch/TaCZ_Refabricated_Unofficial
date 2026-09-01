package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Step 2 of the depth-based scope PIP prototype: a full-screen diagnostic pass that paints only
 * the ocular aperture pure magenta, using the depth-aperture judgement already proven by
 * {@link ScopeDepthCopyState} (same criterion as {@code scope_reticle_mask.fsh}).
 *
 * <h2>What this does not do yet</h2>
 * <ul>
 *   <li>It does not re-project the captured world; magnification stays at the implicit 1x.</li>
 *   <li>It does not wire FOV suppression, config knobs, or the final-overlay flush.</li>
 *   <li>It is disabled by default and only runs on the no-shader-pack (vanilla) hand path when
 *       {@code -Dtacz.scope.pip.debug.paint=true} is present.</li>
 * </ul>
 *
 * <h2>Timing</h2>
 * The aperture signal is produced inside the hand pass (ocular rasterize -&gt; depth copy), so this
 * diagnostic is called from {@code GameRenderer#renderItemInHand} RETURN — after the hand pass.
 * Under Iris the hand path bypasses that method, and the Iris depthtex2 bridge is not yet proven;
 * Step 2 therefore deliberately skips shader packs.
 *
 * <h2>Binding the private depth textures into a bare RenderPass</h2>
 * {@code ScopeDepthCopyState} keeps its copies as raw GL textures ({@code glGenTextures} +
 * {@code glFramebufferTexture}). {@code RenderPass#bindTexture} requires a {@code GpuTextureView}.
 * There is no public API that wraps an existing raw GL id into a {@code GpuTexture}, so this
 * diagnostic subclasses the Minecraft {@link GlTexture}/{@link GlTextureView} classes purely to
 * expose the private texture/view to the pass while keeping ownership inside
 * {@link ScopeDepthCopyState} ({@link #close()} is a no-op by design).
 *
 * <p>26.1.2 适配：{@code GlTexture} 构造器末位参数直接赋给私有 {@code id} 字段（借用外部
 * GL id 的入口，字节码核实）；{@code Builder} 无 {@code withoutBlend()/withColorWrite(bool)}，
 * 等价形式为 {@code withColorTargetState(ColorTargetState(Optional.empty(), WRITE_COLOR))}。</p>
 *
 * <p><b>Unverified until the real machine run:</b> whether
 * {@code bindTexture} + this view survives actual GL binding for a packed depth-stencil texture
 * (the copies may be {@code GL_DEPTH24_STENCIL8}, while the wrapper reports {@link TextureFormat#DEPTH32}
 * metadata). See the Step 2 handoff doc for the exact risk and the fallback plan.</p>
 */
public final class ScopePipDepthDebug {
    /** System property; absent/false means this diagnostic never touches the screen. */
    public static final String DEBUG_PROPERTY = "tacz.scope.pip.debug.paint";
    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

    private static RenderPipeline pipeline;
    private static boolean failed;
    private static boolean loggedQueued;
    private static boolean loggedSkippedIris;
    private static boolean loggedAndDisabled;

    // Cached wrappers around the two private depth textures. The raw gl id does not change while
    // the main target size stays stable, so these are built once and reused.
    private static ImportedDepthTexture worldTexture;
    private static ImportedDepthTexture apertureTexture;
    private static ImportedDepthTextureView worldView;
    private static ImportedDepthTextureView apertureView;
    private static int worldTextureId;
    private static int apertureTextureId;

    private ScopePipDepthDebug() {
    }

    /** Invoked from {@code GameRenderer#renderItemInHand} RETURN (vanilla hand path only). */
    public static void renderAfterHand(Minecraft mc) {
        if (!ENABLED || failed || mc == null) {
            return;
        }
        // Step 3 is the real PIP composite and owns the lens pixels; the magenta diagnostic must
        // not overwrite it when both system properties happen to be set.
        if (ScopePipRenderState.isEnabled()) {
            return;
        }
        if (!loggedAndDisabled) {
            loggedAndDisabled = true;
            GunMod.LOGGER.warn(
                    "[TACZ Scope] Step2 magenta debug is ON ({}) but Step3 is not active {}; "
                            + "painting magenta. Property effective at startup: {}.",
                    DEBUG_PROPERTY, ScopePipRenderState.enablePropertySummary(),
                    System.getProperty(DEBUG_PROPERTY, "<unset>"));
        }
        if (IrisCompat.isUsingRenderPack()) {
            if (!loggedSkippedIris) {
                loggedSkippedIris = true;
                GunMod.LOGGER.info(
                        "[TACZ Scope] Step2 depth PIP debug skipped under a shader pack; "
                                + "vanilla path only at this stage.");
            }
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
            ImportedDepthTextureView worldBinding = worldView(world);
            ImportedDepthTextureView apertureBinding = apertureView(aperture);
            if (worldBinding == null || apertureBinding == null) {
                return;
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "tacz_scope_pip_depth_debug",
                    main.getColorTextureView(),
                    OptionalInt.empty())) {
                pass.setPipeline(pipeline());
                // Aperture test is a binary depth comparison, so NEAREST (never LINEAR).
                // Clamp-to-edge is the default address mode for SamplerCache.get(FilterMode).
                pass.bindTexture(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM,
                        worldBinding,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.bindTexture(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM,
                        apertureBinding,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(0, 3);
            }
            if (!loggedQueued) {
                loggedQueued = true;
                GunMod.LOGGER.info(
                        "[TACZ Scope] Step2 depth PIP debug painted the ocular aperture magenta ({}x{}, "
                                + "world tex={}, aperture tex={}).",
                        world.width(), world.height(), world.textureId(), aperture.textureId());
            }
        } catch (Exception e) {
            failed = true;
            GunMod.LOGGER.error(
                    "[TACZ Scope] Step2 depth PIP debug failed; the diagnostic disabled itself. "
                            + "(No PIP feature is affected.)", e);
        }
    }

    private static RenderPipeline pipeline() {
        if (pipeline == null) {
            // Start from the vanilla full-screen blit pipeline so the vertex format matches a
            // gl_VertexID triangle. Replace both stages and keep only our two depth samplers.
            RenderPipeline source = RenderPipelines.ENTITY_OUTLINE_BLIT;
            pipeline = RenderPipelines.register(
                    RenderPipeline.builder()
                            .withLocation(Identifier.fromNamespaceAndPath(
                                    GunMod.MOD_ID, "pipeline/scope_pip_depth_debug"))
                            .withVertexShader(Identifier.fromNamespaceAndPath(
                                    "minecraft", "core/screenquad"))
                            .withFragmentShader(Identifier.fromNamespaceAndPath(
                                    GunMod.MOD_ID, "core/scope_pip_debug"))
                            .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
                            .withSampler(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM)
                            .withSampler(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM)
                            .withCull(false)
                            // 26.1.2 没有 withoutBlend()/withColorWrite(bool)：等价形式是
                            // 「无混合 + 全色写」。刻意不设 DepthStencilState —— 不请求深度
                            // 附件，准星深度语义不受扰动。
                            .withColorTargetState(new ColorTargetState(
                                    Optional.empty(), ColorTargetState.WRITE_COLOR))
                            .build());
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

    /**
     * A {@link GlTexture} that borrows an existing GL texture object.
     *
     * <p>Ownership stays in {@link ScopeDepthCopyState}; this class only lets the render pipeline
     * treat the raw id as a {@code GpuTexture}/{@code GpuTextureView}. {@link #close()} is a no-op,
     * so no code path can delete the private depth copy out from under the existing mask pipeline.</p>
     */
    private static final class ImportedDepthTexture extends GlTexture {
        ImportedDepthTexture(ScopeDepthCopyState.DepthHandle handle, String label) {
            super(GpuTexture.USAGE_TEXTURE_BINDING, label, TextureFormat.DEPTH32,
                    Math.max(1, handle.width()), Math.max(1, handle.height()), 1, 1,
                    handle.textureId());
        }

        @Override
        public void close() {
            // Never release the borrowed texture; ScopeDepthCopyState owns and reuses it.
            // Deliberately no-op (and nothing clears the inherited closed flag, because this
            // texture must remain bindable for as long as the frame uses it).
        }
    }

    /**
     * A view that never closes or marks itself closed. Combined with {@link ImportedDepthTexture},
     * this guarantees the pass can never decrement the ref-count of, or free, the private copy.
     */
    private static final class ImportedDepthTextureView extends GlTextureView {
        ImportedDepthTextureView(GlTexture texture) {
            super(texture, 0, 1);
        }

        @Override
        public void close() {
            // Never mark this view closed or release the borrowed texture.
        }
    }
}
