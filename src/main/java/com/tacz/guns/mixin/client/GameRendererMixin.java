package com.tacz.guns.mixin.client;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.event.RenderItemInHandBobEvent;
import com.tacz.guns.api.client.event.RenderLevelBobEvent;
import com.tacz.guns.client.render.scope.ScopeDepthCopyState;
import com.tacz.guns.client.render.scope.ScopeFinalOverlayState;
import com.tacz.guns.client.render.scope.ScopePipDepthDebug;
import com.tacz.guns.client.render.scope.ScopePipRenderState;
import com.tacz.guns.client.render.scope.ScopePipRerender;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.11 bob / hand-pass hooks. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private Minecraft minecraft;

    @Unique
    private boolean tacz$renderingItemInHand;

    // 1.21.11: renderItemInHand(float partialTick, boolean renderHand, Matrix4f projection)
    // 26.1 的签名是 (CameraRenderState, float, Matrix4fc)——多了 state、少了 boolean、
    // 且是 Matrix4fc 接口而非 Matrix4f 实现类。三处都要跟着改（javap 核实）。
    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void tacz$beginHandPass(float partialTick,
                                    boolean renderHand,
                                    Matrix4f projection,
                                    CallbackInfo ci) {
        this.tacz$renderingItemInHand = true;
        // poly_mesh GPU：进入 vanilla 手部 pass —— shouldSubmitGpu 据此只收
        // 第一人称手部骨骼，避免世界/GUI 泄漏（关 PR WORLD_DRAWS 的坑）。
        PolyMeshGpuRenderer.setInHandPass(true);
        // Step 3 (real PIP): before the gun/hand is drawn, copy the already-rendered world color
        // into a private off-screen target. The lens will later sample this so no gun/hand appears
        // inside it. No-op unless -Dtacz.scope.pip.enable=true.
        ScopePipRenderState.captureScene(this.minecraft);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void tacz$endHandPass(float partialTick,
                                  boolean renderHand,
                                  Matrix4f projection,
                                  CallbackInfo ci) {
        this.tacz$renderingItemInHand = false;
        // poly_mesh GPU 的绘制不在此处：1.21.11 的手部几何是在
        // ItemInHandRenderer#renderHandsWithItems 末尾自己 flush 的（不是延迟到
        // renderLevel 末尾），所以 GPU 骨骼必须画进那次 flush 的紧后 ——
        // 见 ItemInHandRendererMixin#tacz$drawMeshGpuAfterHandFeatureFlush。在这里的
        // ModelView 已被还原、目标覆写已退出，
        // 光影下那次 flush 也已经过去，画在这里只会得到「位置恒定」或「整把枪消失」。
        PolyMeshGpuRenderer.setInHandPass(false);
        // Step 3 (real PIP): after the hand pass the aperture/world depth copies are complete, so
        // paste the captured pre-hand world into the lens at the scope zoom. Step 2's magenta
        // diagnostic is deferred to later so the two never overwrite the same pixels.
        ScopePipRenderState.compositeAfterHand(this.minecraft);
        // When the PIP lens is active the normal solid-pass reticle and ocular shade were already
        // covered by the composite. The scope submitted them through ScopeFinalOverlayState instead,
        // so flush that overlay NOW, after the lens, restoring the physical order: picture, then
        // crosshair, then shade. The method no-ops when nothing was queued, and it is only reached
        // on the vanilla path here (Iris drives its own post-composite flush and PIP is skipped there).
        // hasPendingOverlay() also guards the transient where the reticle/rim were queued a moment
        // before isEnabled() was re-evaluated (for example during a slow aim transition), so nothing
        // stays stranded under the lens. The whole flush is vanilla-only: under a shader pack Iris
        // drives its own post-final-composite flush (IrisFinalScopeOverlayMixin), and flushing from
        // renderItemInHand would draw the reticle/rim before Iris' composite passes.
        if (!IrisCompat.isUsingRenderPack()
                && (ScopeFinalOverlayState.hasPendingOverlay()
                || ScopePipRenderState.isEnabled())) {
            ScopeFinalOverlayState.renderAfterFinalComposite();
        }
        // Step 2 (depth PIP diagnostic): paint the lens magenta when the debug system property is
        // set and Step 3 is not active. No-op in normal play; Iris paths are skipped by the debug.
        ScopePipDepthDebug.renderAfterHand(this.minecraft);
    }

    @Unique
    private boolean tacz$isItemInHandBobPass() {
        // Vanilla path: GameRenderer#renderItemInHand toggles tacz$renderingItemInHand.
        // Iris shader path: HandRenderer bypasses that method and calls ItemInHandRenderer directly,
        // so we must query Iris' own hand-pass flag via reflection.
        return this.tacz$renderingItemInHand || IrisCompat.isHandRendererActive();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    // 1.21.11: bobHurt(PoseStack, float partialTick)；26.1 是 (CameraRenderState, PoseStack)。
    // partialTick 由形参直接给出，比原先从 DeltaTracker 现取更准。
    private void tacz$bobHurt(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (minecraft.getCameraEntity() instanceof LocalPlayer player && !player.isDeadOrDying()) {
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
    // 1.21.11: bobView(PoseStack, float partialTick)；26.1 是 (CameraRenderState, PoseStack)。
    private void tacz$bobView(PoseStack poseStack, float partialTick, CallbackInfo ci) {
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
     * 镜内二次渲染（ScopePipRerender）的注入点：vanilla 在 {@code renderLevel} 里只调一次
     * {@code LevelRenderer#renderLevel(...)}，这里先跑窄 FOV 的镜内那遍并拷走成品，
     * 再原样直通宽 FOV 的 vanilla 那遍覆盖主目标。关着该特性时等价于零开销直通。
     *
     * <p>1.21.11 的 10 参签名（javap 核实）：{@code renderLevel(GraphicsResourceAllocator,
     * DeltaTracker, boolean, Camera, Matrix4f, Matrix4f, Matrix4f, GpuBufferSlice, Vector4f,
     * boolean)}；三个矩阵依次是视图旋转、投影、裁剪投影，倒数第二是雾缓冲、最后两个参数
     * 是雾颜色与 renderSky。</p>
     */
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
    private void tacz$scopeRenderLevel(LevelRenderer levelRenderer,
                                       GraphicsResourceAllocator allocator,
                                       DeltaTracker deltaTracker,
                                       boolean blockOutline,
                                       Camera camera,
                                       Matrix4f viewMatrix,
                                       Matrix4f projectionMatrix,
                                       Matrix4f cullingMatrix,
                                       GpuBufferSlice fogBuffer,
                                       Vector4f fogColor,
                                       boolean renderSky) {
        // poly_mesh 世界 GPU：把「本帧正在跑世界渲染」这件事告诉渲染器（世界表的消费点
        // 挂在 renderAllFeatures 的 RETURN 上，那个 API 是公开的，必须圈定语境才安全）。
        // try/finally：世界渲染中途抛异常也不能把标志卡在 true。
        PolyMeshGpuRenderer.setLevelRenderActive(true);
        try {
            ScopePipRerender.renderScopeView(levelRenderer, allocator, deltaTracker, blockOutline,
                    camera, viewMatrix, projectionMatrix, cullingMatrix, fogBuffer, fogColor, renderSky);
            levelRenderer.renderLevel(allocator, deltaTracker, blockOutline, camera,
                    viewMatrix, projectionMatrix, cullingMatrix, fogBuffer, fogColor, renderSky);
        } finally {
            PolyMeshGpuRenderer.setLevelRenderActive(false);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void tacz$renderTickStart(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        // poly_mesh GPU：帧首归零绘制表 + 检测光影开关翻转（烘焙世代号）。
        PolyMeshGpuRenderer.beginFrame();
        // 目镜掩码周期的帧戳推进（ScopeDepthCopyState#onClientFrameStart）：帧计数 +1，
        // 「本帧/上一帧有无掩码周期」的时效查询以此为基准。不清 maskValid —— 终局叠加
        // 在本帧手部阶段之前还要用它。
        ScopeDepthCopyState.onClientFrameStart();
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

    /**
     * 26.1 的 {@code Camera#calculateFov} / {@code calculateHudFov} 在 1.21.11 不存在；
     * 这一版 FOV 统一由 {@code GameRenderer#getFov(Camera,float,boolean)} 计算，
     * 布尔参 true=世界 FOV、false=手部/HUD FOV
     * （字节码确认：只有 true 分支会读 options.fov() 并乘 fovModifier）。
     * 因此两个事件在这里按该参数分派，语义与 26.1.2 一致。
     */
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private float tacz$modifyFov(float original, Camera camera, float partialTick, boolean useFovSetting) {
        ViewportEvent.ComputeFov event =
                new ViewportEvent.ComputeFov(camera, partialTick, useFovSetting, original);
        ViewportEvent.FOV.invoker().onComputeFov(event);
        return (float) event.getFOV();
    }
}
