package com.tacz.guns.test.mixin;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ScopeThermalCapture.class)
public interface CurrentHeatTarget {
    @Invoker("current") static TextureTarget get() { throw new AssertionError(); }
}
