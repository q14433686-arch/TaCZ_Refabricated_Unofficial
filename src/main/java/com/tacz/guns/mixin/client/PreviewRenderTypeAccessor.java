package com.tacz.guns.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.class)
public interface PreviewRenderTypeAccessor {
    @Accessor("state") RenderSetup tacz$previewSetup();
}
