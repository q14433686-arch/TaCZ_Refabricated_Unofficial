package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Scope body and reticle render types that sample the ocular mask texture.
 *
 * <p>Replaces the old depth-manipulation render types (DEPTH_APERTURE, DEPTH_CLEANUP, etc.)
 * with mask-based clipping. The scope body discards pixels inside the ocular projection
 * (stencil EQUAL 0 equivalent), while the reticle discards pixels outside (stencil EQUAL i+1).</p>
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>The ocular mask is rendered to an offscreen RGBA8 FBO at the phase boundary
 *       by {@link ScopeMaskRenderer}.</li>
 *   <li>Body/reticle shaders ({@code scope_body.fsh}) sample this mask via {@code ScopeMaskSampler}
 *       and discard pixels based on the {@code SCOPE_MASK} / {@code SCOPE_MASK_INVERT} defines.</li>
 *   <li>The mask texture is bound by the {@link DepthCopyRenderType} wrapper before each draw.</li>
 * </ol>
 *
 * <h2>Iris compatibility</h2>
 * Under Iris, the custom scope_body shader is replaced by Iris's hand shader.
 * {@link IrisShaderCreatorMixin} injects a dormant mask branch, and
 * {@link IrisScopeMaskState} activates it per-draw via the encoder mixin.
 */
@Environment(EnvType.CLIENT)
public final class ScopeBodyRenderTypes {

    /** Sampler name for the mask texture. Must match scope_body.fsh and Iris injection. */
    public static final String MASK_SAMPLER = "ScopeMaskSampler";

    private static final RenderSetup FAKE_SETUP = RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
            .createRenderSetup();

    private static final Map<Identifier, RenderType> BODY_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> RETICLE_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> RETICLE_EMISSIVE_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> EMISSIVE_CACHE = new HashMap<>();

    // ── Pipelines ──────────────────────────────────────────────────────

    /**
     * Body pipeline: discards pixels INSIDE the ocular projection.
     * Equivalent to upstream: scope_body: stencilFunc(GL_EQUAL, 0)
     */
    private static final RenderPipeline CLIPPED_PIPELINE =
            buildPipeline("scope_body_clipped", true, false, false);

    /**
     * Reticle pipeline: discards pixels OUTSIDE the ocular projection.
     * Equivalent to upstream: renderDivisionOnly: stencilFunc(GL_EQUAL, i+1)
     */
    private static final RenderPipeline RETICLE_PIPELINE =
            buildPipeline("scope_reticle_clipped", true, true, false);

    /** Emissive reticle: same clipping as reticle but full brightness, no directional lighting. */
    private static final RenderPipeline RETICLE_EMISSIVE_PIPELINE =
            buildPipeline("scope_reticle_emissive_clipped", true, true, true);

    /** Emissive reticle without clipping (fallback when mask is unavailable). */
    private static final RenderPipeline EMISSIVE_PIPELINE =
            buildPipeline("scope_reticle_emissive", false, false, true);

    private static boolean irisAssigned;

    // ── Public API ─────────────────────────────────────────────────────

    /** Assign all scope pipelines to Iris' HAND program (once). */
    public static void ensureIrisCompatibility() {
        if (irisAssigned) return;
        irisAssigned = true;
        IrisCompat.assignPipelineToIris(CLIPPED_PIPELINE, "HAND", "scope_body_clipped");
        IrisCompat.assignPipelineToIris(RETICLE_PIPELINE, "HAND", "scope_reticle_clipped");
        IrisCompat.assignPipelineToIris(RETICLE_EMISSIVE_PIPELINE, "HAND", "scope_reticle_emissive_clipped");
        IrisCompat.assignPipelineToIris(EMISSIVE_PIPELINE, "HAND", "scope_reticle_emissive");
    }

    /** Scope body: draws only where the ocular does NOT cover. */
    public static RenderType clipped(Identifier texture) {
        ensureIrisCompatibility();
        return BODY_CACHE.computeIfAbsent(texture,
                t -> create("tacz_scope_body_clipped", CLIPPED_PIPELINE, t, true));
    }

    /** Etched reticle: draws only where the ocular DOES cover. */
    public static RenderType reticle(Identifier texture) {
        ensureIrisCompatibility();
        return RETICLE_CACHE.computeIfAbsent(texture,
                t -> create("tacz_scope_reticle_clipped", RETICLE_PIPELINE, t, true));
    }

    /** Emissive reticle with clipping. */
    public static RenderType reticleEmissive(Identifier texture) {
        ensureIrisCompatibility();
        return RETICLE_EMISSIVE_CACHE.computeIfAbsent(texture,
                t -> create("tacz_scope_reticle_emissive_clipped", RETICLE_EMISSIVE_PIPELINE, t, true));
    }

    /** Emissive reticle without clipping (fallback). */
    public static RenderType emissive(Identifier texture) {
        ensureIrisCompatibility();
        return EMISSIVE_CACHE.computeIfAbsent(texture,
                t -> create("tacz_scope_reticle_emissive", EMISSIVE_PIPELINE, t, false));
    }

