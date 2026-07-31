package com.tacz.guns.api.item;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.GunProperty;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 这里不包含枪械的逻辑，只包含枪械的各种 nbt 访问。<br>
 * 你可以在 {@link AbstractGunItem} 看到枪械逻辑
 */
public interface IGun {
    /**
     * @return 如果物品类型为 IGun 则返回显式转换后的实例，否则返回 null。
     */
    @Nullable
    static IGun getIGunOrNull(@Nullable ItemStack stack) {
        if (stack == null) {
            return null;
        }
        if (stack.getItem() instanceof IGun iGun) {
            return iGun;
        }
        return null;
    }

    /**
     * 是否主手持枪
     */
    @Deprecated
    static boolean mainhandHoldGun(LivingEntity livingEntity) {
        return livingEntity.getMainHandItem().getItem() instanceof IGun;
    }

    /**
     * 是否主手持枪
     */
    static boolean mainHandHoldGun(LivingEntity livingEntity) {
        return livingEntity.getMainHandItem().getItem() instanceof IGun;
    }

    /**
     * 获取主手枪械的开火模式
     */
    @Deprecated
    static FireMode getMainhandFireMode(LivingEntity livingEntity) {
        ItemStack mainHandItem = livingEntity.getMainHandItem();
        if (mainHandItem.getItem() instanceof IGun iGun) {
            return iGun.getFireMode(mainHandItem);
        }
        return FireMode.UNKNOWN;
    }

    /**
     * 获取主手枪械的开火模式
     */
    static FireMode getMainHandFireMode(LivingEntity livingEntity) {
        ItemStack mainHandItem = livingEntity.getMainHandItem();
        if (mainHandItem.getItem() instanceof IGun iGun) {
            return iGun.getFireMode(mainHandItem);
        }
        return FireMode.UNKNOWN;
    }

    /**
     * 获取瞄准放大倍率
     */
    float getAimingZoom(ItemStack gunItem);

    /**
     * 枪械换弹时是否使用"虚拟备弹"而不是背包里的实际弹药
     */
    boolean useDummyAmmo(ItemStack gun);

    /**
     * 获取枪械当前的"虚拟备弹"数量
     */
    int getDummyAmmoAmount(ItemStack gun);

    /**
     * 设置枪械当前的"虚拟备弹"数量
     */
    void setDummyAmmoAmount(ItemStack gun, int amount);

    /**
     * 添加枪械当前的"虚拟备弹"数量
     */
    void addDummyAmmoAmount(ItemStack gun, int amount);

    /**
     * 检查是否有设置"虚拟备弹"最大数量
     */
    boolean hasMaxDummyAmmo(ItemStack gun);

    /**
     * 获取枪械当前的"虚拟备弹"最大数量
     */
    int getMaxDummyAmmoAmount(ItemStack gun);

    /**
     * 设置枪械当前的"虚拟备弹"最大数量
     */
    void setMaxDummyAmmoAmount(ItemStack gun, int amount);

    /**
     * 获取枪械的"配件锁"情况
     */
    boolean hasAttachmentLock(ItemStack gun);

    /**
     * 设置枪械的"配件锁"
     */
    void setAttachmentLock(ItemStack gun, boolean locked);

    /**
     * 获取枪械 ID
     */
    @NotNull
    Identifier getGunId(ItemStack gun);

    /**
     * 设置枪械 ID
     */
    void setGunId(ItemStack gun, @Nullable Identifier gunId);

    /**
     * 获取枪械客户端效果 ID, 如果是默认皮肤将返回 {@link DefaultAssets#DEFAULT_GUN_DISPLAY_ID}<br/>
     * 你应该使用 {@link com.tacz.guns.api.TimelessAPI#getGunDisplay(ItemStack)} 获取正确的客户端效果
     */
    @NotNull
    Identifier getGunDisplayId(ItemStack gun);

    /**
     * 设置枪械客户端效果 ID
     */
    void setGunDisplayId(ItemStack gun, @Nullable Identifier displayId);

    /**
     * 获取输入的经验值对应的等级。
     *
     * @param exp 经验值
     * @return 对应的等级
     */
    int getLevel(int exp);

    /**
     * 获取输入的等级需要至少多少的经验值。
     *
     * @param level 等级
     * @return 至少需要的经验值
     */
    int getExp(int level);

    /**
     * 返回允许的最大等级。
     *
     * @return 最大等级
     */
    int getMaxLevel();

