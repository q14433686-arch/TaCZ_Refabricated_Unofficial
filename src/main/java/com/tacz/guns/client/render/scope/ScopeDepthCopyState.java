package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.GunMod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the true ocular screen-space mask around the ordered scope batches:
 *
 * <pre>
 * 1. BACKUP        save the original world depth
 * 2. (ocular draw) the invisible ocular writes near depth into the attachment
 * 3. APERTURE_COPY at the scope-body draw boundary, copy the resulting depth:
 *                  it differs from the world backup only where the ocular rasterized
 * 4. (body draw)   the scope body draws normally; pixels behind the aperture fail depth
 * 5. RESTORE       cleanup geometry writes the original world depth back
 * 6. MASK          reticle draws sample BOTH depths at gl_FragCoord:
 *                  the original world depth and the ocular aperture depth
 * 7.               only pixels where ocularDepth &lt; worldDepth - epsilon may draw
 * 8.               every other pixel discards
 * </pre>
 *
 * The mask needs no stencil attachment and never replaces FBO attachments: the aperture copy
 * is a private sampleable depth texture, and the reticle fragment performs the comparison.
 */
public final class ScopeDepthCopyState {
    public enum Operation {
        NONE,
        BACKUP,
        APERTURE_COPY,
        RESTORE,
        /** Keep fragments inside the ocular (reticles). */
        MASK,
        /** Keep fragments outside the ocular (viewmodel muzzle-flash layers). */
        MASK_OUTSIDE
    }

    public static final String MODE_UNIFORM = "tacz_DepthRestoreMode";
    public static final String SAMPLER_UNIFORM = "tacz_DepthBackupSampler";
    public static final String IRIS_WORLD_DEPTH_UNIFORM = "depthtex2";

    /** Enables the reticle screen-space mask branch. 0 keeps every ordinary shader dormant. */
    public static final String MASK_MODE_UNIFORM = "tacz_ScopeMaskMode";
    /** Marks the no-fog post-composite shader, which must use our private world-depth copy. */
    public static final String FINAL_OVERLAY_UNIFORM = "tacz_ScopeFinalOverlay";
    /** Vanilla mask shaders read the pre-ocular world-depth backup from this sampler. */
    public static final String MASK_WORLD_SAMPLER_UNIFORM = "tacz_WorldDepthSampler";
    /** All mask implementations read the post-ocular aperture depth from this sampler. */
    public static final String APERTURE_SAMPLER_UNIFORM = "tacz_ApertureDepthSampler";
    /** Cleanup reads depth after the scope body draw to preserve visible hand geometry. */
    public static final String POST_BODY_SAMPLER_UNIFORM = "tacz_PostBodyDepthSampler";

    private static final ThreadLocal<Operation> CURRENT = ThreadLocal.withInitial(() -> Operation.NONE);

    /** Exact pre-ocular world depth (steps 1/5); unused when Iris offers depthtex2 instead. */
    private static final DepthTextureTarget WORLD_TARGET = new DepthTextureTarget();
    /** Depth copied at the body boundary: world depth plus only the ocular differences (step 3). */
    private static final DepthTextureTarget APERTURE_TARGET = new DepthTextureTarget();
    /** Depth after the body draw; differs from APERTURE_TARGET where visible scope geometry survived. */
    private static final DepthTextureTarget POST_BODY_TARGET = new DepthTextureTarget();

    /**
     * Read-only snapshot of the exact pre-ocular world-depth copy, intended for the depth-based
     * PIP composite phase.
     *
     * <p><b>Accessor-only (Step 1).</b> This method creates no resources and never mutates the
     * copy; it currently only lets the PIP/composite code observe the raw GL depth texture and
     * its dimensions/format. The actual {@link com.mojang.blaze3d.textures.GpuTextureView}
     * binding path is still an open/verified-by-runtime item (see the handoff doc).</p>
     *
     * @return immutable handle; {@link DepthHandle#available()} is false before the first BACKUP
     *         / APERTURE_COPY cycle has allocated or blitted into the target.
     */
    public static DepthHandle worldDepthTarget() {
        return WORLD_TARGET.snapshot();
    }

    /**
     * Read-only snapshot of the aperture depth copy (world + ocular near-depth, copied at the
     * boundary between the invisible ocular draw and the scope-body draw).
     *
     * <p><b>Accessor-only (Step 1).</b> Same lifetime/reentrancy contract as
     * {@link #worldDepthTarget()}: this is a value snapshot, not the live target.</p>
     */
    public static DepthHandle apertureDepthTarget() {
        return APERTURE_TARGET.snapshot();
    }