    // ── Pipeline construction ──────────────────────────────────────────

    private static RenderPipeline buildPipeline(String name, boolean mask, boolean invert, boolean emissive) {
        RenderPipeline source = RenderPipelines.ENTITY_CUTOUT;
        RenderPipeline.Builder builder = clonePipeline(source,
                Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/" + name));

        // Custom scope body shader with built-in mask logic
        builder.withVertexShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"));
        builder.withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"));

        if (emissive) {
            builder.withShaderDefine("EMISSIVE");
            builder.withShaderDefine("NO_CARDINAL_LIGHTING");
        } else {
            builder.withShaderDefine("PER_FACE_LIGHTING");
        }

        if (mask) {
            builder.withShaderDefine("SCOPE_MASK");
            builder.withSampler(MASK_SAMPLER);
            if (invert) {
                builder.withShaderDefine("SCOPE_MASK_INVERT");
            }
        }

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        IrisCompat.assignPipelineToIris(pipeline, "HAND", name);
        return pipeline;
    }

    private static RenderPipeline.Builder clonePipeline(RenderPipeline source, Identifier location) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(source.getVertexShader())
                .withFragmentShader(source.getFragmentShader())
                .withPolygonMode(source.getPolygonMode())
                .withCull(source.isCull())
                .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode());

        source.getShaderDefines().flags().forEach(builder::withShaderDefine);
        source.getShaderDefines().values().forEach((name, value) -> copyDefine(builder, name, value));
        source.getSamplers().forEach(builder::withSampler);
        source.getUniforms().forEach(uniform -> {
            if (uniform.textureFormat() == null) {
                builder.withUniform(uniform.name(), uniform.type());
            } else {
                builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
            }
        });
        builder.withColorTargetState(source.getColorTargetState());
        DepthStencilState sourceDepth = source.getDepthStencilState();
        if (sourceDepth == null) {
            builder.withDepthStencilState(Optional.empty());
        } else {
            builder.withDepthStencilState(sourceDepth);
        }
        return builder;
    }

    private static void copyDefine(RenderPipeline.Builder builder, String name, String value) {
        try {
            if (value.indexOf('.') >= 0 || value.indexOf('e') >= 0 || value.indexOf('E') >= 0) {
                builder.withShaderDefine(name, Float.parseFloat(value));
            } else {
                builder.withShaderDefine(name, Integer.parseInt(value));
            }
        } catch (NumberFormatException ignored) {
            builder.withShaderDefine(name);
        }
    }

    // ── RenderType construction ────────────────────────────────────────

    private static RenderType create(String name, RenderPipeline pipeline, Identifier tex, boolean bindMask) {
        RenderType base = RenderType.create(name, RenderSetup.builder(pipeline)
                .withTexture("Sampler0", tex)
                .useLightmap()
                .useOverlay()
                .createRenderSetup());

        // Wrap in MaskAwareRenderType to bind mask texture before each draw
        return bindMask ? new MaskAwareRenderType(name, base) : base;
    }

    /**
     * Wraps a RenderType to bind the mask FBO texture and set mask uniforms
     * before each draw call.
     */
    private static final class MaskAwareRenderType extends RenderType {
        private final RenderType wrapped;

        private MaskAwareRenderType(String name, RenderType wrapped) {
            super(name, FAKE_SETUP);
            this.wrapped = wrapped;
        }

        @Override
        public void draw(MeshData meshData) {
            // Bind mask texture and set uniforms before the wrapped draw
            ScopeMaskState.beginMaskDraw();
            ScopeMaskState.bindMaskTexture();
            int program = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
            // mode will be set by the encoder mixin based on pipeline name,
            // but we set it here as a safety net
            ScopeMaskState.setMaskUniforms(program, 1); // default to body mode

            try {
                this.wrapped.draw(meshData);
            } finally {
                ScopeMaskState.endMaskDraw();
            }
        }

        @Override public boolean hasBlending() { return this.wrapped.hasBlending(); }
        @Override public OutputTarget outputTarget() { return this.wrapped.outputTarget(); }
        @Override public int bufferSize() { return this.wrapped.bufferSize(); }
        @Override public VertexFormat format() { return this.wrapped.format(); }
        @Override public VertexFormat.Mode mode() { return this.wrapped.mode(); }
        @Override public boolean isOutline() { return this.wrapped.isOutline(); }
        @Override public RenderPipeline pipeline() { return this.wrapped.pipeline(); }
        @Override public boolean affectsCrumbling() { return this.wrapped.affectsCrumbling(); }
        @Override public boolean canConsolidateConsecutiveGeometry() { return this.wrapped.canConsolidateConsecutiveGeometry(); }
        @Override public boolean sortOnUpload() { return this.wrapped.sortOnUpload(); }
    }
}
