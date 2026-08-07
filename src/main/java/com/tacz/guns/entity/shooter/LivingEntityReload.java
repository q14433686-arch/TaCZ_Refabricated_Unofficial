package com.tacz.guns.entity.shooter;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunReload;
import com.tacz.guns.industry.magazine.EnBlocClipService;
import com.tacz.guns.industry.magazine.InternalFeedService;
import com.tacz.guns.industry.magazine.PhysicalMagazineService;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
        TimelessAPI.getCommonGunIndex(gunId).ifPresent(gunIndex -> {
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
            // Sneak + reload is the explicit magazine-eject action. It follows
            // the same state gates as a normal reload but does not start an
            // animation or consume loose ammunition.
            if (shooter instanceof Player && shooter.isShiftKeyDown()) {
                if (PhysicalMagazineService.usesPhysicalMagazine(currentGunItem)) {
                    PhysicalMagazineService.ejectMagazine(shooter, currentGunItem);
                    return;
                }
                if (EnBlocClipService.usesEnBlocClip(currentGunItem)) {
                    EnBlocClipService.ejectClip(shooter, currentGunItem);
                    return;
                }
            }
            boolean physicalReload = shooter instanceof Player
                    && PhysicalMagazineService.usesPhysicalMagazine(currentGunItem);
            boolean enBlocReload = shooter instanceof Player
                    && EnBlocClipService.usesEnBlocClip(currentGunItem);
            boolean internalReload = shooter instanceof Player
                    && InternalFeedService.usesInternalFeed(currentGunItem);
            boolean managedIndustryReload = physicalReload || enBlocReload || internalReload;

            // External carriers and physical internal feeds reserve their own
            // transaction here. Legacy Java/Lua logic supplies animation only.
            if (!managedIndustryReload
                    && IGunOperator.fromLivingEntity(shooter).needCheckAmmo()
                    && !gunItem.canReload(shooter, currentGunItem)) {
                return;
            }

            // 触发装弹事件
            GunReloadEvent gunReloadEvent = new GunReloadEvent(shooter, currentGunItem, LogicalSide.SERVER);
            GunReloadEvent.CALLBACK.invoker().post(gunReloadEvent);
            if (gunReloadEvent.isCanceled()) {
                return;
            }

            Bolt boltType = gunIndex.getGunData().getBolt();
            int ammoCount = gunItem.getCurrentAmmoCount(currentGunItem)
                    + (gunItem.hasBulletInBarrel(currentGunItem) && boltType != Bolt.OPEN_BOLT ? 1 : 0);
            boolean tactical = ammoCount > 0;
            if (physicalReload
                    && PhysicalMagazineService.beginReload(data, shooter, currentGunItem, tactical) == null) {
                return;
            }
            if (enBlocReload
                    && EnBlocClipService.beginReload(data, shooter, currentGunItem, tactical) == null) {
                return;
            }
            if (internalReload
                    && InternalFeedService.beginReload(data, shooter, currentGunItem, tactical) == null) {
                return;
            }

            NetworkHandler.sendToTrackingEntity(new ServerMessageGunReload(shooter.getId(), currentGunItem), shooter);
            if (!tactical) {
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
                PhysicalMagazineService.clearReloadPlan(data);
                EnBlocClipService.clearReloadPlan(data);
                InternalFeedService.clearReloadPlan(data);
            }
        });
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
            PhysicalMagazineService.clearReloadPlan(data);
            EnBlocClipService.clearReloadPlan(data);
            InternalFeedService.clearReloadPlan(data);
            return result;
        }

        ReloadState.StateType previousState = data.reloadStateType;
        ItemStack currentGunItem = ItemStack.EMPTY;
        // 调用枪械逻辑。它仍然负责动画时间点和 Lua 状态机，但物理
        // 弹匣的库存/子弹变更统一在下面的 transition hook 完成。
        if (data.currentGunItem != null) {
            currentGunItem = data.currentGunItem.get();
            if (!currentGunItem.isEmpty() && currentGunItem.getItem() instanceof AbstractGunItem abstractGunItem) {
                result = abstractGunItem.tickReload(data, currentGunItem, shooter);
            }
        }

        if (!currentGunItem.isEmpty()) {
            PhysicalMagazineService.onReloadStateTransition(
                    data, shooter, currentGunItem, previousState, result.getStateType()
            );
            EnBlocClipService.onReloadStateTransition(
                    data, shooter, currentGunItem, previousState, result.getStateType()
            );
            InternalFeedService.onReloadStateTransition(
                    data, shooter, currentGunItem, previousState, result.getStateType()
            );
        } else {
            PhysicalMagazineService.clearReloadPlan(data);
            EnBlocClipService.clearReloadPlan(data);
            InternalFeedService.clearReloadPlan(data);
        }

        // 将 tick 的结果保存到 data holder
        data.reloadStateType = result.getStateType();
        if (!result.getStateType().isReloading()) {
            data.reloadTimestamp = -1;
            PhysicalMagazineService.clearReloadPlan(data);
            EnBlocClipService.clearReloadPlan(data);
            InternalFeedService.clearReloadPlan(data);
        }
        return result;
    }
}
