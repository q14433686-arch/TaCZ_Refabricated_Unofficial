package com.tacz.guns.test.mixin;

import com.tacz.guns.client.model.papi.PapiManager;
import com.tacz.guns.client.resource.pojo.display.gun.Rangefinder;
import com.tacz.guns.test.RangefinderSmoke;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PapiManager.class, remap = false)
abstract class RangeTextObserver {
    @Inject(method = "getTextShow(Ljava/lang/String;Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/client/resource/pojo/display/gun/Rangefinder;)Ljava/lang/String;", at = @At("RETURN"))
    private static void observeHeldText(String key, ItemStack stack, Rangefinder config, CallbackInfoReturnable<String> cir) {
        if (config != null && key.contains("%rangefinder%")) RangefinderSmoke.observe(cir.getReturnValue());
    }
}
