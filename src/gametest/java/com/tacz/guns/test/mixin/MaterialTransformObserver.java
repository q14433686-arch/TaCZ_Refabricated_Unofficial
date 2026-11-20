package com.tacz.guns.test.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Observe the UV transform on the selected render setup, not the name of its shader. */
@Mixin(RenderSetup.class)
public interface MaterialTransformObserver {
    @Accessor("textureTransform") TextureTransform taczTest$textureTransform();
}
