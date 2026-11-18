package com.tacz.guns.api.client.animation;

import net.minecraft.resources.Identifier;

/** Immutable request; locator is resolved against the animated model, never a live deferred pose. */
public record AnimationParticleEvent(Identifier effect, String locator, String preEffectScript) {}
