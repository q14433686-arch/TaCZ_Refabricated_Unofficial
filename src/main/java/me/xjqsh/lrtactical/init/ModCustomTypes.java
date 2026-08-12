package me.xjqsh.lrtactical.init;

import me.xjqsh.lrtactical.entity.GrenadeEntity;
import me.xjqsh.lrtactical.item.throwable.ThrowableType;
import me.xjqsh.lrtactical.item.throwable.explode.ExplodeThrowableData;
import me.xjqsh.lrtactical.item.throwable.explode.ExplodeType;

/**
 * 投掷物 / 近战「类型」的注册。
 *
 * <p>数据包里写 {@code "type": "lrtactical:explode"}，加载器据此在这里查表，
 * 找到对应的解析器与实体工厂。
 *
 * <p>五种上游投掷物类型（explode / sticky / smoke / stun / effect_cloud）及其实体均已注册。
 * 近战沿用上游唯一的 {@code normal} 类型，具体动作、碰撞体与倍率继续由数据驱动。
 */
public final class ModCustomTypes {
    public static final ThrowableType<ExplodeThrowableData, GrenadeEntity> EXPLODE =
            ModRegistries.registerThrowableType(ModRegistries.id("explode"), ExplodeType.EXPLODE);

    /**
     * 粘性手雷 —— 与 {@link #EXPLODE} <b>共用</b> {@link ExplodeThrowableData}。
     *
     * <p>数据层完全相同（伤害/半径/连锁引爆都通用），差异只在实体行为，
     * 因此不需要新的 data 类。内容包只要把 {@code "type"} 从
     * {@code lrtactical:explode} 改成 {@code lrtactical:sticky} 即可。
     */
    public static final ThrowableType<ExplodeThrowableData, me.xjqsh.lrtactical.entity.StickyGrenadeEntity> STICKY =
            ModRegistries.registerThrowableType(ModRegistries.id("sticky"),
                    me.xjqsh.lrtactical.item.throwable.explode.StickyType.STICKY);

    /** 烟雾弹 —— 数据用基础 ThrowableData，无专属 data 类。 */
    public static final ThrowableType<me.xjqsh.lrtactical.item.throwable.ThrowableData,
            me.xjqsh.lrtactical.entity.SmokeGrenadeEntity> SMOKE =
            ModRegistries.registerThrowableType(ModRegistries.id("smoke"),
                    me.xjqsh.lrtactical.item.throwable.smoke.SmokeType.SMOKE);

    /** 效果云 —— 药水云 / 喷溅弹。 */
    public static final ThrowableType<me.xjqsh.lrtactical.item.throwable.area.EffectCloudThrowableData,
            me.xjqsh.lrtactical.entity.EffectCloudGrenadeEntity> EFFECT_CLOUD =
            ModRegistries.registerThrowableType(ModRegistries.id("effect_cloud"),
                    me.xjqsh.lrtactical.item.throwable.area.CloudType.EFFECT_CLOUD);

    /** 闪光弹 —— 致盲 + 耳鸣。 */
    public static final ThrowableType<me.xjqsh.lrtactical.item.throwable.flash.StunThrowableData,
            me.xjqsh.lrtactical.entity.StunGrenadeEntity> STUN =
            ModRegistries.registerThrowableType(ModRegistries.id("stun"),
                    me.xjqsh.lrtactical.item.throwable.flash.StunType.STUN);

    /**
     * 唯一的近战武器类型。
     *
     * <p>上游同样只有 {@code normal} 一种 —— 近战的差异全部由数据驱动
     * （{@code CombatData} 里的连招/hitbox/倍率），不需要多个类型。
     * 因此 serializer 直接用通用的 {@code MeleeWeaponData}。
     */
    public static final me.xjqsh.lrtactical.item.melee.MeleeWeaponType<me.xjqsh.lrtactical.item.melee.MeleeWeaponData> NORMAL_MELEE =
            ModRegistries.registerMeleeType(ModRegistries.id("normal"),
                    new me.xjqsh.lrtactical.item.melee.MeleeWeaponType<>(json ->
                            me.xjqsh.lrtactical.resource.CommonAssetsManager.GSON.fromJson(
                                    json, me.xjqsh.lrtactical.item.melee.MeleeWeaponData.class)));

    private ModCustomTypes() {
    }

    public static void init() {
        // 触发静态初始化，完成注册
    }
}
