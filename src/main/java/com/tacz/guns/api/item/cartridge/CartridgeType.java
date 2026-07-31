package com.tacz.guns.api.item.cartridge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.item.enums.RimType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 口径/弹药规格类型。
 * <p>
 * <b>只保留物理规格常量</b>——这些才是口径真正恒定不变的物理事实。
 * <p>
 * 与 {@link com.tacz.guns.api.item.enums.BulletType} 职责明确区分：
 * <ul>
 *   <li>CartridgeType — 决定口径/弹壳几何/物理兼容性（能否装进某把枪/某个弹匣）</li>
 *   <li>BulletType — 决定弹头终点弹道效果（FMJ/AP/空尖/曳光/亚音速）</li>
 * </ul>
 * <p>
 * <b>设计哲学：此 record 不含任何"衍生值"。</b>
 * <ul>
 *   <li>❌ 不含伤害值 — 伤害由终末弹道公式在命中时实时计算：f(命中区域, 弹头类型, 动能, ...)</li>
 *   <li>❌ 不含初速 — 初速由弹道公式在开火时实时计算：f(弹头质量, 装药量, 枪管长度, ...)</li>
 *   <li>❌ 不含有效射程 — 射程由弹道/风偏/散布综合决定</li>
 * </ul>
 * <p>
 * 这些衍生值应该在 P4/P5 由公式在开火那一刻实时算出来：
 * <pre>
 * 实际初速 = f(cartridge.standardBulletMass, loadedRound.powderCharge, gunData.barrelLength, ...)
 * 实际动能 = 0.5 × 弹头质量 × 初速²
 * 最终伤害 = f(命中区域, 弹头类型, 动能是否足够穿透护甲, ...)
 * </pre>
 * <p>
 * 数据驱动注册：通过 {@link CartridgeTypeManager} 注册，由 {@link Identifier} 唯一标识，
 * 后续可通过 JSON 数据包或 API 持续新增口径，无需修改代码。
 * <p>
 * 对应设计文档：B.1 口径体系 & N.1 供弹具机构差异化
 */
