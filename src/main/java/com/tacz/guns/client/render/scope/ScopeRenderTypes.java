package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Render types for the depth-aperture scope fallback used on Minecraft 1.21.11. */
public final class ScopeRenderTypes {
    private static final RenderSetup FAKE_SETUP = RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
            .createRenderSetup();

    private static final Map<RenderType, RenderType> APERTURE_COPY_BODIES = new IdentityHashMap<>();
    private static final Map<Identifier, RenderType> DEPTH_APERTURES = new HashMap<>();
    private static final Map<Identifier, RenderType> DEPTH_CLEANUPS = new HashMap<>();
    private static final Map<Identifier, RenderType> ETCHED_RETICLES = new HashMap<>();
    private static final Map<Identifier, RenderType> VISIBLE_RETICLES = new HashMap<>();
    private static final Map<Identifier, RenderType> VIEWMODEL_CUTOUT_TYPES = new HashMap<>();
    private static final Map<Identifier, RenderType> FLASH_TRANSLUCENT_TYPES = new HashMap<>();
    private static final Map<Identifier, RenderType> FLASH_SWIRL_TYPES = new HashMap<>();

    /**
     * 1.21.11 的 {@code DepthTestFunction} 没有 {@code ALWAYS}。
     * <p>
     * depth-cleanup 需要把较远的世界深度写回较近的手部深度上方；
     * {@code GREATER_DEPTH_TEST} 对这一个写深度操作仍是正确的等价比较。
     * <p>
     * 两条 reticle 管线则需要无条件通过，且<b>不能</b>覆盖 cleanup 恢复的世界深度。
     * 它们因此声明 {@code NO_DEPTH_TEST + depthWrite=false}；在真正 draw 边界由
     * {@code GlCommandEncoderScopeDepthCopyMixin} 仅把深度测试改成
     * {@code GL_ALWAYS}，不改变深度写入掩码。不要为 reticle 恢复
     * {@code _depthMask(true)}。
     */
    private static final DepthTestFunction ALWAYS_PASS_KEEPING_DEPTH_WRITES =
            DepthTestFunction.GREATER_DEPTH_TEST;

    /**
     * 供 encoder mixin 识别的「需要强制 GL_ALWAYS」的 reticle 管线集合。
     * 它们保留 {@code depthWrite=false}；mixin 只重新开启深度测试并改比较函数，
     * 不会重新开启深度写入。
     */
    private static final Set<RenderPipeline> FORCE_ALWAYS_DEPTH_PIPELINES =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /** @return 该管线是否需要 mixin 把深度比较函数强制成 GL_ALWAYS。 */
    public static boolean needsForcedAlwaysDepth(@Nullable RenderPipeline pipeline) {
        return pipeline != null && FORCE_ALWAYS_DEPTH_PIPELINES.contains(pipeline);
    }

    /** Set during extraction when this first-person gun submission actually queued an ocular aperture. */
    private static boolean apertureScheduledForViewmodel;

    /**
     * Writes ocular geometry to the existing hand depth attachment without touching color.
     * Scope-body fragments behind that geometry fail their ordinary depth test, leaving world color visible.
     */
    private static final RenderPipeline DEPTH_APERTURE_PIPELINE = createDepthAperturePipeline();

    /** Restores the aperture region from the exact world-depth backup before later translucent world passes. */
    private static final RenderPipeline DEPTH_CLEANUP_PIPELINE = createDepthCleanupPipeline();

    /**
     * Etched reticles sample the world-depth backup and the ocular aperture depth per pixel
     * and only survive where ocularDepth &lt; worldDepth - epsilon.
     */
    private static final RenderPipeline ETCHED_RETICLE_PIPELINE = createEtchedReticlePipeline();

    /**
     * Small illuminated reticles use the same screen-space ocular mask without replacing the
     * world depth restored by cleanup.
     */
    private static final RenderPipeline VISIBLE_RETICLE_PIPELINE = createVisibleReticlePipeline();