    /**
     * 获取枪械当前等级
     */
    int getLevel(ItemStack gun);

    /**
     * 获取积累的全部经验值。
     *
     * @param gun 输入物品
     * @return 全部经验值
     */
    int getExp(ItemStack gun);

    /**
     * 获取到下个等级需要的经验值。
     *
     * @param gun 输入物品
     * @return 到下个等级需要的经验值。如果等级已经到达最大，则返回 0
     */
    int getExpToNextLevel(ItemStack gun);

    /**
     * 获取当前等级已经积累的经验值。
     *
     * @param gun 输入物品
     * @return 当前等级已经积累的经验值
     */
    int getExpCurrentLevel(ItemStack gun);

    /**
     * 获取开火模式
     *
     * @param gun 枪
     * @return 开火模式
     */
    FireMode getFireMode(ItemStack gun);

    /**
     * 设置开火模式
     */
    void setFireMode(ItemStack gun, @Nullable FireMode fireMode);

    /**
     * 获取当前枪械弹药数
     */
    int getCurrentAmmoCount(ItemStack gun);

    /**
     * 设置当前枪械弹药数
     */
    void setCurrentAmmoCount(ItemStack gun, int ammoCount);

    /**
     * 减少一个当前枪械弹药数
     */
    void reduceCurrentAmmoCount(ItemStack gun);

    /**
     * 动态修改枪械的属性。
     * 注意：对于某些复杂属性来说，{@code GunProperty} 的类型可能会和值的类型不一样。
     * 比如伤害和精准度这样的复杂属性，GunProperty 的类型是复杂的数据结构，传入和返回的值就只是简单的浮点数。
     *
     * @param dataHolder 状态数据
     * @param gunItem    枪械物品
     * @param shooter    射击者
     * @param id         属性 id，请参阅 {@link com.tacz.guns.api.GunProperties}
     * @param type       属性的数据类型
     * @param original   属性原来的值
     * @param <T>        属性的数据类型
     * @return 脚本或子类修改后的属性
     * @author ChloePrime
     * @since 1.1.7
     */
    default <T> T modifyProperty(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter,
                                 GunProperty<?> id, Class<T> type, T original) {
        return modifyProperty(dataHolder, gunItem, shooter, id.name(), type, original);
    }

    /**
     * 动态修改枪械的属性
     *
     * @param dataHolder 状态数据
     * @param gunItem    枪械物品
     * @param shooter    射击者
     * @param id         属性 id，请参阅 {@link com.tacz.guns.api.GunProperties}
     * @param type       属性的数据类型
     * @param original   属性原来的值
     * @param <T>        属性的数据类型
     * @return 脚本或子类修改后的属性
     * @author ChloePrime
     * @since 1.1.7
     */
    default <T> T modifyProperty(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter,
                                 String id, Class<T> type, T original) {
        return modifyProperty(dataHolder, gunItem, shooter, "modify_property", id, type, original);
    }

    /**
     * 动态修改枪械的属性，
     * 允许指定修改用的 lua 函数的名称
     *
     * @param dataHolder    状态数据
     * @param gunItem       枪械物品
     * @param shooter       射击者
     * @param luaMethodName 修改属性的 lua 函数的函数名
     * @param id            属性 id，请参阅 {@link com.tacz.guns.api.GunProperties}
     * @param type          属性的数据类型
     * @param original      属性原来的值
     * @param <T>           属性的数据类型
     * @return 脚本或子类修改后的属性
     * @author ChloePrime
     * @since 1.1.7
     */
    default <T> T modifyProperty(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter,
                                 String luaMethodName, String id, Class<T> type, T original) {
        return original;
    }

    /**
     * 取下枪内所有子弹。玩家的特殊方法，默认卸载弹药时使用
     */
    void dropAllAmmo(Player player, ItemStack gun);

    /**
     * 获取当前枪械指定类型的配件
     */
    @Nonnull
    ItemStack getAttachment(ItemStack gun, AttachmentType type);

    @Nonnull
    ItemStack getBuiltinAttachment(ItemStack gun, AttachmentType type);

    /**
     * 获取当前枪械指定类型的配件的 NBT 数据
     *
     * @return 如果为空，那么没有配件数据
     */
    @Nullable
    CompoundTag getAttachmentTag(ItemStack gun, AttachmentType type);

