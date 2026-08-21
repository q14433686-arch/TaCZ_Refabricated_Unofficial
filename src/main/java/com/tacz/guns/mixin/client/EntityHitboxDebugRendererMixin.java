package com.tacz.guns.mixin.client;

import com.tacz.guns.client.event.RenderHeadShotAABB;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11 的实体碰撞箱改由 {@link EntityHitboxDebugRenderer} 在 per-frame
 * GizmoCollector 里发射。爆头范围必须挂在同一条路径上，不能再走
 * {@code SubmitNodeCollector#submitCustomGeometry(RenderTypes.lines())}。
 */
@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {
    @Inject(method = "showHitboxes(Lnet/minecraft/world/entity/Entity;FZ)V", at = @At("TAIL"))
    private void tacz$emitHeadShotHitbox(Entity entity, float partialTick, boolean inLocalServer, CallbackInfo ci) {
        RenderHeadShotAABB.emitGizmo(entity, partialTick, inLocalServer);
    }
}
