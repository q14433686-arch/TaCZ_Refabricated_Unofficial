package com.tacz.guns.event;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import net.minecraft.server.level.ServerPlayer;

public class PlayerRespawnEvent {
    public static void onPlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        // 重生自动换弹
        if (!GunConfig.AUTO_RELOAD_WHEN_RESPAWN.get()) return;

        newPlayer.getInventory().getNonEquipmentItems().forEach(itemStack -> {
            if (!(itemStack.getItem() instanceof IGun)) return;

            var api = new ModernKineticGunScriptAPI();
            api.setItemStack(itemStack);
            api.setShooter(newPlayer);

            // getGunIndex() 可能为 null（枪包未加载/ID 不匹配），必须防御
            var gunIndex = api.getGunIndex();
            if (gunIndex == null) return;

            // 针对背包直读特殊处理
            var reloadType = gunIndex.getGunData().getReloadData().getType();
            var useInventoryAmmo = reloadType == FeedType.INVENTORY;
            // 如果为背包直读则不进行换弹
            if (useInventoryAmmo) {
                return;
            }

            // 针对燃料类型特殊处理
            var isFuel = reloadType == FeedType.FUEL;
            int needAmmoCount = api.getNeededAmmoAmount();

            if (newPlayer.isCreative()) {
                api.putAmmoInMagazine(needAmmoCount);
            } else {
                int consumedAmount = api.consumeAmmoFromPlayer(isFuel ? 1 : needAmmoCount);
                api.putAmmoInMagazine(isFuel ? (needAmmoCount * consumedAmount) : consumedAmount);
            }
        });
    }
}
