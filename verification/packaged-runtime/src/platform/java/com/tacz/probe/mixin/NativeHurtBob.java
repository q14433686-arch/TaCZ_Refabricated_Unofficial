package com.tacz.probe.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface NativeHurtBob {
    @Invoker("bobHurt") void platform$hurt(PoseStack pose,float partialTick);
}
