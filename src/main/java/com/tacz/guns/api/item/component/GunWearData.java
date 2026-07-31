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
 * 7个部件的独立耐久值（当前值/最大值均可配置）：
 * <ul>
 *   <li>枪管(barrel): 默认5000 — 每次射击消耗1，精度权重0.40</li>
 *   <li>机匣(receiver): 默认20000 — 每次射击消耗1，可靠性权重0.20</li>
 *   <li>枪机(bolt): 默认10000 — 每次射击消耗1，可靠性权重0.30</li>
 *   <li>复进簧(recoil_spring): 默认5000 — 每次射击消耗1，可靠性权重0.20</li>
 *   <li>弹匣弹簧(magazine_spring): 默认2000 — 每次供弹循环消耗1，可靠性权重0.15</li>
 *   <li>扳机组(trigger_group): 默认15000 — 每次扣扳机消耗1，可靠性权重0.05</li>
 *   <li>枪口装置(muzzle_device): 默认8000 — 每次射击消耗1，精度权重0.25</li>
 * </ul>
 * <p>
 * P1 收尾：升级为完整的耐久系统，包括：
 * <ul>
 *   <li>每个部件独立最大耐久值（可 JSON 配置）</li>
 *   <li>按文档 I.2.1 的磨损速率表独立扣减</li>
 *   <li>加权可靠性/精度公式（文档 I.2.2）</li>
 *   <li>维修/更换接口（文档 I.2.4）</li>
 * </ul>
 */