    /**
     * Whether the current frame completed a full mask cycle (world backup + aperture copy),
     * i.e. the depth textures {@code prepareMaskDraw} binds are this frame's live data.
     *
     * <p>Scope text uses this as its "mask available" gate (26.2's
     * {@code ScopeTextSubmitter} checked its own target sync the same way): when this returns
     * false the deferred text submission falls back to vanilla {@code submitText} instead of
     * sampling a stale aperture copy that would clip at last frame's lens position.</p>
     */
    public static boolean isMaskCycleValid() {
        return maskValid;
    }

    /**
     * 帧首推进帧计数（{@code GameRenderer.render} HEAD）。只递增 {@code frameCounter}
     * ——<b>不</b>清 {@code maskValid}：终局叠加/reticle 的掩码绘制发生在 Iris 终局钩子
     * （本帧手部阶段之前），那里读的正是上一帧的 {@code maskValid} 真值，清了会把光影下
     * 的 reticle 掩码整个打回 mode 0。时效边界改由帧戳表达：BACKUP→APERTURE_COPY 成功时
     * 盖上 {@code maskCycleFrame = frameCounter}；消费者经
     * {@link #hasMaskCycleThisFrame()} 查询「周期是否落在当前帧」。</p>
     */
    public static void onClientFrameStart() {
        RenderSystem.assertOnRenderThread();
        frameCounter++;
    }

    /**
     * 本帧手部阶段是否已完成整帧掩码周期（{@code maskValid} 且周期落在当前帧）。
     *
     * <p>供「与目镜序列同帧」的消费者使用：poly_mesh 手部批次的孔外剔除（目镜序列先
     * 于枪身绘制）、Iris 终局钩子处的 PIP 合成（Iris 26.1 把手部搬进了
     * LevelRenderer#renderLevel 内部，finalizeLevelRendering 跑在同一遍的手部阶段之后，
     * 拷贝是本帧的）。没有当帧周期的帧（腰射，或当帧周期被身份守卫否决）闸门为假：
     * 前者不拿陈旧镜孔裁枪身（实机 2026-09-01 的静态透视面病例），后者不拿遗留拷贝
     * 贴镜内画面（同日的一帧「截图」贴屏病例）—— 都 fail-closed。</p>
     */
    public static boolean hasMaskCycleThisFrame() {
        return maskValid && maskCycleFrame == frameCounter;
    }


    /**
     * 非 RenderType 绘制路径（poly_mesh GPU 手部批次）的 MASK_OUTSIDE 准备。
     *
     * <p>与 vanilla 的 {@code DepthCopyRenderType} 完全同构：{@link #begin} 记录操作，
     * {@link #beforeDraw()} 在「当前已绑定的 program」上跑 {@code prepareMaskDraw(mode 2)}
     * —— 身份守卫、绑定 aperture 拷贝单元、置 {@code tacz_ScopeMaskMode=2}。Iris 下这
     * 正是打补丁的 hand ExtendedShader（IrisDepthRestoreShaderMixin 注入的休眠分支），
     * world 深度按既有路线取 depthtex2；掩码失效或 program 无该 uniform 时 mode 恒 0
     * = 不裁剪（fail-open），与 vanilla 语义一致。调用方绘制完成后必须配对
     * {@link #end()} 归还被占用的纹理单元。</p>
     */
    public static void beginExternalMaskOutsideDraw() {
        RenderSystem.assertOnRenderThread();
        begin(Operation.MASK_OUTSIDE);
        beforeDraw();

    }

