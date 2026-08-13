package cn.sh1rocu.tacz.mixin.client;

import cn.sh1rocu.tacz.api.event.RenderLivingEvent;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    // 1.21.11: LivingEntityRenderer 没有 render(LivingEntity,LivingEntityRenderState,float)，
    // 该阶段在这一版叫 extractRenderState(T,S,float)（javap 确认，26.1 同名同签名）。
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    public void tacz$onPostEvent(LivingEntity entity, LivingEntityRenderState renderState, float partialTick, CallbackInfo ci) {
        var event = new RenderLivingEvent.Post(entity, (LivingEntityRenderer) (Object) this, partialTick, renderState);
        RenderLivingEvent.POST.invoker().post(event);
    }


    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("TAIL"))
    public void tacz$onSubmitPostEvent(LivingEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        var event = new RenderLivingEvent.Post(null, (LivingEntityRenderer) (Object) this, 0, renderState, poseStack, collector, cameraRenderState);
        RenderLivingEvent.POST.invoker().post(event);
    }
}
