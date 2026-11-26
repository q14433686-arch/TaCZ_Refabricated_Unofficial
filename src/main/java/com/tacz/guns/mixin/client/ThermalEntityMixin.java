package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tacz.guns.client.particle.NativeMuzzleEffects;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把"这条渲染状态属于哪个实体"带给 1.2.0 的原生枪口特效（{@link NativeMuzzleEffects}）。
 *
 * <p>26.2 里同一个 mixin（{@code ThermalEntityMixin}）还负责热成像：在
 * {@code extractEntity} 处读 {@code thermal_imaging/*} 标签、在 {@code submit} 外面开/关
 * "记录热源实体"的开关。热成像本轮<b>未移植</b>到 1.21.11（它依赖 26.2 的
 * {@code SubmitNodeCollection} phase 拆分与 {@code RenderPipeline}/{@code BindGroupLayout}
 * 那一代 GPU 描述，本线要重写才能跑；详见
 * {@code docs/lineage/PORT_262_120_TO_12111_STATUS.md}），所以这里只剩枪口特效那半边。</p>
 */
@Mixin(EntityRenderDispatcher.class)
abstract class ThermalEntityMixin {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void tacz$readMuzzleOwner(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        NativeMuzzleEffects.extracted(entity, cir.getReturnValue());
    }

    @WrapMethod(method = "submit")
    private void tacz$muzzleOwnerScope(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                       PoseStack pose, SubmitNodeCollector out, Operation<Void> original) {
        int muzzleOwner = NativeMuzzleEffects.beginEntity(state);
        try {
            original.call(state, camera, x, y, z, pose, out);
        } finally {
            NativeMuzzleEffects.endEntity(muzzleOwner);
        }
    }
}
