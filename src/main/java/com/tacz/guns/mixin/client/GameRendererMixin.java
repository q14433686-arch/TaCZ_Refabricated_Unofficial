package com.tacz.guns.mixin.client;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.event.RenderItemInHandBobEvent;
import com.tacz.guns.api.client.event.RenderLevelBobEvent;
import com.tacz.guns.client.render.scope.ScopeFinalOverlayState;
import com.tacz.guns.client.render.scope.ScopePipDepthDebug;
import com.tacz.guns.client.render.scope.ScopePipRenderState;
import com.tacz.guns.client.render.scope.ScopePipRerender;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.2 bob hooks using CameraRenderState signatures. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private Minecraft minecraft;

    @Unique
    private boolean tacz$renderingItemInHand;

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void tacz$beginHandPass(CameraRenderState cameraState,
                                    float partialTick,
                                    Matrix4fc projection,
                                    CallbackInfo ci) {
        this.tacz$renderingItemInHand = true;
        // poly_mesh GPU：进入 vanilla 手部 pass —— shouldSubmitGpu 据此只收
        // 第一人称手部骨骼，避免世界/GUI 泄漏（关 PR WORLD_DRAWS 的坑）。
        // 注意 renderItemInHand 开头还有一次「清空遗留 world 几何」的 renderAllFeatures
        // 预 flush（26.1.2 字节码 @9-@22）：它发生在本标志置位之后，因此被
        // PolyMeshGpuRenderer#renderAtWorldFlush 的 inHandPass 门正确拒收，不会误记
        // 世界钩子的存活证明。
        PolyMeshGpuRenderer.setInHandPass(true);
        // Step 3 (real PIP): before the gun/hand is drawn, copy the already-rendered world color
        // into a private off-screen target. The lens will later sample this so no gun/hand appears
        // inside it. No-op unless the config toggle or -Dtacz.scope.pip.enable is on.
        ScopePipRenderState.captureScene(this.minecraft);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void tacz$endHandPass(CameraRenderState cameraState,
                                  float partialTick,
                                  Matrix4fc projection,
                                  CallbackInfo ci) {
        this.tacz$renderingItemInHand = false;
        // poly_mesh GPU 的绘制不在此处：26.1.2 与 1.21.11 同构，手部几何在
        // ItemInHandRenderer#renderHandsWithItems 末尾自己 flush（26.1.2 字节码实测：
        // 该方法尾部是 getFeatureRenderDispatcher().renderAllFeatures() +
        // mc.renderBuffers().bufferSource().endBatch()，之后才回到本方法做 popMatrix），
        // 所以 GPU 骨骼必须画进那次 flush 的紧后 ——
        // 见 ItemInHandRendererMixin#tacz$drawMeshGpuAfterHandFeatureFlush。在 RETURN 这里
        // ModelView 已被还原、目标覆写已退出，光影下那次 flush 也已经过去，
        // 画在这里只会得到「位置恒定」或「整把枪消失」。
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

    @Inject(method = "render", at = @At("HEAD"))
    private void tacz$renderTickStart(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        // poly_mesh GPU：帧首归零绘制表 + 检测光影开关翻转（烘焙世代号）+ 释放延迟释放池。
        // 必须先于 RenderTickEvent 分发：ShaderStateTracker（START 相位）会把当帧光影状态
        // 交给 PolyRenderPolicy 缓存，两者的先后要与 1211 分支一致。
        PolyMeshGpuRenderer.beginFrame();
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
     * poly_mesh 世界 GPU：把「本帧正在跑世界渲染」这件事告诉渲染器（世界表的消费点
     * 挂在 {@code FeatureRenderDispatcher#renderAllFeatures} 的 RETURN 上，那个 API 是公开的、
     * GUI 侧也会调用，必须圈定语境才安全）。
     *
     * <p><b>26.1.2 注入点实测（本地 merged jar 字节码，2026-09-01）</b>：26.1.2 的
     * {@code GameRenderer#renderLevel(DeltaTracker)} 内部对
     * {@code LevelRenderer.renderLevel(GraphicsResourceAllocator, DeltaTracker, boolean,
     * CameraRenderState, Matrix4fc, GpuBufferSlice, Vector4f, boolean, ChunkSectionsToRender)}
     * 只有 <b>一次</b> 调用（@412），世界那一次 feature flush 在同方法尾部 @570
     * （renderItemInHand @517 → ScreenEffectRenderer → renderAllFeatures @570 → endBatch @575）。
     * 1211 分支用于镜内 PIP 二次渲染的 {@code ScopePipRerender} 已随 PIP 深度线移植恢复：
     * 先跑窄 FOV 的镜内那遍并拷走成品，再原样直通宽 FOV 的 vanilla 那遍覆盖主目标；
     * 特性关闭时 {@link ScopePipRerender#renderScopeView} 自身在闸门处返回，等价于零开销直通。</p>
     *
     * <p>1211 用 @Redirect 的先例已验证可行（Iris 26.1 的 {@code MixinGameRenderer}
     * 对 {@code renderLevel} 只有 TAIL 的 @Inject、对 {@code renderItemInHand} 里
     * {@code renderHandsWithItems} 的调用做 @Redirect——与本注入点互不相碰，源码核实）。</p>
     */
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V"))
    private void tacz$scopeRenderLevel(LevelRenderer levelRenderer,
                                       GraphicsResourceAllocator allocator,
                                       DeltaTracker deltaTracker,
                                       boolean blockOutline,
                                       CameraRenderState cameraState,
                                       Matrix4fc viewMatrix,
                                       GpuBufferSlice fogBuffer,
                                       Vector4f fogColor,
                                       boolean renderSky,
                                       ChunkSectionsToRender chunkSectionsToRender) {
        // try/finally：世界渲染中途抛异常也不能把标志卡在 true —— 卡住的标志会让
        // GUI 侧的 renderAllFeatures 调用（GuiEntityRenderer 等）误入世界表消费点。
        PolyMeshGpuRenderer.setLevelRenderActive(true);
        try {
            // 镜内二次渲染（PIP B1）：默认关闭；开启且闸门全过时先用窄 FOV 画一遍世界
            // 并拷走成品，然后 vanilla 那遍宽 FOV 重画覆盖主目标。
            ScopePipRerender.renderScopeView(levelRenderer, allocator, deltaTracker, blockOutline,
                    cameraState, viewMatrix, fogBuffer, fogColor, renderSky, chunkSectionsToRender);
            levelRenderer.renderLevel(allocator, deltaTracker, blockOutline, cameraState,
                    viewMatrix, fogBuffer, fogColor, renderSky, chunkSectionsToRender);
        } finally {
            PolyMeshGpuRenderer.setLevelRenderActive(false);
        }
    }
}