    /**
     * 写回已安装配件自身的 custom_data 标签。
     *
     * <p>第 18 轮：接口上补回该声明（上游 IGun 第 314 行有，我们移植时漏了）。
     * 实现见 {@code GunItemDataAccessor#setAttachmentTag}（第 16 轮补回）。
     * 主要用途是可变倍瞄具切换倍率后把 ZoomNumber 持久化。
     */
    void setAttachmentTag(ItemStack gun, AttachmentType type, CompoundTag attachmentTag);

    @Nonnull
    Identifier getBuiltInAttachmentId(ItemStack gun, AttachmentType type);

    /**
     * 获取枪械的配件 ID
     * <p>
     * 如果不存在，返回 {@link DefaultAssets#EMPTY_ATTACHMENT_ID};
     */
    @Nonnull
    Identifier getAttachmentId(ItemStack gun, AttachmentType type);

    /**
     * 安装配件
     */
    void installAttachment(@Nonnull ItemStack gun, @Nonnull ItemStack attachment);

    /**
     * 卸载配件
     */
    void unloadAttachment(@Nonnull ItemStack gun, AttachmentType type);

    /**
     * 该枪械是否允许装配该配件
     */
    boolean allowAttachment(ItemStack gun, ItemStack attachmentItem);

    /**
     * 该枪械是否允许某类型配件
     */
    boolean allowAttachmentType(ItemStack gun, AttachmentType type);

    /**
     * 枪管中是否有子弹，用于闭膛待击的枪械
     */
    boolean hasBulletInBarrel(ItemStack gun);

    /**
     * 设置枪管中的子弹有无，用于闭膛待击的枪械
     */
    void setBulletInBarrel(ItemStack gun, boolean bulletInBarrel);

    /**
     * 枪械是否为备弹直读
     */
    boolean useInventoryAmmo(ItemStack gun);

    /**
     * 获取枪械是否有备弹 (只针对背包直读读的机制使用)
     */
    boolean hasInventoryAmmo(LivingEntity shooter, ItemStack gun, boolean needCheckAmmo);

    /**
     * 获取 RPM
     */
    int getRPM(ItemStack gun);

    /**
     * 获取是否可以趴下
     */
    boolean isCanCrawl(ItemStack gun);

    boolean hasCustomLaserColor(ItemStack gun);

    int getLaserColor(ItemStack gun);

    void setLaserColor(ItemStack gun, int color);

    /**
     * Heat Data
     */
    boolean hasHeatData(ItemStack gun);

    /**
     * 是否完全过热
     */
    boolean isOverheatLocked(ItemStack gun);

    void setOverheatLocked(ItemStack gun, boolean locked);

    /**
     * 设置当前过热值
     */
    void setHeatAmount(ItemStack gun, float amount);

    float lerpRPM(ItemStack gun);

    float lerpInaccuracy(ItemStack gun);

    float getHeatAmount(ItemStack gun);

    // ====== 扩展：P0 基础架构新增方法 ======

    /**
     * 获取枪械运行状态数据组件。
     * <p>
     * 包含枪机循环状态、故障类型、烧蚀、枪管损伤、异物、撞针磨损、保险状态。
     * 如果不存在，返回默认数据。
     */
    default com.tacz.guns.api.item.component.GunStateData getGunStateData(ItemStack gun) {
        com.tacz.guns.api.item.component.GunStateData data = gun.get(com.tacz.guns.init.ModDataComponents.GUN_STATE_DATA);
        return data != null ? data : com.tacz.guns.api.item.component.GunStateData.createDefault();
    }

    /**
     * 设置枪械运行状态数据组件
     */
    default void setGunStateData(ItemStack gun, com.tacz.guns.api.item.component.GunStateData data) {
        gun.set(com.tacz.guns.init.ModDataComponents.GUN_STATE_DATA, data);
    }

    /**
     * 获取枪械模块化耐久数据组件。
     * <p>
     * 包含7个部件的独立耐久值。
     * 如果不存在，返回默认满耐久数据。
     */
    default com.tacz.guns.api.item.component.GunWearData getGunWearData(ItemStack gun) {
        com.tacz.guns.api.item.component.GunWearData data = gun.get(com.tacz.guns.init.ModDataComponents.GUN_WEAR_DATA);
        return data != null ? data : com.tacz.guns.api.item.component.GunWearData.createDefault();
    }

