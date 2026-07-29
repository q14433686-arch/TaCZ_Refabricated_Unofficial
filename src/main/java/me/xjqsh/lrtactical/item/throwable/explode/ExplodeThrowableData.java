package me.xjqsh.lrtactical.item.throwable.explode;

import com.google.gson.annotations.SerializedName;
import me.xjqsh.lrtactical.item.throwable.ThrowableData;
import org.jetbrains.annotations.NotNull;

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
