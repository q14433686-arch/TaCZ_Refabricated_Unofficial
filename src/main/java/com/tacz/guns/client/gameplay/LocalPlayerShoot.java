package com.tacz.guns.client.gameplay;

import cn.sh1rocu.tacz.api.LogicalSide;
import cn.sh1rocu.tacz.mixin.accessor.BlockableEventLoopAccessor;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.AmmoAvailability;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.message.ClientMessagePlayerShoot;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.ChargeData;
import com.tacz.guns.resource.pojo.data.gun.ChargeType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.sound.SoundManager;
import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class LocalPlayerShoot {
    /**
     * 开火状态锁的<b>身份令牌</b>。
     *
     * <p>本字段不仅承载判定逻辑，还承担身份语义：{@code LocalPlayerShoot} 通过
     * {@code data.lockedCondition != SHOOT_LOCKED_CONDITION} 的<b>引用比较</b>识别
     * 「当前锁是不是开火锁」。因此下游 mod 若想改变判定，应 mixin/覆写
     * {@link #isShootLocked(IGunOperator)}，<b>不要</b>替换本字段（替换会破坏引用比较，
     * 使切枪后开火覆盖其它动作的保护静默失效）。</p>
     */
    private static final Predicate<IGunOperator> SHOOT_LOCKED_CONDITION = LocalPlayerShoot::isShootLocked;

    /**
     * 「是否处于开火冷却」的具名判定。原先是 {@code SHOOT_LOCKED_CONDITION} 里的匿名 lambda
     * （合成名 {@code lambda$static$N}），现提取为稳定的 public static 方法供下游 mixin。
     *
     * @param operator 射击者
     * @return 同步到的射击冷却是否大于 0
     */
    public static boolean isShootLocked(IGunOperator operator) {
        return operator.getSynShootCoolDown() > 0;
    }

    private final LocalPlayerDataHolder data;
    private final LocalPlayer player;

    public LocalPlayerShoot(LocalPlayerDataHolder data, LocalPlayer player) {
        this.data = data;
        this.player = player;
    }

    public boolean chargeShoot(boolean isCharging) {
        // 因为开火冷却检测用了特别定制的方法，所以不检查状态锁，而是手动检查是否换弹、切枪
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        ItemStack mainHandItem = player.getMainHandItem();
        // 暂定为只有主手能开枪
        if (!(mainHandItem.getItem() instanceof IGun iGun)) {
            data.chargeProgress = 0f;
            return false;
        }
        Identifier gunId = iGun.getGunId(mainHandItem);
        Optional<ClientGunIndex> gunIndexOptional = TimelessAPI.getClientGunIndex(gunId);
        GunDisplayInstance display = TimelessAPI.getGunDisplay(mainHandItem).orElse(null);
        if (gunIndexOptional.isEmpty() || display == null) {
            return false;
        }
        ClientGunIndex gunIndex = gunIndexOptional.get();
        GunData gunData = gunIndex.getGunData();
        FireMode fireMode = iGun.getFireMode(mainHandItem);

        ChargeData chargeData = gunData.getChargeData(fireMode);
        if (chargeData == null) {
            return isCharging;
        }

        boolean canChargeDuringCooldown = chargeData.isChargeDuringCooldown() || getCoolDown(iGun, mainHandItem, gunData) < 50;
        boolean canCharge = canChargeDuringCooldown && preCheck(iGun, gunOperator, gunIndex, mainHandItem, display, gunData, isCharging) == null;
        float chargeProgress = data.chargeProgress;
        ChargeType type = chargeData.getChargeType();

        if (type == ChargeType.AUTO) {
            if (isCharging && canCharge) {
                data.isCharging = true;
                data.chargeProgress = Math.min(chargeProgress + chargeData.getIncreasePerTick(), chargeData.getMaxCharge());
                return data.chargeProgress >= chargeData.getMaxCharge();
            } else {
                data.isCharging = false;
                data.chargeProgress = Math.max(chargeProgress - chargeData.getDecreasePerTick(), 0f);
            }
        } else if (type == ChargeType.HOLD) {
            if (isCharging && canCharge) {
                data.isCharging = true;
                data.chargeProgress = Math.min(chargeProgress + chargeData.getIncreasePerTick(), chargeData.getMaxCharge());
            } else {
                if (canChargeDuringCooldown && chargeProgress >= chargeData.getFireThreshold()) {
                    return true;
                }
                data.isCharging = false;
                data.chargeProgress = Math.max(chargeProgress - chargeData.getDecreasePerTick(), 0f);
            }
        } else if (type == ChargeType.DELAY) {
            if ((isCharging || chargeProgress > 0) && canCharge) {
                data.isCharging = true;
                data.chargeProgress = Math.min(chargeProgress + chargeData.getIncreasePerTick(), chargeData.getMaxCharge());
                return data.chargeProgress >= chargeData.getMaxCharge();
            } else {
                data.isCharging = false;
                data.chargeProgress = Math.max(chargeProgress - chargeData.getDecreasePerTick(), 0f);
            }
        }
        return false;
    }

    public ShootResult shoot() {
        // 因为开火冷却检测用了特别定制的方法，所以不检查状态锁，而是手动检查是否换弹、切枪
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        ItemStack mainHandItem = player.getMainHandItem();
        // 暂定为只有主手能开枪
        if (!(mainHandItem.getItem() instanceof IGun iGun)) {
            return ShootResult.NOT_GUN;
        }
        Identifier gunId = iGun.getGunId(mainHandItem);
        Optional<ClientGunIndex> gunIndexOptional = TimelessAPI.getClientGunIndex(gunId);
        GunDisplayInstance display = TimelessAPI.getGunDisplay(mainHandItem).orElse(null);
        if (gunIndexOptional.isEmpty() || display == null) {
            return ShootResult.ID_NOT_EXIST;
        }
        ClientGunIndex gunIndex = gunIndexOptional.get();
        GunData gunData = gunIndex.getGunData();
        long coolDown = this.getCoolDown(iGun, mainHandItem, gunData);

        // 如果上一次异步开火的效果还未执行，则直接返回，等待异步开火效果执行
        if (!data.isShootRecorded) {
            return ShootResult.COOL_DOWN;
        }
        // 如果状态锁正在准备锁定，且不是开火的状态锁，则不允许开火(主要用于防止切枪后开火动作覆盖切枪动作)
        if (data.clientStateLock && data.lockedCondition != SHOOT_LOCKED_CONDITION && data.lockedCondition != null) {
            data.isShootRecorded = true;
            // 因为这块主要目的是防止切枪后开火动作覆盖切枪动作，返回 IS_DRAWING
            return ShootResult.IS_DRAWING;
        }

        // 如果射击冷却大于等于 1 tick (即 50 ms)，则不允许开火
        if (coolDown >= 50) {
            return ShootResult.COOL_DOWN;
        }

        // 基础检查
        ShootResult result = preCheck(iGun, gunOperator, gunIndex, mainHandItem, display, gunData, true);
        if (result != null) {
            return result;
        }

        // 检查是否正在奔跑
        if (gunOperator.getSynSprintTime() > 0) {
            return ShootResult.IS_SPRINTING;
        }
        // 触发开火事件
        var event = new GunShootEvent(player, mainHandItem, LogicalSide.CLIENT);
        GunShootEvent.CALLBACK.invoker().post(event);
        if (event.isCanceled()) {
            return ShootResult.FORGE_EVENT_CANCEL;
        }
        // 切换状态锁，不允许换弹、检视等行为进行。
        data.lockState(SHOOT_LOCKED_CONDITION);
        data.isShootRecorded = false;
        // 调用开火逻辑
        float finalChargeProgress = data.chargeProgress;
        this.doShoot(display, iGun, mainHandItem, gunData, coolDown, finalChargeProgress);

        FireMode fireMode = iGun.getFireMode(mainHandItem);
        ChargeData chargeData = gunData.getChargeData(fireMode);
        if (chargeData != null) {
            if (chargeData.getChargeType() == ChargeType.DELAY) {
                data.chargeProgress = 0f;
            } else {
                data.chargeProgress = Math.max(0f, data.chargeProgress - chargeData.getDecreaseOnFire());
            }
        }

        return ShootResult.SUCCESS;
    }

    private @Nullable ShootResult preCheck(IGun iGun, IGunOperator gunOperator, ClientGunIndex gunIndex, ItemStack mainHandItem,
                                           GunDisplayInstance display, GunData gunData, boolean playDrySound) {
        // 按钮冷却时间未到，防止点击按钮后误触开火
        // 默认设置为 50 ms
        if (System.currentTimeMillis() - LocalPlayerDataHolder.clientClickButtonTimestamp < 50) {
            return ShootResult.COOL_DOWN;
        }

        // 检查是否正在换弹
        if (gunOperator.getSynReloadState().getStateType().isReloading()) {
            return ShootResult.IS_RELOADING;
        }
        // 检查是否正在切枪
        if (gunOperator.getSynDrawCoolDown() != 0) {
            return ShootResult.IS_DRAWING;
        }
        // 检查是否正在拉栓
        if (gunOperator.getSynIsBolting()) {
            return ShootResult.IS_BOLTING;
        }
        // 判断是否处于近战冷却时间
        if (gunOperator.getSynMeleeCoolDown() != 0) {
            return ShootResult.IS_MELEE;
        }
        // 判断子弹数（统一收敛到 AmmoAvailability，见 AbstractGunItem#checkAmmoAvailability）
        Bolt boltType = gunIndex.getGunData().getBolt();
        AmmoAvailability ammo = AbstractGunItem.checkAmmoAvailability(iGun, player, mainHandItem, boltType, gunOperator.needCheckAmmo());
        boolean hasAmmoInBarrel = ammo.hasAmmoInBarrel;
        if (ammo.isNoAmmoToShoot()) {
            if (playDrySound) {
                SoundPlayManager.playDryFireSound(player, display);
            }
            return ShootResult.NO_AMMO;
        }
        //Handle Heat Data
        if (gunData.hasHeatData()) {
            if (iGun.isOverheatLocked(mainHandItem)) {
                if (playDrySound) {
                    SoundPlayManager.playDryFireSound(player, display);
                }
                return ShootResult.OVERHEATED;
            }
        }
        // 检查膛内子弹
        if (boltType == Bolt.MANUAL_ACTION && !hasAmmoInBarrel) {
            IClientPlayerGunOperator.fromLocalPlayer(player).bolt();
            return ShootResult.NEED_BOLT;
        }
        return null;
    }

    private void doShoot(GunDisplayInstance display, IGun iGun, ItemStack mainHandItem, GunData gunData, long delay, float chargeProgress) {
        FireMode fireMode = iGun.getFireMode(mainHandItem);
        Bolt boltType = gunData.getBolt();
        // 获取余弹数
        boolean consumeAmmo = IGunOperator.fromLivingEntity(player).consumesAmmoOrNot();
        boolean hasAmmoInBarrel = iGun.hasBulletInBarrel(mainHandItem) && boltType != Bolt.OPEN_BOLT;
        int ammoCount = consumeAmmo ? iGun.getCurrentAmmoCount(mainHandItem) + (hasAmmoInBarrel ? 1 : 0) : Integer.MAX_VALUE;
        // 连发射击间隔
        long period = fireMode == FireMode.BURST ? gunData.getBurstShootInterval() : 1;
        // 最大连发数
        final int maxCount = Math.min(ammoCount, fireMode == FireMode.BURST ? gunData.getBurstData().getCount() : 1);
        // 连发计数器
        AtomicInteger count = new AtomicInteger(0);

        LocalPlayerDataHolder.SCHEDULED_EXECUTOR_SERVICE.scheduleAtFixedRate(
                () -> runBurstTick(display, iGun, mainHandItem, gunData, maxCount, count, chargeProgress),
                delay, period, TimeUnit.MILLISECONDS);
    }

    /**
     * 连发调度器的单个 tick。原先是 {@code scheduleAtFixedRate} 里的匿名 lambda（合成名
     * {@code lambda$doShoot$N}），现提取为具名方法，便于下游 mixin 与调试。
     */
    private void runBurstTick(GunDisplayInstance display, IGun iGun, ItemStack mainHandItem, GunData gunData, int maxCount, AtomicInteger count, float chargeProgress) {
        if (count.get() == 0) {
            // 转换 isRecord 状态，允许下一个tick的开火检测。
            data.isShootRecorded = true;
        }
        //Handle Heat Data
        if (gunData.hasHeatData()) {
            if (iGun.isOverheatLocked(mainHandItem)) {
                ScheduledFuture<?> future = (ScheduledFuture<?>) Thread.currentThread();
                future.cancel(false); // 取消当前任务
                return;
            }
        }
        // 如果达到最大连发次数，或者玩家已经死亡，取消任务
        if (count.get() >= maxCount || player.isDeadOrDying()) {
            ScheduledFuture<?> future = (ScheduledFuture<?>) Thread.currentThread();
            future.cancel(false); // 取消当前任务
            return;
        }

        // 以下逻辑只需要执行一次
        if (count.get() == 0) {
            // 如果状态锁正在准备锁定，且不是开火的状态锁，则不允许开火(主要用于防止切枪后开火动作覆盖切枪动作)
            if (data.clientStateLock && data.lockedCondition != SHOOT_LOCKED_CONDITION && data.lockedCondition != null) {
                return;
            }
            // 记录新的开火时间戳
            data.clientLastShootTimestamp = data.clientShootTimestamp;
            data.clientShootTimestamp = System.currentTimeMillis();
            // 发送开火的数据包，通知服务器
            ClientPlayNetworking.send(new ClientMessagePlayerShoot(data.clientShootTimestamp - data.clientBaseTimestamp, chargeProgress));
        }

        // The burst scheduler runs off-thread. Sound managers, animation state and Fabric event
        // listeners are client-thread state, so hand them back to Minecraft's event loop to avoid CME.
        // 播放声音和状态机触发需要从异步线程上传到主线程执行，否则会引起cme
        ((BlockableEventLoopAccessor) Minecraft.getInstance()).tacz$submitAsync(() -> fireOnce(display, mainHandItem, gunData));

        count.getAndIncrement();
    }

    /**
     * 执行一次客户端击发：触发击发事件、播放动画与声音。原先是 {@code tacz$submitAsync} 里的匿名 lambda。
     */
    private void fireOnce(GunDisplayInstance display, ItemStack mainHandItem, GunData gunData) {
        // 触发击发事件
        var event = new GunFireEvent(player, mainHandItem, LogicalSide.CLIENT);
        GunFireEvent.CALLBACK.invoker().post(event);
        boolean fire = !event.isCanceled();
        if (fire) {
            // 动画和声音循环播放
            AnimationStateMachine<?> animationStateMachine = display.getAnimationStateMachine();
            if (animationStateMachine != null) {
                animationStateMachine.trigger(GunAnimationConstant.INPUT_SHOOT);
            }
            // 获取消音
            final boolean useSilenceSound = this.useSilenceSound();
            // 开火需要打断检视
            SoundPlayManager.stopPlayGunSound(display, SoundManager.INSPECT_SOUND);
            if (useSilenceSound) {
                SoundPlayManager.playSilenceSound(player, display, gunData);
            } else {
                SoundPlayManager.playShootSound(player, display, gunData);
            }
        }
    }

    private boolean useSilenceSound() {
        AttachmentCacheProperty cacheProperty = IGunOperator.fromLivingEntity(player).getCacheProperty();
        if (cacheProperty != null) {
            Pair<Integer, Boolean> silence = cacheProperty.getCache(SilenceModifier.ID);
            return silence.right();
        }
        return false;
    }

    private long getCoolDown(IGun iGun, ItemStack mainHandItem, GunData gunData) {
        // 注意：此处<b>有意不</b>减去 ShooterDataHolder.LATENCY_WINDOW_MS（服务端会减 5 ms 补偿延迟）。
        // 客户端/服务端冷却语义本就是两条独立路径（另有 charge 的 <50ms 分支依赖本函数），
        // 请勿把两端强行合并进同一个冷却计算 API。
        FireMode fireMode = iGun.getFireMode(mainHandItem);
        long coolDown;
        if (fireMode == FireMode.BURST) {
            coolDown = (long) (gunData.getBurstData().getMinInterval() * 1000f) - (System.currentTimeMillis() - data.clientShootTimestamp);
        } else {
            coolDown = gunData.getShootInterval(this.player, fireMode, mainHandItem) - (System.currentTimeMillis() - data.clientShootTimestamp);
        }
        return Math.max(coolDown, 0);
    }

    public long getClientShootCoolDown() {
        ItemStack mainHandItem = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(mainHandItem);
        if (iGun == null) {
            return -1;
        }
        Identifier gunId = iGun.getGunId(mainHandItem);
        Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(gunId);
        return gunIndexOptional.map(commonGunIndex -> getCoolDown(iGun, mainHandItem, commonGunIndex.getGunData())).orElse(-1L);
    }
}
