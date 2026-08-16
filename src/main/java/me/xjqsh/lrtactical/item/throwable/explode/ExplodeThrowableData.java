package me.xjqsh.lrtactical.item.throwable.explode;

import com.google.gson.annotations.SerializedName;
import me.xjqsh.lrtactical.item.throwable.ThrowableData;
import me.xjqsh.lrtactical.util.TooltipLine;
import me.xjqsh.lrtactical.util.TooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 爆炸类投掷物的配置，对应数据包里 {@code "type": "lrtactical:explode"} 的 {@code data} 段。
 *
 * <p>纯 Gson POJO，与上游逐字对应。
 */
public class ExplodeThrowableData extends ThrowableData {
    @SerializedName("explode")
    private ExplodeData explode = new ExplodeData();

    @NotNull
    public ExplodeData getExplode() {
        return explode;
    }

    @Override
    public List<TooltipLine> getTooltipLines() {
        List<TooltipLine> lines = super.getTooltipLines();
        lines.add(TooltipLine.normal(Component.translatable(
                "tooltip.lrtactical.throwable.explode.line",
                TooltipUtil.format((float) explode.getDamage()), TooltipUtil.format(explode.getRadius()))));
        List<Component> traits = new ArrayList<>();
        if (explode.isDestroyBlocks()) {
            traits.add(Component.translatable("tooltip.lrtactical.throwable.explode.destroy_blocks"));
        }
        if (explode.isRemoteDetonation()) {
            traits.add(Component.translatable("tooltip.lrtactical.throwable.explode.remote_detonation"));
        }
        if (!traits.isEmpty()) {
            MutableComponent joined = traits.get(0).copy();
            for (int i = 1; i < traits.size(); i++) {
                joined.append(", ").append(traits.get(i));
            }
            lines.add(TooltipLine.normal(Component.translatable(
                    "tooltip.lrtactical.throwable.explode.traits", joined)));
        }
        return lines;
    }

    public static class ExplodeData {
        @SerializedName("radius")
        private float radius = 5.5f;

        @SerializedName("damage")
        private double damage = 22.0;

        @SerializedName("destroy_blocks")
        private boolean destroyBlocks = false;

        @SerializedName("destroy_multiplier")
        private float destroyMultiplier = 1.0f;

        @SerializedName("trigger_on_explode")
        private boolean triggerOnExplode = false;

        @SerializedName("remote_detonation")
        private boolean remoteDetonation = false;

        @SerializedName("screen_shake_time")
        private double screenShakeTime = 20;

        @SerializedName("screen_shake_amplitude")
        private double screenShakeAmplitude = 50;

        public float getRadius() {
            return radius;
        }

        public double getDamage() {
            return damage;
        }

        public boolean isDestroyBlocks() {
            return destroyBlocks;
        }

        public float getDestroyMultiplier() {
            return destroyMultiplier;
        }

        public boolean isTriggerOnExplode() {
            return triggerOnExplode;
        }

        public boolean isRemoteDetonation() {
            return remoteDetonation;
        }

        public double getScreenShakeTime() {
            return screenShakeTime;
        }

        public double getScreenShakeAmplitude() {
            return screenShakeAmplitude;
        }
    }
}
