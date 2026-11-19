package com.tacz.guns.test.mixin;

import com.tacz.guns.block.entity.DisplayRackBlockEntity;
import com.tacz.guns.client.renderer.block.DisplayRackRenderer;
import com.tacz.guns.test.RackLoadSmoke;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=DisplayRackRenderer.class,remap=false)
abstract class RackExtractionTimer {
    private static final String METHOD="extractRenderState(Lcom/tacz/guns/block/entity/DisplayRackBlockEntity;Lcom/tacz/guns/client/renderer/block/DisplayRackRenderer$State;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V";
    @Inject(method=METHOD,at=@At("HEAD")) private void start(DisplayRackBlockEntity b,DisplayRackRenderer.State state,float tick,Vec3 eye,ModelFeatureRenderer.CrumblingOverlay o,CallbackInfo ci){RackLoadSmoke.start(b.getBlockPos());}
    @Inject(method=METHOD,at=@At("RETURN")) private void end(CallbackInfo ci){RackLoadSmoke.end();}
}
