package com.tacz.guns.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla hand projection for projected readouts. For masks use the CPU mirror of the
 * actual bound projection, including when Iris renders its own hand pass. */
@Mixin(GameRenderer.class)
public interface GameRendererProjectionAccessor {
    @Accessor("hudProjection")
    Projection tacz$getHudProjection();

    /**
     * 取 {@code GameRenderer#fogRenderer}：掩码 pass 需要它的
     * {@code getBuffer(FogMode.NONE)}（全零颜色、起止 = MAX_VALUE 的「空雾」UBO），
     * 把 {@code core/position.fsh} 里的 {@code apply_fog} 彻底关掉。
     * 见 {@code ScopeMaskRenderer#drawMask}。
     */
    @Accessor("fogRenderer")
    FogRenderer tacz$getFogRenderer();
}
