package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SubmitNodeCollection.class)
abstract class ThermalBodyNodesMixin {
    @WrapOperation(method = {"submitModel", "submitItem", "submitBlockModel", "submitMovingBlock"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V"))
    private void tacz$copySolidBody(SimpleFeatureRenderPhase phase, SubmitNode node, Operation<Void> original) {
        original.call(phase, node);
        if (phase == ((SubmitNodeCollection)(Object)this).solid) ScopeThermalCapture.recordSolid(node);
    }
}
