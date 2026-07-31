package cn.sh1rocu.tacz.industry.api.tolerance.rule;

import cn.sh1rocu.tacz.industry.api.tolerance.AssemblyWeights;
import cn.sh1rocu.tacz.industry.api.tolerance.MachineTsWindow;
import cn.sh1rocu.tacz.industry.api.tolerance.TsGrade;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * TS 公差规则层（A-8 全公式，纯函数）。
 *
 * <p><b>零件公式（A-8 设计原文）：</b></p>
 * <pre>零件TS = clamp( 机器基础TS + 材料加成 + 稳定性加成(×10) + 模具血统(模具TS-50)/10 + N(0,σ) , 0, 100 )</pre>
 *
 * <p><b>装配公式：</b>部件/整枪 TS = 下层 TS 按 {@link AssemblyWeights} 归一化加权。</p>
 *
 * <p><b>诊断需求（F 章事故报告的前置投资）：</b>计算返回 {@link PartTsBreakdown} 明细而不仅是裸分数，
 * 让玩家能在 tooltip/事故报告里看到"主要失分在哪一项"——这是 P3 F 章"权重主因"展示的同一数据源。</p>
 */
public final class ToleranceRules {
    private ToleranceRules() {
    }

    /** 阶段随机抖动 σ（A-8：阶段值；P1 手搓档 σ=5，随机器升级收窄到 1）。 */
    public static final float SIGMA_HANDICRAFT = 5f;
    public static final float SIGMA_T2_MANUAL = 3.5f;
    public static final float SIGMA_T3_POWER = 2.5f;
    public static final float SIGMA_T4_PRECISION = 1.5f;
    public static final float SIGMA_T5 = 1.0f;

    /**
     * 计算一件零件的 TS（全公式一次走完）。
     *
     * @param window        机器基础窗口（A-8a）
     * @param materialBonus 材料加成（MaterialType.toleranceBonus，A-8b）
     * @param stability     稳定性 0–1（手摇=小游戏评分；动力=energy_stability；NONE 类机器传 0）
     * @param moldTs        模具 TS（无模具工序传 null——模具血统加成按 0 计）
     * @param sigma         抖动幅度（按机器阶段选常数，数据包不可调——A-8 阶段语义，防整合包魔改平衡基石）
     * @param seed          零件级种子（装配序列号/工作台刻痕派生，保证同一零件结果恒等可复查）
     */
    public static PartTsResult partTs(MachineTsWindow window, float materialBonus, float stability,
                                      Integer moldTs, float sigma, long seed) {
        Random random = new Random(seed);
        int machineBase = window.minTs() + (window.maxTs() > window.minTs()
                ? random.nextInt(window.maxTs() - window.minTs() + 1) : 0);
        float stabilityBonus = clamp01(stability) * 10f;
        float moldBonus = moldTs == null ? 0f : (moldTs - 50) / 10f;
        float jitter = (float) (random.nextGaussian() * sigma);
        float raw = machineBase + materialBonus + stabilityBonus + moldBonus + jitter;
        int ts = clampTs(Math.round(raw));
        return new PartTsResult(ts, new PartTsBreakdown(machineBase, materialBonus, stabilityBonus, moldBonus, jitter));
    }

    /**
     * 装配加权（零件→部件、部件→整枪 同用）。
     *
     * @param weights 权重方案（运行时归一化，写错和不归一不掀表）
     * @param tsByKey 各下层零件 TS（缺键的按"无贡献"处理；权重表中的键若全部缺席→返回 0 并视为坏装配）
     */
    public static int assembleTs(AssemblyWeights weights, Map<String, Integer> tsByKey) {
        float acc = 0f;
        float wsum = 0f;
        for (Map.Entry<String, Float> e : weights.weights().entrySet()) {
            Integer ts = tsByKey.get(e.getKey());
            if (ts == null) {
                continue;
            }
            acc += e.getValue() * ts;
            wsum += e.getValue();
        }
        if (wsum <= 0f) {
            return 0;
        }
        return clampTs(Math.round(acc / wsum));
    }

    /** TS → 分级带（A-8d 表查询；未命中返回 null，调用方兜底最低档）。 */
    public static TsGrade gradeOf(List<TsGrade> bands, int ts) {
        for (TsGrade band : bands) {
            if (band.contains(ts)) {
                return band;
            }
        }
        return null;
    }

    public static int clampTs(int ts) {
        if (ts < 0) {
            return 0;
        }
        return Math.min(ts, 100);
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }

    /**
     * TS 明细（事故报告/tooltip 数据源；序列化无需——重算可得，属瞬态）。
     */
    public record PartTsBreakdown(int machineBase, float materialBonus, float stabilityBonus,
                                  float moldBonus, float jitter) {
        /** 权重主因（幅度最大的一项；tooltip 显示"主要影响因素"用）。 */
        public String dominantFactor() {
            float aMat = Math.abs(materialBonus);
            float aSta = Math.abs(stabilityBonus);
            float aMold = Math.abs(moldBonus);
            float aJit = Math.abs(jitter);
            float max = Math.max(Math.max(aMat, aSta), Math.max(aMold, aJit));
            if (max == aJit) {
                return "luck";
            }
            if (max == aMold) {
                return "mold";
            }
            if (max == aSta) {
                return "stability";
            }
            return "material";
        }
    }

    public record PartTsResult(int ts, PartTsBreakdown breakdown) {
    }
}
