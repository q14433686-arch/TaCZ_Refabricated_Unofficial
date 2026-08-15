package com.tacz.guns.client.gameplay;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.AmmoAvailability;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.message.ClientMessagePlayerBoltGun;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public class LocalPlayerBolt {
    private final LocalPlayerDataHolder data;
    private final LocalPlayer player;

    public LocalPlayerBolt(LocalPlayerDataHolder data, LocalPlayer player) {
        this.data = data;
        this.player = player;
    }

    public void bolt() {
        // 检查状态锁
        if (data.clientStateLock) {
            return;
        }
        if (data.isBolting) {
            return;
        }
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof IGun iGun)) {
            return;
        }
        GunData gunData = TimelessAPI.getClientGunIndex(iGun.getGunId(mainHandItem)).map(ClientGunIndex::getGunData).orElse(null);
        if (gunData == null) {
            return;
        }

        TimelessAPI.getGunDisplay(mainHandItem).ifPresent(display -> {
            IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
            // 检查 bolt 类型是否是 manual action
            Bolt boltType = gunData.getBolt();
            // 膛内/弹匣/直读容器的弹药可用性（统一收敛到 AmmoAvailability，见 AbstractGunItem#checkAmmoAvailability）
            AmmoAvailability ammo = AbstractGunItem.checkAmmoAvailability(iGun, player, mainHandItem, boltType, gunOperator.needCheckAmmo());
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
            // 锁上状态锁
            data.lockState(IGunOperator::getSynIsBolting);
            data.isBolting = true;
            // 发包通知服务器
            ClientPlayNetworking.send(new ClientMessagePlayerBoltGun());
            // 播放动画和音效
            AnimationStateMachine<?> animationStateMachine = display.getAnimationStateMachine();
            if (animationStateMachine != null) {
                SoundPlayManager.playBoltSound(player, display);
                animationStateMachine.trigger(GunAnimationConstant.INPUT_BOLT);
            }
        });
    }

    public void tickAutoBolt() {
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof IGun iGun)) {
            data.isBolting = false;
            return;
        }
        bolt();
        if (data.isBolting) {
            // 对于客户端来说，膛内弹药被填入的状态同步到客户端的瞬间，bolt 过程才算完全结束
            if (iGun.hasBulletInBarrel(mainHandItem)) {
                data.isBolting = false;
            }
        }
    }
}
