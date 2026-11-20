package com.tacz.guns.mixin.client;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.client.event.RenderItemInHandBobEvent;
import com.tacz.guns.api.client.event.RenderLevelBobEvent;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.render.scope.ScopeFinalOverlayState;
import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import com.tacz.guns.client.render.scope.ScopePipResourceProbe;
import com.tacz.guns.client.render.scope.ScopePipTrace;
import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private FogRenderer fogRenderer;

    @Shadow
    @Final
    private GameRenderState gameRenderState;

    @Unique
    private boolean tacz$renderingItemInHand;

    @Inject(method = "extract", at = @At("HEAD"))
    private void tacz$beginScopeFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        IrisCompat.beginFrame();
        ScopeMaskRenderer.beginFrame();
        ScopePipRenderer.beginFrame();
        PolyMeshGpuRenderer.beginFrame();
        ScopeFinalOverlayState.beginFrame();
        ScopePipRenderer.prewarmShaderPipelineIfNeeded();
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
        PolyMeshGpuRenderer.setLevelRenderActive(true);
        try {
            ChunkSectionsToRender effectiveChunks = ScopePipRenderer.renderScopeView(
                    this.minecraft,
                    allocator,
                    this.fogRenderer,
                    this.gameRenderState,
                    deltaTracker,
                    chunkSectionsToRender);

            ScopePipTrace.mark("VANILLA LevelRenderer#renderLevel BEGIN (its clear pass wipes the main target)");
            ScopeThermalCapture.beginWorldPass();
            try {
                levelRenderer.renderLevel(
                        allocator,
                        deltaTracker,
                        blockOutline,
                        cameraState,
                        viewMatrix,
                        fogBuffer,
                        fogColor,
                        renderSky,
                        effectiveChunks);
            } finally {
                ScopePipTrace.mark("VANILLA LevelRenderer#renderLevel END (anything after this draws over the finished world)");
            }

            ScopePipRenderer.captureScene(this.minecraft);
            ScopePipRenderer.compositeAfterLevelUnderShaders();
            ScopeFinalOverlayState.renderAfterLevelComposite();
        } finally {
            PolyMeshGpuRenderer.setLevelRenderActive(false);
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void tacz$beginHandPass(CameraRenderState cameraState,
                                    float partialTick,
                                    Matrix4fc projection,
                                    CallbackInfo ci) {
        this.tacz$renderingItemInHand = true;
        PolyMeshGpuRenderer.setInHandPass(true);
        ScopeMaskRenderer.setInHandPass(true);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void tacz$endHandPass(CameraRenderState cameraState,
                                  float partialTick,
                                  Matrix4fc projection,
                                  CallbackInfo ci) {
        this.tacz$renderingItemInHand = false;
        PolyMeshGpuRenderer.setInHandPass(false);
        ScopeMaskRenderer.setInHandPass(false);
    }

    @Unique
    private boolean tacz$isItemInHandBobPass() {
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

    @ModifyArg(method = "bobHurt", at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 2), index = 0)
    private float tacz$heldGunHurtTilt(float angle) {
        if (minecraft.options.getCameraType().isFirstPerson()
                && minecraft.getCameraEntity() instanceof LocalPlayer player && !player.isDeadOrDying()
                && player.getMainHandItem().getItem() instanceof AbstractGunItem) {
            return GunHurtBobTweak.heldGunTilt(angle, player.getHurtDir(), RenderConfig.GUN_HURT_BOB_MULTIPLIER.get());
        }
        return angle;
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
}
