package com.tacz.guns.client.model.papi;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

public class AmmoCountPapi implements Function<ItemStack, String> {
    public static final String NAME = "ammo_count";

    @Override
    public String apply(ItemStack stack) {
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun != null) {
            Identifier gunId = iGun.getGunId(stack);
            ClientGunIndex gunIndex = TimelessAPI.getClientGunIndex(gunId).orElse(null);
            if (gunIndex == null) {
                return "";
            }
            var data = gunIndex.getGunData();
            if (data == null) return "";
            var player = net.minecraft.client.Minecraft.getInstance().player;
            int ammoCount = iGun.useInventoryAmmo(stack) && player != null && player.getMainHandItem() == stack
                    ? com.tacz.guns.client.gameplay.ClientAmmoCount.getCached(player, stack, iGun)
                    : iGun.getCurrentAmmoCount(stack);
            ammoCount += iGun.hasBulletInBarrel(stack) && data.getBolt() != Bolt.OPEN_BOLT ? 1 : 0;
            return "" + ammoCount;
        }
        return "";
    }
}
