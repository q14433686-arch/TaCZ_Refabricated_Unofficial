package com.tacz.guns.entity.shooter;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.industry.maintenance.IndustryMaintenanceService;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageMaintenanceGunState;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
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
        // The flag is set only by the dedicated C2S clear-feed-jam request.
        // Consume it before every validation return so a rejected packet cannot
        // accidentally authorise a later automatic bolt call.
        boolean clearFeedJamRequested = data.industryFeedJamClearRequested;
        data.industryFeedJamClearRequested = false;
        // Never let an incidental repeated C2S bolt packet cancel evidence
        // collection for a bolt cycle that is already in progress.
        if (data.isBolting) {
            return;
        }
        data.industryFeedJamClearInProgress = false;
        if (data.currentGunItem == null) {
            return;
        }
        ItemStack currentGunItem = data.currentGunItem.get();
        if (!(currentGunItem.getItem() instanceof AbstractGunItem iGun)) {
            return;
        }
        Identifier gunId = iGun.getGunId(currentGunItem);
        TimelessAPI.getCommonGunIndex(gunId)
                .ifPresent(gunIndex -> boltWithIndex(iGun, currentGunItem, gunIndex, clearFeedJamRequested));
    }

    /**
     * Stable server-side manual-bolt hook after the gun index has been resolved.
     *
     * <p>Kept for downstream mods that overrode the release-line signature; it performs an
     * ordinary bolt and never clears an industrial feed fault.</p>
     */
    protected void boltWithIndex(AbstractGunItem iGun, ItemStack currentGunItem, CommonGunIndex gunIndex) {
        boltWithIndex(iGun, currentGunItem, gunIndex, false);
    }

    /**
     * Stable server-side manual-bolt hook, extended with the industrial feed-fault route.
     *
     * @param clearFeedJamRequested whether the dedicated C2S clear request authorised this bolt
     */
    protected void boltWithIndex(AbstractGunItem iGun, ItemStack currentGunItem, CommonGunIndex gunIndex,
                                 boolean clearFeedJamRequested) {
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
        boolean feedJammed = IndustryMaintenanceService.isFeedJammed(currentGunItem);
        // Ordinary client auto-bolt packets are intentionally refused while
        // a feed fault exists. Only the explicit C2S clear request can turn
        // the existing manual-bolt animation into a clearing transaction.
        if (feedJammed && !clearFeedJamRequested) {
            return;
        }
        if (clearFeedJamRequested && (!feedJammed
                || !IndustryMaintenanceService.canClearFeedJamWithBolt(currentGunItem))) {
            return;
        }
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(shooter);
        // 检查 bolt 类型是否是 manual action
        Bolt boltType = gunIndex.getGunData().getBolt();
        // 是否为背包直读
        boolean useInventoryAmmo = iGun.useInventoryAmmo(currentGunItem);
        // 膛内是否有子弹
        boolean hasAmmoInBarrel = iGun.hasBulletInBarrel(currentGunItem) && boltType != Bolt.OPEN_BOLT;
        // 背包内是否还有子弹 (创造模式是否消耗背包备弹)
        boolean hasInventoryAmmo = iGun.hasInventoryAmmo(shooter, currentGunItem, gunOperator.needCheckAmmo());
        // 判断没有子弹的条件 (背包直读且包内没子弹 / 非背包直读且弹匣子弹数 < 1)
        boolean noAmmo = useInventoryAmmo && !hasInventoryAmmo ||
                !useInventoryAmmo && iGun.getCurrentAmmoCount(currentGunItem) < 1;
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
        data.boltTimestamp = System.currentTimeMillis();
        data.isBolting = iGun.startBolt(data, currentGunItem, shooter);
        data.industryFeedJamClearInProgress = data.isBolting && clearFeedJamRequested;
    }

    public void tickBolt() {
        // bolt cool down 为 -1 时，代表拉栓逻辑进程没有开始，不需要tick
        if (!data.isBolting) {
            return;
        }
        if (data.currentGunItem == null) {
            data.isBolting = false;
            data.industryFeedJamClearInProgress = false;
            return;
        }
        ItemStack currentGunItem = data.currentGunItem.get();
        if (!(currentGunItem.getItem() instanceof AbstractGunItem iGun)) {
            data.isBolting = false;
            data.industryFeedJamClearInProgress = false;
            return;
        }
        Identifier gunId = iGun.getGunId(currentGunItem);
        Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(gunId);
        boolean remainsBolting = gunIndex.map(index -> iGun.tickBolt(data, currentGunItem, shooter)).orElse(false);
        data.isBolting = remainsBolting;
        if (!remainsBolting && data.industryFeedJamClearInProgress) {
            // This is the only point at which a C.2 feed fault can disappear:
            // the ordinary server bolt script has ended and its real chamber
            // flag proves that it fed a round. A failed feed leaves the fault.
            IndustryMaintenanceService.completeFeedJamClear(currentGunItem, iGun.hasBulletInBarrel(currentGunItem));
            data.industryFeedJamClearInProgress = false;
            if (shooter instanceof ServerPlayer player) {
                NetworkHandler.sendToClientPlayer(new ServerMessageMaintenanceGunState(currentGunItem), player);
            }
        }
    }
}
