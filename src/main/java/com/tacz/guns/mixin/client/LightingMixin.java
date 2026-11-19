package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.platform.Lighting;
import com.tacz.guns.client.model.bedrock.UnshadedVertices;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RenderPearl exposes the selected UBO slice, not its floats. Keep the primary normal for
 * each entry of this Lighting instance when vanilla uploads it, including UI and Nether. */
@Mixin(Lighting.class)
abstract class LightingMixin {
    @Unique private UnshadedVertices.Normal[] tacz$normals;

    @Inject(method = "updateBuffer", at = @At("HEAD"))
    private void tacz$remember(Lighting.Entry entry, Vector3fc first, Vector3fc second, CallbackInfo ci) {
        if (tacz$normals == null) tacz$normals = new UnshadedVertices.Normal[Lighting.Entry.values().length];
        tacz$normals[entry.ordinal()] = UnshadedVertices.Normal.fromLight(first);
    }

    @Inject(method = "setupFor", at = @At("RETURN"))
    private void tacz$select(Lighting.Entry entry, CallbackInfo ci) {
        UnshadedVertices.select(tacz$normals == null ? null : tacz$normals[entry.ordinal()]);
    }
}
