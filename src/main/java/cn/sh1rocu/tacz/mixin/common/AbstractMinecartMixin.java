package cn.sh1rocu.tacz.mixin.common;

import cn.sh1rocu.tacz.api.extension.IMinecart;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecart.class)
public class AbstractMinecartMixin {
    @Inject(method = "isRideable", at = @At("HEAD"), cancellable = true)
    private void tacz$canBeRidden(CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof IMinecart minecart)
            cir.setReturnValue(minecart.tacz$canBeRidden());
    }
}
