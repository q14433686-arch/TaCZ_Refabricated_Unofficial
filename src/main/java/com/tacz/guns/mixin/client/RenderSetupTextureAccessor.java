package com.tacz.guns.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** 读 {@code RenderSetup#textures}（采样器名 → {@code TextureBinding}）。 */
@Mixin(RenderSetup.class)
public interface RenderSetupTextureAccessor {
    @Accessor("textures")
    Map<String, ?> tacz$textures();
}
