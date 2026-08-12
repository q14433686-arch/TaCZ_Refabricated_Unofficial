package me.xjqsh.lrtactical.item.throwable.flash;

import com.google.gson.annotations.SerializedName;
import me.xjqsh.lrtactical.item.throwable.ThrowableData;
import me.xjqsh.lrtactical.util.TooltipLine;
import me.xjqsh.lrtactical.util.TooltipUtil;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 闪光弹配置，对应数据包里 {@code "type": "lrtactical:stun"} 的 {@code data} 段。
 *
 * <p>纯 Gson POJO + 两个插值公式，与上游逐字对应（无 26.2 API 依赖）。
 *
 * <h2>致盲与耳鸣的差别</h2>
 * <ul>
 *   <li><b>耳鸣</b>只按<b>距离</b>衰减 —— 捂眼睛没用，声音照样震；</li>
 *   <li><b>致盲</b>还要再按<b>视线夹角</b>衰减，且背对时（超过
 *       {@code max_angle}）完全不致盲 —— 这正是「闪光弹要看着才瞎」的来源。</li>
 * </ul>
 */
public class StunThrowableData extends ThrowableData {
    @SerializedName("stun")
    private StunData stunData = new StunData();

    @NotNull
    public StunData getStunData() {
        return stunData;
    }

    @Override
    public List<TooltipLine> getTooltipLines() {
        List<TooltipLine> lines = super.getTooltipLines();
        lines.add(TooltipLine.normal(Component.translatable(
                "tooltip.lrtactical.throwable.stun.radius", TooltipUtil.format(stunData.getRadius()))));
        lines.add(TooltipLine.normal(Component.translatable(
                "tooltip.lrtactical.throwable.stun.blind_deafened",
                TooltipUtil.formatTicks(stunData.getBlind().getMinDuration()),
                TooltipUtil.formatTicks(stunData.getBlind().getMaxDuration()))));
        return lines;
    }

    public static class StunData {
        /** 影响半径；超出此距离完全无效果。 */
        @SerializedName("radius")
        private float radius = 24f;

        @SerializedName("blind")
        private BlindData blind = new BlindData();

        @SerializedName("deafened")
        private DeafenedData deafened = new DeafenedData();

        public float getRadius() {
            return radius;
        }

        public BlindData getBlind() {
            return blind;
        }

        public DeafenedData getDeafened() {
            return deafened;
        }

        /**
         * 致盲时长：先按距离在 [min, max] 间线性插值，再按视线夹角二次衰减。
         *
         * @param distance 目标到爆点的距离
         * @param angle    目标视线与「目标→爆点」连线的夹角（度）
         */
        public int calcBlindDuration(double distance, double angle) {
            int mx = blind.getMaxDuration();
            int mn = blind.getMinDuration();
            int byDistance = (int) Math.round(mx - (mx - mn) * (distance / radius));
            double maxAngle = blind.getMaxAngle();
            double factor = blind.getViewAngleFactor();
            // 正视时系数为 1，到 maxAngle 时衰减到 viewAngleFactor
            return (int) (byDistance * (1.0 - angle * (1.0 - factor) / maxAngle));
        }

        /** 耳鸣时长：只按距离插值（背对也照样耳鸣）。 */
        public int calcDeafenedDuration(double distance) {
            int mx = deafened.getMaxDuration();
            int mn = deafened.getMinDuration();
            return (int) Math.round(mx - (mx - mn) * (distance / radius));
        }
    }

    public static class DeafenedData {
        @SerializedName("max_duration")
        private int maxDuration = 200;

        @SerializedName("min_duration")
        private int minDuration = 10;

        public int getMaxDuration() {
            return maxDuration;
        }

        public int getMinDuration() {
            return minDuration;
        }
    }

    public static class BlindData {
        @SerializedName("max_duration")
        private int maxDuration = 200;

        @SerializedName("min_duration")
        private int minDuration = 10;

        /** 目标视线与爆点连线的最大夹角，超过则完全不致盲。 */
        @SerializedName("max_angle")
        private double maxAngle = 85;

        /** 恰好在 {@code max_angle} 时保留的时长百分比。 */
        @SerializedName("view_angle_factor")
        private double viewAngleFactor = 0.5;

        public int getMaxDuration() {
            return maxDuration;
        }

        public int getMinDuration() {
            return minDuration;
        }

        public double getMaxAngle() {
            return maxAngle;
        }

        public double getViewAngleFactor() {
            return viewAngleFactor;
        }
    }
}