public record GunWearData(
        // 当前耐久值
        int barrelDurability,
        int receiverDurability,
        int boltDurability,
        int recoilSpringDurability,
        int magazineSpringDurability,
        int triggerGroupDurability,
        int muzzleDeviceDurability,
        // 最大耐久值（可 JSON 配置，允许不同枪械有不同的最大耐久）
        int barrelMax,
        int receiverMax,
        int boltMax,
        int recoilSpringMax,
        int magazineSpringMax,
        int triggerGroupMax,
        int muzzleDeviceMax
) {
    // ====== 默认最大耐久值（文档 I.2.1） ======
    public static final int DEFAULT_BARREL_MAX = 5000;
    public static final int DEFAULT_RECEIVER_MAX = 20000;
    public static final int DEFAULT_BOLT_MAX = 10000;
    public static final int DEFAULT_RECOIL_SPRING_MAX = 5000;
    public static final int DEFAULT_MAGAZINE_SPRING_MAX = 2000;
    public static final int DEFAULT_TRIGGER_GROUP_MAX = 15000;
    public static final int DEFAULT_MUZZLE_DEVICE_MAX = 8000;

    /**
     * 创建满耐久的默认数据（使用默认最大值）
     */
    public static GunWearData createDefault() {
        return new GunWearData(
                DEFAULT_BARREL_MAX, DEFAULT_RECEIVER_MAX, DEFAULT_BOLT_MAX,
                DEFAULT_RECOIL_SPRING_MAX, DEFAULT_MAGAZINE_SPRING_MAX,
                DEFAULT_TRIGGER_GROUP_MAX, DEFAULT_MUZZLE_DEVICE_MAX,
                DEFAULT_BARREL_MAX, DEFAULT_RECEIVER_MAX, DEFAULT_BOLT_MAX,
                DEFAULT_RECOIL_SPRING_MAX, DEFAULT_MAGAZINE_SPRING_MAX,
                DEFAULT_TRIGGER_GROUP_MAX, DEFAULT_MUZZLE_DEVICE_MAX
        );
    }

    /**
     * 创建满耐久的自定义数据（使用指定的最大值）
     */
    public static GunWearData createCustom(int barrelMax, int receiverMax, int boltMax,
                                            int recoilSpringMax, int magazineSpringMax,
                                            int triggerGroupMax, int muzzleDeviceMax) {
        return new GunWearData(
                barrelMax, receiverMax, boltMax, recoilSpringMax,
                magazineSpringMax, triggerGroupMax, muzzleDeviceMax,
                barrelMax, receiverMax, boltMax, recoilSpringMax,
                magazineSpringMax, triggerGroupMax, muzzleDeviceMax
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
                    Codec.INT.fieldOf("muzzle_device").forGetter(GunWearData::muzzleDeviceDurability),
                    Codec.INT.fieldOf("barrel_max").forGetter(GunWearData::barrelMax),
                    Codec.INT.fieldOf("receiver_max").forGetter(GunWearData::receiverMax),
                    Codec.INT.fieldOf("bolt_max").forGetter(GunWearData::boltMax),
                    Codec.INT.fieldOf("recoil_spring_max").forGetter(GunWearData::recoilSpringMax),
                    Codec.INT.fieldOf("magazine_spring_max").forGetter(GunWearData::magazineSpringMax),
                    Codec.INT.fieldOf("trigger_group_max").forGetter(GunWearData::triggerGroupMax),
                    Codec.INT.fieldOf("muzzle_device_max").forGetter(GunWearData::muzzleDeviceMax)
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
            ByteBufCodecs.INT, GunWearData::barrelMax,
            ByteBufCodecs.INT, GunWearData::receiverMax,
            ByteBufCodecs.INT, GunWearData::boltMax,
            ByteBufCodecs.INT, GunWearData::recoilSpringMax,
            ByteBufCodecs.INT, GunWearData::magazineSpringMax,
            ByteBufCodecs.INT, GunWearData::triggerGroupMax,
            ByteBufCodecs.INT, GunWearData::muzzleDeviceMax,
            GunWearData::new
    );

    // ====== 磨损比例（0.0=完好，1.0=完全磨损） ======

    public float barrelWearRatio() { return 1.0f - (float) barrelDurability / barrelMax; }
    public float receiverWearRatio() { return 1.0f - (float) receiverDurability / receiverMax; }
    public float boltWearRatio() { return 1.0f - (float) boltDurability / boltMax; }
    public float recoilSpringWearRatio() { return 1.0f - (float) recoilSpringDurability / recoilSpringMax; }
    public float magazineSpringWearRatio() { return 1.0f - (float) magazineSpringDurability / magazineSpringMax; }
    public float triggerGroupWearRatio() { return 1.0f - (float) triggerGroupDurability / triggerGroupMax; }
    public float muzzleDeviceWearRatio() { return 1.0f - (float) muzzleDeviceDurability / muzzleDeviceMax; }

    // ====== 加权公式（文档 I.2.2） ======

    /**
     * 计算综合可靠性修正（0.0~1.0）。
     * <p>
     * 权重表（文档 I.2.2）：
     * <ul>
     *   <li>枪机: 0.30（枪机磨损直接影响供弹/抽壳/抛壳可靠性）</li>
     *   <li>机匣: 0.20（机匣变形影响所有运动部件的配合）</li>
     *   <li>复进簧: 0.20（复进簧疲劳直接影响供弹可靠性）</li>
     *   <li>弹匣弹簧: 0.15（弹匣弹簧疲劳影响供弹可靠性）</li>
     *   <li>扳机组: 0.05（扳机组磨损影响击发可靠性）</li>
     *   <li>枪管: 0.10（枪管磨损对可靠性影响较小）</li>
     * </ul>
     * <p>
     * 注意：此公式与 FeedDeviceData 中的 springFatigue 是两套独立的系统。
     * springFatigue 是供弹具的磨损，此处的 magazineSpringWear 是枪械自带弹簧的磨损。
     * 两者通过加权公式统一：recoilSpringWear(0.20) + magazineSpringWear(0.15) = 0.35
     * 覆盖了弹簧疲劳对可靠性的影响，不存在双重计算。
     */
    public float calculateOverallReliability() {
        float weightedWear = barrelWearRatio() * 0.10f
                + receiverWearRatio() * 0.20f
                + boltWearRatio() * 0.30f
                + recoilSpringWearRatio() * 0.20f
                + magazineSpringWearRatio() * 0.15f
                + triggerGroupWearRatio() * 0.05f;
        return 1.0f - weightedWear;
    }

    /**
     * 计算综合精度修正（0.0~1.0）。
     * <p>
     * 权重表（文档 I.2.2）：
     * <ul>
     *   <li>枪管: 0.40（枪管磨损是精度下降的首要因素）</li>
     *   <li>枪口装置: 0.25（枪口装置磨损影响制退/消焰效果）</li>
     *   <li>枪机: 0.15（枪机闭锁间隙影响弹膛同心度）</li>
     *   <li>机匣: 0.10（机匣变形影响导轨精度）</li>
     *   <li>扳机组: 0.05（扳机行程影响射击精度）</li>
     *   <li>复进簧: 0.05（复进簧影响枪机回位精度）</li>
     * </ul>
     */
    public float calculateOverallAccuracy() {
        float weightedWear = barrelWearRatio() * 0.40f
                + receiverWearRatio() * 0.10f
                + boltWearRatio() * 0.15f
                + muzzleDeviceWearRatio() * 0.25f
                + triggerGroupWearRatio() * 0.05f
                + recoilSpringWearRatio() * 0.05f;
        return 1.0f - weightedWear;
    }

    // ====== 磨损消耗（文档 I.2.1 磨损速率表） ======

    /**
     * 射击一次的耐久消耗。
     * <p>
     * 各部件消耗量不同（文档 I.2.1）：
     * <ul>
     *   <li>枪管: 1（膛压直接磨损）</li>
     *   <li>机匣: 1（后坐力传导）</li>
     *   <li>枪机: 1（开锁/闭锁冲击）</li>
     *   <li>复进簧: 1（压缩/伸展循环）</li>
     *   <li>枪口装置: 1（火药燃气冲刷）</li>
     *   <li>扳机组: 1（击发机构磨损）</li>
     *   <li>弹匣弹簧: 0（射击时不消耗，供弹循环时消耗）</li>
     * </ul>
     * <p>
     * 装药过量额外消耗：枪管+1，枪机+1
     * 腐蚀性弹药额外消耗：枪管+2
     */
    public GunWearData withShootWear(boolean overcharged, boolean corrosive) {
        int barrelWear = 1 + (overcharged ? 1 : 0) + (corrosive ? 2 : 0);
        int receiverWear = 1;
        int boltWear = 1 + (overcharged ? 1 : 0);
        int recoilSpringWear = 1;
        int triggerGroupWear = 1;
        int muzzleDeviceWear = 1;
        return new GunWearData(
                Math.max(0, barrelDurability - barrelWear),
                Math.max(0, receiverDurability - receiverWear),
                Math.max(0, boltDurability - boltWear),
                Math.max(0, recoilSpringDurability - recoilSpringWear),
                magazineSpringDurability,  // 射击时不消耗弹匣弹簧
                Math.max(0, triggerGroupDurability - triggerGroupWear),
                Math.max(0, muzzleDeviceDurability - muzzleDeviceWear),
                barrelMax, receiverMax, boltMax, recoilSpringMax,
                magazineSpringMax, triggerGroupMax, muzzleDeviceMax
        );
    }

    /**
     * 供弹循环的弹匣弹簧消耗（每次从供弹具取弹时调用）。
     * <p>
     * 弹匣弹簧: 1（每次供弹循环压缩/伸展）
     */
    public GunWearData withMagazineSpringWear() {
        return new GunWearData(
                barrelDurability, receiverDurability, boltDurability, recoilSpringDurability,
                Math.max(0, magazineSpringDurability - 1),
                triggerGroupDurability, muzzleDeviceDurability,
                barrelMax, receiverMax, boltMax, recoilSpringMax,
                magazineSpringMax, triggerGroupMax, muzzleDeviceMax
        );
    }

    // ====== 维修/更换（文档 I.2.4） ======

    /**
     * 部件标识符，用于维修/更换接口。
     */
    public enum ComponentType {
        BARREL, RECEIVER, BOLT, RECOIL_SPRING, MAGAZINE_SPRING, TRIGGER_GROUP, MUZZLE_DEVICE
    }

    /**
     * 获取指定部件的磨损比例。
     */
    public float getWearRatio(ComponentType type) {
        return switch (type) {
            case BARREL -> barrelWearRatio();
            case RECEIVER -> receiverWearRatio();
            case BOLT -> boltWearRatio();
            case RECOIL_SPRING -> recoilSpringWearRatio();
            case MAGAZINE_SPRING -> magazineSpringWearRatio();
            case TRIGGER_GROUP -> triggerGroupWearRatio();
            case MUZZLE_DEVICE -> muzzleDeviceWearRatio();
        };
    }

    /**
     * 现场保养：磨损 &lt; 30% 时，恢复 10% 最大耐久。
     * <p>
     * 对应文档 I.2.4：现场保养（仅限轻微磨损）
     *
     * @param type 要保养的部件
     * @return 保养后的数据，如果磨损超过30%则返回 this
     */
    public GunWearData fieldMaintain(ComponentType type) {
        if (getWearRatio(type) >= 0.30f) return this;
        int restore = (int) (getMax(type) * 0.10f);
        return withComponentDurability(type, Math.min(getMax(type), getCurrent(type) + restore));
    }

    /**
     * 工作台修复：磨损 30%-70% 时，恢复 50% 最大耐久。
     * <p>
     * 对应文档 I.2.4：工作台修复（中等磨损，需要材料消耗）
     *
     * @param type 要修复的部件
     * @return 修复后的数据，如果磨损不在30%-70%范围则返回 this
     */
    public GunWearData benchRepair(ComponentType type) {
        float wear = getWearRatio(type);
        if (wear < 0.30f || wear > 0.70f) return this;
        int restore = (int) (getMax(type) * 0.50f);
        return withComponentDurability(type, Math.min(getMax(type), getCurrent(type) + restore));
    }

    /**
     * 更换部件：磨损 &gt; 70% 时，替换为全新部件。
     * <p>
     * 对应文档 I.2.4：更换部件（严重磨损，需要全新部件物品）
     *
     * @param type 要更换的部件
     * @return 更换后的数据（该部件耐久恢复到最大值），如果磨损未超过70%则返回 this
     */
    public GunWearData replaceComponent(ComponentType type) {
        if (getWearRatio(type) <= 0.70f) return this;
        return withComponentDurability(type, getMax(type));
    }

    /**
     * 强制更换部件（不受磨损限制，用于测试/创造模式）。
     */
    public GunWearData forceReplace(ComponentType type) {
        return withComponentDurability(type, getMax(type));
    }

    /**
     * 强制更换多个部件（不受磨损限制，用于炸膛等极端后果）。
     */
    public GunWearData forceReplace(ComponentType... types) {
        GunWearData result = this;
        for (ComponentType type : types) {
            result = result.withComponentDurability(type, getMax(type));
        }
        return result;
    }

    /**
     * 获取指定部件的当前耐久值。
     */
    public int getCurrent(ComponentType type) {
        return switch (type) {
            case BARREL -> barrelDurability;
            case RECEIVER -> receiverDurability;
            case BOLT -> boltDurability;
            case RECOIL_SPRING -> recoilSpringDurability;
            case MAGAZINE_SPRING -> magazineSpringDurability;
            case TRIGGER_GROUP -> triggerGroupDurability;
            case MUZZLE_DEVICE -> muzzleDeviceDurability;
        };
    }

    /**
     * 获取指定部件的最大耐久值。
     */
    public int getMax(ComponentType type) {
        return switch (type) {
            case BARREL -> barrelMax;
            case RECEIVER -> receiverMax;
            case BOLT -> boltMax;
            case RECOIL_SPRING -> recoilSpringMax;
            case MAGAZINE_SPRING -> magazineSpringMax;
            case TRIGGER_GROUP -> triggerGroupMax;
            case MUZZLE_DEVICE -> muzzleDeviceMax;
        };
    }

    /**
     * 设置指定部件的当前耐久值。
     */
    public GunWearData withComponentDurability(ComponentType type, int value) {
        return switch (type) {
            case BARREL -> new GunWearData(value, receiverDurability, boltDurability, recoilSpringDurability,
                    magazineSpringDurability, triggerGroupDurability, muzzleDeviceDurability,
                    barrelMax, receiverMax, boltMax, recoilSpringMax, magazineSpringMax, triggerGroupMax, muzzleDeviceMax);
            case RECEIVER -> new GunWearData(barrelDurability, value, boltDurability, recoilSpringDurability,
                    magazineSpringDurability, triggerGroupDurability, muzzleDeviceDurability,
                    barrelMax, receiverMax, boltMax, recoilSpringMax, magazineSpringMax, triggerGroupMax, muzzleDeviceMax);
            case BOLT -> new GunWearData(barrelDurability, receiverDurability, value, recoilSpringDurability,
                    magazineSpringDurability, triggerGroupDurability, muzzleDeviceDurability,
                    barrelMax, receiverMax, boltMax, recoilSpringMax, magazineSpringMax, triggerGroupMax, muzzleDeviceMax);
            case RECOIL_SPRING -> new GunWearData(barrelDurability, receiverDurability, boltDurability, value,
                    magazineSpringDurability, triggerGroupDurability, muzzleDeviceDurability,
                    barrelMax, receiverMax, boltMax, recoilSpringMax, magazineSpringMax, triggerGroupMax, muzzleDeviceMax);
            case MAGAZINE_SPRING -> new GunWearData(barrelDurability, receiverDurability, boltDurability, recoilSpringDurability,
                    value, triggerGroupDurability, muzzleDeviceDurability,
                    barrelMax, receiverMax, boltMax, recoilSpringMax, magazineSpringMax, triggerGroupMax, muzzleDeviceMax);
            case TRIGGER_GROUP -> new GunWearData(barrelDurability, receiverDurability, boltDurability, recoilSpringDurability,
                    magazineSpringDurability, value, muzzleDeviceDurability,
                    barrelMax, receiverMax, boltMax, recoilSpringMax, magazineSpringMax, triggerGroupMax, muzzleDeviceMax);
            case MUZZLE_DEVICE -> new GunWearData(barrelDurability, receiverDurability, boltDurability, recoilSpringDurability,
                    magazineSpringDurability, triggerGroupDurability, value,
                    barrelMax, receiverMax, boltMax, recoilSpringMax, magazineSpringMax, triggerGroupMax, muzzleDeviceMax);
        };
    }
}
