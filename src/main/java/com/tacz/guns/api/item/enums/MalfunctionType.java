package com.tacz.guns.api.item.enums;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * 枪械故障类型枚举。
 * <p>
 * 对应设计文档：E.2.2 卡壳类型细分
 * <p>
 * 不同故障类型有不同的清除交互方式，不能用同一个"修理"按钮解决。
 */
public enum MalfunctionType {
    /**
     * 进弹失败 (Failure to Feed)
     * 弹头卡在进弹坡上，枪机未完全闭锁
     * 清除：Tap-Rack-Bang（拉机柄→重新推弹入膛）
     */
    @SerializedName("failure_to_feed")
    FAILURE_TO_FEED,

    /**
     * 抽壳失败 (Failure to Extract)
     * 弹壳留在膛内，枪机空退
     * 清除：必须用清膛工具从枪口捅出弹壳
     */
    @SerializedName("failure_to_extract")
    FAILURE_TO_EXTRACT,

    /**
     * 抛壳失败/烟囱 (Failure to Eject / Stovepipe)
     * 弹壳卡在抛壳口竖直方向
     * 清除：拉机柄→取出弹壳
     */
    @SerializedName("failure_to_eject")
    FAILURE_TO_EJECT,

    /**
     * 双重进弹 (Double Feed)
     * 新弹与旧弹同时卡在膛口
     * 清除：卸弹匣→拉机柄→取出卡壳弹→重新装弹匣
     */
    @SerializedName("double_feed")
    DOUBLE_FEED,

    /**
     * 瞎火/延迟点火 (Hangfire)
     * 扣扳机后无立即反应，但0.5~3秒后弹突然击发
     * 清除：保持枪口安全方向等待5秒
     */
    @SerializedName("hangfire")
    HANGFIRE,

    /**
     * 哑弹 (Squib)
     * 弹头卡在枪管内未飞出
     * 如果继续射击会导致炸膛！
     * 清除：停止射击→用通条检查枪管→捅出弹头
     */
    @SerializedName("squib")
    SQUIB,

    /**
     * 走火 (Slam Fire)
     * 不扣扳机就自动击发，或扣一次扳机连续击发2发以上
     * 清除：立即卸弹匣→拉机柄排空膛内弹
     */
    @SerializedName("slam_fire")
    SLAM_FIRE,

    /**
     * 不发火 (Misfire)
     * 底火未击发，与瞎火的区别是延迟后也不会击发
     * 清除：等待5秒→拉机柄排除哑弹
     */
    @SerializedName("misfire")
    MISFIRE;

    public static final Codec<MalfunctionType> CODEC = Codec.STRING.xmap(MalfunctionType::valueOf, MalfunctionType::name);
    public static final IntFunction<MalfunctionType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, MalfunctionType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, MalfunctionType::ordinal);

    /**
     * 故障严重度等级
     */
    public int getSeverity() {
        return switch (this) {
            case FAILURE_TO_FEED -> 1;       // Type 1 轻度
            case FAILURE_TO_EXTRACT -> 2;     // Type 2 中度
            case FAILURE_TO_EJECT -> 2;       // Type 2 中度
            case DOUBLE_FEED -> 3;            // Type 3 重度
            case HANGFIRE -> 4;               // 特殊（危险）
            case SQUIB -> 5;                  // 极重（后续致命）
            case SLAM_FIRE -> 4;              // 特殊（危险）
            case MISFIRE -> 1;                // Type 1 轻度
        };
    }

    /**
     * 清除故障所需时间（秒）
     */
    public float getClearTimeSeconds() {
        return switch (this) {
            case FAILURE_TO_FEED -> 1.5f;
            case FAILURE_TO_EXTRACT -> 5.0f;
            case FAILURE_TO_EJECT -> 2.0f;
            case DOUBLE_FEED -> 8.0f;
            case HANGFIRE -> 5.0f;            // 等待时间
            case SQUIB -> 10.0f;
            case SLAM_FIRE -> 3.0f;
            case MISFIRE -> 5.0f;
        };
    }

    /**
     * 是否需要工具才能清除
     */
    public boolean requiresTool() {
        return switch (this) {
            case FAILURE_TO_EXTRACT, SQUIB -> true;
            default -> false;
        };
    }

    /**
     * 此故障是否会导致后续炸膛风险（Squib）
     */
    public boolean causesCatastrophicRisk() {
        return this == SQUIB;
    }
}
