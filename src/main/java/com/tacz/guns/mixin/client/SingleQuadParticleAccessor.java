package com.tacz.guns.mixin.client;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 1.21.11 的 {@code SingleQuadParticle#setAlpha(float)} 是 {@code protected}
 * （26.1.2 上是 public，donor 的 {@code HitSurfaceParticleSpawner} 直调）。
 * 命中特效的自定义 alpha 只有这一个写入口，故用 {@code @Invoker} 桥接 ——
 * refmap 负责在混淆生产环境里映射到正确的方法名。
 */
@Mixin(SingleQuadParticle.class)
public interface SingleQuadParticleAccessor {
    @Invoker("setAlpha")
    void tacz$setAlpha(float alpha);
}
