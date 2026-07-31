package cn.sh1rocu.tacz.industry.api.process.rule;

import cn.sh1rocu.tacz.industry.api.heat.HeatWorkData;
import cn.sh1rocu.tacz.industry.api.heat.rule.HeatRules;
import cn.sh1rocu.tacz.industry.api.process.WorkProcessType;

/**
 * 工序执行规则层（A-2，纯函数）：把"工件 + 工序"推进一格的判定与变换全部收敛于此。
 *
 * <p>方块实体/UI 落地后只负责搬运世界状态，规则逻辑任何变动只改这里。</p>
 */
public final class ProcessRules {
    private ProcessRules() {
    }

    /**
     * 准入判定：工件当前材料与工序入料匹配，且工件未完成它工序（或在同一工序继续）。
     */
    public static boolean canApply(WorkProcessType process, HeatWorkData work) {
        if (!work.material().equals(process.inputMaterial())) {
            return false;
        }
        return work.processId().isEmpty() || work.processId().get().equals(process.id()) || work.progress() <= 0f;
    }

    /**
     * 一次锤击结算：判定热度结局 → 有进度则推进并扣温。
     * 返回新工件；无效锤击返回原工件（调用方按 {@link HeatRules.StrikeOutcome} 给打击反馈）。
     *
     * @return 结算结果（结局 + 新工件实例；不可变写回模式与 P0 组件一致）
     */
    public static StrikeResult strike(WorkProcessType process, HeatWorkData work) {
        HeatRules.StrikeOutcome outcome = HeatRules.strikeOutcome(process.band(), work.heat());
        if (outcome == HeatRules.StrikeOutcome.TOO_COLD || outcome == HeatRules.StrikeOutcome.OVERHEATED) {
            return new StrikeResult(outcome, work);
        }
        if (!process.isStrikeBased()) {
            return new StrikeResult(outcome, work);
        }
        // 有效推进：进度 +1/strikes，热度 -heatPerStrike
        float step = 1.0f / process.strikesRequired();
        HeatWorkData next = work
                .withProgress(work.progress() + step)
                .withHeat(work.heat() - process.heatPerStrike())
                .withProcess(process.id());
        return new StrikeResult(outcome, next);
    }

    /**
     * 一道锤击完成后的"可收锤"判断（进度满 + 温度仍可给出有效结局）。
     */
    public static boolean canFinish(WorkProcessType process, HeatWorkData work) {
        return work.isComplete();
    }

    public record StrikeResult(HeatRules.StrikeOutcome outcome, HeatWorkData work) {
        public boolean progressed() {
            return outcome == HeatRules.StrikeOutcome.EFFECTIVE || outcome == HeatRules.StrikeOutcome.WEAK;
        }
    }
}
