package com.tacz.guns.api.item.ballistics;

import com.tacz.guns.api.item.component.GunStateData;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import org.jetbrains.annotations.Nullable;

/**
 * Cook-off系统（膛内弹药自燃）。
 * <p>
 * 当枪管过热且枪膛内有弹时，膛内弹药可能自燃。
 * 这是闭膛待击武器的重大风险，开膛待击武器不受影响。
 * <p>
 * 对应设计文档：G.2.5 炸膛风险随温度升高而增加
 * <p>
 * 概率模型：
 * <pre>
 * if (heat_percentage > cookoff_threshold AND has_round_in_chamber AND is_closed_bolt):
 *     cookoff_probability = (heat_percentage - 0.85) × 0.001 per tick
 *     // heat 85%: 0.0%/tick
 *     // heat 90%: 0.005%/tick
 *     // heat 100%: 0.015%/tick
 * </pre>
 * <p>
 * Cook-off后果：
 * <ul>
 *   <li>膛内弹药自燃，自动发射（无人操控）</li>
 *   <li>不消耗射手冷却时间，但视为一次射击</li>
 *   <li>精度极差（无射手控制，散布极大）</li>
 *   <li>如果枪口朝向友军，可能造成误伤</li>
 * </ul>
 * <p>
 * 此类为纯计算工具，无状态，线程安全。
 */
public final class CookOffSystem {

    /** Cook-off导致的精度惩罚（散布倍率） */
    public static final float COOKOFF_INACCURACY_MULTIPLIER = 5.0f;

    private CookOffSystem() {}

    /**
     * 检查Cook-off是否发生。
     * <p>
     * 在每tick的过热逻辑中调用。
     * <p>
     * 判定条件：
     * <ol>
     *   <li>heat_percentage > cookoff_threshold</li>
     *   <li>枪膛内有弹（chamberedRound != null）</li>
     *   <li>闭膛待击（CLOSED_BOLT 或 MANUAL_ACTION）</li>
     * </ol>
     *
     * @param gunStateData    枪械运行状态数据
     * @param heatPercentage  当前热量百分比（0.0~1.0）
     * @param heatData        过热数据（提供cookoffThreshold）
     * @param boltType        枪机类型
     * @return 是否发生Cook-off
     */
    public static boolean checkCookOff(GunStateData gunStateData,
                                        float heatPercentage,
                                        GunHeatData heatData,
                                        Bolt boltType) {
        // 开膛待击武器不受影响
        if (boltType == Bolt.OPEN_BOLT) {
            return false;
        }

        // 枪膛内无弹，不会Cook-off
        if (!gunStateData.hasChamberedRound()) {
            return false;
        }

        // 热量未达到阈值
        if (heatPercentage <= heatData.getCookoffThreshold()) {
            return false;
        }

        // 计算Cook-off概率
        float probability = gunStateData.getCookoffProbability(heatPercentage, heatData.getCookoffThreshold());

        // 随机判定
        return Math.random() < probability;
    }

    /**
     * 获取Cook-off的精度惩罚倍率。
     * <p>
     * Cook-off是无人操控的自发射击，精度极差。
     *
     * @return 散布倍率（5.0 = 散布增大5倍）
     */
    public static float getInaccuracyMultiplier() {
        return COOKOFF_INACCURACY_MULTIPLIER;
    }

    /**
     * 获取当前Cook-off风险描述。
     * <p>
     * 用于UI显示（如过热警告）。
     *
     * @param heatPercentage 当前热量百分比
     * @param cookoffThreshold Cook-off阈值
     * @return 风险等级（0=无风险，1=低风险，2=中风险，3=高风险）
     */
    public static int getCookOffRiskLevel(float heatPercentage, float cookoffThreshold) {
        if (heatPercentage <= cookoffThreshold) return 0;
        float excess = heatPercentage - cookoffThreshold;
        if (excess < 0.05f) return 1;  // 低风险
        if (excess < 0.10f) return 2;  // 中风险
        return 3;                       // 高风险
    }
}
