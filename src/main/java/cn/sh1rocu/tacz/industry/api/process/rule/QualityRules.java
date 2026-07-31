package cn.sh1rocu.tacz.industry.api.process.rule;

import cn.sh1rocu.tacz.industry.api.heat.HeatBand;
import cn.sh1rocu.tacz.industry.api.process.WorkProcessType;

import java.util.Random;

/**
 * 收锤质量判定规则层（A-2"在理想区间收锤得高质量" + quality_seed 防刷）。
 *
 * <p><b>formula</b>：
 * {@code quality = clamp( band.qualityGradient(heat) + idealBonus(理想带时) + jitter(seed) , 0, 1 )}
 * 抖动项由工件上的 qualitySeed 驱动——同一工件重进存档收锤结果恒等，
 * 杜绝"反复读档刷极品"（A-2 明确要求）。</p>
 */
public final class QualityRules {
    private QualityRules() {
    }

    /**
     * 收锤质量判定。
     *
     * @param process 工序
     * @param heat    收锤时热度
     * @param seed    工件 qualitySeed（0 允许——按无种子处理，抖动取 0，确定性回归）
     * @return 0–1 质量分（1=完美收在理想带）
     */
    public static float finishQuality(WorkProcessType process, int heat, long seed) {
        HeatBand band = process.band();
        float base = band.qualityGradient(heat);
        if (band.isIdeal(heat)) {
            base += process.idealFinishBonus();
        }
        float jitter = 0f;
        if (seed != 0L && process.qualityJitter() != 0f) {
            // java.util.Random 足以担当"单机玩法掷骰"；种子 64 位来自物品起源信息
            Random random = new Random(seed);
            jitter = (random.nextFloat() * 2f - 1f) * process.qualityJitter();
        }
        float q = base + jitter;
        if (q < 0f) {
            return 0f;
        }
        return Math.min(q, 1f);
    }

    /**
     * 质量分 → 展示档（手册/tooltip 共用；TS 体系 A-8 的"工艺手感侧"先行量）。
     */
    public static String qualityLabel(float quality) {
        if (quality >= 0.95f) {
            return "masterwork";
        }
        if (quality >= 0.8f) {
            return "fine";
        }
        if (quality >= 0.5f) {
            return "standard";
        }
        if (quality >= 0.25f) {
            return "rough";
        }
        return "flawed";
    }
}