    /** Entity cutout plus an outside-aperture mask for the gun body and non-scope attachments. */
    private static final RenderPipeline VIEWMODEL_CUTOUT_PIPELINE = createViewmodelCutoutPipeline();

    /** Ordinary entity translucency plus an outside-aperture fragment mask for the flash quad. */
    private static final RenderPipeline FLASH_TRANSLUCENT_PIPELINE = createFlashTranslucentPipeline();

    /** Vanilla energy-swirl states plus the same outside-aperture mask for the glow layer. */
    private static final RenderPipeline FLASH_SWIRL_PIPELINE = createFlashSwirlPipeline();

    private ScopeRenderTypes() {
    }

    /** Forces registration before ShaderManager's initial resource reload. */
    public static void init() {
    }

    /** Starts extraction of one first-person gun; prevents a previous frame's aperture from clipping fire. */
    public static void beginViewmodelSubmission() {
        apertureScheduledForViewmodel = false;
    }

    /** @return whether this gun submission queued a valid depth-aperture sequence before its FX. */
    public static boolean hasScheduledViewmodelAperture() {
        return apertureScheduledForViewmodel;
    }

    /**
     * Wraps the plain scope-body type so its draw boundary first copies the aperture depth
     * (world depth plus only the ocular differences) into the mask texture, then draws the body.
     */
    public static RenderType apertureCopy(RenderType base) {
        return APERTURE_COPY_BODIES.computeIfAbsent(base, ScopeRenderTypes::createApertureCopyType);
    }

    public static RenderType depthAperture(Identifier texture) {
        // This method is called while extracting an active first-person ocular, before the gun's
        // functional muzzle-flash node is visited. The flag only selects a masked RenderType;
        // draw-time validation still fails open when a depth copy is unavailable.
        apertureScheduledForViewmodel = true;
        return DEPTH_APERTURES.computeIfAbsent(texture, ScopeRenderTypes::createDepthApertureType);
    }

    public static RenderType depthCleanup(Identifier texture) {
        return DEPTH_CLEANUPS.computeIfAbsent(texture, ScopeRenderTypes::createDepthCleanupType);
    }

    public static RenderType etchedReticle(Identifier texture) {
        return ETCHED_RETICLES.computeIfAbsent(texture, ScopeRenderTypes::createEtchedReticleType);
    }

    public static RenderType visibleReticle(Identifier texture) {
        return VISIBLE_RETICLES.computeIfAbsent(texture, ScopeRenderTypes::createVisibleReticleType);
    }

    /**
     * Replaces an ordinary first-person gun/attachment cutout type only after an ocular was queued.
     * All other contexts and failed aperture cycles retain the caller's original behavior.
     */
    public static RenderType clipForViewmodel(RenderType original, Identifier texture, boolean applies) {
        if (!applies || !apertureScheduledForViewmodel) {
            return original;
        }
        // Gun displays may opt into entityTranslucent; retain that blend/sort recipe rather than
        // silently forcing every body through cutout. AttachmentRender supplies cutout here.
        if (hasBlending(original)) {
            return FLASH_TRANSLUCENT_TYPES.computeIfAbsent(texture, ScopeRenderTypes::createFlashTranslucentType);
        }
        return VIEWMODEL_CUTOUT_TYPES.computeIfAbsent(texture, ScopeRenderTypes::createViewmodelCutoutType);
    }

    /** Muzzle-flash background quad: retain vanilla appearance outside the ocular, discard inside. */
    public static RenderType flashTranslucentClipped(Identifier texture) {
        return FLASH_TRANSLUCENT_TYPES.computeIfAbsent(texture, ScopeRenderTypes::createFlashTranslucentType);
    }

    /** Muzzle-flash additive glow: retain vanilla energy-swirl appearance outside the ocular. */
    public static RenderType flashSwirlClipped(Identifier texture) {
        return FLASH_SWIRL_TYPES.computeIfAbsent(texture, ScopeRenderTypes::createFlashSwirlType);
    }