    /**
     * 设置枪械模块化耐久数据组件
     */
    default void setGunWearData(ItemStack gun, com.tacz.guns.api.item.component.GunWearData data) {
        gun.set(com.tacz.guns.init.ModDataComponents.GUN_WEAR_DATA, data);
    }

    /**
     * 获取枪械保养状态数据组件。
     * <p>
     * 包含积碳、锈蚀、润滑、枪管异物等状态。
     * 如果不存在，返回默认保养数据。
     */
    default com.tacz.guns.api.item.component.GunMaintenanceData getGunMaintenanceData(ItemStack gun) {
        com.tacz.guns.api.item.component.GunMaintenanceData data = gun.get(com.tacz.guns.init.ModDataComponents.GUN_MAINTENANCE_DATA);
        return data != null ? data : com.tacz.guns.api.item.component.GunMaintenanceData.createDefault();
    }

    /**
     * 设置枪械保养状态数据组件
     */
    default void setGunMaintenanceData(ItemStack gun, com.tacz.guns.api.item.component.GunMaintenanceData data) {
        gun.set(com.tacz.guns.init.ModDataComponents.GUN_MAINTENANCE_DATA, data);
    }

    /**
     * 获取枪械公差评分数据组件。
     * <p>
     * 如果不存在，返回基于科技阶段的默认公差数据。
     */
    default com.tacz.guns.api.item.component.ToleranceData getToleranceData(ItemStack gun) {
        com.tacz.guns.api.item.component.ToleranceData data = gun.get(com.tacz.guns.init.ModDataComponents.TOLERANCE_DATA);
        return data != null ? data : com.tacz.guns.api.item.component.ToleranceData.createDefault(0);
    }

    /**
     * 设置枪械公差评分数据组件
     */
    default void setToleranceData(ItemStack gun, com.tacz.guns.api.item.component.ToleranceData data) {
        gun.set(com.tacz.guns.init.ModDataComponents.TOLERANCE_DATA, data);
    }

    // ====== 扩展：P0 补充 供弹具数据系统 ======

    /**
     * 获取供弹具数据组件。
     * <p>
     * 如果枪上安装了可拆卸供弹具（弹匣/弹鼓），返回其内部数据。
     * 如果不存在，返回 null。
     * <p>
     * 注意：此方法获取的是枪上已安装的供弹具数据。
     * 独立存在于背包中的供弹具物品，其数据通过 ItemStack 的 DataComponent 直接获取。
     */
    default com.tacz.guns.api.item.component.FeedDeviceData getFeedDeviceData(ItemStack gun) {
        return gun.get(com.tacz.guns.init.ModDataComponents.FEED_DEVICE_DATA);
    }

    /**
     * 设置供弹具数据组件。
     * <p>
     * 当供弹具安装到枪上或从枪上取下时调用。
     */
    default void setFeedDeviceData(ItemStack gun, com.tacz.guns.api.item.component.FeedDeviceData data) {
        gun.set(com.tacz.guns.init.ModDataComponents.FEED_DEVICE_DATA, data);
    }

    /**
     * 检查枪上是否安装了供弹具。
     */
    default boolean hasFeedDevice(ItemStack gun) {
        return getFeedDeviceData(gun) != null;
    }

    /**
     * 获取枪膛内弹药（从 GunStateData 中获取）。
     * <p>
     * P0补充：替代原有的 {@link #hasBulletInBarrel(ItemStack)} 简单布尔值，
     * 现在可以追溯到具体这一发子弹的完整数据。
     */
    default com.tacz.guns.api.item.component.LoadedRound getChamberedRound(ItemStack gun) {
        com.tacz.guns.api.item.component.GunStateData stateData = getGunStateData(gun);
        return stateData.chamberedRound();
    }

    /**
     * 设置枪膛内弹药。
     */
    default void setChamberedRound(ItemStack gun, com.tacz.guns.api.item.component.LoadedRound round) {
        com.tacz.guns.api.item.component.GunStateData stateData = getGunStateData(gun);
        setGunStateData(gun, stateData.withChamberedRound(round));
    }

    /**
     * 检查枪膛内是否有弹。
     * <p>
     * 兼容旧接口 {@link #hasBulletInBarrel(ItemStack)}，
     * 现在基于 GunStateData 的 chamberedRound 判定。
     */
    default boolean hasChamberedRound(ItemStack gun) {
        com.tacz.guns.api.item.component.GunStateData stateData = getGunStateData(gun);
        return stateData.hasChamberedRound();
    }
}