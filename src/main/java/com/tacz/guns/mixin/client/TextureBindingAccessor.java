package com.tacz.guns.mixin.client;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读 {@code RenderSetup.TextureBinding#location}（1.21.11 里它是 {@code record TextureBinding(Identifier location, Supplier<GpuSampler>)}）。
 */
@Mixin(targets = "net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding")
public interface TextureBindingAccessor {
    @Accessor("location")
    Identifier tacz$location();
}
