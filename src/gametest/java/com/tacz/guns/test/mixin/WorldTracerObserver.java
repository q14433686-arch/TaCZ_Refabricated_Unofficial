package com.tacz.guns.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.entity.EntityBulletRenderer;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.test.WorldTracerSmoke;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityBulletRenderer.class, remap = false)
abstract class WorldTracerObserver {
    @Inject(method = "renderTracerAmmo", at = @At("HEAD"))
    private void entry(EntityKineticBullet bullet, float[] color, float tick, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        WorldTracerSmoke.begin(bullet, tick, pose);
    }
    @Inject(method = "renderTracerAmmo", at = @At("RETURN"))
    private void exit(CallbackInfo ci) { WorldTracerSmoke.end(); }
}
