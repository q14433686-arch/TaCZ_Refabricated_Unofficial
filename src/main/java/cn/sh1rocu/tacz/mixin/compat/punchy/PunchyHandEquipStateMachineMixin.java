package cn.sh1rocu.tacz.mixin.compat.punchy;

import com.tacz.guns.compat.firstperson.FirstPersonAnimationCompat;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feeds TACZ animated items through Punchy's supported blacklist transition path. */
@Pseudo
@Mixin(targets = "punchy.client.state.HandEquipStateMachine", remap = false)
public abstract class PunchyHandEquipStateMachineMixin {
    @Inject(method = "wasItemBlacklisted", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private void tacz$treatAnimatedItemAsBlacklisted(ItemStack stack,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (FirstPersonAnimationCompat.isTaczViewmodel(stack)) {
            cir.setReturnValue(true);
        }
    }
}
