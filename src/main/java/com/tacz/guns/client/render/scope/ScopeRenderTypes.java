package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Render types for the depth-aperture scope fallback used on Minecraft 26.1.2. */
public final class ScopeRenderTypes {
    private static final RenderSetup FAKE_SETUP = RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
            .createRenderSetup();

    private static final Map<Identifier, RenderType> DEPTH_APERTURES = new HashMap<>();
    private static final Map<Identifier, RenderType> DEPTH_CLEANUPS = new HashMap<>();
    private static final Map<Identifier, RenderType> VISIBLE_RETICLES = new HashMap<>();

    /**
     * Writes ocular geometry to the existing hand depth attachment without touching color.
     * Scope-body fragments behind that geometry fail their ordinary depth test, leaving world color visible.
     */
    private static final RenderPipeline DEPTH_APERTURE_PIPELINE = createDepthAperturePipeline();

    /** Restores the aperture region from the exact world-depth backup before later translucent world passes. */
    private static final RenderPipeline DEPTH_CLEANUP_PIPELINE = createDepthCleanupPipeline();

    /** Small illuminated reticles are safe to draw without the old division/blackout geometry. */
    private static final RenderPipeline VISIBLE_RETICLE_PIPELINE = createVisibleReticlePipeline();

    private ScopeRenderTypes() {
    }

    /** Forces registration before ShaderManager's initial resource reload. */
    public static void init() {
    }

    public static RenderType depthAperture(Identifier texture) {
        return DEPTH_APERTURES.computeIfAbsent(texture, ScopeRenderTypes::createDepthApertureType);
    }

    public static RenderType depthCleanup(Identifier texture) {
        return DEPTH_CLEANUPS.computeIfAbsent(texture, ScopeRenderTypes::createDepthCleanupType);
    }

    public static RenderType visibleReticle(Identifier texture) {
        return VISIBLE_RETICLES.computeIfAbsent(texture, ScopeRenderTypes::createVisibleReticleType);
    }

    private static RenderType createDepthApertureType(Identifier texture) {
        RenderSetup setup = RenderSetup.builder(DEPTH_APERTURE_PIPELINE)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .createRenderSetup();
        RenderType base = RenderType.create("tacz_scope_depth_aperture_base", setup);
        return new DepthCopyRenderType(
                "tacz_scope_depth_aperture",
                base,
                ScopeDepthCopyState.Operation.BACKUP
        );
    }

    private static RenderType createDepthCleanupType(Identifier texture) {
        RenderSetup setup = RenderSetup.builder(DEPTH_CLEANUP_PIPELINE)
                .withTexture("Sampler0", texture)
                // Satisfy RenderPass validation; ScopeDepthCopyState replaces this binding with backup depth.
                .withTexture(ScopeDepthCopyState.SAMPLER_UNIFORM, texture)
                .useLightmap()
                .useOverlay()
                .createRenderSetup();
        RenderType base = RenderType.create("tacz_scope_depth_cleanup_base", setup);
        return new DepthCopyRenderType(
                "tacz_scope_depth_cleanup",
                base,
                ScopeDepthCopyState.Operation.RESTORE
        );
    }

    private static RenderType createVisibleReticleType(Identifier texture) {
        RenderSetup setup = RenderSetup.builder(VISIBLE_RETICLE_PIPELINE)
                .withTexture("Sampler0", texture)
                .useOverlay()
                .affectsCrumbling()
                .sortOnUpload()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup();
        return RenderType.create("tacz_scope_visible_reticle", setup);
    }

    private static RenderPipeline createDepthAperturePipeline() {
        RenderPipeline source = RenderPipelines.ENTITY_CUTOUT;
        RenderPipeline.Builder builder = clonePipeline(source,
                Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_depth_aperture"));

        ColorTargetState sourceColor = source.getColorTargetState();
        builder.withColorTargetState(new ColorTargetState(
                sourceColor.blendFunction(),
                ColorTargetState.WRITE_NONE
        ));
        DepthStencilState sourceDepth = source.getDepthStencilState();
        CompareOp depthTest = sourceDepth == null ? CompareOp.LESS_THAN_OR_EQUAL : sourceDepth.depthTest();
        // Pull the invisible ocular very slightly toward the camera to avoid coplanar scope-body leakage.
        builder.withDepthStencilState(new DepthStencilState(depthTest, true, -1.0F, -1.0F));

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        IrisCompat.assignPipelineToIris(pipeline, "HAND", "scope_depth_aperture");
        return pipeline;
    }

    private static RenderPipeline createDepthCleanupPipeline() {
        RenderPipeline source = RenderPipelines.ENTITY_CUTOUT;
        RenderPipeline.Builder builder = clonePipeline(source,
                Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_depth_cleanup"));
        builder.withFragmentShader(Identifier.fromNamespaceAndPath(
                GunMod.MOD_ID, "core/scope_depth_cleanup"));
        builder.withSampler(ScopeDepthCopyState.SAMPLER_UNIFORM);

        ColorTargetState sourceColor = source.getColorTargetState();
        builder.withColorTargetState(new ColorTargetState(
                sourceColor.blendFunction(),
                ColorTargetState.WRITE_NONE
        ));
        builder.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true));

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        IrisCompat.assignPipelineToIris(pipeline, "HAND", "scope_depth_cleanup");
        return pipeline;
    }

    private static RenderPipeline createVisibleReticlePipeline() {
        RenderPipeline source = RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE;
        RenderPipeline.Builder builder = clonePipeline(source,
                Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_visible_reticle"));
        // The ocular depth writer must not hide the small dot/cross geometry placed behind the lens.
        builder.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false));

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        IrisCompat.assignPipelineToIris(pipeline, "HAND_TRANSLUCENT", "scope_visible_reticle");
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

    /** Marks the synchronous delegated draw so GlCommandEncoder can back up or restore the active depth FBO. */
    private static final class DepthCopyRenderType extends RenderType {
        private final RenderType wrapped;
        private final ScopeDepthCopyState.Operation operation;

        private DepthCopyRenderType(String name,
                                    RenderType wrapped,
                                    ScopeDepthCopyState.Operation operation) {
            super(name, FAKE_SETUP);
            this.wrapped = wrapped;
            this.operation = operation;
        }

        @Override
        public void draw(MeshData meshData) {
            ScopeDepthCopyState.begin(this.operation);
            try {
                this.wrapped.draw(meshData);
            } finally {
                ScopeDepthCopyState.end();
            }
        }

        @Override
        public boolean hasBlending() {
            return this.wrapped.hasBlending();
        }

        @Override
        public OutputTarget outputTarget() {
            return this.wrapped.outputTarget();
        }

        @Override
        public int bufferSize() {
            return this.wrapped.bufferSize();
        }

        @Override
        public VertexFormat format() {
            return this.wrapped.format();
        }

        @Override
        public VertexFormat.Mode mode() {
            return this.wrapped.mode();
        }

        @Override
        public Optional<RenderType> outline() {
            return this.wrapped.outline();
        }

        @Override
        public boolean isOutline() {
            return this.wrapped.isOutline();
        }

        @Override
        public RenderPipeline pipeline() {
            return this.wrapped.pipeline();
        }

        @Override
        public boolean affectsCrumbling() {
            return this.wrapped.affectsCrumbling();
        }

        @Override
        public boolean canConsolidateConsecutiveGeometry() {
            return this.wrapped.canConsolidateConsecutiveGeometry();
        }

        @Override
        public boolean sortOnUpload() {
            return this.wrapped.sortOnUpload();
        }
    }
}
