package cn.sh1rocu.tacz.industry.api.feed.device;

import cn.sh1rocu.tacz.industry.api.ammo.LoadedRound;
import cn.sh1rocu.tacz.industry.api.feed.FeedDeviceData;
import cn.sh1rocu.tacz.industry.api.feed.FeedSystemType;
import cn.sh1rocu.tacz.industry.api.internal.IndustryCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 可拆盒式弹匣数据。
 *
 * <p><b>物理形状：</b>有序列表 + LIFO 语义——列表末位 = 栈顶 = 将被供入枪膛的下一发；
 * 装填压入栈底方向（append 后由后续装填压深，peek 永远看末位）。</p>
 *
 * <p><b>两条独立磨损轨（I 章 I-2.1）：</b></p>
 * <ul>
 *   <li>{@code springFatigue} 弹匣弹簧疲软度 0–1：满匣存放与供弹循环累积，&gt;0.15 起抬升 FTF 权重</li>
 *   <li>{@code feedLipDamage} 供弹口（抱弹唇）损伤度 0–1：掉落/劣质装配累积，直接进 FTF/进弹口型双进弹权重</li>
 * </ul>
 *
 * <p>磨损只随使用事件写入（事件驱动，不在 tick 衰减），由 I 章耐久系统消费。</p>
 */
public record BoxMagazineData(
        Identifier cartridge,
        int capacity,
        List<LoadedRound> rounds,
        float springFatigue,
        float feedLipDamage
) implements FeedDeviceData {

    public static final MapCodec<BoxMagazineData> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    IndustryCodecs.IDENTIFIER.fieldOf("cartridge").forGetter(BoxMagazineData::cartridge),
                    Codec.INT.fieldOf("capacity").forGetter(BoxMagazineData::capacity),
                    LoadedRound.CODEC.listOf().optionalFieldOf("rounds", List.of()).forGetter(BoxMagazineData::rounds),
                    Codec.FLOAT.optionalFieldOf("spring_fatigue", 0f).forGetter(BoxMagazineData::springFatigue),
                    Codec.FLOAT.optionalFieldOf("feed_lip_damage", 0f).forGetter(BoxMagazineData::feedLipDamage)
            ).apply(instance, BoxMagazineData::new)
    ).validate(BoxMagazineData::validateShape);

    public BoxMagazineData {
        rounds = List.copyOf(rounds);
    }

    private static DataResult<BoxMagazineData> validateShape(BoxMagazineData data) {
        if (data.capacity() <= 0) {
            return DataResult.error(() -> "BoxMagazine capacity must be positive");
        }
        if (data.rounds().size() > data.capacity()) {
            return DataResult.error(() -> "BoxMagazine rounds exceed capacity: " + data.rounds().size() + " > " + data.capacity());
        }
        return DataResult.success(data);
    }

    public static BoxMagazineData empty(Identifier cartridge, int capacity) {
        return new BoxMagazineData(cartridge, capacity, List.of(), 0f, 0f);
    }

    @Override
    public FeedSystemType feedSystem() {
        return FeedSystemType.BOX_MAGAZINE;
    }

    @Override
    public int loadedCount() {
        return rounds.size();
    }

    @Override
    public Optional<LoadedRound> peekNext() {
        // LIFO：栈顶（列表末位）即下一发
        return rounds.isEmpty() ? Optional.empty() : Optional.of(rounds.get(rounds.size() - 1));
    }

    @Override
    public FeedDeviceData ejectNext() {
        if (rounds.isEmpty()) {
            return this;
        }
        List<LoadedRound> copy = new ArrayList<>(rounds);
        copy.remove(copy.size() - 1);
        return new BoxMagazineData(cartridge, capacity, copy, springFatigue, feedLipDamage);
    }

    @Override
    public Optional<FeedDeviceData> tryLoad(LoadedRound round) {
        if (rejectsLoad(round) || rounds.size() >= capacity) {
            return Optional.empty();
        }
        List<LoadedRound> copy = new ArrayList<>(rounds);
        copy.add(round);
        return Optional.of(new BoxMagazineData(cartridge, capacity, copy, springFatigue, feedLipDamage));
    }

    /**
     * 写入磨损（只增不减语义由调用方保证）。返回新实例。
     */
    public BoxMagazineData withWear(float springFatigue, float feedLipDamage) {
        return new BoxMagazineData(cartridge, capacity, rounds,
                clamp01(springFatigue), clamp01(feedLipDamage));
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }
}
