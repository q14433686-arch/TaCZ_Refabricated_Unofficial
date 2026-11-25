package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Depth-aperture PIP. The native pre-hand capture, Iris finished-frame capture and optional
 * narrow world pass share one colour target. Only a completed world/ocular depth cycle from
 * this frame can put that colour on screen; projected bounds steer sampling, never coverage.
 * Reprojection magnifies about the moving ocular and returns to the camera axis on alignment.
 * A narrow pass supplies already magnified colour and must be sampled at 1x.
 *
 * <h2>Whole-screen FOV zoom replacement</h2>
 * {@link #suppressesWorldFovZoom(float)} is consulted by {@code CameraSetupEvent#applyScopeMagnification}
 * so the camera does <b>not</b> apply the old full-screen zoom; instead the caller applies
 * {@code ScopePipWorldZoomShare} of it (0 = world stays 1×, 1 = full whole-screen zoom) and the lens
 * shows the {@code Z}-magnified re-projection. If PIP fails or is disabled this returns false and the
 * existing whole-screen FOV zoom resumes unchanged.
 *
 * <h2>26.1.2 适配（相对 1.21.11 源的差异）</h2>
 * <ul>
 *   <li>{@code RenderPipeline.Builder} 在 26.1.2 没有 {@code withoutBlend()/withColorWrite(bool)}
 *       便利链，等价形式是 {@code withColorTargetState(new ColorTargetState(Optional.empty(),
 *       ColorTargetState.WRITE_COLOR))}（无混合 + 全色写入；字节码/常量已对官方 jar 核实）。</li>
 *   <li>其余触点（{@code CommandEncoder#copyTextureToTexture}、
 *       {@code createRenderPass(Supplier, GpuTextureView, OptionalInt)}、
 *       {@code RenderPass#bindTexture(String, GpuTextureView, GpuSampler)}、
 *       {@code RenderSystem.getSamplerCache().getClampToEdge(FilterMode)}、
 *       借用式 {@code GlTexture}/{@code GlTextureView} 子类化）两版同形，逐项对
 *       26.1.2 merged jar 核实过签名。</li>
 * </ul>
 */
public final class ScopePipRenderState {
    public static final String ENABLE_PROPERTY = "tacz.scope.pip.enable";
    private static final String SCENE_SAMPLER_UNIFORM = "tacz_SceneColorSampler";
    private static final float DEFAULT_MIN_AIMING_PROGRESS = 0.05f;
    // Zoom, sharpness and debug paint are draw uniforms; no pipeline variants per ADS frame.
    private static RenderPipeline plainPipeline, lensPipeline;
    private static boolean failed;
    private static boolean sceneCaptured;
    private static boolean loggedCapture;
    private static boolean loggedCaptureFailure;
    private static boolean loggedComposite;
    private static boolean loggedNoComposite;

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

    /**
     * PIP is active when the dev JVM property (-Dtacz.scope.pip.enable) <b>or</b> the in-game config
     * toggle (RenderConfig.SCOPE_PIP_ENABLE) is on, and the runtime has not permanently failed. The
     * config is read lazily (it is still <b>null</b> before {@code ClientConfig.init}), so keep the
     * null guard: a null field means "config not loaded yet", not "disabled".
     */
    public static boolean isEnabled() {
        return (devPropertyEnabled() || configEnabled()) && !failed;
    }

    private static boolean devPropertyEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLE_PROPERTY, "false"));
    }

    private static boolean configEnabled() {
        return RenderConfig.SCOPE_PIP_ENABLE != null && RenderConfig.SCOPE_PIP_ENABLE.get();
    }

    /**
     * One-line explanation for diagnostics when Step 3 did not paint: which dev JVM property was seen,
     * what the in-game config currently says, and whether the runtime marked the pipeline failed.
     */
    public static String enablePropertySummary() {
        return "(devProperty=" + System.getProperty(ENABLE_PROPERTY, "<unset>")
                + ", config=" + (RenderConfig.SCOPE_PIP_ENABLE != null ? RenderConfig.SCOPE_PIP_ENABLE.get() : false)
                + ", failed=" + failed + ")";
    }

    /**
     * Whether {@code CameraSetupEvent#applyScopeMagnification} should take the PIP path instead of
     * the old whole-screen zoom. When true the caller applies only {@code ScopePipWorldZoomShare} of
     * the zoom to the world (default 0 = world unchanged).
     *
     * <p>True while PIP is neither disabled nor failed, the held gun is a real magnifying scope and
     * the player is entering/holding ADS. This is deliberately a <b>stable per-frame query</b> based
     * on the client aim state, <b>not</b> on {@link #sceneCaptured}: that flag is written mid-frame
     * at the hand-pass HEAD, so gating the FOV on it made the world POV jump while the player was
     * entering/leaving ADS. The whole-screen zoom must be replaced for the whole transition, not only
     * on frames where the lens capture happened to be written before the FOV was computed.</p>
     *
     * <p>{@code partialTicks} must be the <b>same</b> frame partial-tick that
     * {@code CameraSetupEvent#applyScopeMagnification} uses for its own fallback zoom, because the
     * gate is literally asking "would this frame apply a non-1x whole-screen zoom?". A fixed tick
     * value does not answer that: {@code partialTicks=1} reads the current tick, which reaches 0 one
     * tick before the interpolated value on the exit boundary, so the gate dropped one frame early
     * and let a residual zoom pulse through (the remaining exit POV jump).</p>
     */
    public static boolean suppressesWorldFovZoom(float partialTicks) {
        // Without a shader pack the lens is drawn on the vanilla hand-pass RETURN and the world
        // must stay at 1x. With a shader pack the lens is drawn after Iris' final composite, but
        // only when the player explicitly opted in (ScopePipAllowShaderPacks); otherwise Iris
        // keeps the classic whole-screen FOV zoom because there is no PIP picture to take its
        // place. Min magnification keeps low-power scopes on the classic whole-screen zoom too.
        return isEnabled() && irisCompatible()
                && currentZoom() >= minMagnification() && isAimingStarted(partialTicks);
    }

    /**
     * Whether the ordered scope reticle and physical ocular rim must be drawn after the PIP lens
     * composite. When the real PIP lens is active it owns the aperture pixels at the hand-pass end,
     * so the normal solid-pass reticle/rim would already be under it. Deferring those two overlays
     * to {@link ScopeFinalOverlayState} restores the physical lens order (crosshair and shade on top
     * of the picture) without moving the composite into the middle of the hand batch.
     *
     * <p>Defer as soon as this path owns the FOV, not just after the colour capture. A user
     * reveal floor or missing depth can still skip the picture; the queued rim remains drawable.</p>
     */
    public static boolean shouldDeferReticleOverlay() {
        if (ScopeLensFrame.expected != null) return true;
        return suppressesWorldFovZoom(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
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
     * 供 meshloader 的距离闸门用：当前画面把远处放大了多少倍（裸眼 = 1）。
     *
     * <p>同步自 26.2 线 {@code 0886909}（那边的 {@code ScopePipRenderer#currentDetailZoom}），
     * 本分支没有 {@code ScopePipRenderer}（镜内合成走 {@code ScopePipRerender} +
     * {@code ScopeFinalOverlayState}），倍率与开镜进度都从本类取，公式一致。</p>
     *
     * <h3>为什么距离闸门需要它</h3>
     * poly 层的提交发生在 extract 阶段、每帧一次，{@code MeshMaxRenderDistance} 与
     * {@code MeshWorldFullDetailDistance} 都按<b>主相机裸眼距离</b>判定；而镜内那一遍复用
     * 同一批提交节点，不会重新过闸门。于是 4x 镜下 48 格的 poly 上限观感只剩 12 格、
     * 16 格全模豁免观感只剩 4 格 —— 举镜看到的掉落物/第三人称 mesh 枪几乎必然是立方体。
     *
     * <p>「多远该有细节」本质是<b>角尺寸</b>判定：把阈值乘上当前放大倍数即恢复语义一致。
     * 取值随开镜进度渐变（与整屏变焦的 {@code 1+(zoom-1)·progress} 同式），收镜自动回 1，
     * 经典整屏变焦与 PIP 两种模式同样适用（两者都放大了世界观感）。</p>
     *
     * <p><b>证据级别</b>：机制与公式 = 26.2 线实机回报 + 其实现；本分支这一份 =
     * 同形移植，<b>实机未验</b>。</p>
     */
    public static float currentDetailZoom() {
        Minecraft mc = Minecraft.getInstance();
        float progress = currentAimingProgress(mc,
                mc.getDeltaTracker() == null ? 0.0f : mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (progress <= 0.0f) {
            return 1.0f;
        }
        float magnification = Math.max(1.0f, currentZoom());
        return 1.0f + (magnification - 1.0f) * progress;
    }

    // ------------------------------------------------------------------
    // 游戏内配置读取（配置可能尚未加载，带 null 兜底）
    // ------------------------------------------------------------------

    private static float minAimingProgress() {
        return RenderConfig.SCOPE_PIP_MIN_AIMING_PROGRESS == null
                ? DEFAULT_MIN_AIMING_PROGRESS
                : RenderConfig.SCOPE_PIP_MIN_AIMING_PROGRESS.get().floatValue();
    }

    /**
     * 低于该倍率的瞄具不走 PIP，回落到旧整屏变焦。
     *
     * <p>这里<b>刻意开放</b>：镜内裁手/裁火光也用同一条线 —— 低倍镜（配置注释举的 2×/3×）
     * 没有「镜内画面」可让位，把手臂/火光挖个洞露出未放大的背景只会像破图，见
     * {@link com.tacz.guns.client.render.scope.ScopeRenderTypes#viewmodelFxClipApplies()}。</p>
     */
    public static float minMagnification() {
        return RenderConfig.SCOPE_PIP_MIN_MAGNIFICATION == null
                ? 4.0f
                : RenderConfig.SCOPE_PIP_MIN_MAGNIFICATION.get().floatValue();
    }

    /** 开镜时世界要多放大多少倍的下限（满开镜目标）。 */
    private static float worldZoomShare() {
        return RenderConfig.SCOPE_PIP_WORLD_ZOOM_SHARE == null
                ? 0.0f
                : Mth.clamp(RenderConfig.SCOPE_PIP_WORLD_ZOOM_SHARE.get().floatValue(), 0.0f, 1.0f);
    }

    /**
     * 满开镜时镜外世界应放大的倍数：{@code Z^share}（{@code share=0} 恒为 1，即纯 PIP）。
     */
    public static float worldZoomTarget() {
        // 二次渲染模式镜内像素是真画出来的，没有分辨率上限，世界放大只会白牺牲镜外画质。
        if (ScopePipRerender.worldZoomForcedToOne()) {
            return 1.0f;
        }
        float zoom = currentZoom();
        if (zoom <= 1.0f) {
            return 1.0f;
        }
        float share = worldZoomShare();
        return share <= 0.0f ? 1.0f : (float) Math.pow(zoom, share);
    }

    /**
     * 镜内相对已放大世界的再放大倍数：{@code Z / Z^share = Z^(1-share)}。
     */
    public static float lensZoom() {
        return lensZoomAtProgress(1.0f);
    }

    /** Divide out the world share at THIS progress, not the steady-state share. Interpolating
     * the two factors separately makes their product smaller than the requested total in transit. */
    private static float lensZoomAtProgress(float progress) {
        return Mth.lerp(progress, 1.0f, currentZoom()) / worldZoomAtProgress(progress);
    }

    /**
     * 某帧开镜进度下，世界实际应放大的倍数。与 {@code CameraSetupEvent} 的回落分支同式：
     * {@code 1 + (worldZoomTarget - 1) * progress}。
     */
    public static float worldZoomAtProgress(float aimingProgress) {
        float target = worldZoomTarget();
        return 1.0f + (target - 1.0f) * Mth.clamp(aimingProgress, 0.0f, 1.0f);
    }

    private static float sharpness() {
        return RenderConfig.SCOPE_PIP_SHARPNESS == null
                ? 0.0f
                : Mth.clamp(RenderConfig.SCOPE_PIP_SHARPNESS.get().floatValue(), 0.0f, 1.0f);
    }

    private static boolean allowShaderPacks() {
        return RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS != null
                && RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.get();
    }

    /**
     * Whether the post-final-composite Iris variant is active on this frame.
     *
     * <p>Used by {@code ScopeDepthCopyState} at the BACKUP draw boundary to decide whether a
     * private pre-ocular world-depth copy is mandatory. Without it the composite at
     * {@code finalizeLevelRendering} would lose the world depth (Iris stops binding depthtex2 after
     * its final passes) and silently skip the lens while the world stayed at 1x.</p>
     */
    public static boolean needsIrisWorldDepthCopy() {
        Minecraft mc = Minecraft.getInstance();
        return IrisCompat.isUsingRenderPack() && shaderRerenderAllowed() && wantsPipPicture(mc);
    }

    /**
     * PIP may take this frame's FOV/lens path when Iris is not active, or when the player opted
     * into the post-final-composite Iris variant AND that variant is the one the final-overlay mixin
     * was bytecode-audited against. On unverified Iris lines the old whole-screen zoom stays on.
     * Stable per-frame fact, not a mid-frame capture outcome, so the world POV never oscillates
     * between whole-screen zoom and PIP while a shader pack is enabled.
     */
    /**
     * 光影下的二次渲染是否被允许：{@code ScopePipAllowShaderPacks} 显式 opt-in（默认 false
     * 的雷区不绕）且 final-overlay 钩子可用（Iris 1.11 门）。由 {@link ScopePipRerender} 的
     * 光影分支使用；与 {@link #irisCompatible()} 的差别是不含「光影未启用」这一恒真项。
     */
    public static boolean shaderRerenderAllowed() {
        return allowShaderPacks() && IrisCompat.supportsFinalScopeOverlay();
    }

    private static boolean irisCompatible() {
        return !IrisCompat.isUsingRenderPack()
                || (allowShaderPacks() && IrisCompat.supportsFinalScopeOverlay());
    }

    private static boolean debugNoComposite() {
        return RenderConfig.SCOPE_PIP_DEBUG_NO_COMPOSITE != null
                && RenderConfig.SCOPE_PIP_DEBUG_NO_COMPOSITE.get();
    }

    private static boolean debugPaintLens() {
        return RenderConfig.SCOPE_PIP_DEBUG_PAINT_LENS != null
                && RenderConfig.SCOPE_PIP_DEBUG_PAINT_LENS.get();
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
        return currentAimingProgress(Minecraft.getInstance(), partialTicks) > 0.0f;
    }

    private static float frameProgress(Minecraft mc) {
        return currentAimingProgress(mc, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    /** Same interpolated clock as the ocular. Keep the user's reveal floor distinct from FOV
     * ownership (> 0) and depth validity (only known after the hand). A floor of 1 allows full ADS. */
    private static boolean wantsPipPicture(Minecraft mc) {
        if (mc == null || !isEnabled() || currentZoom() < minMagnification()) return false;
        return pictureProgress(frameProgress(mc));
    }

    private static boolean pictureProgress(float progress) {
        return progress > 0.0f && progress >= minAimingProgress();
    }

    /**
     * Client-side interpolated aim progress for the local player (or the synced progress for a
     * non-local entity). Uses the same formula as {@code CameraSetupEvent.applyScopeMagnification}.
     */
    private static float currentAimingProgress(Minecraft mc, float partialTicks) {
        if (mc == null || mc.player == null) {
            return 0.0f;
        }
        ItemStack stack = KeepingItemRenderer.getRenderer().getCurrentItem();
        if (!(stack.getItem() instanceof IGun)) {
            return 0.0f;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(mc.player);
        if (operator == null) {
            IGunOperator entityOperator = IGunOperator.fromLivingEntity(mc.player);
            return entityOperator == null ? 0.0f : entityOperator.getSynAimingProgress();
        }
        return Mth.clamp(operator.getClientAimingProgress(partialTicks), 0.0f, 1.0f);
    }

    /**
     * Captures the fully-rendered world color before the hand/gun is drawn.
     *
     * <p>Called from {@code GameRenderer#renderItemInHand} HEAD. At that point the main target
     * already contains the whole world (the same target that Step 2 verified is readable), and the
     * gun has not been rasterized into it yet, so this copy is exactly the clean lens source.</p>
     */
    public static void captureScene(Minecraft mc) {
        if (!IrisCompat.isUsingRenderPack()) captureWideScene(mc, false);
    }

    /**
     * Captures the <b>finished</b> frame for the Iris path. Called at the end of
     * {@code IrisRenderingPipeline#finalizeLevelRendering()}: the main target at that instant
     * holds Iris' fully composited/tone-mapped frame, and the aperture region is already a clean
     * 1x world because the scope body was depth-clipped by the invisible ocular. Reprojecting this
     * frame therefore shows exactly the same world pixels that the player would see outside the
     * lens, with no pack-specific colortex guessing (the reference 26.2 branch proves this concept
     * for its own Iris pipeline; this port uses the same finished-frame property on 26.1.2).
     */
    public static void captureSceneAfterIrisFinal(Minecraft mc) {
        if (IrisCompat.supportsFinalScopeOverlay()) captureWideScene(mc, true);
    }

    private static void captureWideScene(Minecraft mc, boolean irisFinal) {
        boolean rerender = ScopePipRerender.rerenderMode();
        if (rerender && ScopePipRerender.hasScene()) return; // don't overwrite the narrow pass
        // A predicted pack-authored classic lens needs colour too, independent of the PIP switch.
        if (mc == null || failed || (ScopeLensFrame.expected == null &&
                (rerender || (irisFinal && !allowShaderPacks()) || !wantsPipPicture(mc)))) {
            sceneCaptured = false;
            return;
        }
        if (copyMainColor(mc) && !loggedCapture) {
            loggedCapture = true;
            GunMod.LOGGER.info("[TACZ Scope] Captured {}x{} {} for the lens.", sceneTarget().width(),
                    sceneTarget().height(), irisFinal ? "Iris finished frame" : "pre-hand world");
        }
    }

    /**
     * Copies the current main color texture into the reusable off-screen scene target. Shared by
     * the pre-hand (vanilla), post-final-composite (Iris) and the second-render
     * ({@link ScopePipRerender}) capture paths so the target sizing, format reuse and failure
     * handling stay identical.
     *
     * @return {@code true} on success, with {@code sceneCaptured} set accordingly.
     */
    public static boolean captureSceneFromMain(Minecraft mc) {
        return copyMainColor(mc);
    }

    /**
     * Copies the current main color texture into the reusable off-screen scene target. Shared by
     * the pre-hand (vanilla) and post-final-composite (Iris) capture paths so the target sizing,
     * format reuse and failure handling stay identical.
     *
     * @return {@code true} on success, with {@code sceneCaptured} set accordingly.
     */
    private static boolean copyMainColor(Minecraft mc) {
        var main = mc.getMainRenderTarget();
        if (main == null || main.getColorTexture() == null) {
            sceneCaptured = false;
            return false;
        }
        GpuTexture source = main.getColorTexture();
        if (source.isClosed()) {
            sceneCaptured = false;
            return false;
        }
        int width = source.getWidth(0);
        int height = source.getHeight(0);
        if (width <= 0 || height <= 0) {
            sceneCaptured = false;
            return false;
        }
        try {
            SceneColorTarget target = sceneTarget(width, height, source.getFormat());
            if (target == null || !target.copyFrom(source)) {
                sceneCaptured = false;
                logCaptureFailure();
                return false;
            }
            sceneCaptured = true;
            return true;
        } catch (Exception e) {
            failed = true;
            sceneCaptured = false;
            GunMod.LOGGER.error(
                    "[TACZ Scope] Step3 scene capture failed; PIP disabled, "
                            + "falling back to whole-screen FOV zoom.", e);
            return false;
        }
    }

    /** Composites the captured scene into the lens after the hand pass has finished. */
    public static void compositeAfterHand(Minecraft mc) {
        if (!IrisCompat.isUsingRenderPack()) compositeCurrentFrame(mc, false);
    }

    /** The finished shader frame precedes the queued reticle/rim. Never enter from the narrow pass. */
    public static void compositeAfterIrisFinal(Minecraft mc) {
        if (IrisCompat.supportsFinalScopeOverlay()) compositeCurrentFrame(mc, true);
    }

    private static void compositeCurrentFrame(Minecraft mc, boolean irisFinal) {
        if (mc == null || failed || !sceneCaptured || !ScopeDepthCopyState.hasMaskCycleThisFrame()) return;
        boolean rerender = ScopePipRerender.rerenderMode();
        boolean narrow = rerender && ScopePipRerender.hasScene(); // not last ADS's colour target
        boolean pip = isEnabled() && (!irisFinal || allowShaderPacks()) && currentZoom() >= minMagnification();
        if (pip && (!rerender || narrow)) {
            float progress = frameProgress(mc);
            if (!pictureProgress(progress)) return;
            float zoom = narrow ? ScopePipRerender.compositeZoom() : lensZoomAtProgress(progress);
            compositeScene(mc, zoom, irisFinal && !narrow && progress < 1.0f);
        } else if (ScopeLensFrame.current() != null) {
            // Below the PIP cutover (or no narrow pass): only a predicted AND observed pack lens
            // may use the wide capture at 1x. Do not turn ordinary classic sights into PIP.
            compositeScene(mc, 1.0f, false);
        }
    }

    private static void compositeScene(Minecraft mc, float compositeZoom, boolean guardFinishedSource) {
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
        if (debugNoComposite()) {
            // Diagnostic: capture path already proved the offscreen/lens plumbing; skip only the
            // composite so an overflow can be attributed to either capture or compositing.
            if (!loggedNoComposite) {
                loggedNoComposite = true;
                GunMod.LOGGER.info(
                        "[TACZ Scope] Step3 debug no-composite: captured {}x world but skipped "
                                + "the {}x lens draw.", (int) worldZoomTarget(), (int) compositeZoom);
            }
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
            // DynamicUniformStorage maps a GPU buffer. 26.1 forbids this while a pass is open.
            var lens = ScopeLensFrame.current();
            var heat = lens == null ? null : ScopeThermalCapture.current();
            var uniforms = RenderSystem.getDynamicUniforms().writeTransform(ScopeLensFrame.uniforms(lens, heat != null),
                    new Vector4f(compositeZoom, sharpness(), debugPaintLens() ? 1 : 0, guardFinishedSource ? 1 : 0),
                    new Vector3f(), new Matrix4f());
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "tacz_scope_pip_composite",
                    main.getColorTextureView(),
                    OptionalInt.empty())) {
                pass.setPipeline(pipeline(lens != null));
                pass.setUniform("DynamicTransforms", uniforms);
                if (lens != null) {
                    pass.bindTexture("HeatSampler", heat == null ? scene.view() : heat.getColorTextureView(),
                            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                }
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
                        "[TACZ Scope] Step3 composite painted the {}x lens from a {}-magnified world "
                                + "(total {}x; scene tex={}, world tex={}, aperture tex={}).",
                        (int) compositeZoom, (int) worldZoomTarget(), (int) currentZoom(),
                        scene.textureId(), world.textureId(), aperture.textureId());
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

    private static RenderPipeline pipeline(boolean lens) {
        RenderPipeline cached = lens ? lensPipeline : plainPipeline;
        if (cached != null) return cached;
        RenderPipeline source = RenderPipelines.ENTITY_OUTLINE_BLIT;
        var builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID,
                        lens ? "pipeline/scope_pip_composite_lens" : "pipeline/scope_pip_composite"))
                .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_pip"))
                .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withSampler(SCENE_SAMPLER_UNIFORM)
                .withSampler(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM)
                .withSampler(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_COLOR));
        if (lens) builder.withShaderDefine("TACZ_SCOPE_LENS").withSampler("HeatSampler");
        var pipeline = RenderPipelines.register(builder.build());
        if (lens) lensPipeline = pipeline; else plainPipeline = pipeline;
        return pipeline;
    }

    /**
     * A {@link GpuTextureView} over this frame's pre-ocular world depth copy (id-cached). For the
     * mesh-GPU hand body's outside-aperture clip, which binds the same live copies the composite
     * samples. Returns {@code null} when the handle is not available.
     */
    public static GpuTextureView worldDepthViewFor(ScopeDepthCopyState.DepthHandle handle) {
        if (!handle.available()) {
            return null;
        }
        return worldView(handle);
    }

    /** See {@link #worldDepthViewFor(ScopeDepthCopyState.DepthHandle)}. */
    public static GpuTextureView apertureDepthViewFor(ScopeDepthCopyState.DepthHandle handle) {
        if (!handle.available()) {
            return null;
        }
        return apertureView(handle);
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
            // 新画布 = 新代数：离屏纹理内容是未定义的，隔帧复用闸门（ScopePipRerender 的
            // interval）据此丢弃上一帧的镜内画面 —— 26.2 的 ScopePipTarget.generation()
            // 同一语义（比较代数而非引用：引用相等无法区分同对象与销毁后恰好复用同地址）。
            sceneTargetGeneration++;
        }
        return SceneColorTarget.instance;
    }

    private static int sceneTargetGeneration;

    /** 离屏镜内画布的重建代数：真正 new 过一次就 +1，供隔帧复用判断画面是否还躺在同一块纹理里。 */
    public static int sceneTargetGeneration() {
        return sceneTargetGeneration;
    }

    /**
     * A {@link GlTexture} that borrows an existing private depth texture. Never frees it.
     *
     * <p>26.1.2 的 {@code GlTexture} 构造器是
     * {@code (int usage, String label, TextureFormat, int width, int height, int depth, int layers, int glId)}
     * —— 末位参数直接赋给私有 {@code id} 字段（字节码核实），正是借用外部 GL id 的入口。</p>
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