    private static int backupSourceFbo;
    /** FBO bound while the ocular aperture drew; retained for diagnostics only. */
    private static int ocularSourceFbo;
    /**
     * Identity of the depth attachment the ocular wrote near depth into (recorded at BACKUP).
     *
     * <p><b>为什么用「深度附件身份」而不是 FBO id 做比对</b>：Iris 会把 LEVEL 渲染在其自建的
     * gbuffer FBO 上执行，并且同一 program 会因为 {@code before/afterTranslucent} 切换
     * 绑定<b>另一块 GlFramebuffer 对象</b>（在我们的 order(-3) 与 order(-2) 两个批次之间实测
     * 发生：fbo 90→94 / 89→93 / 91→95）。但这些 FBO 的深度附件是<b>同一块</b>
     * {@code currentDepthTexture}（Iris {@code RenderTargets#createGbufferFramebuffer} 对每个
     * gbuffer FBO 一律 {@code addDepthAttachment(currentDepthTexture)}，而 currentDepthTexture
     * 就是主渲染目标的深度纹理）。所以「ocular 写入的深度」在换绑后的 FBO 上同样可读，
     * 只看 FBO id 相等会把合法路径整体误杀（4:31/4:33 实测：Iris 下 mask 每次都被拒、
     * reticle 无裁剪外露）。反之，真正的「写错目标」（独立深度的轮廓线/GUI 离屏 FBO 等）
     * 由附件身份准确拦下。</p>
     */
    private static @javax.annotation.Nullable DepthIdentity ocularDepthIdentity;
    /** Identity of the depth attachment the vanilla world backup was blitted from. */
    private static @javax.annotation.Nullable DepthIdentity worldDepthIdentity;
    /** Identity of the depth attachment the aperture copy was taken from (recorded at step 3). */
    private static @javax.annotation.Nullable DepthIdentity apertureDepthIdentity;
    private static boolean backupValid;
    private static boolean maskValid;
    /**
     * 帧计数（{@link #onClientFrameStart()} 递增）与最近一次成功掩码周期的帧戳。
     * 初值 MIN_VALUE：任何「本帧/上一帧」查询在第一个周期完成前都为假（fail-closed）。
     */
    private static int frameCounter;
    private static int maskCycleFrame = Integer.MIN_VALUE;
    /** Whether a usable world-depth source exists for the mask (Iris depthtex2 or the vanilla copy). */
    private static boolean maskWorldValid;
    private static boolean useIrisPreHandDepth;
    private static boolean loggedIrisPreHandDepth;
    private static boolean loggedApertureActive;
    private static boolean loggedSelectiveRestoreActive;
    private static boolean loggedMaskActiveIris;
    private static boolean loggedMaskActiveVanilla;
    private static boolean loggedMaskActiveFinal;

    private static final List<OverriddenUnit> OVERRIDDEN_UNITS = new ArrayList<>(3);
    private static boolean loggedActive;
    /**
     * Reasons already logged. A strict "last reason only" dedup floods the log when a degraded
     * cycle alternates between two reasons (e.g. Iris' twin framebuffers), so dedup per reason
     * with a bounded vocabulary instead.
     */
    private static final java.util.Set<String> LOGGED_FAILURES = new java.util.HashSet<>();

    private ScopeDepthCopyState() {
    }

    public static void begin(Operation operation) {
        RenderSystem.assertOnRenderThread();
        CURRENT.set(operation);
    }

    /** @return whether GlCommandEncoder should execute the pending draw. */
    public static boolean beforeDraw() {
        RenderSystem.assertOnRenderThread();
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        return switch (CURRENT.get()) {
            case BACKUP -> {
                disableScopeBranches(program);
                maskValid = false;
                maskWorldValid = false;
                // Recorded on BOTH the Iris and vanilla paths: this is the surface the ocular draw
                // is about to write near depth into, and step 3 must copy exactly this surface.
                // Note the depth attachment identity is the durable key — Iris rebinds a different
                // GlFramebuffer (sharing the same depth texture) between our ordered batches, so
                // comparing FBO ids would veto every valid Iris cycle (observed fbo 90->94/89->93/91->95).
                ocularSourceFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                ocularDepthIdentity = captureDepthIdentity();
                if (program > 0
                        && GL20.glGetUniformLocation(program, MODE_UNIFORM) >= 0
                        && GL20.glGetUniformLocation(program, IRIS_WORLD_DEPTH_UNIFORM) >= 0) {
                    // Iris copies exact world depth before HAND_SOLID into depthtex2.
                    useIrisPreHandDepth = true;
                    backupValid = true;
                    maskWorldValid = true;
                    // R11 final-overlay shaders intentionally run after Iris has stopped binding
                    // depthtex2. When a frozen final reticle exists, take one private copy now;
                    // it is the same pre-ocular world depth but remains sampleable after final
                    // composite. Ordinary Iris paths keep using depthtex2 without this blit.
                    // The PIP path also needs it: the lens composite runs after finalizeLevelRendering,
                    // so a bare-rim frame (or any frame where the reticle was not queued) must still
                    // force the private copy or the composite has no world depth to mask against.
                    if (ScopeFinalOverlayState.hasPendingReticles()
                            || ScopePipRenderState.needsIrisWorldDepthCopy()) {
                        boolean copied = copyCurrentDepth(WORLD_TARGET, "final-overlay world depth");
                        worldDepthIdentity = copied ? captureDepthIdentity() : null;
                        if (!copied) {
                            maskWorldValid = false;
                        }
                    }
                    if (!loggedIrisPreHandDepth) {
                        loggedIrisPreHandDepth = true;
                        GunMod.LOGGER.info("[TACZ Scope] Using Iris depthtex2 as exact pre-hand depth backup.");
                    }
                } else {
                    useIrisPreHandDepth = false;
                    maskWorldValid = backupCurrentDepth();
                }
                yield true;
            }
            case APERTURE_COPY -> {
                // Step 3 runs at the body draw boundary: nothing but the ocular draw at order -3 has
                // written depth since the world backup, so this copy isolates the ocular footprint.
                disableScopeBranches(program);
                maskValid = copyApertureDepth() && maskWorldValid;
                if (maskValid) {
                    maskCycleFrame = frameCounter;
                }
                yield true;
            }
            case RESTORE -> prepareRestoreDraw(program);
            case MASK -> prepareMaskDraw(program, 1);
            case MASK_OUTSIDE -> prepareMaskDraw(program, 2);
            case NONE -> {
                disableScopeBranches(program);
                yield true;
            }
        };
    }

