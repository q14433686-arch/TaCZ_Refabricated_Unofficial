package com.tacz.guns.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Projection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla hand projection for projected readouts. For masks use the CPU mirror of the
 * actual bound projection, including when Iris renders its own hand pass. */
@Mixin(GameRenderer.class)
public interface GameRendererProjectionAccessor {
    @Accessor("hudProjection")
    Projection tacz$getHudProjection();

}
