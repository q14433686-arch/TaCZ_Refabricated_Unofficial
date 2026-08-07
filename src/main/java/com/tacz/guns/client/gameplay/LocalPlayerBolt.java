package com.tacz.guns.client.gameplay;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.industry.maintenance.IndustryMaintenanceService;
import com.tacz.guns.network.message.ClientMessageClearFeedJam;
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

    /** Existing automatic/manual chambering route; it must never clear a C.2 feed fault. */
    public void bolt() {
        startBolt(false);
    }

    /**
     * Player-initiated C.2 clear route. The client plays the same established
     * manual-bolt animation, but the C2S message is distinct so the server can
     * reject automatic/replayed bolt packets while a jam exists.
     */
    public void clearFeedJam() {
        startBolt(true);
    }

    private void startBolt(boolean clearFeedJam) {
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
        if (clearFeedJam && !IndustryMaintenanceService.isFeedJammed(mainHandItem)) {
            return;
        }

        TimelessAPI.getGunDisplay(mainHandItem).ifPresent(display -> {
            IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
            // 检查 bolt 类型是否是 manual action
            Bolt boltType = gunData.getBolt();
            // 是否为背包直读
            boolean useInventoryAmmo = iGun.useInventoryAmmo(mainHandItem);
            // 膛内是否有子弹
            boolean hasAmmoInBarrel = iGun.hasBulletInBarrel(mainHandItem) && boltType != Bolt.OPEN_BOLT;
            // 背包内是否还有子弹 (创造模式是否消耗背包备弹)
            boolean hasInventoryAmmo = iGun.hasInventoryAmmo(player, mainHandItem, gunOperator.needCheckAmmo());
            // 判断没有子弹的条件 (背包直读且包内没子弹 / 非背包直读且弹匣子弹数 < 1)
            boolean noAmmo = useInventoryAmmo && !hasInventoryAmmo ||
                    !useInventoryAmmo && iGun.getCurrentAmmoCount(mainHandItem) < 1;
            if (boltType != Bolt.MANUAL_ACTION) {
                return;
            }
            // 检查是否有弹药在枪膛内
            if (hasAmmoInBarrel) {
                return;
            }
            // 检查弹匣内是否有子弹
            if (noAmmo) {
                return;
            }
            // 锁上状态锁
            data.lockState(IGunOperator::getSynIsBolting);
            data.isBolting = true;
            data.isClearingFeedJam = clearFeedJam;
            // A normal auto-bolt and an intentional fault-clear have different
            // server semantics even though both render the same real animation.
            if (clearFeedJam) {
                ClientPlayNetworking.send(new ClientMessageClearFeedJam());
            } else {
                ClientPlayNetworking.send(new ClientMessagePlayerBoltGun());
            }
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
            data.isClearingFeedJam = false;
            return;
        }
        // A server-created feed fault must wait for the player's explicit C2S
        // clear request. Preserve the local state during that one requested
        // action; the authoritative S2C snapshot resolves it on success/fail.
        if (IndustryMaintenanceService.isFeedJammed(mainHandItem)) {
            if (!data.isClearingFeedJam) {
                data.isBolting = false;
            }
            return;
        }
        bolt();
        if (data.isBolting) {
            // 对于客户端来说，膛内弹药被填入的状态同步到客户端的瞬间，bolt 过程才算完全结束
            if (iGun.hasBulletInBarrel(mainHandItem)) {
                data.isBolting = false;
                data.isClearingFeedJam = false;
            }
        }
    }
}
