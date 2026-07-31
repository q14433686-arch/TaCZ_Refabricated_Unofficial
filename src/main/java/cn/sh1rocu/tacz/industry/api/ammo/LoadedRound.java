package cn.sh1rocu.tacz.industry.api.ammo;

import cn.sh1rocu.tacz.industry.api.internal.IndustryCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * 一发具体子弹的完整个体数据。
 *
 * <p>与静态定义的区别：{@code CartridgeType}/{@code BulletType} 是"类型档案"（数据驱动
 * 注册表，全局唯一实例）；LoadedRound 是"这一发"的运行时个体——它可能来自工厂批次、
 * 玩家复装台，带着自己的弹壳履历与装药偏差。后续系统的判定入口：</p>
 * <ul>
 *   <li>混装弹药：FeedDeviceData 内部就是 LoadedRound 的异构队列</li>
 *   <li>腐蚀判定：H 章读取 {@link #corrosivePrimer()} 决定是否开启腐蚀倒计时</li>
 *   <li>过压/炸膛：F 章读取 {@link #chargeDeviation()} 与 CartridgeType.pressureClass 合成风险池</li>
 *   <li>哑弹/瞎火：E 章按个体装药偏差与底火数据掷骰</li>
 * </ul>
 *
 * @param cartridge       口径（→ CartridgeRegistry 查表）
 * @param bulletType      弹头类型（→ BulletRegistry 查表）
 * @param caseMaterial    弹壳材质
 * @param caseState       弹壳状态（损坏壳在供弹具规则层被拒装）
 * @param primerType      底火结构（Boxer/Berdan）
 * @param corrosivePrimer 该发底火是否腐蚀性（腐蚀性与结构类型无必然关系，独立字段）
 * @param chargeDeviation 装药量偏差（名义装的百分偏差，+0.15=超装 15%；欠装过深进入 Squib 风险档）
 */
public record LoadedRound(
        Identifier cartridge,
        Identifier bulletType,
        CaseMaterial caseMaterial,
        CaseState caseState,
        PrimerType primerType,
        boolean corrosivePrimer,
        float chargeDeviation
) {
    /**
     * Squib 风险装药线（B 章 B-5）：低于该偏差值视为临界欠装。
     * 常量放此处是因为它是"个体弹药数据的语义阈值"，规则层统一引用。数值进平衡 JSON 后此值作废（P3 TODO）。
     */
    public static final float SQUIB_RISK_DEVIATION = -0.45f;

    public static final Codec<LoadedRound> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            IndustryCodecs.IDENTIFIER.fieldOf("cartridge").forGetter(LoadedRound::cartridge),
            IndustryCodecs.IDENTIFIER.optionalFieldOf("bullet_type", cn.sh1rocu.tacz.industry.api.bullet.BulletType.defaultId()).forGetter(LoadedRound::bulletType),
            CaseMaterial.CODEC.optionalFieldOf("case_material", CaseMaterial.BRASS).forGetter(LoadedRound::caseMaterial),
            CaseState.CODEC.optionalFieldOf("case_state", CaseState.FACTORY_NEW).forGetter(LoadedRound::caseState),
            PrimerType.CODEC.optionalFieldOf("primer_type", PrimerType.BOXER).forGetter(LoadedRound::primerType),
            Codec.BOOL.optionalFieldOf("corrosive_primer", false).forGetter(LoadedRound::corrosivePrimer),
            Codec.FLOAT.optionalFieldOf("charge_deviation", 0f).forGetter(LoadedRound::chargeDeviation)
    ).apply(instance, LoadedRound::new));

    /**
     * 工厂弹快捷构造：黄铜壳、全新、Boxer 无腐蚀、无偏差。
     */
    public static LoadedRound factory(Identifier cartridge, Identifier bulletType) {
        return new LoadedRound(cartridge, bulletType, CaseMaterial.BRASS, CaseState.FACTORY_NEW, PrimerType.BOXER, false, 0f);
    }

    /**
     * 击发后同一发（弹壳状态转为空壳的"个体化身"），用于抛壳落地物携带履历。
     */
    public LoadedRound asSpent() {
        return new LoadedRound(cartridge, bulletType, caseMaterial, CaseState.FIRED_SPENT, primerType, corrosivePrimer, chargeDeviation);
    }

    public boolean isSquibRisk() {
        return chargeDeviation <= SQUIB_RISK_DEVIATION;
    }

    public boolean isOverpressure() {
        return chargeDeviation > 0.05f;
    }
}
