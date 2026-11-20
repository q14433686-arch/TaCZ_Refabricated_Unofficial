package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SubmitNodeCollection.class)
abstract class ThermalBodyNodesMixin {
    @ModifyReceiver(method = {"submitModel", "submitItem", "submitBlockModel", "submitMovingBlock"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V"))
    private SimpleFeatureRenderPhase tacz$copySolidBody(SimpleFeatureRenderPhase phase, SubmitNode node) {
        // Outline, crumbling and water-mask draws also call SimpleFeatureRenderPhase.submit.
        if (phase == ((SubmitNodeCollection)(Object)this).solid) ScopeThermalCapture.recordSolid(node);
        return phase;
    }

    // 26.2 has a distinct translucent phase and node descriptor.
    @ModifyReceiver(method = {"submitModel", "submitItem", "submitBlockModel", "submitMovingBlock"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/TranslucentFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/TranslucentSubmit;)V"))
    private TranslucentFeatureRenderPhase tacz$copyBlendedBody(TranslucentFeatureRenderPhase phase, TranslucentSubmit node) {
        // recordSolid retains the same depth-test and eligible-entity gates.
        ScopeThermalCapture.recordSolid(node);
        return phase;
    }
}
