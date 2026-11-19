package com.tacz.guns.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.class)
public interface ThermalRenderTypeAccessor {
    @Accessor("state") RenderSetup tacz$thermalSetup();
}
