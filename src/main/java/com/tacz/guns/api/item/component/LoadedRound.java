package com.tacz.guns.api.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.item.enums.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 一发具体子弹的完整个体数据。
 * <p>
 * 代表"一发物理子弹"的完整数据快照，用于支持：
 * <ul>
 *   <li>混装弹药：同一弹匣内不同弹头类型/弹壳材质/装药量的弹药共存</li>
 *   <li>腐蚀弹药判定：钢制弹壳在潮湿环境中生锈</li>
 *   <li>装药过量炸膛判定：powderCharge > 1.0 时膛压超过安全阈值</li>
 *   <li>哑弹/瞎火判定：根据底火/弹壳状态/装药偏差计算</li>
 *   <li>复装弹壳追踪：reloadCount 记录弹壳已复装次数</li>
 * </ul>
 * <p>
 * 与 {@link AmmoData} 的职责区分：
 * <ul>
 *   <li>{@code AmmoData} — 弹药堆叠级数据，所有同堆叠弹药共享同一模板</li>
 *   <li>{@code LoadedRound} — 单发级数据，每发子弹独立记录完整状态</li>
 * </ul>
 * <p>
 * 转换关系：{@code AmmoData → LoadedRound} 通过 {@link #fromAmmoData(Identifier, AmmoData)}，
 * 反向通过 {@link #toAmmoData()} 提取堆叠级数据（丢失 cartridgeType 和 powderChargeDeviation）。
 * <p>
 * 对应设计文档：B.3 装填弹药数据 & E.4.1 枪膛状态
 */
public record LoadedRound(
        /** 口径类型标识符，引用 {@link com.tacz.guns.api.item.cartridge.CartridgeTypeManager} */
        Identifier cartridgeType,
        /** 弹头类型：FMJ/HP/AP/曳光/亚音速 */
        BulletType bulletType,
        /** 弹壳材质：黄铜/钢/铝 */
        CaseMaterial caseMaterial,
        /** 弹壳状态：完好/良好/磨损/裂纹/变形/锈蚀 */
        CaseCondition caseCondition,
        /** 底火类型：Boxer/Berdan */
        PrimerType primerType,
        /** 发射药类型：黑火药/无烟火药 */
        PowderType powderType,
        /** 装药量（1.0 = 标准装药，>1.0 = 装药过量，<1.0 = 减装药） */
        float powderCharge,
        /** 装药偏差（制造随机偏差，0.0 = 无偏差） */
        float powderChargeDeviation,
        /** 复装次数（0 = 原装，n = 已复装n次） */
        int reloadCount
) {
    /**
     * 从 AmmoData 创建标准 LoadedRound。
     * <p>
     * 用于将堆叠级弹药数据转换为单发级数据，供弹具装填时使用。
     * 装药偏差默认为0（标准装药）。
     * <p>
     * <b>同步契约</b>：此方法必须映射 AmmoData 的所有重叠字段。
     * 新增 AmmoData 字段时必须同步更新此方法。
     * 详见 {@link AmmoDataRoundContract}。
     *
     * @param cartridgeType 口径类型标识符
     * @param ammoData      弹药堆叠数据
     * @return 新的 LoadedRound 实例
     */
    public static LoadedRound fromAmmoData(Identifier cartridgeType, AmmoData ammoData) {
        return new LoadedRound(
                cartridgeType,
                ammoData.bulletType(),
                ammoData.caseMaterial(),
                ammoData.caseCondition(),
                ammoData.primerType(),
                ammoData.powderType(),
                ammoData.powderCharge(),
                0.0f,  // 标准装药无偏差
                ammoData.reloadCount()
        );
    }

    /**
     * 从 AmmoData 创建带随机装药偏差的 LoadedRound。
     * <p>
     * 模拟制造工艺偏差，装药偏差在 [-maxDeviation, +maxDeviation] 范围内随机。
     *
     * @param cartridgeType 口径类型标识符
     * @param ammoData      弹药堆叠数据
     * @param maxDeviation  最大装药偏差（如 0.02 = ±2%）
     * @param random        随机数生成器
     * @return 新的 LoadedRound 实例
     */
    public static LoadedRound fromAmmoDataWithDeviation(Identifier cartridgeType, AmmoData ammoData,
                                                         float maxDeviation, java.util.Random random) {
        float deviation = maxDeviation > 0
                ? (random.nextFloat() * 2 - 1) * maxDeviation
                : 0.0f;
        return new LoadedRound(
                cartridgeType,
                ammoData.bulletType(),
                ammoData.caseMaterial(),
                ammoData.caseCondition(),
                ammoData.primerType(),
                ammoData.powderType(),
                ammoData.powderCharge(),
                deviation,
                ammoData.reloadCount()
        );
    }

    /**
     * 创建默认的膛内弹药（用于从旧系统迁移时填充）。
     * <p>
     * 使用标准无烟火药、黄铜弹壳、Boxer底火、FMJ弹头、完好弹壳状态。
     * 当从 TACZ 旧系统（hasBulletInBarrel=true）迁移到新系统时，
     * 由于旧系统没有具体的弹药数据，因此使用默认值。
     */
    public static LoadedRound createDefault() {
        return new LoadedRound(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("tacz", "default"),
                BulletType.FMJ, CaseMaterial.BRASS, CaseCondition.PRISTINE,
                PrimerType.BOXER, PowderType.SMOKELESS,
                1.0f, 0.0f, 0
        );
    }

    /** 默认9mm FMJ弹 */
    public static LoadedRound defaultNineMm() {
        return new LoadedRound(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("tacz", "9mm"),
                BulletType.FMJ, CaseMaterial.BRASS, CaseCondition.PRISTINE,
                PrimerType.BOXER, PowderType.SMOKELESS,
                1.0f, 0.0f, 0
        );
    }

    public static final Codec<LoadedRound> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Identifier.CODEC.fieldOf("cartridge_type").forGetter(LoadedRound::cartridgeType),
                    BulletType.CODEC.fieldOf("bullet_type").forGetter(LoadedRound::bulletType),
                    CaseMaterial.CODEC.fieldOf("case_material").forGetter(LoadedRound::caseMaterial),
                    CaseCondition.CODEC.fieldOf("case_condition").forGetter(LoadedRound::caseCondition),
                    PrimerType.CODEC.fieldOf("primer_type").forGetter(LoadedRound::primerType),
                    PowderType.CODEC.fieldOf("powder_type").forGetter(LoadedRound::powderType),
                    Codec.FLOAT.fieldOf("powder_charge").forGetter(LoadedRound::powderCharge),
                    Codec.FLOAT.fieldOf("powder_charge_deviation").forGetter(LoadedRound::powderChargeDeviation),
                    Codec.INT.fieldOf("reload_count").forGetter(LoadedRound::reloadCount)
            ).apply(instance, LoadedRound::new)
    );

    public static final StreamCodec<ByteBuf, LoadedRound> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, LoadedRound::cartridgeType,
            BulletType.STREAM_CODEC, LoadedRound::bulletType,
            CaseMaterial.STREAM_CODEC, LoadedRound::caseMaterial,
            CaseCondition.STREAM_CODEC, LoadedRound::caseCondition,
            PrimerType.STREAM_CODEC, LoadedRound::primerType,
            PowderType.STREAM_CODEC, LoadedRound::powderType,
            ByteBufCodecs.FLOAT, LoadedRound::powderCharge,
            ByteBufCodecs.FLOAT, LoadedRound::powderChargeDeviation,
            ByteBufCodecs.INT, LoadedRound::reloadCount,
            LoadedRound::new
    );

    // ====== 业务方法 ======

    /**
     * 获取实际装药量（标准装药 + 偏差）
     */
    public float getEffectivePowderCharge() {
        return powderCharge + powderChargeDeviation;
    }

    /**
     * 是否为装药过量（超过标准装药 30%）
     * <p>
     * 装药过量的弹药有炸膛风险，膛压可能超过枪管承受极限。
     */
    public boolean isOvercharged() {
        return getEffectivePowderCharge() > 1.30f;
    }

    /**
     * 是否为减装药（低于标准装药 50%）
     * <p>
     * 减装药可能导致哑弹或弹头卡在枪管内（Squib）。
     */
    public boolean isUndercharged() {
        return getEffectivePowderCharge() < 0.50f;
    }

    /**
     * 是否为腐蚀性弹药（Berdan底火 + 钢制弹壳）
     * <p>
     * 腐蚀性弹药发射后会在枪膛内留下腐蚀性盐类残渣，
     * 如果不及时清洁会导致枪管生锈。
     */
    public boolean isCorrosive() {
        return primerType == PrimerType.BERDAN || caseMaterial == CaseMaterial.STEEL;
    }

    /**
     * 计算此弹药的可靠性修正（0.0~1.0）
     * <p>
     * 综合考虑：弹壳状态、装药量、复装次数、腐蚀性
     */
    public float getReliabilityModifier() {
        float modifier = 1.0f;

        // 弹壳状态修正
        modifier *= caseCondition.getReliabilityModifier();

        // 装药量修正
        float effectiveCharge = getEffectivePowderCharge();
        if (effectiveCharge > 1.3f) {
            modifier *= 0.7f;  // 装药过量：膛压异常，供弹不稳定
        } else if (effectiveCharge < 0.5f) {
            modifier *= 0.5f;  // 减装药：可能哑弹
        }

        // 腐蚀性弹药修正（长期使用后可靠性下降更快）
        if (isCorrosive()) {
            modifier *= 0.95f;
        }

        return modifier;
    }

    /**
     * 提取为堆叠级 AmmoData（丢失 powderChargeDeviation 单发级数据）。
     * <p>
     * <b>同步契约</b>：此方法必须映射所有重叠字段到 AmmoData。
     * 新增 AmmoData 字段时必须同步更新此方法。
     * 详见 {@link AmmoDataRoundContract}。
     * <p>
     * 注意：cartridgeType 会被保留到 AmmoData 中。
     */
    public AmmoData toAmmoData() {
        return new AmmoData(
                cartridgeType,
                caseMaterial, primerType, powderType, powderCharge,
                bulletType, reloadCount, caseCondition
        );
    }

    /**
     * 创建修改弹壳状态的副本（用于击发后弹壳状态变化）
     */
    public LoadedRound withCaseCondition(CaseCondition condition) {
        return new LoadedRound(cartridgeType, bulletType, caseMaterial, condition,
                primerType, powderType, powderCharge, powderChargeDeviation, reloadCount);
    }

    /**
     * 创建修改弹头类型的副本（用于弹头替换）
     */
    public LoadedRound withBulletType(BulletType type) {
        return new LoadedRound(cartridgeType, type, caseMaterial, caseCondition,
                primerType, powderType, powderCharge, powderChargeDeviation, reloadCount);
    }
}
