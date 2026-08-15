package com.tacz.guns.entity.shooter;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.AmmoAvailability;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class LivingEntityBolt {
    private final ShooterDataHolder data;
    private final LivingEntityDrawGun draw;
    private final LivingEntityShoot shoot;
    private final LivingEntity shooter;

    public LivingEntityBolt(ShooterDataHolder data, LivingEntity shooter, LivingEntityDrawGun draw, LivingEntityShoot shoot) {
        this.data = data;
        this.draw = draw;
        this.shoot = shoot;
        this.shooter = shooter;
    }

    public void bolt() {
        if (data.currentGunItem == null) {
            return;
        }
        ItemStack currentGunItem = data.currentGunItem.get();
        if (!(currentGunItem.getItem() instanceof AbstractGunItem iGun)) {
            return;
        }
        Identifier gunId = iGun.getGunId(currentGunItem);
        TimelessAPI.getCommonGunIndex(gunId).ifPresent(gunIndex -> {
            // 判断是否正在射击冷却
            if (shoot.getShootCoolDown() != 0) {
                return;
            }
            // 检查是否正在换弹
            if (data.reloadStateType.isReloading()) {
                return;
            }
            // 检查是否在切枪
            if (draw.getDrawCoolDown() != 0) {
                return;
            }
            // 检查是否在拉栓
            if (data.isBolting) {
                return;
            }
            IGunOperator gunOperator = IGunOperator.fromLivingEntity(shooter);
            // 检查 bolt 类型是否是 manual action
            Bolt boltType = gunIndex.getGunData().getBolt();
            AmmoAvailability ammo = AbstractGunItem.checkAmmoAvailability(iGun, shooter, currentGunItem, boltType, gunOperator.needCheckAmmo());
            boolean hasAmmoInBarrel = ammo.hasAmmoInBarrel;
            if (boltType != Bolt.MANUAL_ACTION) {
                return;
            }
            // 检查是否有弹药在枪膛内
            if (hasAmmoInBarrel) {
                return;
            }
            // 检查弹匣内是否有子弹（拉栓路径：枪膛子弹单独判断，见 AmmoAvailability#isNoAmmoToBolt）
            if (ammo.isNoAmmoToBolt()) {
                return;
            }
            data.boltTimestamp = System.currentTimeMillis();
            data.isBolting = iGun.startBolt(data, currentGunItem, shooter);
        });
    }

    public void tickBolt() {
        // bolt cool down 为 -1 时，代表拉栓逻辑进程没有开始，不需要tick
        if (!data.isBolting) {
            return;
        }
        if (data.currentGunItem == null) {
            data.isBolting = false;
            return;
        }
        ItemStack currentGunItem = data.currentGunItem.get();
        if (!(currentGunItem.getItem() instanceof AbstractGunItem iGun)) {
            data.isBolting = false;
            return;
        }
        Identifier gunId = iGun.getGunId(currentGunItem);
        Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(gunId);
        data.isBolting = gunIndex.map(index -> iGun.tickBolt(data, currentGunItem, shooter)).orElse(false);
    }
}
