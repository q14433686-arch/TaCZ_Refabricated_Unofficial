package com.tacz.guns.test.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.particle.bedrock.particle.render.BillboardHelper;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.tacz.guns.test.MuzzleQuadProbe;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BillboardHelper.class, remap = false)
abstract class MuzzleVertexObserver {
    @Inject(method="vertex", at=@At("HEAD"))
    private static void vertex(VertexConsumer out, Matrix4f pose, Matrix3f normal, float x, float y, float z,
                               float u, float v, ParticleInstance p, int light, CallbackInfo ci) {
        MuzzleQuadProbe.vertex(p,x,y,z);
    }
}
