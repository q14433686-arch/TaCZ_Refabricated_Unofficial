package com.tacz.guns.mixin.client;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 产出相机角度事件（TACZ 的后坐力/晃动靠它）。
 *
 * <h2>1.21.11 与 26.1.2 的差异</h2>
 * 26.1 把 FOV 计算搬进了 {@code Camera}，于是有
 * {@code calculateFov} / {@code calculateHudFov} / {@code update(DeltaTracker)} 三个方法。
 * 1.21.11 的 {@code Camera} <b>这三个全都没有</b>（javap 对 merged jar 逐一确认）：
 * <ul>
 *   <li>FOV 仍在 {@code GameRenderer#getFov(Camera,float,boolean)}，
 *       布尔参 true=世界 FOV、false=手部/HUD FOV
 *       （字节码确认：true 分支才乘 {@code fovModifier}）。
 *       因此两个 FOV 事件改由 {@link GameRendererMixin} 在那一个方法上产出；</li>
 *   <li>相机更新入口是
 *       {@code Camera#setup(Level, Entity, boolean detached, boolean thirdPersonReverse, float partialTick)}，
 *       对应 26.1 的 {@code update}。partialTick 由形参直接给出，
 *       不必再走 26.1 才有的 {@code getCameraEntityPartialTicks(DeltaTracker)}。</li>
 * </ul>
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void tacz$applyCameraAnimations(net.minecraft.world.level.Level level,
                                            net.minecraft.world.entity.Entity entity,
                                            boolean detached,
                                            boolean thirdPersonReverse,
                                            float partialTick,
                                            CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        ViewportEvent.ComputeCameraAngles event = new ViewportEvent.ComputeCameraAngles(
                self, partialTick, self.yRot(), self.xRot(), 0.0F
        );
        ViewportEvent.CAMERA.invoker().onComputeCameraAngles(event);
        this.setRotation(event.getYaw(), event.getPitch());
        if (event.getRoll() != 0.0F) {
            self.rotation().mul(Axis.ZP.rotationDegrees(event.getRoll()));
        }
    }
}
