package com.tacz.guns.entity.shooter;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunReload;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class LivingEntityReload {
    private final LivingEntity shooter;
    private final ShooterDataHolder data;
    private final LivingEntityDrawGun draw;
    private final LivingEntityShoot shoot;

    public LivingEntityReload(LivingEntity shooter, ShooterDataHolder data, LivingEntityDrawGun draw, LivingEntityShoot shoot) {
        this.shooter = shooter;
        this.data = data;
        this.draw = draw;
        this.shoot = shoot;
    }

    public void reload() {
        if (data.currentGunItem == null) {
            return;
        }
        ItemStack currentGunItem = data.currentGunItem.get();
        if (!(currentGunItem.getItem() instanceof AbstractGunItem gunItem)) {
            return;
        }
        Identifier gunId = gunItem.getGunId(currentGunItem);
        TimelessAPI.getCommonGunIndex(gunId).ifPresent(gunIndex -> performReload(gunItem, currentGunItem, gunIndex));
    }

    /**
     * 服务端换弹的门槛 + 触发逻辑（原先是 {@link #reload()} 里的匿名 lambda）。
     *
     * <p>这是服务端「能否开始换弹」的具名决策点，供下游 mixin/覆写。
     * <b>注意：</b>客户端 {@code LocalPlayerReload#performReload} 的门槛序列与本方法
     * <b>有意不同</b>（客户端多一个射击后 100ms 保护，且没有本方法的冷却/拉栓检查）——
     * 两条路径语义各自成立，请勿强行合并成同一个「统一换弹门槛」API。</p>
     */
    protected void performReload(AbstractGunItem gunItem, ItemStack currentGunItem, CommonGunIndex gunIndex) {
        // 检查是否为背包直读
        if (gunItem.useInventoryAmmo(currentGunItem)) {
            return;
        }
        // 检查换弹是否还未完成
        if (data.reloadStateType.isReloading()) {
            return;
        }
        // 检查是否正在开火冷却
        if (shoot.getShootCoolDown() != 0) {
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
        // 检查弹药
        if (IGunOperator.fromLivingEntity(shooter).needCheckAmmo() && !gunItem.canReload(shooter, currentGunItem)) {
            return;
        }
        // 触发装弹事件
        GunReloadEvent gunReloadEvent = new GunReloadEvent(shooter, currentGunItem, LogicalSide.SERVER);
        GunReloadEvent.CALLBACK.invoker().post(gunReloadEvent);
        if (gunReloadEvent.isCanceled()) {
            return;
        }
        NetworkHandler.sendToTrackingEntity(new ServerMessageGunReload(shooter.getId(), currentGunItem), shooter);
        Bolt boltType = gunIndex.getGunData().getBolt();
        int ammoCount = gunItem.getCurrentAmmoCount(currentGunItem) + (gunItem.hasBulletInBarrel(currentGunItem) && boltType != Bolt.OPEN_BOLT ? 1 : 0);
        if (ammoCount <= 0) {
            // 初始化空仓换弹的 tick 的状态
            data.reloadStateType = ReloadState.StateType.EMPTY_RELOAD_FEEDING;
        } else {
            // 初始化战术换弹的 tick 的状态
            data.reloadStateType = ReloadState.StateType.TACTICAL_RELOAD_FEEDING;
        }
        data.reloadTimestamp = System.currentTimeMillis();
        // 调用枪械逻辑
        if (!gunItem.startReload(data, currentGunItem, shooter)) {
            data.reloadStateType = ReloadState.StateType.NOT_RELOADING;
            data.reloadTimestamp = -1;
        }
    }

    public void cancelReload() {
        if (data.currentGunItem == null) {
            return;
        }
        ItemStack currentGunItem = data.currentGunItem.get();
        if (!(currentGunItem.getItem() instanceof AbstractGunItem gunItem)) {
            return;
        }
        // 检查是否在换弹
        if (!data.reloadStateType.isReloading()) {
            return;
        }
        gunItem.interruptReload(data, currentGunItem, shooter);
    }

    public ReloadState tickReloadState() {
        ReloadState result = new ReloadState();
        // 如果没有在换弹，直接返回
        if (data.reloadTimestamp == -1) {
            return result;
        }
        // 调用枪械逻辑
        if (data.currentGunItem != null) {
            ItemStack currentGunItem = data.currentGunItem.get();
            if (currentGunItem != null && currentGunItem.getItem() instanceof AbstractGunItem abstractGunItem) {
                result = abstractGunItem.tickReload(data, currentGunItem, shooter);
            }
        }
        // 将 tick 的结果保存到 data holder
        data.reloadStateType = result.getStateType();
        if (!result.getStateType().isReloading()) {
            data.reloadTimestamp = -1;
        }
        return result;
    }
}
