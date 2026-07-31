package cn.sh1rocu.tacz.industry.api.heat;

/**
 * 热度语义常量（A-2 热加工系统）。
 *
 * <p><b>游戏化"炉温单位"</b>：0–1000 的抽象刻度，仅为玩法节奏服务——
 * 不映射任何现实温度（安全红线：只做概念级工艺抽象）。</p>
 *
 * <p>参考锚点（用于数据包作者校准手感）：</p>
 * <ul>
 *   <li>{@link #AMBIENT} 室温档（工件冷却的下限）</li>
 *   <li>T1 锻造带典型区间 650–900（具体数值以 process JSON 的 heat_band 为准）</li>
 *   <li>{@link #MAX} 炉温理论上限（坩埚+鼓风档）</li>
 * </ul>
 */
public final class HeatUnits {
    private HeatUnits() {
    }

    public static final int AMBIENT = 20;
    public static final int MAX = 1000;

    /** 截断到合法热域（规则层与组件写入共用的防线）。 */
    public static int clamp(int heat) {
        if (heat < AMBIENT) {
            return AMBIENT;
        }
        return Math.min(heat, MAX);
    }
}
