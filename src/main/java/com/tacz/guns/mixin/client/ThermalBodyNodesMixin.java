package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SubmitNodeCollection.class)
abstract class ThermalBodyNodesMixin {
    @ModifyReceiver(method = {"submitModel", "submitItem", "submitBlockModel", "submitMovingBlock"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V"))
    private SimpleFeatureRenderPhase tacz$copySolidBody(SimpleFeatureRenderPhase phase, SubmitNode node) {
        ScopeThermalCapture.recordSolid(node);
        return phase;
    }

    // Vanilla's blending branch invokes the FeatureRenderPhase interface, not
    // SimpleFeatureRenderPhase. Iris can reroute it to the simple branch, which
    // hid this missing hook in the OpenGL test. Keep both invocation sites covered.
    @ModifyReceiver(method = {"submitModel", "submitItem", "submitBlockModel", "submitMovingBlock"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/FeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V"))
    private FeatureRenderPhase tacz$copyBlendedBody(FeatureRenderPhase phase, SubmitNode node) {
        // recordSolid retains the same depth-test and eligible-entity gates.
        ScopeThermalCapture.recordSolid(node);
        return phase;
    }
}
