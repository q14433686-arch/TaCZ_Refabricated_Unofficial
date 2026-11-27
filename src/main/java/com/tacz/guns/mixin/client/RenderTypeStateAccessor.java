package com.tacz.guns.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读 {@code RenderType#state}（1.21.11 的字段名就是 {@code state}，类型 {@code RenderSetup}）。
 *
 * <p>用途：给定一个 vanilla {@code RenderType} 实例，反查它绑的贴图，从而为 1.2.0 的
 * "发光材质"（{@link com.tacz.guns.client.model.bedrock.IlluminatedMaterials}）构造同贴图、
 * 但走自建发光管线的对应类型。26.2 的同类 accessor 叫 {@code ThermalRenderTypeAccessor}
 * （当时只有热成像在用）；本线两个消费者，故按用途改名。</p>
 */
@Mixin(RenderType.class)
public interface RenderTypeStateAccessor {
    @Accessor("state")
    RenderSetup tacz$renderSetup();
}
