package com.tacz.guns.api.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * 公差评分数据组件。
 * <p>
 * 对应设计文档：A.4.1 零件公差组件
 * <p>
 * 公差评分影响最终武器的精度、可靠性和耐久属性区间。
 * 由制造时的机器等级、动力稳定性、材料等级和随机因素共同决定。
 */
public record ToleranceData(
        float toleranceScore,
        int techLevel,
        UUID crafterUuid,
        long craftTimestamp
) {
    /**
     * 创建默认公差数据（工业级，T1阶段）
     */
    public static ToleranceData createDefault(int techLevel) {
        float baseScore = switch (techLevel) {
            case 0 -> 40.0f;  // T0: 手工
            case 1 -> 60.0f;  // T1: 小作坊
            case 2 -> 75.0f;  // T2: 初级工业
            case 3 -> 88.0f;  // T3: 中级工业
            case 4 -> 96.0f;  // T4: 重度自动化
            default -> 50.0f;
        };
        return new ToleranceData(baseScore, techLevel, new UUID(0, 0), System.currentTimeMillis());
    }

    public static final Codec<ToleranceData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("score").forGetter(ToleranceData::toleranceScore),
                    Codec.INT.fieldOf("tech_level").forGetter(ToleranceData::techLevel),
                    Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("crafter").forGetter(ToleranceData::crafterUuid),
                    Codec.LONG.fieldOf("timestamp").forGetter(ToleranceData::craftTimestamp)
            ).apply(instance, ToleranceData::new)
    );

    public static final StreamCodec<ByteBuf, ToleranceData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ToleranceData::toleranceScore,
            ByteBufCodecs.INT, ToleranceData::techLevel,
            ByteBufCodecs.LONG, d -> d.crafterUuid().getMostSignificantBits(),
            ByteBufCodecs.LONG, d -> d.crafterUuid().getLeastSignificantBits(),
            ByteBufCodecs.LONG, ToleranceData::craftTimestamp,
            (score, tech, msb, lsb, ts) -> new ToleranceData(score, tech, new UUID(msb, lsb), ts)
    );

    /**
     * 精度修正系数（0.4~1.0）
     */
    public float getAccuracyModifier() {
        return 0.4f + 0.6f * (toleranceScore / 100.0f);
    }

    /**
     * 可靠性修正系数（0.5~1.0）
     */
    public float getReliabilityModifier() {
        return 0.5f + 0.5f * (toleranceScore / 100.0f);
    }

    /**
     * 耐久修正系数（0.5~1.0）
     */
    public float getDurabilityModifier() {
        return 0.5f + 0.5f * (toleranceScore / 100.0f);
    }

    /**
     * 公差等级描述
     */
    public String getGradeDescription() {
        if (toleranceScore >= 95) return "军规级";
        if (toleranceScore >= 85) return "工业级";
        if (toleranceScore >= 70) return "民用级";
        if (toleranceScore >= 50) return "粗制级";
        return "劣质级";
    }
}