    private static RenderType createApertureCopyType(RenderType base) {
        return new DepthCopyRenderType(
                "tacz_scope_body_aperture_copy",
                base,
                ScopeDepthCopyState.Operation.APERTURE_COPY
        );
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
                // Satisfy RenderPass validation; ScopeDepthCopyState replaces these placeholders
                // with world/aperture/post-body depth copies at the actual draw boundary.
                .withTexture(ScopeDepthCopyState.SAMPLER_UNIFORM, texture)
                .withTexture(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM, texture)
                .withTexture(ScopeDepthCopyState.POST_BODY_SAMPLER_UNIFORM, texture)
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

    private static RenderType createEtchedReticleType(Identifier texture) {
        RenderSetup setup = RenderSetup.builder(ETCHED_RETICLE_PIPELINE)
                .withTexture("Sampler0", texture)
                // Placeholder bindings satisfy RenderPass validation; ScopeDepthCopyState rebinds
                // both samplers to the live world/aperture depth copies when the mask draw runs.
                .withTexture(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM, texture)
                .withTexture(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM, texture)
                .useLightmap()
                .useOverlay()
                .createRenderSetup();
        RenderType base = RenderType.create("tacz_scope_etched_reticle_base", setup);
        return new DepthCopyRenderType(
                "tacz_scope_etched_reticle",
                base,
                ScopeDepthCopyState.Operation.MASK
        );
    }

    private static RenderType createVisibleReticleType(Identifier texture) {
        RenderSetup setup = RenderSetup.builder(VISIBLE_RETICLE_PIPELINE)
                .withTexture("Sampler0", texture)
                // See createEtchedReticleType: placeholders replaced with live depth at draw time.
                .withTexture(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM, texture)
                .withTexture(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM, texture)
                .useOverlay()
                .affectsCrumbling()
                .sortOnUpload()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup();
        RenderType base = RenderType.create("tacz_scope_visible_reticle_base", setup);
        return new DepthCopyRenderType(
                "tacz_scope_visible_reticle",
                base,
                ScopeDepthCopyState.Operation.MASK
        );
    }

    private static RenderType createViewmodelCutoutType(Identifier texture) {
        // Bytecode-equivalent to RenderTypes.entityCutout(texture, true), plus the two depth
        // samplers consumed by the outside-aperture branch.
        RenderSetup setup = RenderSetup.builder(VIEWMODEL_CUTOUT_PIPELINE)
                .withTexture("Sampler0", texture)
                .withTexture(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM, texture)
                .withTexture(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM, texture)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup();
        RenderType base = RenderType.create("tacz_scope_viewmodel_cutout_base", setup);
        return new DepthCopyRenderType(
                "tacz_scope_viewmodel_cutout",
                base,
                ScopeDepthCopyState.Operation.MASK_OUTSIDE
        );
    }

    private static RenderType createFlashTranslucentType(Identifier texture) {
        // Bytecode-equivalent to RenderTypes.entityTranslucent(texture, true), with two placeholder
        // depth samplers added. ScopeDepthCopyState replaces those bindings at the real draw boundary.
        RenderSetup setup = RenderSetup.builder(FLASH_TRANSLUCENT_PIPELINE)
                .withTexture("Sampler0", texture)
                .withTexture(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM, texture)
                .withTexture(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM, texture)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .sortOnUpload()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup();
        RenderType base = RenderType.create("tacz_scope_flash_translucent_base", setup);
        return new DepthCopyRenderType(
                "tacz_scope_flash_translucent",
                base,
                ScopeDepthCopyState.Operation.MASK_OUTSIDE
        );
    }

    private static RenderType createFlashSwirlType(Identifier texture) {
        // Exact RenderTypes.energySwirl setup: animated UV transform, lightmap/overlay bindings and
        // upload sorting are preserved; only the depth-mask samplers are additional.
        RenderSetup setup = RenderSetup.builder(FLASH_SWIRL_PIPELINE)
                .withTexture("Sampler0", texture)
                .withTexture(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM, texture)
                .withTexture(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM, texture)
                .setTextureTransform(new TextureTransform.OffsetTextureTransform(1.0F, 1.0F))
                .useLightmap()
                .useOverlay()
                .sortOnUpload()
                .createRenderSetup();
        RenderType base = RenderType.create("tacz_scope_flash_swirl_base", setup);
        return new DepthCopyRenderType(
                "tacz_scope_flash_swirl",
                base,
                ScopeDepthCopyState.Operation.MASK_OUTSIDE
        );
    }

    private static RenderPipeline createDepthAperturePipeline() {
        RenderPipeline source = RenderPipelines.ENTITY_CUTOUT;
        RenderPipeline.Builder builder = clonePipeline(source,
                Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_depth_aperture"));

        // 1.21.11 没有 ColorTargetState/DepthStencilState 这两个聚合对象，
        // 等价状态在 Builder 上是扁平的 withColorWrite / withDepthWrite /
        // withDepthTestFunction / withDepthBias（语义一一对应，见 clonePipeline 的注释）。
        builder.withColorWrite(false);                              // == ColorTargetState.WRITE_NONE
        builder.withDepthTestFunction(source.getDepthTestFunction());
        builder.withDepthWrite(true);
        // Pull the invisible ocular very slightly toward the camera to avoid coplanar scope-body leakage.
        builder.withDepthBias(-1.0F, -1.0F);

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
        builder.withSampler(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM);
        builder.withSampler(ScopeDepthCopyState.POST_BODY_SAMPLER_UNIFORM);

        builder.withColorWrite(false);
        // Cleanup geometry rasterizes only the ocular footprint and writes exact sampled world depth.
        builder.withDepthTestFunction(ALWAYS_PASS_KEEPING_DEPTH_WRITES);
        builder.withDepthWrite(true);

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        IrisCompat.assignPipelineToIris(pipeline, "HAND", "scope_depth_cleanup");
        return pipeline;
    }

    private static RenderPipeline createEtchedReticlePipeline() {
        RenderPipeline source = RenderPipelines.ENTITY_CUTOUT;
        RenderPipeline.Builder builder = clonePipeline(source,
                Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_etched_reticle"));
        // entity.fsh clone plus the ocular screen-space mask branch at the top of main().
        builder.withFragmentShader(Identifier.fromNamespaceAndPath(
                GunMod.MOD_ID, "core/scope_reticle_mask"));
        builder.withSampler(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM);
        builder.withSampler(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM);
        // Large blackout panels are still removed on the CPU; the retained thin marks render after
        // the exact depth restore and the mask clips them to the ocular footprint.
        //
        // 准星必须【无条件通过】深度测试：它在手部近深度，而目镜区域刚被 depth-cleanup
        // 写回了世界远深度，任何 near<far 的比较都会把它丢掉（实机已证实 GREATER 会让
        // 无光影下的准星完全消失）。声明为 NO_DEPTH_TEST，再由 encoder mixin 还原成 GL_ALWAYS。
        //
        // 【深度写入必须关闭】depth-cleanup 已把目镜区域恢复为世界深度；准星只需显示，
        // 不应以手部近深度覆盖该结果。depthWrite=false 保证 GL_ALWAYS 只影响本次
        // 准星颜色绘制，不改变后续 world/composite pass 所读的深度。
        //
        // 这只能保证深度恢复不被破坏，不能单独保证 shader pack 的 HAND 或更晚的
        // composite 阶段不会改变准星颜色；那是独立的绘制时序/着色问题，须实机验证。
        builder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
        builder.withDepthWrite(false);

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        FORCE_ALWAYS_DEPTH_PIPELINES.add(pipeline);
        IrisCompat.assignPipelineToIris(pipeline, "HAND", "scope_etched_reticle");
        return pipeline;
    }

    private static RenderPipeline createVisibleReticlePipeline() {
        RenderPipeline source = RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE;
        RenderPipeline.Builder builder = clonePipeline(source,
                Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_visible_reticle"));
        // Same entity.fsh clone plus ocular mask; under Iris the equivalent branch is injected.
        builder.withFragmentShader(Identifier.fromNamespaceAndPath(
                GunMod.MOD_ID, "core/scope_reticle_mask"));
        builder.withSampler(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM);
        builder.withSampler(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM);
        // The ocular depth writer must not hide the small dot/cross geometry placed behind the lens.
        // 同 etched reticle：NO_DEPTH_TEST + encoder mixin 强制 GL_ALWAYS，且【不写深度】；
        // 这样不会覆盖 depth-cleanup 恢复的世界深度。
        builder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
        builder.withDepthWrite(false);

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        FORCE_ALWAYS_DEPTH_PIPELINES.add(pipeline);
        IrisCompat.assignPipelineToIris(pipeline, "HAND_TRANSLUCENT", "scope_visible_reticle");
        return pipeline;
    }

    private static RenderPipeline createViewmodelCutoutPipeline() {
        RenderPipeline.Builder builder = clonePipeline(
                RenderPipelines.ENTITY_CUTOUT,
                Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_viewmodel_cutout"));
        builder.withFragmentShader(Identifier.fromNamespaceAndPath(
                GunMod.MOD_ID, "core/scope_flash_clip"));
        builder.withSampler(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM);
        builder.withSampler(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM);

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        IrisCompat.assignPipelineToIris(pipeline, "HAND", "scope_viewmodel_cutout");
        return pipeline;
    }

    private static RenderPipeline createFlashTranslucentPipeline() {
        RenderPipeline.Builder builder = clonePipeline(
                RenderPipelines.ENTITY_TRANSLUCENT,
                Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_flash_translucent"));
        builder.withFragmentShader(Identifier.fromNamespaceAndPath(
                GunMod.MOD_ID, "core/scope_flash_clip"));
        builder.withSampler(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM);
        builder.withSampler(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM);

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        IrisCompat.assignPipelineToIris(pipeline, "HAND_TRANSLUCENT", "scope_flash_translucent");
        return pipeline;
    }

    private static RenderPipeline createFlashSwirlPipeline() {
        RenderPipeline.Builder builder = clonePipeline(
                RenderPipelines.ENERGY_SWIRL,
                Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_flash_swirl"));
        builder.withFragmentShader(Identifier.fromNamespaceAndPath(
                GunMod.MOD_ID, "core/scope_flash_clip"));
        builder.withSampler(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM);
        builder.withSampler(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM);

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        IrisCompat.assignPipelineToIris(pipeline, "HAND_TRANSLUCENT", "scope_flash_swirl");
        return pipeline;
    }

    /**
     * 1.21.11 的 {@code RenderType} 不再暴露 {@code hasBlending()}（那是 26.1 加的）。
     * 等价信息在其 {@code RenderPipeline} 上：存在 BlendFunction 即为混合类型。
     */
    private static boolean hasBlending(RenderType type) {
        return type.pipeline().getBlendFunction().isPresent();
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
        // 26.1 把混合/颜色写入打包进 ColorTargetState，把深度测试/写入/bias 打包进
        // DepthStencilState。1.21.11 全部是 Builder 上的独立 setter，这里逐项复制。
        Optional<BlendFunction> blend = source.getBlendFunction();
        if (blend.isPresent()) {
            builder.withBlend(blend.get());
        } else {
            builder.withoutBlend();
        }
        builder.withColorWrite(source.isWriteColor());
        builder.withDepthTestFunction(source.getDepthTestFunction());
        builder.withDepthWrite(source.isWriteDepth());
        builder.withDepthBias(source.getDepthBiasScaleFactor(), source.getDepthBiasConstant());
        builder.withColorLogic(source.getColorLogic());
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

        // 1.21.11 的 RenderType 没有 hasBlending()/outputTarget()（26.1 才加的）。
        // 混合信息改由 pipeline().getBlendFunction() 表达，输出目标则由 RenderSetup 决定，
        // 包装类无需再转发，删掉这两个 override。

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
