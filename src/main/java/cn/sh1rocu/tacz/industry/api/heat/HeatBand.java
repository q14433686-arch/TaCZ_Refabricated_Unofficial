package cn.sh1rocu.tacz.industry.api.heat;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * 工序温度带（A-2 热度条机制的核心判定区间）。
 *
 * <p>四条界：</p>
 * <ul>
 *   <li>[{@code workMin}, {@code workMax}] 可工作带——带外锤击无效（过冷）或造成烧损（过热）</li>
 *   <li>[{@code idealMin}, {@code idealMax}] 理想带——收锤质量满分的判定区（A-2"在理想区间收锤得高质量"）</li>
 * </ul>
 *
 * @param workMin  可工作下界（含）
 * @param workMax  可工作上界（含）
 * @param idealMin 理想带下界（含，落在工作带内）
 * @param idealMax 理想带上界（含，落在工作带内）
 */
public record HeatBand(int workMin, int workMax, int idealMin, int idealMax) {

    public static HeatBand fromJson(JsonObject json) {
        int workMin = GsonHelper.getAsInt(json, "work_min");
        int workMax = GsonHelper.getAsInt(json, "work_max");
        int idealMin = GsonHelper.getAsInt(json, "ideal_min", workMin);
        int idealMax = GsonHelper.getAsInt(json, "ideal_max", workMax);
        if (workMin < HeatUnits.AMBIENT || workMax > HeatUnits.MAX || workMin >= workMax) {
            throw new IllegalArgumentException("heat_band 工作带非法: [" + workMin + "," + workMax + "]");
        }
        if (idealMin < workMin || idealMax > workMax || idealMin > idealMax) {
            throw new IllegalArgumentException("heat_band 理想带必须落在工作带内: ideal=[" + idealMin + "," + idealMax
                    + "] work=[" + workMin + "," + workMax + "]");
        }
        return new HeatBand(workMin, workMax, idealMin, idealMax);
    }

    public boolean canWork(int heat) {
        return heat >= workMin && heat <= workMax;
    }

    public boolean isIdeal(int heat) {
        return heat >= idealMin && heat <= idealMax;
    }

    public boolean isTooCold(int heat) {
        return heat < workMin;
    }

    public boolean isOverheated(int heat) {
        return heat > workMax;
    }

    /**
     * 质量梯度：理想带内 1.0；工作带内按"距最近理想边界的距离 / 到工作边界的最大可能距离"线性衰减到 {@code #minInBand}；
     * 带外返回 0（规则层另行区分过冷/过热语义）。
     */
    public float qualityGradient(int heat) {
        if (isIdeal(heat)) {
            return 1.0f;
        }
        if (!canWork(heat)) {
            return 0f;
        }
        float dist;
        float span;
        if (heat < idealMin) {
            dist = idealMin - heat;
            span = Math.max(1, idealMin - workMin);
        } else {
            dist = heat - idealMax;
            span = Math.max(1, workMax - idealMax);
        }
        // 带内最低保底 0.4：弱锤也该有点质量贡献（A-2 手感：理想带收锤是进阶技巧而非硬门槛）
        return 1.0f - (1.0f - MIN_IN_BAND) * Math.min(1.0f, dist / span);
    }

    public static final float MIN_IN_BAND = 0.4f;
}
