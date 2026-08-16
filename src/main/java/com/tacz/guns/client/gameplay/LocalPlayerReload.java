package com.tacz.guns.client.gameplay;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.message.ClientMessagePlayerCancelReload;
import com.tacz.guns.network.message.ClientMessagePlayerReloadGun;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class LocalPlayerReload {
    private final LocalPlayerDataHolder data;
    private final LocalPlayer player;

    public LocalPlayerReload(LocalPlayerDataHolder data, LocalPlayer player) {
        this.data = data;
        this.player = player;
    }

    public void cancelReload() {
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof AbstractGunItem)) {
            return;
        }

        TimelessAPI.getGunDisplay(mainHandItem).ifPresent(this::cancelReloadWithDisplay);
    }

    /**
     * Stable client-side reload-cancellation hook after display data has been resolved.
     */
    protected void cancelReloadWithDisplay(GunDisplayInstance display) {
        // 如果没在换弹，则返回
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        ReloadState reloadState = gunOperator.getSynReloadState();
        if (!reloadState.getStateType().isReloading()) {
            return;
        }
        // 发包通知服务器
        ClientPlayNetworking.send(new ClientMessagePlayerCancelReload());
        // 执行本地取消换弹逻辑
        this.triggerClientReloadCancelAnimation(display);
    }

    public void reload() {
        // 暂定只有主手可以装弹
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof AbstractGunItem gunItem)) {
            return;
        }
        Identifier gunId = gunItem.getGunId(mainHandItem);
        GunData gunData = TimelessAPI.getClientGunIndex(gunId).map(ClientGunIndex::getGunData).orElse(null);
        if (gunData == null) {
            return;
        }
        TimelessAPI.getGunDisplay(mainHandItem)
                .ifPresent(display -> reloadWithDisplay(gunItem, display, gunData, mainHandItem));
    }

    /**
     * Stable client-side reload hook after display data has been resolved.
     */
    protected void reloadWithDisplay(AbstractGunItem gunItem, GunDisplayInstance display, GunData gunData, ItemStack mainHandItem) {
        // 检查是否为背包直读
        if (gunItem.useInventoryAmmo(mainHandItem)) {
            return;
        }
        // 检查状态锁
        if (data.clientStateLock) {
            return;
        }
        if (System.currentTimeMillis() - data.clientShootTimestamp < 100) {
            return;
        }
        // 弹药简单检查
        boolean canReload = gunItem.canReload(player, mainHandItem);
        if (IGunOperator.fromLivingEntity(player).needCheckAmmo() && !canReload) {
            return;
        }
        // 锁上状态锁
        data.lockState(this::isReloadLockActive);
        data.chargeProgress = 0f;
        // 触发换弹事件
        GunReloadEvent gunReloadEvent = new GunReloadEvent(player, player.getMainHandItem(), LogicalSide.CLIENT);
        GunReloadEvent.CALLBACK.invoker().post(gunReloadEvent);
        if (gunReloadEvent.isCanceled()) {
            return;
        }
        // 发包通知服务器
        ClientPlayNetworking.send(new ClientMessagePlayerReloadGun());
        // 执行客户端 reload 相关内容
        this.triggerClientReloadAnimation(gunItem, display, gunData, mainHandItem);
    }

    protected boolean isReloadLockActive(IGunOperator operator) {
        return operator.getSynReloadState().getStateType().isReloading();
    }

    protected void triggerClientReloadAnimation(IGun iGun, GunDisplayInstance display, GunData gunData, ItemStack mainHandItem) {
        var animationStateMachine = display.getAnimationStateMachine();
        if (animationStateMachine != null) {
            Bolt boltType = gunData.getBolt();
            boolean noAmmo;
            if (boltType == Bolt.OPEN_BOLT) {
                noAmmo = iGun.getCurrentAmmoCount(mainHandItem) <= 0;
            } else {
                noAmmo = !iGun.hasBulletInBarrel(mainHandItem);
            }
            // 触发 reload，停止播放声音
            SoundPlayManager.stopPlayGunSound();
            SoundPlayManager.playReloadSound(player, display, noAmmo);
            animationStateMachine.trigger(GunAnimationConstant.INPUT_RELOAD);
        }
    }

    protected void triggerClientReloadCancelAnimation(GunDisplayInstance display) {
        var animationStateMachine = display.getAnimationStateMachine();
        if (animationStateMachine != null) {
            animationStateMachine.trigger(GunAnimationConstant.INPUT_CANCEL_RELOAD);
        }
    }
}