    public static void end() {
        for (int i = OVERRIDDEN_UNITS.size() - 1; i >= 0; i--) {
            OverriddenUnit unit = OVERRIDDEN_UNITS.get(i);
            int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit.unit());
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, unit.previousBinding());
            GL13.glActiveTexture(previousActiveTexture);
        }
        OVERRIDDEN_UNITS.clear();
        CURRENT.set(Operation.NONE);
    }

    private static void disableScopeBranches(int program) {
        if (program <= 0) {
            return;
        }
        zeroUniform(program, MODE_UNIFORM);
        zeroUniform(program, MASK_MODE_UNIFORM);
        zeroUniform(program, FINAL_OVERLAY_UNIFORM);
    }

    private static void zeroUniform(int program, String name) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) {
            GL20.glUniform1i(location, 0);
        }
    }

    private static boolean backupCurrentDepth() {
        backupValid = copyCurrentDepth(WORLD_TARGET, "world depth backup");
        worldDepthIdentity = backupValid ? captureDepthIdentity() : null;
        if (backupValid && !loggedActive) {
            loggedActive = true;
            DepthTextureTarget target = WORLD_TARGET;
            GunMod.LOGGER.info("[TACZ Scope] Exact ocular depth backup active (fbo={}, size={}x{}, format=0x{}).",
                    backupSourceFbo, target.width(), target.height(), Integer.toHexString(target.internalFormat()));
        }
        return backupValid;
    }

    private static boolean copyApertureDepth() {
        if (!backupValid) {
            logFailure("ocular aperture copy skipped: no valid world-depth backup in this cycle");
            return false;
        }
        int sourceFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        DepthIdentity currentDepth = captureDepthIdentity();
        if (ocularDepthIdentity == null || !ocularDepthIdentity.equals(currentDepth)) {
            logFailure("ocular aperture copy depth attachment " + currentDepth
                    + " (fbo " + sourceFbo + ") does not match the ocular-written depth "
                    + ocularDepthIdentity + " (fbo " + ocularSourceFbo + ")");
            return false;
        }
        boolean copied = copyCurrentDepth(APERTURE_TARGET, "ocular aperture depth");
        if (copied) {
            apertureDepthIdentity = currentDepth;
        }
        if (copied && !loggedApertureActive) {
            loggedApertureActive = true;
            GunMod.LOGGER.info("[TACZ Scope] Ocular aperture screen-space mask active.");
        }
        return copied;
    }

    private static boolean copyCurrentDepth(DepthTextureTarget target, String debugName) {
        int sourceFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        DepthInfo depth = inspectDepthAttachment();
        if (sourceFbo == 0 || depth == null || depth.samples() != 0 || !target.ensure(depth)) {
            logFailure("cannot prepare sampleable " + debugName + " for fbo=" + sourceFbo);
            return false;
        }

        clearGlErrors();
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.framebuffer());
        GL30.glBlitFramebuffer(
                0, 0, depth.width(), depth.height(),
                0, 0, depth.width(), depth.height(),
                GL11.GL_DEPTH_BUFFER_BIT,
                GL11.GL_NEAREST
        );
        int error = GL11.glGetError();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);

        boolean copied = error == GL11.GL_NO_ERROR;
        backupSourceFbo = copied ? sourceFbo : 0;
        if (!copied) {
            logFailure(debugName + " blit failed with GL error 0x" + Integer.toHexString(error));
        }
        return copied;
    }

    private static boolean prepareRestoreDraw(int program) {
        if (!backupValid || program <= 0) {
            return false;
        }

        int modeLocation = GL20.glGetUniformLocation(program, MODE_UNIFORM);
        // Under Iris the cleanup program is the shared HAND shader: a mask flag left over from a
        // reticle draw must never survive into the restore draw (and vice versa below).
        zeroUniform(program, MASK_MODE_UNIFORM);

        int destinationFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        DepthInfo destination = inspectDepthAttachment();
        DepthIdentity currentDepth = captureDepthIdentity();

        // Capture depth AFTER the scope body. Comparing it with APERTURE_TARGET lets the cleanup
        // distinguish untouched invisible-ocular pixels from visible scope geometry that wrote a
        // nearer depth. The old unconditional restore erased both, allowing later Iris water,
        // particles and clouds to composite over low-power sight internals.
        boolean selectiveRestore = maskValid
                && destination != null
                && destination.width() == APERTURE_TARGET.width()
                && destination.height() == APERTURE_TARGET.height()
                && destination.internalFormat() == APERTURE_TARGET.internalFormat()
                && apertureDepthIdentity != null
                && apertureDepthIdentity.equals(currentDepth)
                && copyCurrentDepth(POST_BODY_TARGET, "post-scope-body depth");

        int apertureLocation = GL20.glGetUniformLocation(program, APERTURE_SAMPLER_UNIFORM);
        int postBodyLocation = GL20.glGetUniformLocation(program, POST_BODY_SAMPLER_UNIFORM);
        if (selectiveRestore && (apertureLocation < 0 || postBodyLocation < 0)) {
            selectiveRestore = false;
            logFailure("cleanup shader has no selective depth-preservation samplers; using legacy restore");
        }
        if (selectiveRestore && !loggedSelectiveRestoreActive) {
            loggedSelectiveRestoreActive = true;
            GunMod.LOGGER.info("[TACZ Scope] Selective depth cleanup active; visible scope depth will survive world restore.");
        }

        if (useIrisPreHandDepth) {
            int irisDepthLocation = GL20.glGetUniformLocation(program, IRIS_WORLD_DEPTH_UNIFORM);
            if (modeLocation < 0 || irisDepthLocation < 0) {
                logFailure("Iris cleanup shader has no depthtex2 restore branch");
                return false;
            }
            if (selectiveRestore) {
                int apertureUnit = bindDepthTexture(1, APERTURE_TARGET.texture());
                int postBodyUnit = bindDepthTexture(2, POST_BODY_TARGET.texture());
                GL20.glUniform1i(apertureLocation, apertureUnit);
                GL20.glUniform1i(postBodyLocation, postBodyUnit);
            }
            // Mode 2 preserves body depth; mode 1 is the old fail-safe unconditional restore.
            GL20.glUniform1i(modeLocation, selectiveRestore ? 2 : 1);
            backupValid = false;
            return true;
        }

        // Same shared-depth rule as the aperture copy: a different FBO is allowed only when it
        // carries the same depth attachment. Vanilla additionally validates the world backup format.
        if (worldDepthIdentity == null || !worldDepthIdentity.equals(currentDepth)
                || destination == null
                || destination.width() != WORLD_TARGET.width()
                || destination.height() != WORLD_TARGET.height()
                || destination.internalFormat() != WORLD_TARGET.internalFormat()) {
            backupValid = false;
            logFailure("depth restore target does not match the backed-up hand depth attachment (fbo "
                    + destinationFbo + ", backup fbo " + backupSourceFbo + ")");
            return false;
        }

        int samplerLocation = GL20.glGetUniformLocation(program, SAMPLER_UNIFORM);
        if (modeLocation < 0 || samplerLocation < 0) {
            logFailure("active cleanup shader has no depth-restore uniforms");
            return false;
        }

        if (selectiveRestore) {
            int apertureUnit = bindDepthTexture(1, APERTURE_TARGET.texture());
            int postBodyUnit = bindDepthTexture(2, POST_BODY_TARGET.texture());
            int worldUnit = bindDepthTexture(3, WORLD_TARGET.texture());
            GL20.glUniform1i(apertureLocation, apertureUnit);
            GL20.glUniform1i(postBodyLocation, postBodyUnit);
            GL20.glUniform1i(samplerLocation, worldUnit);
        } else {
            int worldUnit = bindDepthTexture(1, WORLD_TARGET.texture());
            GL20.glUniform1i(samplerLocation, worldUnit);
        }
        GL20.glUniform1i(modeLocation, selectiveRestore ? 2 : 1);
        backupValid = false;
        return true;
    }

    /**
     * Steps 6-8: bind the world-depth backup and the ocular aperture copy, then enable the mask
     * branch. When anything is missing the draw falls back to the previous unmasked behavior
     * (mode stays 0), which never produces stale or garbage masking.
     */
    private static boolean prepareMaskDraw(int program, int maskMode) {
        if (program <= 0) {
            return true;
        }
        int maskLocation = GL20.glGetUniformLocation(program, MASK_MODE_UNIFORM);
        if (maskLocation < 0) {
            // The active program has no mask branch (not a reticle shader); draw it untouched.
            return true;
        }
        int finalOverlayLocation = GL20.glGetUniformLocation(program, FINAL_OVERLAY_UNIFORM);
        boolean finalOverlay = finalOverlayLocation >= 0;
        // The mask and restore branches share Iris' HAND shader; never let the restore flag bleed
        // into the reticle draw.
        zeroUniform(program, MODE_UNIFORM);
        if (!maskValid) {
            GL20.glUniform1i(maskLocation, 0);
            if (finalOverlay) {
                GL20.glUniform1i(finalOverlayLocation, 0);
            }
            return true;
        }

        // Final-overlay geometry samples only the two immutable private depth copies. It does not
        // use or write the destination depth attachment, which may be a post-composite main target
        // rather than Iris' original hand FBO. Ordinary MASK draws retain the strict identity
        // guard that prevents stale copies from leaking into unrelated render targets.
        if (!finalOverlay) {
            DepthInfo destination = inspectDepthAttachment();
            DepthIdentity currentDepth = captureDepthIdentity();
            if (destination == null
                    || destination.width() != APERTURE_TARGET.width()
                    || destination.height() != APERTURE_TARGET.height()
                    || destination.internalFormat() != APERTURE_TARGET.internalFormat()
                    || apertureDepthIdentity == null || !apertureDepthIdentity.equals(currentDepth)) {
                GL20.glUniform1i(maskLocation, 0);
                logFailure("scope mask target does not match the aperture copy surface (depth "
                        + currentDepth + " vs copied " + apertureDepthIdentity + ")");
                return true;
            }
        }

        int apertureLocation = GL20.glGetUniformLocation(program, APERTURE_SAMPLER_UNIFORM);
        int irisWorldLocation = GL20.glGetUniformLocation(program, IRIS_WORLD_DEPTH_UNIFORM);
        int worldLocation = GL20.glGetUniformLocation(program, MASK_WORLD_SAMPLER_UNIFORM);

        if (apertureLocation < 0) {
            GL20.glUniform1i(maskLocation, 0);
            logFailure("reticle shader has no ocular aperture sampler");
            return true;
        }
        boolean irisWorld = !finalOverlay && useIrisPreHandDepth && irisWorldLocation >= 0;
        // The vanilla branch and final overlay may only bind a world-depth sampler when this cycle
        // actually owns a private copy; otherwise texture 0 would discard every reticle pixel.
        if (!irisWorld && (worldLocation < 0 || WORLD_TARGET.texture() == 0)) {
            GL20.glUniform1i(maskLocation, 0);
            logFailure("reticle shader has no usable world-depth source for the ocular mask");
            return true;
        }

        // The ocular copy occupies the highest unit; the vanilla world backup sits one below it.
        // Iris mask shaders sample its depthtex2 instead of the second unit.
        int apertureUnit = bindDepthTexture(1, APERTURE_TARGET.texture());
        GL20.glUniform1i(apertureLocation, apertureUnit);
        if (!irisWorld) {
            int worldUnit = bindDepthTexture(2, WORLD_TARGET.texture());
            GL20.glUniform1i(worldLocation, worldUnit);
        }
        // 1 keeps the ocular interior (reticles); 2 discards it (muzzle flash/viewmodel FX).
        GL20.glUniform1i(maskLocation, maskMode);
        if (finalOverlay) {
            GL20.glUniform1i(finalOverlayLocation, 1);
        }
        // Log each mask flavour once: toggling a shader pack switches between them mid-session,
        // and a single boolean would hide the Iris/final path ever becoming active.
        if (finalOverlay) {
            if (!loggedMaskActiveFinal) {
                loggedMaskActiveFinal = true;
                GunMod.LOGGER.info("[TACZ Scope] Final overlay masked by private world/aperture depth copies.");
            }
        } else if (irisWorld ? !loggedMaskActiveIris : !loggedMaskActiveVanilla) {
            if (irisWorld) {
                loggedMaskActiveIris = true;
            } else {
                loggedMaskActiveVanilla = true;
            }
            GunMod.LOGGER.info("[TACZ Scope] Scope draws now masked by the ocular aperture depth"
                    + (irisWorld ? " (Iris depthtex2 world source)." : " (vanilla world-depth backup)."));
        }
        return true;
    }

    /**
     * Binds a depth texture to a high texture unit that vanilla/Iris sampler setup does not use,
     * records the previous binding for {@link #end()}, and returns the unit index.
     *
     * @param fromTop 1 selects {@code GL_MAX_TEXTURE_IMAGE_UNITS - 1}, 2 selects the unit below it
     */
    private static int bindDepthTexture(int fromTop, int texture) {
        int textureUnit = Math.max(3, GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS) - fromTop);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit);
        int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL13.glActiveTexture(previousActiveTexture);
        OVERRIDDEN_UNITS.add(new OverriddenUnit(textureUnit, previousBinding));
        return textureUnit;
    }

    /**
     * Captures the identity of the depth attachment of the currently bound framebuffer, as
     * {@code (objectType, objectName)}. Two framebuffers sharing one depth texture strip (e.g.
     * Iris' before/after-translucent twins) report equal identities; a genuinely different
     * depth surface (outline targets, GUI offscreen buffers, missing attachment) does not.
     */
    private static @javax.annotation.Nullable DepthIdentity captureDepthIdentity() {
        int type = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
        );
        if (type == GL11.GL_NONE) {
            return null;
        }
        int name = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
        );
        if (name == 0) {
            return null;
        }
        return new DepthIdentity(type, name);
    }

    private static DepthInfo inspectDepthAttachment() {
        int type = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
        );
        if (type == GL11.GL_NONE) {
            return null;
        }
        int name = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
        );

        if (type == GL11.GL_TEXTURE) {
            int level = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL
            );
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, name);
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);
            int format = GL11.glGetTexLevelParameteri(
                    GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            return width > 0 && height > 0 ? new DepthInfo(width, height, format, 0) : null;
        }

        if (type == GL30.GL_RENDERBUFFER) {
            int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, name);
            int width = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_WIDTH);
            int height = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_HEIGHT);
            int format = GL30.glGetRenderbufferParameteri(
                    GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_INTERNAL_FORMAT);
            int samples = GL30.glGetRenderbufferParameteri(
                    GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_SAMPLES);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
            return width > 0 && height > 0 ? new DepthInfo(width, height, format, samples) : null;
        }
        return null;
    }

    private static TextureAllocation textureAllocation(int internalFormat) {
        if (internalFormat == GL30.GL_DEPTH24_STENCIL8) {
            return new TextureAllocation(GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8);
        }
        if (internalFormat == GL32.GL_DEPTH32F_STENCIL8) {
            return new TextureAllocation(GL30.GL_DEPTH_STENCIL, GL32.GL_FLOAT_32_UNSIGNED_INT_24_8_REV);
        }
        if (internalFormat == GL14.GL_DEPTH_COMPONENT16) {
            return new TextureAllocation(GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_SHORT);
        }
        if (internalFormat == GL14.GL_DEPTH_COMPONENT24) {
            return new TextureAllocation(GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT);
        }
        if (internalFormat == GL14.GL_DEPTH_COMPONENT32 || internalFormat == GL30.GL_DEPTH_COMPONENT32F) {
            return new TextureAllocation(GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT);
        }
        return null;
    }

    private static boolean isPackedDepthStencil(int internalFormat) {
        return internalFormat == GL30.GL_DEPTH24_STENCIL8
                || internalFormat == GL32.GL_DEPTH32F_STENCIL8;
    }

    private static void clearGlErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // drain stale errors so blit diagnostics are attributable
        }
    }

    private static void logFailure(String reason) {
        if (LOGGED_FAILURES.size() > 32) {
            LOGGED_FAILURES.clear();
        }
        if (LOGGED_FAILURES.add(reason)) {
            GunMod.LOGGER.warn("[TACZ Scope] {}", reason);
        }
    }

    private record DepthInfo(int width, int height, int internalFormat, int samples) {
    }

    /** GL object identity of a framebuffer's depth attachment: {@code OBJECT_TYPE + OBJECT_NAME}. */
    private record DepthIdentity(int objectType, int objectName) {
    }

    private record TextureAllocation(int externalFormat, int type) {
    }

    private record OverriddenUnit(int unit, int previousBinding) {
    }

    /**
     * Immutable, accessor-only view of one of {@code ScopeDepthCopyState}'s private depth copies.
     *
     * <p>The composite/PIP phase must not hold the live {@link #framebuffer()} or
     * {@link #textureId()} beyond the render thread frame in which it is consumed; the copy
     * targets are reallocated/rebound in place on cross-frame FBO/format changes. Do not call
     * {@code glDelete*}, {@code glFramebufferTexture}, or otherwise mutate what this handle
     * points at — ownership stays inside {@link ScopeDepthCopyState}.</p>
     */
    public record DepthHandle(int textureId, int framebuffer, int width, int height,
                              int internalFormat, boolean available) {

        public DepthHandle {
            // Normalize hidden invalid states: zero texture + zero framebuffer is "no copy yet".
            if (textureId == 0 && framebuffer == 0 && (width != 0 || height != 0 || internalFormat != 0)) {
                throw new IllegalArgumentException("inconsistent depth handle: zero GL ids with nonzero size");
            }
        }

        public boolean available() {
            return available && textureId != 0;
        }
    }

    /** A private sampleable depth texture plus the depth-only FBO wrapped around it. */
    private static final class DepthTextureTarget {
        private int framebuffer;
        private int texture;
        private int width;
        private int height;
        private int internalFormat;

        int framebuffer() {
            return this.framebuffer;
        }

        int texture() {
            return this.texture;
        }

        int width() {
            return this.width;
        }

        int height() {
            return this.height;
        }

        int internalFormat() {
            return this.internalFormat;
        }

        DepthHandle snapshot() {
            return new DepthHandle(
                    this.texture,
                    this.framebuffer,
                    this.width,
                    this.height,
                    this.internalFormat,
                    this.texture != 0 && this.width > 0 && this.height > 0
            );
        }

        boolean ensure(DepthInfo depth) {
            if (this.framebuffer == 0 || !GL30.glIsFramebuffer(this.framebuffer)) {
                this.framebuffer = GL30.glGenFramebuffers();
            }
            if (this.texture == 0 || !GL11.glIsTexture(this.texture)) {
                this.texture = GL11.glGenTextures();
                this.width = 0;
                this.height = 0;
                this.internalFormat = 0;
            }

            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
            if (this.width != depth.width()
                    || this.height != depth.height()
                    || this.internalFormat != depth.internalFormat()) {
                TextureAllocation allocation = textureAllocation(depth.internalFormat());
                if (allocation == null) {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
                    return false;
                }
                GL11.glTexImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        depth.internalFormat(),
                        depth.width(),
                        depth.height(),
                        0,
                        allocation.externalFormat(),
                        allocation.type(),
                        (java.nio.ByteBuffer) null
                );
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
                this.width = depth.width();
                this.height = depth.height();
                this.internalFormat = depth.internalFormat();
            }

            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
            GL32.glFramebufferTexture(
                    GL30.GL_FRAMEBUFFER,
                    isPackedDepthStencil(depth.internalFormat())
                            ? GL30.GL_DEPTH_STENCIL_ATTACHMENT
                            : GL30.GL_DEPTH_ATTACHMENT,
                    this.texture,
                    0
            );
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            return status == GL30.GL_FRAMEBUFFER_COMPLETE;
        }
    }
}
