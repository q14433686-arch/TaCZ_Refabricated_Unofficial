package com.tacz.guns.mixin.client;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.event.RenderItemInHandBobEvent;
import com.tacz.guns.api.client.event.RenderLevelBobEvent;
import com.tacz.guns.client.render.scope.ScopeFinalOverlayState;
import com.tacz.guns.client.render.scope.ScopePipDepthDebug;
import com.tacz.guns.client.render.scope.ScopePipRenderState;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
