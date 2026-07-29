package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A draw-time wrapper around a normal entity {@link RenderType}.
 *
 * <p>The wrapper deliberately has a distinct identity for mask, body and reticle geometry. This prevents
 * {@code MultiBufferSource} from merging all three phases into one delayed batch. Its {@link #draw(MeshData)}
 * method marks the phase while delegating the actual draw to the original render type; the command-encoder
 * mixin then configures stencil against the framebuffer that vanilla/Iris really bound.</p>
 */
public final class ScopeStencilRenderType extends RenderType {
    private static final RenderSetup FAKE_SETUP = RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
            .createRenderSetup();

    private static final Map<WrapperKey, RenderType> WRAPPERS = new HashMap<>();
    private static final Map<Identifier, RenderType> MASK_WRITERS = new HashMap<>();

    private static final RenderPipeline MASK_PIPELINE = createMaskPipeline();

    private final RenderType wrapped;
    private final ScopeStencilState.Phase phase;

    private ScopeStencilRenderType(String name, RenderType wrapped, ScopeStencilState.Phase phase) {
        super(name, FAKE_SETUP);
        this.wrapped = wrapped;
        this.phase = phase;
    }

    /** Forces pipeline registration during client initialization, before ShaderManager compiles pipelines. */
    public static void init() {
        // Class initialization creates and registers MASK_PIPELINE.
    }

    public static RenderType maskWriter(Identifier texture) {
        return MASK_WRITERS.computeIfAbsent(texture, ScopeStencilRenderType::createMaskWriter);
    }

    public static RenderType outside(RenderType original) {
        return wrap(original, ScopeStencilState.Phase.DRAW_OUTSIDE);
    }

    public static RenderType inside(RenderType original) {
        return wrap(original, ScopeStencilState.Phase.DRAW_INSIDE);
    }

    private static RenderType wrap(RenderType original, ScopeStencilState.Phase phase) {
        WrapperKey key = new WrapperKey(original, phase);
        return WRAPPERS.computeIfAbsent(key, ignored -> new ScopeStencilRenderType(
                "tacz_scope_" + phase.name().toLowerCase(Locale.ROOT), original, phase));
    }

    private static RenderType createMaskWriter(Identifier texture) {
        RenderSetup setup = RenderSetup.builder(MASK_PIPELINE)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .createRenderSetup();
        RenderType base = RenderType.create("tacz_scope_mask_writer", setup);
        return new ScopeStencilRenderType(
                "tacz_scope_write_mask",
                base,
                ScopeStencilState.Phase.WRITE_MASK
        );
    }

    /**
     * Clone vanilla's entity-cutout pipeline but disable color and depth writes.
     *
     * <p>Using a pipeline state (rather than {@code glColorMask} around vertex emission) is essential: the
     * command encoder reapplies pipeline color/depth state immediately before the real draw, and Iris does the
     * same for its replacement HAND shader.</p>
     */
    private static RenderPipeline createMaskPipeline() {
        RenderPipeline source = RenderPipelines.ENTITY_CUTOUT;
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_stencil_mask"))
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

        ColorTargetState sourceColor = source.getColorTargetState();
        builder.withColorTargetState(new ColorTargetState(
                sourceColor.blendFunction(),
                ColorTargetState.WRITE_NONE
        ));

        DepthStencilState sourceDepth = source.getDepthStencilState();
        if (sourceDepth != null) {
            builder.withDepthStencilState(new DepthStencilState(
                    sourceDepth.depthTest(),
                    false,
                    sourceDepth.depthBiasScaleFactor(),
                    sourceDepth.depthBiasConstant()
            ));
        } else {
            builder.withDepthStencilState(Optional.empty());
        }

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        IrisCompat.assignScopePipelineToHand(pipeline, "scope_stencil_mask");
        return pipeline;
    }

    private static void copyDefine(RenderPipeline.Builder builder, String name, String value) {
        try {
            if (value.indexOf('.') >= 0 || value.indexOf('e') >= 0 || value.indexOf('E') >= 0) {
                builder.withShaderDefine(name, Float.parseFloat(value));
            } else {
                builder.withShaderDefine(name, Integer.parseInt(value));
            }
        } catch (NumberFormatException ignored) {
            // Current vanilla pipeline defines are numeric. Keeping an unknown future define as a flag is safer
            // than failing class initialization and making every attachment model disappear.
            builder.withShaderDefine(name);
        }
    }

    @Override
    public void draw(MeshData meshData) {
        ScopeStencilState.begin(this.phase);
        try {
            this.wrapped.draw(meshData);
        } finally {
            ScopeStencilState.end();
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

    @Override
    public String toString() {
        return "tacz_scope[" + this.phase + ", " + this.wrapped + ']';
    }

    private record WrapperKey(RenderType renderType, ScopeStencilState.Phase phase) {
    }
}
