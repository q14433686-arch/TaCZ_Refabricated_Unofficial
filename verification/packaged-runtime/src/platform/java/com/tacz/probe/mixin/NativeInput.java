package com.tacz.probe.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface NativeInput {
    @Invoker("keyPress") void platform$key(long window,int action,KeyEvent key);
}

