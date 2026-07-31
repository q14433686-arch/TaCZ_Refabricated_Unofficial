package com.tacz.guns.api.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 枪械模块化耐久数据组件。
 * <p>
 * 每个独立部件拥有独立耐久值，而非武器整体一个耐久条。
 * <p>
 * 对应设计文档：I.4.1 模块化耐久数据
 * <p>
 * 默认最大耐久值（可通过JSON配置覆盖）：
 * <ul>
 *   <li>枪管(barrel): 5000</li>
 *   <li>机匣(receiver): 20000</li>
 *   <li>枪机(bolt): 10000</li>
 *   <li>复进簧(recoil_spring): 5000</li>
 *   <li>弹匣弹簧(magazine_spring): 2000</li>
 *   <li>扳机组(trigger_group): 15000</li>
 *   <li>枪口装置(muzzle_device): 8000</li>
 * </ul>
 */
public record GunWearData(
        int barrelDurability,
        int receiverDurability,
        int boltDurability,
        int recoilSpringDurability,
        int magazineSpringDurability,
        int triggerGroupDurability,
        int muzzleDeviceDurability
) {
    // 默认最大耐久值
    public static final int DEFAULT_BARREL_MAX = 5000;
    public static final int DEFAULT_RECEIVER_MAX = 20000;
    public static final int DEFAULT_BOLT_MAX = 10000;
    public static final int DEFAULT_RECOIL_SPRING_MAX = 5000;
    public static final int DEFAULT_MAGAZINE_SPRING_MAX = 2000;
    public static final int DEFAULT_TRIGGER_GROUP_MAX = 15000;
    public static final int DEFAULT_MUZZLE_DEVICE_MAX = 8000;

    /**
     * 创建满耐久的默认数据
     */
    public static GunWearData createDefault() {
        return new GunWearData(
                DEFAULT_BARREL_MAX,
                DEFAULT_RECEIVER_MAX,
                DEFAULT_BOLT_MAX,
                DEFAULT_RECOIL_SPRING_MAX,
                DEFAULT_MAGAZINE_SPRING_MAX,
                DEFAULT_TRIGGER_GROUP_MAX,
                DEFAULT_MUZZLE_DEVICE_MAX
        );
    }

    public static final Codec<GunWearData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("barrel").forGetter(GunWearData::barrelDurability),
                    Codec.INT.fieldOf("receiver").forGetter(GunWearData::receiverDurability),
                    Codec.INT.fieldOf("bolt").forGetter(GunWearData::boltDurability),
                    Codec.INT.fieldOf("recoil_spring").forGetter(GunWearData::recoilSpringDurability),
                    Codec.INT.fieldOf("magazine_spring").forGetter(GunWearData::magazineSpringDurability),
                    Codec.INT.fieldOf("trigger_group").forGetter(GunWearData::triggerGroupDurability),
                    Codec.INT.fieldOf("muzzle_device").forGetter(GunWearData::muzzleDeviceDurability)
            ).apply(instance, GunWearData::new)
    );

    public static final StreamCodec<ByteBuf, GunWearData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, GunWearData::barrelDurability,
            ByteBufCodecs.INT, GunWearData::receiverDurability,
            ByteBufCodecs.INT, GunWearData::boltDurability,
            ByteBufCodecs.INT, GunWearData::recoilSpringDurability,
            ByteBufCodecs.INT, GunWearData::magazineSpringDurability,
            ByteBufCodecs.INT, GunWearData::triggerGroupDurability,
            ByteBufCodecs.INT, GunWearData::muzzleDeviceDurability,
            GunWearData::new
    );

    /**
     * 计算综合可靠性修正（0.0~1.0）
     * 使用加权平均公式
     */
    public float calculateOverallReliability() {
        float barrelWear = 1.0f - (float) barrelDurability / DEFAULT_BARREL_MAX;
        float receiverWear = 1.0f - (float) receiverDurability / DEFAULT_RECEIVER_MAX;
        float boltWear = 1.0f - (float) boltDurability / DEFAULT_BOLT_MAX;
        float recoilSpringWear = 1.0f - (float) recoilSpringDurability / DEFAULT_RECOIL_SPRING_MAX;
        float magazineSpringWear = 1.0f - (float) magazineSpringDurability / DEFAULT_MAGAZINE_SPRING_MAX;
        float triggerGroupWear = 1.0f - (float) triggerGroupDurability / DEFAULT_TRIGGER_GROUP_MAX;

        // 权重：枪机(0.30) + 机匣(0.20) + 复进簧(0.20) + 弹匣弹簧(0.15) + 扳机组(0.05) + 枪管(0.10)
        float weightedWear = barrelWear * 0.10f
                + receiverWear * 0.20f
                + boltWear * 0.30f
                + recoilSpringWear * 0.20f
                + magazineSpringWear * 0.15f
                + triggerGroupWear * 0.05f;

        return 1.0f - weightedWear;
    }

    /**
     * 计算综合精度修正（0.0~1.0）
     */
    public float calculateOverallAccuracy() {
        float barrelWear = 1.0f - (float) barrelDurability / DEFAULT_BARREL_MAX;
        float receiverWear = 1.0f - (float) receiverDurability / DEFAULT_RECEIVER_MAX;
        float boltWear = 1.0f - (float) boltDurability / DEFAULT_BOLT_MAX;
        float muzzleDeviceWear = 1.0f - (float) muzzleDeviceDurability / DEFAULT_MUZZLE_DEVICE_MAX;

        // 权重：枪管(0.40) + 机匣(0.10) + 枪机(0.15) + 枪口装置(0.25) + 扳机组(0.05) + 复进簧(0.05)
        float weightedWear = barrelWear * 0.40f
                + receiverWear * 0.10f
                + boltWear * 0.15f
                + muzzleDeviceWear * 0.25f
                + (1.0f - (float) triggerGroupDurability / DEFAULT_TRIGGER_GROUP_MAX) * 0.05f
                + (1.0f - (float) recoilSpringDurability / DEFAULT_RECOIL_SPRING_MAX) * 0.05f;

        return 1.0f - weightedWear;
    }

    /**
     * 消耗枪管耐久
     */
    public GunWearData withBarrelWear(int amount) {
        return new GunWearData(
                Math.max(0, barrelDurability - amount),
                receiverDurability, boltDurability, recoilSpringDurability,
                magazineSpringDurability, triggerGroupDurability, muzzleDeviceDurability
        );
    }

    /**
     * 消耗所有部件耐久（射击时，各部件消耗量不同）
     */
    public GunWearData withShootWear(int barrelWear, int receiverWear, int boltWear,
                                      int recoilSpringWear, int triggerGroupWear, int muzzleDeviceWear) {
        return new GunWearData(
                Math.max(0, barrelDurability - barrelWear),
                Math.max(0, receiverDurability - receiverWear),
                Math.max(0, boltDurability - boltWear),
                Math.max(0, recoilSpringDurability - recoilSpringWear),
                magazineSpringDurability,
                Math.max(0, triggerGroupDurability - triggerGroupWear),
                Math.max(0, muzzleDeviceDurability - muzzleDeviceWear)
        );
    }

    /**
     * 消耗弹匣弹簧耐久（每次装弹循环）
     */
    public GunWearData withMagazineSpringWear(int amount) {
        return new GunWearData(
                barrelDurability, receiverDurability, boltDurability, recoilSpringDurability,
                Math.max(0, magazineSpringDurability - amount),
                triggerGroupDurability, muzzleDeviceDurability
        );
    }
}
