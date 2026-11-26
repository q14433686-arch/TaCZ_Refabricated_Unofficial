package com.tacz.probe.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface NativeMouse {
    @Invoker("onButton") void platform$mouse(long window,MouseButtonInfo info,int action);
}
