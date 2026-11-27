package com.tacz.probe.mixin;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native fields must be refmap-remapped, not looked up with named reflection. */
@Mixin(SingleQuadParticle.class)
public interface ImpactColorView {
    @Accessor("rCol") float platform$red();
    @Accessor("gCol") float platform$green();
    @Accessor("bCol") float platform$blue();
}
