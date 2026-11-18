package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SubmitNodeCollection.class)
abstract class ThermalBodyNodesMixin {
    @ModifyReceiver(method = {"submitModel", "submitItem", "submitBlockModel", "submitMovingBlock"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V"))
    private SimpleFeatureRenderPhase tacz$copySolidBody(SimpleFeatureRenderPhase phase, SubmitNode node) {
        // Eligible living bodies may use a translucent phase (notably slimes).
        // recordSolid still rejects no-depth/see-through model materials.
        ScopeThermalCapture.recordSolid(node);
        return phase;
    }
}
