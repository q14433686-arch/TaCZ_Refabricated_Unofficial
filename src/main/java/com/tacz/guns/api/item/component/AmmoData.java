package com.tacz.guns.api.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.item.enums.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 弹药扩展数据组件。
 * <p>
 * 存储在弹药物品的 DataComponents 中，定义弹药的具体属性：
 * 口径、弹壳材质、底火类型、发射药类型、弹头类型、装药量、复装次数、弹壳状态。
 * <p>
 * 对应设计文档：B.4.1 弹药数据组件
 * <p>
 * P0补充更新：新增 {@code cartridgeType} 字段，将口径信息引入弹药堆叠级数据。
 * <p>
 * 与 {@link LoadedRound} 的职责区分：
 * <ul>
 *   <li>{@code AmmoData} — 弹药堆叠级数据，所有同堆叠弹药共享同一模板</li>
 *   <li>{@code LoadedRound} — 单发级数据，每发子弹独立记录完整状态</li>
 * </ul>
 * <p>
 * 使用方式：
 * <pre>
 * ItemStack ammo = ...;
 * AmmoData data = ammo.get(ModDataComponents.AMMO_DATA);
 * if (data != null) {
 *     Identifier cartridge = data.cartridgeType();
 *     BulletType bulletType = data.bulletType();
 * }
 * </pre>
 */
public record AmmoData(
        /**
         * 口径类型标识符。
         * <p>
         * P0补充：引用 {@link com.tacz.guns.api.item.cartridge.CartridgeTypeManager} 中注册的口径类型。
         * 决定此弹药能否装进某把枪/某个弹匣。
         * <p>
         * 如果为 null，则使用旧逻辑（通过 ammoId 匹配）。
         */
        @Nullable Identifier cartridgeType,
        CaseMaterial caseMaterial,
        PrimerType primerType,
        PowderType powderType,
        float powderCharge,
        BulletType bulletType,
        int reloadCount,
        CaseCondition caseCondition
) {
    /**
     * 默认弹药数据：9mm + 黄铜弹壳 + Boxer底火 + 无烟火药 + 标准装药 + FMJ弹头 + 未复装 + 完好状态
     */
    public static final AmmoData DEFAULT = new AmmoData(
            Identifier.fromNamespaceAndPath("tacz", "9mm"),
            CaseMaterial.BRASS, PrimerType.BOXER, PowderType.SMOKELESS,
            1.0f, BulletType.FMJ, 0, CaseCondition.PRISTINE
    );

    /**
     * 黑火药弹药默认数据
     */
    public static final AmmoData BLACK_POWDER_DEFAULT = new AmmoData(
            Identifier.fromNamespaceAndPath("tacz", "9mm"),
            CaseMaterial.BRASS, PrimerType.BOXER, PowderType.BLACK_POWDER,
            1.0f, BulletType.FMJ, 0, CaseCondition.PRISTINE
    );

    public static final Codec<AmmoData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Identifier.CODEC.optionalFieldOf("cartridge_type").forGetter(d ->
                            java.util.Optional.ofNullable(d.cartridgeType())),
                    CaseMaterial.CODEC.fieldOf("case_material").forGetter(AmmoData::caseMaterial),
                    PrimerType.CODEC.fieldOf("primer_type").forGetter(AmmoData::primerType),
                    PowderType.CODEC.fieldOf("powder_type").forGetter(AmmoData::powderType),
                    Codec.FLOAT.fieldOf("powder_charge").forGetter(AmmoData::powderCharge),
                    BulletType.CODEC.fieldOf("bullet_type").forGetter(AmmoData::bulletType),
                    Codec.INT.fieldOf("reload_count").forGetter(AmmoData::reloadCount),
                    CaseCondition.CODEC.fieldOf("case_condition").forGetter(AmmoData::caseCondition)
            ).apply(instance, (cartTypeOpt, caseMat, primer, powder, charge, bullet, reload, condition) ->
                    new AmmoData(cartTypeOpt.orElse(null), caseMat, primer, powder, charge, bullet, reload, condition))
    );

    public static final StreamCodec<ByteBuf, AmmoData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), d -> java.util.Optional.ofNullable(d.cartridgeType()),
            CaseMaterial.STREAM_CODEC, AmmoData::caseMaterial,
            PrimerType.STREAM_CODEC, AmmoData::primerType,
            PowderType.STREAM_CODEC, AmmoData::powderType,
            ByteBufCodecs.FLOAT, AmmoData::powderCharge,
            BulletType.STREAM_CODEC, AmmoData::bulletType,
            ByteBufCodecs.INT, AmmoData::reloadCount,
            CaseCondition.STREAM_CODEC, AmmoData::caseCondition,
            (cartTypeOpt, caseMat, primer, powder, charge, bullet, reload, condition) ->
                    new AmmoData(cartTypeOpt.orElse(null), caseMat, primer, powder, charge, bullet, reload, condition)
    );

    /**
     * 检查当前弹药是否可复装
     * 需要同时满足：弹壳材质可复装 + 底火类型可复装 + 弹壳状态可复装 + 未超过复装上限
     */
    public boolean canReload() {
        return caseMaterial.isReloadable()
                && primerType.isReloadable()
                && caseCondition.isReloadable()
                && reloadCount < caseMaterial.getMaxReloadCount();
    }

    /**
     * 创建复装后的新弹药数据
     * 复装次数+1，弹壳状态根据复装次数更新
     */
    public AmmoData withReload() {
        int newCount = reloadCount + 1;
        CaseCondition newCondition = calculateCondition(newCount);
        return new AmmoData(cartridgeType, caseMaterial, primerType, powderType, powderCharge, bulletType, newCount, newCondition);
    }

    /**
     * 根据复装次数计算弹壳状态
     */
    private CaseCondition calculateCondition(int count) {
        int maxReloads = caseMaterial.getMaxReloadCount();
        if (count <= 0) return CaseCondition.PRISTINE;
        if (count <= maxReloads * 0.4) return CaseCondition.GOOD;
        if (count <= maxReloads * 0.8) return CaseCondition.WORN;
        return CaseCondition.CRACKED;
    }

    /**
     * 创建修改装药量的副本
     */
    public AmmoData withPowderCharge(float charge) {
        return new AmmoData(cartridgeType, caseMaterial, primerType, powderType, charge, bulletType, reloadCount, caseCondition);
    }

    /**
     * 创建修改弹头类型的副本
     */
    public AmmoData withBulletType(BulletType type) {
        return new AmmoData(cartridgeType, caseMaterial, primerType, powderType, powderCharge, type, reloadCount, caseCondition);
    }

    /**
     * 创建修改口径类型的副本
     * <p>
     * P0补充：用于口径变更场景。
     */
    public AmmoData withCartridgeType(@Nullable Identifier cartridgeType) {
        return new AmmoData(cartridgeType, caseMaterial, primerType, powderType, powderCharge, bulletType, reloadCount, caseCondition);
    }

    /**
     * 获取有效的口径类型标识符。
     * <p>
     * 如果未指定 cartridgeType，返回 null。
     */
    public @Nullable Identifier getEffectiveCartridgeType() {
        return cartridgeType;
    }
}
