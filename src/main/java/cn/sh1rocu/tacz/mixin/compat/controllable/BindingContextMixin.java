package cn.sh1rocu.tacz.mixin.compat.controllable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mrcrayfish.controllable.client.binding.context.BindingContext;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.compat.controllable.ControllableInner;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Restricts TACZ's controller context to frames where the local player is holding a gun. */
@Pseudo
@Mixin(value = BindingContext.class, remap = false)
public abstract class BindingContextMixin {
    @ModifyReturnValue(method = "isActive", at = @At("RETURN"))
    private boolean tacz$controllableCompat(boolean original) {
        BindingContext context = BindingContext.class.cast(this);
        if (!(context instanceof ControllableInner.GunKeyConflict)) {
            return original;
        }
        var player = Minecraft.getInstance().player;
        return player != null && original && IGun.mainHandHoldGun(player);
    }
}
