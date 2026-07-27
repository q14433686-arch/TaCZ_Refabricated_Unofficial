package com.tacz.guns.mixin.client;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.event.RenderItemInHandBobEvent;
import com.tacz.guns.api.client.event.RenderLevelBobEvent;
import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void tacz$bobHurt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (minecraft.getCameraEntity() instanceof LocalPlayer player && !player.isDeadOrDying()) {
            float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            if (GunHurtBobTweak.onHurtBobTweak(player, poseStack, partialTick)) {
                ci.cancel();
                return;
            }
        }

        if (this.tacz$renderingItemInHand) {
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
        if (this.tacz$renderingItemInHand) {
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
}
