package com.tacz.guns.mixin.client;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.event.RenderItemInHandBobEvent;
import com.tacz.guns.api.client.event.RenderLevelBobEvent;
import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import com.tacz.guns.client.render.scope.ScopePipResourceProbe;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import com.tacz.guns.client.render.scope.ScopePipTrace;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.2 bob hooks using CameraRenderState signatures. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private Minecraft minecraft;

    /**
     * 二次渲染那一遍要用的三样东西，全部取自 GameRenderer 自己的字段。
     *
     * <p>不用 @Inject(locals = ...) 捕获 renderLevel 的局部变量：局部变量表随编译器与版本漂移，
     * 捕获式注入极脆。而这三个字段正是 vanilla 传给 LevelRenderer#render 的那几个实参的来源。</p>
     */
    @Shadow @Final private CrossFrameResourcePool resourcePool;
    @Shadow @Final private FogRenderer fogRenderer;
    @Shadow @Final private GameRenderState gameRenderState;

    @Unique
    private boolean tacz$renderingItemInHand;

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void tacz$beginHandPass(CameraRenderState cameraState,
                                    float partialTick,
                                    Matrix4fc projection,
                                    CallbackInfo ci) {
        this.tacz$renderingItemInHand = true;
        // renderAllFeatures 每帧被调用多次（世界一次、手持一次），
        // 瞄具只存在于手持那次。掩码必须只在那次绘制，否则世界那次会先把
        // target 清空，把手持那次的结果冲掉。
        ScopeMaskRenderer.setInHandPass(true);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void tacz$endHandPass(CameraRenderState cameraState,
                                  float partialTick,
                                  Matrix4fc projection,
                                  CallbackInfo ci) {
        this.tacz$renderingItemInHand = false;
        ScopeMaskRenderer.setInHandPass(false);
    }

    @Unique
    private boolean tacz$isItemInHandBobPass() {
        // Vanilla path: GameRenderer#renderItemInHand toggles tacz$renderingItemInHand.
        // Iris shader path: HandRenderer bypasses that method and calls ItemInHandRenderer directly,
        // so we must query Iris' own hand-pass flag via reflection.
        return this.tacz$renderingItemInHand || IrisCompat.isHandRendererActive();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void tacz$bobHurt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (minecraft.getCameraEntity() instanceof LocalPlayer player && !player.isDeadOrDying()) {
            float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            if (GunHurtBobTweak.onHurtBobTweak(player, poseStack, partialTick)) {
                ci.cancel();
                return;
            }
        }

        if (this.tacz$isItemInHandBobPass()) {
            RenderItemInHandBobEvent.BobHurt event = new RenderItemInHandBobEvent.BobHurt();
            RenderItemInHandBobEvent.HURT.invoker().post(event);
            if (event.isCanceled()) {
                ci.cancel();
            }
        } else {
            RenderLevelBobEvent.BobHurt event = new RenderLevelBobEvent.BobHurt();
            RenderLevelBobEvent.HURT.invoker().post(event);
            if (event.isCanceled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void tacz$bobView(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (this.tacz$isItemInHandBobPass()) {
            RenderItemInHandBobEvent.BobView event = new RenderItemInHandBobEvent.BobView();
            RenderItemInHandBobEvent.VIEW.invoker().post(event);
            if (event.isCanceled()) {
                ci.cancel();
            }
        } else {
            RenderLevelBobEvent.BobView event = new RenderLevelBobEvent.BobView();
            RenderLevelBobEvent.VIEW.invoker().post(event);
            if (event.isCanceled()) {
                ci.cancel();
            }
        }
    }

    /**
     * 【镜内画中画 · 抓取本帧世界画面】世界画完、视模开画之前，把主画面拷一份走。
     *
     * <h2>为什么正好是这一刻</h2>
     * 镜内那张图是主画面按倍率重采样来的，所以拷贝必须夹在一个很窄的窗口里：
     * <ul>
     *   <li>{@code LevelRenderer#render} 之<b>后</b> —— 早一点世界还没画完；</li>
     *   <li>{@code renderItemInHand} 之<b>前</b> —— 晚一点拷贝里就混进了枪和手，
     *       镜片里会出现一把缩小的枪。</li>
     * </ul>
     * renderLevel 偏移 405 之后、502 之前正是这个唯一窗口。
     *
     * <h2>这里不再重定向任何 target</h2>
     * 早前的版本在这个位置把 {@code mainRenderTarget()} 换成离屏 target、
     * 再跑一次 {@code LevelRenderer#render}。那条路<b>与 Sodium 不兼容</b>（实测）：
     * Sodium 接管地形渲染后根本不走 {@code mainRenderTarget()}，
     * 重定向只对原版路径的实体/粒子生效，镜内两套画面糊在一起；
     * 而一帧内两次驱动 {@code LevelRenderer#render} 还会打乱 Sodium 与
     * ImmediatelyFast 的逐帧状态，把镜外的实体也弄没。
     *
     * <p>现在只剩一次 {@code copyTextureToTexture} —— 只读最终颜色缓冲，
     * 那些像素是谁画的都无所谓，因此对任何地形渲染替换 mod 都天然兼容。</p>
     */
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void tacz$captureSceneForScopePip(DeltaTracker deltaTracker, CallbackInfo ci) {
        ScopePipTrace.mark("VANILLA LevelRenderer#render END (anything after this draws over the finished world)");
        ScopePipRenderer.captureScene(this.minecraft);
        // 【光影路径】Iris 把手部渲染搬进了 LevelRenderer#render 内部，所以此刻整条
        // Iris 管线已经收工，主 target 里是最终画面 —— 直接在它上面做镜内放大。
        // 无光影时这一句立即返回（合成仍在阶段边界完成，那里才能让准星盖在 PIP 之上）。
        ScopePipRenderer.compositeAfterLevelUnderShaders();
    }

    /**
     * 【二次渲染模式】用窄 FOV 把世界再画一遍，插在 vanilla 主世界渲染<b>之前</b>。
     *
     * <p>与上面的拷贝注入点刻意<b>相反</b>（BEFORE 而不是 AFTER），因为两种模式的约束相反：
     * <ul>
     *   <li>拷贝要 AFTER —— 世界得先画完才有得拷；</li>
     *   <li>二次渲染要 BEFORE —— 让 vanilla 那一遍<b>收尾</b>，
     *       把我们可能污染到的共享状态覆盖回去。最典型的是
     *       {@code LevelRenderer.entityOutlineTarget}：它是字段持有、尺寸跟窗口走，
     *       而发光描边 post chain 按 {@code mainRenderTarget()} 的尺寸入帧图；
     *       我们重定向期间写进去的那份，会被 {@code renderLevel} 返回后的
     *       {@code doEntityOutline()}（GameRenderer 偏移 275）贴到真实主画面上。
     *       跑在前面，vanilla 随后就把它覆盖了。</li>
     * </ul>
     * 两个注入点各自判断模式，同一时刻只有一个会真正做事。
     *
     * <p>注入点选 {@code INVOKE + BEFORE} 而不是方法 HEAD：投影矩阵（偏移 291-303）
     * 与雾缓冲（316-338）都在那之后、这次调用之前才准备好，而我们两样都要用。</p>
     */
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void tacz$renderScopePipView(DeltaTracker deltaTracker, CallbackInfo ci) {
        ScopePipRenderer.renderScopeView(this.minecraft, this.resourcePool,
                this.fogRenderer, this.gameRenderState, deltaTracker);
        // 紧接着就是 vanilla 那一遍。有了这个界标，日志里「谁在什么阶段解析了哪个 target」
        // 就能一眼分段：镜内那一遍 / vanilla 那一遍 / 之后。
        ScopePipTrace.mark("VANILLA LevelRenderer#render BEGIN (its clear pass wipes the main target)");
    }

    /**
     * 【二次渲染模式 · 输出重定向】那一遍期间，把主 target 换成离屏 target。
     *
     * <p>Sodium 的地形输出<b>也</b>走这个方法（{@code TerrainRenderPass} 字节码实读），
     * 所以一处注入就能把原版路径与 Sodium 地形一起带走。
     * 第一版之所以镜内地形没跟着放大，不是这里没生效，而是 Sodium 的<b>投影</b>
     * 另有一份快照 —— 那条由 {@code SodiumCompat} 负责。</p>
     *
     * <p>刻意注入<b>方法</b>而不是 {@code @Accessor} 改
     * {@code private final mainRenderTarget} 字段：final 字段可能被 JIT 常量折叠，
     * 也容易被别处缓存住引用，而所有调用方取 target 走的都是这个 public 方法。</p>
     *
     * <p>{@link ScopePipRenderer#redirectTarget()} 只在那一次调用的 try/finally
     * 窗口内返回非 null，其余任何时候都返回 null，所以对不开二次渲染的玩家
     * 这就是一次 null 判断。</p>
     */
    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void tacz$redirectMainRenderTarget(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget scopeTarget = ScopePipRenderer.redirectTarget();
        // 【诊断】这里是所有渲染目标解析的必经之路 —— Sodium 的 TerrainRenderPass、
        // 经由它的 Voxy、vanilla 的 clear lambda、帧图导入，全都从这过。
        // 打开 ScopePipDebugTrace 就能看清「镜内那一遍期间谁还在解析真正的主 target」。
        if (ScopePipTrace.enabled()) {
            ScopePipTrace.targetResolved(scopeTarget, scopeTarget != null);
        }
        if (scopeTarget != null) {
            cir.setReturnValue(scopeTarget);
        }
    }

    /**
     * 每帧唯一的「瞄具帧状态归零」点。
     *
     * <p>接在 {@code extract} 的 HEAD 上，因为 {@code Minecraft#runTick} 的顺序是
     * <b>extract（偏移 441）→ render（偏移 520）</b> —— 这是本帧最早、且一定会执行到的位置，
     * 于是本帧所有消费者（{@code extract} 里的 FOV 事件、{@code renderLevel} 里的镜内抓取、
     * 手部 pass 里的合成）看到的都是同一份定义明确的状态。</p>
     *
     * <p>绝不能放在手部 pass 里归零：Iris 的 {@code HandRenderer} 一帧调用两次
     * {@code renderAllFeatures}（{@code renderSolid} 与 {@code renderTranslucent}，
     * 两次 {@code ACTIVE} 都为 true），第二次会把第一次的结果抹掉，
     * 导致光影下 PIP 永远看不到掩码。详见 {@code ScopeMaskRenderer#maskDrawnThisFrame}。</p>
     */
    @Inject(method = "extract", at = @At("HEAD"))
    private void tacz$beginScopeFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        IrisCompat.beginFrame();
        ScopePipRenderer.beginFrame();
        ScopeMaskRenderer.beginFrame();
        // poly_mesh GPU 绘制表：与掩码同点归零，一帧内单调累积、手部 pass 消费。
        PolyMeshGpuRenderer.beginFrame();
        // 瞄具那套 Iris 管线在这里预热：extract 在世界渲染之前，不在任何 render pass 内，
        // 也不在镜内那一遍里 —— 是做「编译整份 shaderpack」这种重活的唯一安全位置。
        // 懒加载的话它会落在第一次开镜的那一帧中途，既卡顿又会在帧中途重置全局帧计数。
        ScopePipRenderer.prewarmShaderPipelineIfNeeded();
        // 【诊断】GPU 纹理字节数脉冲探针（默认关）。
        ScopePipResourceProbe.beginFrame();
        ScopePipTrace.beginFrame();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void tacz$renderTickStart(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        RenderTickEvent.EVENT.invoker().onRenderTick(new RenderTickEvent(
                RenderTickEvent.Phase.START,
                deltaTracker.getGameTimeDeltaPartialTick(false)
        ));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void tacz$renderTickEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        RenderTickEvent.EVENT.invoker().onRenderTick(new RenderTickEvent(
                RenderTickEvent.Phase.END,
                deltaTracker.getGameTimeDeltaPartialTick(false)
        ));
    }
}
