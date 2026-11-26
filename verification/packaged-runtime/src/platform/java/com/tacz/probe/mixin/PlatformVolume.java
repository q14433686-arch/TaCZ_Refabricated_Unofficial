package com.tacz.probe.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SoundEngine.class)
public interface PlatformVolume {
    @Invoker("calculateVolume") float platform$volume(SoundInstance sound);
}
