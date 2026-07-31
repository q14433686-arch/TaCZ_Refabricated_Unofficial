package cn.sh1rocu.tacz.industry.api.heat.rule;

import cn.sh1rocu.tacz.industry.api.heat.CoolingCurve;
import cn.sh1rocu.tacz.industry.api.heat.HeatBand;
import cn.sh1rocu.tacz.industry.api.heat.HeatUnits;

/**
 * 热力学规则层（A-2）：纯函数、无世界依赖。
 *
 * <p><b>性能立场（21 章）：</b>所有冷却计算都是"给定经过 tick 数一次性结算"，
 * O(档数) 复杂度；容器内的工件由方块实体按 ≥5 tick 节奏批量调用，禁止每工件每 tick。</p>
 */
public final class HeatRules {
    private HeatRules() {
    }

    /**
     * 一次性结算任意时长冷却：从 {@code heat} 经过 {@code ticks} 刻后到达的温度。
     * 根据曲线阶梯从高到低逐档结算：先按当前档速率散到档界，再切入下一档，直至 ticks 用尽
     * 或抵达环境温度。
     */
    public static int cool(CoolingCurve curve, int heat, int ticks) {
        float temp = heat;
        float t = ticks * 1.0f;
        for (CoolingCurve.Step step : curve.steps()) {
            if (t <= 0f || temp <= curve.ambient()) {
                break;
            }
            if (temp <= step.above()) {
                continue; // 更低档位才生效
            }
            // 从 temp 散到本档下界需要的 ticks
            float ticksInBand = (float) (temp - step.above()) / step.lossPerTick();
            if (ticksInBand >= t) {
                temp -= step.lossPerTick() * t;
                t = 0f;
            } else {
                temp = step.above();
                t -= ticksInBand;
            }
        }
        if (temp < curve.ambient()) {
            temp = curve.ambient();
        }
        return HeatUnits.clamp(Math.round(temp));
    }

    /** 锤击结算结果（A-2 工序推进语义）。 */
    public enum StrikeOutcome {
        /** 理想带内：全额进度推进，收锤可能满分 */
        EFFECTIVE,
        /** 可工作带内但非理想带：进度可推进但质量按梯度衰减（A-2 弱锤） */
        WEAK,
        /** 过冷：锤击无效（进度不变）；连续过冷锤击的开裂损耗归 P3 事故链 */
        TOO_COLD,
        /** 过热：氧化烧损——进度不变且素材折损（折损率留 P3 损耗轨） */
        OVERHEATED
    }

    /** 判定一次锤击在热度 atomic 状态下的结局（无玩家上下文，可服务端单测）。 */
    public static StrikeOutcome strikeOutcome(HeatBand band, int heat) {
        if (band.isIdeal(heat)) {
            return StrikeOutcome.EFFECTIVE;
        }
        if (band.isTooCold(heat)) {
            return StrikeOutcome.TOO_COLD;
        }
        if (band.isOverheated(heat)) {
            return StrikeOutcome.OVERHEATED;
        }
        return StrikeOutcome.WEAK;
    }

    /** 当前热度能否开工/续作（交互门禁：GUI 与逻辑复用同一条线）。 */
    public static boolean canWork(HeatBand band, int heat) {
        return band.canWork(heat);
    }
}
