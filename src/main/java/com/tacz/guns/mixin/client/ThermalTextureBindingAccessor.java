package com.tacz.guns.mixin.client;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding")
public interface ThermalTextureBindingAccessor {
    @Accessor("location") Identifier tacz$thermalTexture();
}