public record CartridgeType(
        /** 口径显示名称，如 "9×19mm Parabellum" */
        String displayName,
        /** 弹头直径（mm），如 9.0 —— 恒定物理事实 */
        float bulletDiameter,
        /** 弹壳长度（mm），如 19.15 —— 恒定物理事实 */
        float caseLength,
        /** 全弹长（mm），如 29.69 —— 恒定物理事实，决定弹匣/弹仓几何 */
        float overallLength,
        /** 底缘直径（mm） —— 恒定物理事实，决定抽壳钩兼容 */
        float rimDiameter,
        /** 底缘类型 —— 恒定物理事实，决定抽壳钩设计和管状弹仓安全性 */
        RimType rimType,
        /** 最大安全膛压（MPa），SAAMI/CIP 标准值 —— 恒定物理事实，用于炸膛判定的分母 */
        float maxSafePressure,
        /** 标准弹头质量（克），如 7.45 —— 恒定参考值，用于动能计算的输入 */
        float standardBulletMass,
        /** 弹壳容积（cm³） —— 恒定物理事实，决定这个壳最多能装多少药（过量装填的物理上限） */
        float caseCapacity,
        /** 制造所需最低科技阶段（0=T0, 4=T4） —— 游戏设计常量 */
        int techLevel
) {
    /** 9×19mm Parabellum — 最常见的手枪口径 */
    public static final CartridgeType NINE_MM = new CartridgeType(
            "9×19mm Parabellum", 9.0f, 19.15f, 29.69f,
            9.96f, RimType.RIMLESS, 235.0f,
            7.45f, 0.86f, 2
    );

    /** 5.56×45mm NATO — 突击步枪标准口径 */
    public static final CartridgeType FIVE_FIVE_SIX = new CartridgeType(
            "5.56×45mm NATO", 5.56f, 44.70f, 57.40f,
            9.60f, RimType.RIMLESS, 380.0f,
            4.00f, 1.76f, 3
    );

    /** 7.62×39mm — AK系列标准口径 */
    public static final CartridgeType SEVEN_SIX_TWO_39 = new CartridgeType(
            "7.62×39mm", 7.62f, 38.70f, 56.00f,
            11.35f, RimType.RIMLESS, 355.0f,
            7.90f, 1.56f, 2
    );

    /** 7.62×51mm NATO — 通用机枪/狙击步枪口径 */
    public static final CartridgeType SEVEN_SIX_TWO_51 = new CartridgeType(
            "7.62×51mm NATO", 7.62f, 51.05f, 71.12f,
            11.94f, RimType.RIMLESS, 415.0f,
            9.33f, 3.64f, 3
    );

    /** .45 ACP — 大口径手枪弹 */
    public static final CartridgeType FORTY_FIVE_ACP = new CartridgeType(
            ".45 ACP", 11.43f, 22.81f, 32.39f,
            12.09f, RimType.RIMLESS, 131.0f,
            14.90f, 0.94f, 2
    );

    /** 12号霰弹 — 霰弹枪口径 */
    public static final CartridgeType TWELVE_GAUGE = new CartridgeType(
            "12 Gauge", 18.53f, 69.85f, 76.20f,
            20.32f, RimType.RIMMED, 85.0f,
            28.35f, 4.18f, 1
    );

    /** .50 BMG — 重机枪/反器材口径 */
    public static final CartridgeType FIFTY_BMG = new CartridgeType(
            ".50 BMG", 12.70f, 99.06f, 138.43f,
            13.97f, RimType.RIMLESS, 380.0f,
            42.00f, 18.36f, 4
    );

    public static final Codec<CartridgeType> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("display_name").forGetter(CartridgeType::displayName),
                    Codec.FLOAT.fieldOf("bullet_diameter").forGetter(CartridgeType::bulletDiameter),
                    Codec.FLOAT.fieldOf("case_length").forGetter(CartridgeType::caseLength),
                    Codec.FLOAT.fieldOf("overall_length").forGetter(CartridgeType::overallLength),
                    Codec.FLOAT.fieldOf("rim_diameter").forGetter(CartridgeType::rimDiameter),
                    RimType.CODEC.fieldOf("rim_type").forGetter(CartridgeType::rimType),
                    Codec.FLOAT.fieldOf("max_safe_pressure").forGetter(CartridgeType::maxSafePressure),
                    Codec.FLOAT.fieldOf("standard_bullet_mass").forGetter(CartridgeType::standardBulletMass),
                    Codec.FLOAT.fieldOf("case_capacity").forGetter(CartridgeType::caseCapacity),
                    Codec.INT.fieldOf("tech_level").forGetter(CartridgeType::techLevel)
            ).apply(instance, CartridgeType::new)
    );

    public static final StreamCodec<ByteBuf, CartridgeType> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CartridgeType::displayName,
            ByteBufCodecs.FLOAT, CartridgeType::bulletDiameter,
            ByteBufCodecs.FLOAT, CartridgeType::caseLength,
            ByteBufCodecs.FLOAT, CartridgeType::overallLength,
            ByteBufCodecs.FLOAT, CartridgeType::rimDiameter,
            RimType.STREAM_CODEC, CartridgeType::rimType,
            ByteBufCodecs.FLOAT, CartridgeType::maxSafePressure,
            ByteBufCodecs.FLOAT, CartridgeType::standardBulletMass,
            ByteBufCodecs.FLOAT, CartridgeType::caseCapacity,
            ByteBufCodecs.INT, CartridgeType::techLevel,
            CartridgeType::new
    );

    /**
     * 判断两个口径是否物理兼容（弹头直径和弹壳长度相同）。
     * <p>
     * 用于判断弹药能否装进某把枪/某个弹匣。
     * <p>
     * 注意：某些口径存在"可互换"关系（如 .223 Remington 与 5.56 NATO），
     * 此类兼容性由 {@link CartridgeTypeManager#isCompatible(Identifier, Identifier)} 处理。
     */
    public boolean isDimensionallyCompatibleWith(CartridgeType other) {
        return Float.compare(this.bulletDiameter, other.bulletDiameter) == 0
                && Float.compare(this.caseLength, other.caseLength) == 0;
    }

    /**
     * 计算装药过量阈值。
     * <p>
     * 当装药量超过弹壳容积所能容纳的发射药上限时，即为装药过量。
     * 具体判定由 P4 弹道公式在开火时实时计算，此处仅提供物理常量。
     * <p>
     * 简化模型：弹壳容积 × 发射药密度 ≈ 最大装药量。
     * 装药量与最大装药量的比值即为装药比。
     */

    /**
     * 装药过量判定的物理上限。
     * <p>
     * 返回弹壳容积对应的最大安全装药量（克）。
     * 当实际装药量超过此值时，膛压将超过 maxSafePressure。
     * <p>
     * 简化模型：假设无烟火药密度约 1.0 g/cm³，
     * 实际装药量（loadedRound.powderCharge）是相对于"标准装药量"的比例，
     * 标准装药量由 {@link #standardBulletMass} 和弹壳容积的比值决定。
     * <p>
     * 此方法仅用于 P1/P4 供弹原语和弹道公式的输入，
     * 不直接参与伤害/初速计算。
     */
    public float getMaxSafePowderCharge() {
        // 简化模型：弹壳容积 × 0.6 ≈ 标准装药量
        // 超过 1.3 倍标准装药量即为危险
        return caseCapacity * 0.6f * 1.3f;
    }
}
