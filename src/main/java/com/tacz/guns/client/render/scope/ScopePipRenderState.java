package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
 *   <li><b>Vanilla / no shader pack by default.</b> Iris is skipped unless
 *       {@code ScopePipAllowShaderPacks} is on, because the pre-hand capture point is not valid
 *       under a shader pack. When allowed, the capture moves to the end of Iris'
 *       {@code finalizeLevelRendering()} ({@link #captureAndCompositeAfterIrisFinal}) where the
 *       main target already contains iris' finished frame; the aperture is already clean world
 *       because the scope body is clipped, so the same screen-space reprojection works there.</li>
 *   <li><b>Configurable.</b> {@code RenderConfig.SCOPE_PIP_*} exposes enable / minimum progress /
 *       minimum magnification / world-zoom share / sharpness / allow shader packs / debug-paint in
 *       the ModMenu config screen; the legacy dev JVM property
 *       {@code -Dtacz.scope.pip.enable=true} still works as an override.</li>
 *   <li><b>No Catmull-Rom.</b> Hardware bilinear + a configurable 5-tap unsharp mask only.</li>
 * </ul>
 *
 * <h2>Whole-screen FOV zoom replacement</h2>
 * {@link #suppressesWorldFovZoom(float)} is consulted by {@code CameraSetupEvent#applyScopeMagnification}
 * so the camera does <b>not</b> apply the old full-screen zoom; instead the caller applies
 * {@code ScopePipWorldZoomShare} of it (0 = world stays 1×, 1 = full whole-screen zoom) and the lens
 * shows the {@code Z}-magnified re-projection. If PIP fails or is disabled this returns false and the
 * existing whole-screen FOV zoom resumes unchanged.
 */
public final class ScopePipRenderState {
    public static final String ENABLE_PROPERTY = "tacz.scope.pip.enable";
    private static final String SCENE_SAMPLER_UNIFORM = "tacz_SceneColorSampler";
    private static final float DEFAULT_MIN_AIMING_PROGRESS = 0.05f;
    /**
     * PIP 合成（二次渲染与重投影同一条）在开镜滑入中开始显示的进度阈。引擎层接管
     * （窄遍/捕获/预热/世界 FOV 让位）在开镜瞬间（进度 &gt; 0）启动；本阈只决定贴片
     * 何时上屏：避开开/退镜边界「镜孔骑在髋部枪身上」的那一段——那时把镜内画面
     * 贴进镜孔，读起来就是「枪身上闪现的截图」（实机 2026-09-01）。滑入过 1/3 即显示
     * （用户裁定：重投影也做开镜即接管，过渡期镜内无画面像 bug；2026-09-01）。
     *
     * <p>滑入中段的「放大镜环残影」（实机 2026-09-01 复现：重投影采样的成品帧中心区
     * 含滑入中的镜环）由 {@link #revealRampedReprojectionZoom} 的倍率渐变处理：
     * 显示阈之上倍率从 1（恒等贴回 = 镜孔区域的干净 1x 世界）步进到稳态。</p>
     */
    private static final float PIP_REVEAL_THRESHOLD = 0.35f;
    /**
     * 重投影倍率渐变的步数。每次步进一次管线构建（常驻注册有界 ≤ 步数+1 次/每稳态
     * 倍率），步间复用同一管线 —— 不是逐帧重建（26.2 母版的 ColorModulator 逐帧
     * uniform 在 26.1.2 的 RenderPass API 上未经验证，见旧注释存档）。
     */
    private static final int REVEAL_RAMP_STEPS = 10;

    /**
     * 重投影滑入渐变：显示阈之上从 1（恒等贴回 = 镜孔自身区域的原画面，即透过物理镜
     * 看到的 1x 世界——干净无残影）线性步进到稳态倍率。实机确认的「放大镜环残影」
     * 病根是固定全倍率采样：滑入中段成品帧的中心区含正在滑入的镜环，放大后贴进镜孔。
     * 倍率从 1 起步时采样区=整个屏幕、且贴回位置=镜孔自身，内容就是孔里原有的画面；
     * 随滑入推进倍率爬升、采样区收缩向中心，镜环同时归位——到稳态时采样区恰在
     * 镜孔内（稳态无残影已被实机证明）。26.2 母版实机观感的来源即此渐变。
     */
    private static float revealRampedReprojectionZoom(float steadyZoom, float progress) {
        float steady = Math.max(1.0f, steadyZoom);
        float t = (progress - PIP_REVEAL_THRESHOLD) / (1.0f - PIP_REVEAL_THRESHOLD);
        if (t >= 1.0f) {
            return steady;
        }
        if (t <= 0.0f) {
            return 1.0f;
        }
        int k = Math.round(t * REVEAL_RAMP_STEPS);
        if (k >= REVEAL_RAMP_STEPS) {
            return steady;
        }
        return 1.0f + (steady - 1.0f) * (k / (float) REVEAL_RAMP_STEPS);
    }

    /**
     * 按参数缓存的合成管线（键 = 倍率/锐度/贴镜开关）。倍率渐变会在会话内产生
     * ≤ 步数+1 个倍率档 × 每把枪的稳态倍率，注册数有界；查表命中则零构建。
     */
    private static final java.util.Map<Long, RenderPipeline> PIPELINES = new java.util.HashMap<>();
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
     * <p>This deliberately uses the <b>same stable per-frame gate</b> as {@link #suppressesWorldFovZoom}
     * rather than {@link #sceneCaptured}. {@code sceneCaptured} is written at the hand-pass HEAD and
     * is the input to the composite, so gating the reticle/rim on it is normally consistent. But the
     * submit of the scope model can run before that flag is set on a frame where the capture fails or
     * the model is submitted through a path that bypasses {@code captureScene}; in that case the
     * reticle/rim stay in the ordinary solid pass and the composite at hand-pass RETURN covers them
     * exactly as reported. The stable gate answers "is PIP taking over the FOV this frame", which is
     * the same condition under which a lens picture is guaranteed to be composited at the end of the
     * hand pass, so deferring under it cannot leave the reticle/rim stranded in the wrong layer.</p>
     */
    public static boolean shouldDeferReticleOverlay() {
        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return isEnabled() && irisCompatible()
                && currentZoom() >= minMagnification() && isAimingStarted(partialTicks);
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
     * 开镜时 mesh 距离闸门的<b>角尺寸补偿</b>（对齐 26.2
     * {@code ScopePipRenderer.currentDetailZoom()}）：{@code 1 + (magnification-1) * progress}，
     * 随开镜进度渐变、收镜自动回 1，经典整屏变焦与 PIP 都适用。
     *
     * <p>由 {@code PolyRenderPolicy.detailZoom()} 消费：两道 mesh 距离闸门按裸眼调参，
     * 但开镜把远处物体的角尺寸放大了 Z 倍，所以阈值必须乘上这个系数，否则举镜看到的
     * 掉落物/第三人称 mesh 枪永远是未烘焙立方体。</p>
     */
    public static float currentDetailZoom() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return 1.0f;
        }
        float progress = currentAimingProgress(mc, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
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

    /** 低于该倍率的瞄具不走 PIP，回落到旧整屏变焦。 */
    private static float minMagnification() {
        return RenderConfig.SCOPE_PIP_MIN_MAGNIFICATION == null
                ? 4.0f
                : RenderConfig.SCOPE_PIP_MIN_MAGNIFICATION.get().floatValue();
    }

    /**
     * 低倍镜（含组合镜当前低倍档）是否<b>保留</b>镜内前景不裁剪。
     *
     * <p>与 PIP 的 {@code minMagnification()} 共用同一阈值：低于它（默认 4×）时瞄具走
     * 经典整屏变焦，镜内孔径对枪身/火光/手臂的「孔外剔除」同样应当关闭 —— 否则低倍镜会
     * 出现「整屏只有镜片中心一小块、而手/火光却在孔内整块消失」的违和观感。组合镜按
     * {@code IGun#getAimingZoom} 读到的<b>当前档位</b>判定，切到低倍档自动回不裁剪。</p>
     */
    public static boolean shouldClipViewmodelForeground() {
        return currentZoom() >= minMagnification();
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
        float zoom = currentZoom();
        float world = worldZoomTarget();
        return world <= 0.0f ? Math.max(1.0f, zoom) : Math.max(1.0f, zoom / world);
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
     * 光影下是否具备跑镜内二次渲染的条件：玩家显式 opt-in <b>且</b> Iris 的终局钩子真在
     * （{@code supportsFinalScopeOverlay()}）。后者不是可选项：终局钩子不可用时窄遍画出来的东西
     * 没有合成点能上屏，结果就是"世界让位了、镜内还是 1x"——所以放行与预热都必须看这条，
     * 不能只看配置键（本线此前只看键，已对齐）。
     */
    public static boolean shaderRerenderAllowed() {
        return allowShaderPacks() && IrisCompat.supportsFinalScopeOverlay();
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
        if (!isEnabled() || failed || !IrisCompat.isUsingRenderPack()
                || !allowShaderPacks() || !IrisCompat.supportsFinalScopeOverlay()) {
            return false;
        }
        if (currentZoom() < minMagnification()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return false;
        }
        float progress = currentAimingProgress(mc,
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        // 与合成显示阈同一条（PIP_REVEAL_THRESHOLD）：低于阈值的帧合成不上屏，
        // 但强制拷贝必须提前就绪——合成显示的第一帧就要有 world 深度可用。
        return progress >= PIP_REVEAL_THRESHOLD;
    }

    /**
     * PIP may take this frame's FOV/lens path when Iris is not active, or when the player opted
     * into the post-final-composite Iris variant AND that variant is the one the final-overlay mixin
     * was bytecode-audited against. On unverified Iris lines the old whole-screen zoom stays on.
     * Stable per-frame fact, not a mid-frame capture outcome, so the world POV never oscillates
     * between whole-screen zoom and PIP while a shader pack is enabled.
     */
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
        return currentAimingProgress(mc, 0.0f) > minAimingProgress();
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
        if (ScopePipRerender.rerenderMode()) {
            // 二次渲染模式：镜内画面由 ScopePipRerender.renderScopeView 在 renderLevel 的
            // 镜内那遍之后拷好。这里再拷一次会用宽视场的手前画面覆盖窄视场成品。
            return;
        }
        if (!isEnabled() || failed || mc == null) {
            sceneCaptured = false;
            return;
        }
        if (IrisCompat.isUsingRenderPack()) {
            sceneCaptured = false;
            return;
        }
        if (currentZoom() < minMagnification()) {
            // Low-power scopes keep the classic whole-screen zoom; see ScopePipMinMagnification.
            sceneCaptured = false;
            return;
        }
        if (!isAiming(mc)) {
            sceneCaptured = false;
            return;
        }
        if (!copyMainColor(mc)) {
            return;
        }
        if (!loggedCapture) {
            loggedCapture = true;
            GunMod.LOGGER.info(
                    "[TACZ Scope] Step3 captured a {}x{} clean pre-hand world for {}x PIP.",
                    sceneTarget().width(), sceneTarget().height(), (int) currentZoom());
        }
    }

    /**
     * Captures the <b>finished</b> frame for the Iris path. Called at the end of
     * {@code IrisRenderingPipeline#finalizeLevelRendering()}: the main target at that instant
     * holds Iris' fully composited/tone-mapped frame, and the aperture region is already a clean
     * 1x world because the scope body was depth-clipped by the invisible ocular. Reprojecting this
     * frame therefore shows exactly the same world pixels that the player would see outside the
     * lens, with no pack-specific colortex guessing (the reference 26.2 branch proves this concept
     * for its own Iris pipeline; ours uses the same finished-frame property on 1.21.11).
     */
    public static void captureSceneAfterIrisFinal(Minecraft mc) {
        if (ScopePipRerender.rerenderMode()) {
            // 二次渲染模式（无光影/光影同理）：镜内画面由 ScopePipRerender 在窄遍返回后立刻拷好。
            // 这里再拷一次会用宽视场的成品帧覆盖窄视场成品 —— 与 captureScene 的守卫同款；
            // 而"镜内画面变成 1x 主画面"就是这个覆盖的直接表现（实机 2026-09-01：镜内外均 1X）。
            // 刻意不清 sceneCaptured：紧随其后的 compositeAfterIrisFinal 还要拿它合成镜内画面。
            return;
        }
        if (!isEnabled() || failed || mc == null) {
            sceneCaptured = false;
            return;
        }
        if (!IrisCompat.isUsingRenderPack() || !allowShaderPacks()
                || !IrisCompat.supportsFinalScopeOverlay()) {
            sceneCaptured = false;
            return;
        }
        if (currentZoom() < minMagnification()) {
            sceneCaptured = false;
            return;
        }
        // 捕获与合成必须同阈（PIP_REVEAL_THRESHOLD）：合成已按用户裁定提前接管
        // （2026-09-01：重投影也做开镜即接管，过渡期镜内无画面像 bug），捕获若仍压在
        // 全 ADS，显示的第一帧就没有成品帧可贴。捕获本身无害（只是拷贝），
        // 何时上屏由合成的同一条阈决定。
        float progress = currentAimingProgress(mc,
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (progress < PIP_REVEAL_THRESHOLD) {
            sceneCaptured = false;
            return;
        }
        if (copyMainColor(mc)) {
            if (!loggedCapture) {
                loggedCapture = true;
                GunMod.LOGGER.info(
                        "[TACZ Scope] Step3 captured a {}x{} Iris finished frame for {}x PIP.",
                        sceneTarget().width(), sceneTarget().height(), (int) currentZoom());
            }
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
        if (!isEnabled() || failed || mc == null) {
            return;
        }
        if (IrisCompat.isUsingRenderPack()) {
            // Iris owns the output path here; compositeAfterIrisFinal does the work after the final
            // composite. The vanilla hand-pass RETURN never sees a shader-pack frame.
            return;
        }
        // 二次渲染模式镜内画面已按窄 FOV 画好（倍率 1），重投影模式才需要 lensZoom()。
        // 二次渲染的 sceneCaptured（本类字段）由 renderLevel 里的窄 FOV 那遍写入，退出开镜后
        // 无人把它清回 false —— 若继续用它做闸门，会拿上一帧残留的镜内贴片逐帧合成到屏幕上
        // （退出后屏幕空间残留一块贴片）。必须改看每帧在 renderScopeView 顶部重置的
        // ScopePipRerender.hasScene()。
        if (ScopePipRerender.rerenderMode()) {
            if (!ScopePipRerender.hasScene()) {
                return;
            }
            // 开/退镜边界闪一下（实机 2026-09-01）：见 PIP_REVEAL_THRESHOLD。
            // （历史备注：被打断的会话轮曾把全 ADS 门扩大到二次渲染（b9f9db7），把
            // 「一帧截图」与「过渡期贴片」混为一谈——前者已被掩码周期帧戳闸根治，
            // 后者按用户现行裁定（开镜即接管、母版实机行为优先）只避开髋部段。）
            if (currentAimingProgress(mc, mc.getDeltaTracker()
                    .getGameTimeDeltaPartialTick(false)) < PIP_REVEAL_THRESHOLD) {
                return;
            }
        } else if (!sceneCaptured) {
            return;
        }
        // 掩码周期时效：本合成点在手部阶段之后，深度拷贝应是本帧的。周期不落在当前帧
        // （瞄具本帧没提交过目镜序列）时，手头的拷贝就是上一段开镜的遗留——贴片位置由
        // 遗留镜孔决定，正是「一帧截图」的病根，宁可整帧不画镜内画面（fail-closed）。
        if (!ScopeDepthCopyState.hasMaskCycleThisFrame()) {
            return;
        }
        compositeScene(mc, revealZoom(compositeZoom(), mc));
    }

    /** 合成上屏倍率：二次渲染恒 1（窄 FOV 真画，无残影问题）；重投影按滑入进度渐变
     * （{@link #revealRampedReprojectionZoom}）。 */
    private static float revealZoom(float steady, Minecraft mc) {
        if (ScopePipRerender.rerenderMode()) {
            return steady;
        }
        float progress = currentAimingProgress(mc,
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        return revealRampedReprojectionZoom(steady, progress);
    }

    /**
     * Composites the captured finished frame into the lens after Iris' final composite.
     *
     * <p>Called by {@code IrisFinalScopeOverlayMixin} right after
     * {@link #captureSceneAfterIrisFinal(Minecraft)} and before
     * {@link ScopeFinalOverlayState#renderAfterFinalComposite()}, restoring the physical order under
     * a shader pack: finished shader frame -> magnified lens picture -> reticle/crosshair -> ocular
     * shade. The aperture/world depth copies used by the mask were made during the hand pass and are
     * private GL textures, so they remain sampleable after Iris has finished binding depthtex2.</p>
     */
    public static void compositeAfterIrisFinal(Minecraft mc) {
        if (!isEnabled() || failed || mc == null) {
            return;
        }
        if (ScopePipRerender.rerenderMode() && !ScopePipRerender.hasScene()) {
            // 二次渲染模式：改看每帧在 renderScopeView 顶部重置的 hasScene()。本类的
            // sceneCaptured 在窄遍停跑后无人清回 false（与 compositeAfterHand 同一滞留问题）
            // —— 若用它做闸门，退出开镜后会把 scene target 里上一帧的旧画面作为最上层
            // 贴片逐帧合成（实机：开关镜触发、静止不动的「截图」贴屏，2026-09-01）。
            // 上一轮的「开镜即接管」拆掉了全 ADS 门这道意外的护栏，此病遂显形。
            return;
        }
        if (!sceneCaptured) {
            return;
        }
        // 一帧「截图」贴屏（实机 2026-09-01）：掩码周期失败的帧里，深度拷贝纹理仍是
        // 上一段开镜的遗留（handle 依旧 available），合成若照跑就把镜内画面按遗留镜孔
        // 的位置贴出去——随机位置闪现一帧、下一帧周期恢复即自愈。只允许「本帧确有完整
        // 周期」时合成：Iris 26.1 把手部搬进了 LevelRenderer#renderLevel 内部（本仓
        // 世界钩子 javadoc 的字节码结论），finalizeLevelRendering 与本合成因此跑在
        // 同一 Level 遍的手部阶段之后，拷贝是本帧的；周期失败的帧 fail-closed ——
        // 宁可一帧不画镜内画面，不贴陈旧截图。（曾误判「终局钩子在手部之前」而用
        // 上一帧闸 hadMaskCycleLastFrame，导致光影下合成永不放行——二次渲染整体失效。）
        if (!ScopeDepthCopyState.hasMaskCycleThisFrame()) {
            return;
        }
        if (!IrisCompat.isUsingRenderPack() || !allowShaderPacks()
                || !IrisCompat.supportsFinalScopeOverlay()) {
            return;
        }
        // Under a shader pack the FINISHED frame includes the gun/hands; the screen-space
        // reprojection therefore reveals via PIP_REVEAL_THRESHOLD and ramps its zoom through
        // revealRampedReprojectionZoom (identity paste first, full zoom at steady state). The
        // ramp is quantized to REVEAL_RAMP_STEPS pipeline builds, not per-frame rebuilds: the
        // 26.2 reference used a per-frame ColorModulator uniform, which our 26.1.2 RenderPass
        // API has never verified; quantized #define pipelines are the verified equivalent.
        float progress = currentAimingProgress(mc,
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        // 显示阈两种模式同一条（PIP_REVEAL_THRESHOLD）。用户裁定（2026-09-01）：
        // 重投影也做开镜即接管——过渡期镜内没有画面、到全 ADS 才突然出现，读起来
        // 就像 bug。已知折衷见字段注释（滑入中段成品帧中心含枪身，镜缘可能有放大
        // 镜环残影）。引擎层接管（窄遍/捕获/预热/FOV 让位）自始就在开镜瞬间。
        if (progress < PIP_REVEAL_THRESHOLD) {
            return;
        }
        // 倍率分流：重投影=整屏重投影的 lensZoom() 按滑入渐变（残影修复，见
        // revealRampedReprojectionZoom）；二次渲染（含光影）=窄 FOV 真画恒 1。
        float steady = compositeZoom();
        float reveal = ScopePipRerender.rerenderMode()
                ? steady
                : revealRampedReprojectionZoom(steady, progress);
        compositeScene(mc, reveal);
    }

    /** Shared composite body (vanilla and Iris call sites). */
    private static void compositeScene(Minecraft mc) {
        compositeScene(mc, lensZoom());
    }

    /**
     * 合成倍率：二次渲染模式下镜内画面已是窄 FOV 真画（屏幕坐标一一对应），倍率恒 1；
     * 重投影模式则是 {@link #lensZoom()}（世界放大后镜内只需再补的那一份）。
     */
    private static float compositeZoom() {
        return ScopePipRerender.rerenderMode() ? ScopePipRerender.compositeZoom() : lensZoom();
    }

    /** Composite body with an explicit lens zoom (the Iris path reuses the same stable zoom value). */
    private static void compositeScene(Minecraft mc, float compositeZoom) {
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
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "tacz_scope_pip_composite",
                    main.getColorTextureView(),
                    OptionalInt.empty())) {
                pass.setPipeline(pipeline(compositeZoom));
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

    private static RenderPipeline pipeline(float lensZoomValue) {
        float clampedZoom = Math.max(1.0f, lensZoomValue);
        int lensZoom1k = (int) Math.round(clampedZoom * 1000.0f);
        int sharpness1k = (int) Math.round(sharpness() * 1000.0f);
        boolean paintLens = debugPaintLens();
        long cacheKey = (lensZoom1k & 0xFFFFFL)
                | ((sharpness1k & 0x3FFL) << 20)
                | (paintLens ? 1L << 30 : 0L);
        RenderPipeline cachedPipeline = PIPELINES.get(cacheKey);
        if (cachedPipeline != null) {
            return cachedPipeline;
        }
        RenderPipeline pipeline;
        {
            RenderPipeline source = RenderPipelines.ENTITY_OUTLINE_BLIT;
            pipeline = RenderPipelines.register(
                    RenderPipeline.builder()
                            .withLocation(Identifier.fromNamespaceAndPath(
                                    GunMod.MOD_ID, "pipeline/scope_pip_composite"))
                            .withVertexShader(Identifier.fromNamespaceAndPath(
                                    "minecraft", "core/screenquad"))
                            .withFragmentShader(Identifier.fromNamespaceAndPath(
                                    GunMod.MOD_ID, "core/scope_pip"))
                            .withShaderDefine("TACZ_PIP_ZOOM", clampedZoom)
                            .withShaderDefine("TACZ_PIP_SHARPNESS", sharpness())
                            .withShaderDefine("TACZ_PIP_PAINT_LENS", paintLens ? 1.0f : 0.0f)
                            .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
                            .withSampler(SCENE_SAMPLER_UNIFORM)
                            .withSampler(ScopeDepthCopyState.MASK_WORLD_SAMPLER_UNIFORM)
                            .withSampler(ScopeDepthCopyState.APERTURE_SAMPLER_UNIFORM)
                            .withCull(false)
                            .withoutBlend()
                            .withColorWrite(true)
                            // No depth in/out: this is a pure screen-space overwrite inside the lens.
                            .build());
            PIPELINES.put(cacheKey, pipeline);
        }
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
    private static int sceneTargetGeneration;

    /** 离屏镜内画布的重建代数：真正 new 过一次就 +1，供隔帧复用判断画面是否还有效。 */
    public static int sceneTargetGeneration() {
        return sceneTargetGeneration;
    }

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
                // 新画布 = 新代数：离屏纹理内容是未定义的，隔帧复用闸门（ScopePipRerender 的 interval）据此
                // 丢弃"上一帧"的镜内画面。26.2 的 ScopePipTarget.generation() 同一语义（比代数不比引用：
                // 引用相等区分不了"同一个对象"与"销毁后恰好复用同一实例"）。
                sceneTargetGeneration++;
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
