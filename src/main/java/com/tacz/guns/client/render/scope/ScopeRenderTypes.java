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
     * <b>移植遗留问题（必须实机验证，勿当成已完成的适配）。</b>
     * <p>
     * 26.1.2 用 {@code CompareOp.ALWAYS_PASS} 表达「深度测试恒通过、但仍然写深度」。
     * 1.21.11 的 {@code DepthTestFunction} 只有
     * {@code NO_DEPTH_TEST / EQUAL / LEQUAL / LESS / GREATER}，<b>没有 ALWAYS</b>。
     * <p>
     * 逐字节码核实（{@code GlCommandEncoder} / {@code GlConst} / {@code GlConst$1}）：
     * <ul>
     *   <li>{@code GlCommandEncoder} 对 {@code NO_DEPTH_TEST} 直接
     *       {@code GlStateManager._disableDepthTest()}，其余分支才是
     *       {@code _enableDepthTest()} + {@code _depthFunc(GlConst.toGl(f))}；</li>
     *   <li>switchmap 序号：NO_DEPTH_TEST=1、EQUAL=2、LESS=3、GREATER=4；
     *       {@code toGl} 对应 1→519(GL_ALWAYS)、2→514(GL_EQUAL)、3→513(GL_LESS)、
     *       4→516(GL_GREATER)、default→515(GL_LEQUAL)。</li>
     * </ul>
     * 也就是说 GL_ALWAYS 只在 {@code NO_DEPTH_TEST} 这一支出现，
     * 而这一支恰恰会 {@code glDisable(GL_DEPTH_TEST)}；
     * OpenGL 在深度测试禁用时<b>连深度写入一并丢弃</b>（glDepthMask 失效）。
     * 所以「恒通过且仍写深度」在 1.21.11 的 pipeline 状态里<b>无法直接表达</b>。
     * <p>
     * 这里退而选 {@code GREATER_DEPTH_TEST}(GL_GREATER)：
     * depth-cleanup 的语义是把<b>更远</b>的世界深度写回近处的手部深度之上，
     * 新深度 &gt; 旧深度，正好落在 GL_GREATER 通过的区间，且深度写入被保留，
     * 这一条是语义等价的。
     * <p>
     * 但 etched / visible reticle 两条管线原本依赖的是「无条件通过」，
     * GL_GREATER 会让位于已写入镜面深度<b>之前</b>的准星像素被丢弃。
     * 若实机出现准星缺失/闪烁，正确解法不是换枚举，而是在既有的
     * {@code GlCommandEncoderScopeDepthCopyMixin}（已 hook 到 {@code drawFromBuffers} HEAD）
     * 里，对这几条管线在 vanilla 应用完 pipeline 状态之后补一次
     * {@code GlStateManager._depthFunc(GL_ALWAYS)}，保持 {@code GL_DEPTH_TEST} 处于启用态。
     */
    // 【实机验证结论 / 2026-08-13】上面的预测成立，GREATER 对 reticle 是错的：
    //   * 无光影：镜内准星完全不显示；
    //   * 有光影：准星显示（Iris 走自己的 HAND 程序，管线深度状态被 Iris 覆写，
    //     所以恰好绕开了这个 bug —— 这正是「有无光影表现相反」的原因）。
    // 原因：depth-cleanup 把【远】的世界深度写回目镜区域，而准星几何在【近】的手部深度。
    // GL_GREATER 要求 new > old，near < far 不成立 → 准星像素被全部丢弃。
    //
    // 因此按注释里给出的正解处理：
    //   * depth-cleanup 保留 GREATER（它本来就是「远盖近」，语义等价，实机也正常）；
    //   * 两条 reticle 管线改用 NO_DEPTH_TEST，并在 GlCommandEncoderScopeDepthCopyMixin
    //     里于 vanilla 应用完管线状态之后补 _enableDepthTest() + _depthFunc(GL_ALWAYS)，
    //     从而得到「恒通过 + 仍写深度」这个 1.21.11 枚举无法直接表达的状态。
    //     （只用 NO_DEPTH_TEST 不行：glDisable(GL_DEPTH_TEST) 会连深度写入一起丢弃。）
    private static final DepthTestFunction ALWAYS_PASS_KEEPING_DEPTH_WRITES =
            DepthTestFunction.GREATER_DEPTH_TEST;

    /**
     * 供 encoder mixin 识别的「需要强制 GL_ALWAYS」的管线集合。
     * <p>
     * 这两条管线在 builder 里声明为 {@link DepthTestFunction#NO_DEPTH_TEST}（这样 vanilla
     * 至少不会写入一个错误的比较函数），随后由 mixin 重新 enable 深度测试并把比较函数
     * 改成 GL_ALWAYS，使深度写入重新生效。
     */
    private static final Set<RenderPipeline> FORCE_ALWAYS_DEPTH_PIPELINES =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /** @return 该管线是否需要 mixin 把深度比较函数强制成 GL_ALWAYS。 */
    public static boolean needsForcedAlwaysDepth(@Nullable Object pipeline) {
        return pipeline instanceof RenderPipeline p && FORCE_ALWAYS_DEPTH_PIPELINES.contains(p);
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
     * Small illuminated reticles use the same screen-space ocular mask and still write near hand
     * depth to protect their surviving pixels from later world translucency.
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
        // 【但深度写入必须关闭 / 2026-08-13 光影实机反馈】
        // depth-cleanup 刚把目镜区域恢复成【世界远深度】，就是为了让 Iris 之后的
        // 水面/雾/云/粒子等 composite 阶段知道"这里是远处的世界"。准星若再写入自己的
        // 【手部近深度】，等于把这份恢复覆盖掉：Iris 会认为该像素是贴脸的手部表面，
        // 于是把雾效/水面按手部距离叠加上去，表现为准星被"覆盖/叠加"、优先级低于雾和水面。
        //
        // 准星只需要被【看见】，不需要参与后续遮挡计算，因此 depthWrite=false：
        // 恒通过深度测试（画得出来）+ 不写深度（不破坏 cleanup 恢复的世界深度）。
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
        // 同 etched reticle：NO_DEPTH_TEST + encoder mixin 强制 GL_ALWAYS，且【不写深度】
        // （写入手部近深度会覆盖 depth-cleanup 恢复的世界深度，导致 Iris 的雾/水面叠加到准星上）。
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
