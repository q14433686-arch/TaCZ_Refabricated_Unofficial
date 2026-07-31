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
 * 管状弹仓数据（杠杆枪/泵动霰弹枪）。
 *
 * <p><b>物理形状：严格 FIFO 队列。</b>从装弹口推入的弹药排到队尾，
 * 枪机始终取队首（最早装入的那发）——与管状弹仓弹簧推送的物理行为一致。
 * 这带来真实玩法特性：无法切换弹种（先装的必先射），但可随时打断装填"顶一发打一发"。</p>
 *
 * <p><b>历史规则（N-1）：</b>尖头步枪弹禁装管仓（管内首尾相抵的安全限制）
 * ——由规则层 {@code tubelarSafetyRule} 在 tryLoad 前判定，数据结构层面只存结果。</p>
 *
 * @param springFatigue 管仓弹簧疲软度（独立磨损轨，语义同弹匣弹簧）
 */
public record TubularMagazineData(
        Identifier cartridge,
        int capacity,
        List<LoadedRound> rounds,
        float springFatigue
) implements FeedDeviceData {

    public static final MapCodec<TubularMagazineData> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    IndustryCodecs.IDENTIFIER.fieldOf("cartridge").forGetter(TubularMagazineData::cartridge),
                    Codec.INT.fieldOf("capacity").forGetter(TubularMagazineData::capacity),
                    LoadedRound.CODEC.listOf().optionalFieldOf("rounds", List.of()).forGetter(TubularMagazineData::rounds),
                    Codec.FLOAT.optionalFieldOf("spring_fatigue", 0f).forGetter(TubularMagazineData::springFatigue)
            ).apply(instance, TubularMagazineData::new)
    ).validate(TubularMagazineData::validateShape);

    public TubularMagazineData {
        rounds = List.copyOf(rounds);
    }

    private static DataResult<TubularMagazineData> validateShape(TubularMagazineData data) {
        if (data.capacity() <= 0) {
            return DataResult.error(() -> "TubularMagazine capacity must be positive");
        }
        if (data.rounds().size() > data.capacity()) {
            return DataResult.error(() -> "TubularMagazine rounds exceed capacity");
        }
        return DataResult.success(data);
    }

    public static TubularMagazineData empty(Identifier cartridge, int capacity) {
        return new TubularMagazineData(cartridge, capacity, List.of(), 0f);
    }

    @Override
    public FeedSystemType feedSystem() {
        return FeedSystemType.TUBULAR;
    }

    @Override
    public int loadedCount() {
        return rounds.size();
    }

    @Override
    public Optional<LoadedRound> peekNext() {
        // FIFO：队首（索引 0）= 最早装入 = 下一发出膛
        return rounds.isEmpty() ? Optional.empty() : Optional.of(rounds.get(0));
    }

    @Override
    public FeedDeviceData ejectNext() {
        if (rounds.isEmpty()) {
            return this;
        }
        List<LoadedRound> copy = new ArrayList<>(rounds);
        copy.remove(0);
        return new TubularMagazineData(cartridge, capacity, copy, springFatigue);
    }

    @Override
    public Optional<FeedDeviceData> tryLoad(LoadedRound round) {
        if (rejectsLoad(round) || rounds.size() >= capacity) {
            return Optional.empty();
        }
        List<LoadedRound> copy = new ArrayList<>(rounds);
        copy.add(round); // 队尾装填
        return Optional.of(new TubularMagazineData(cartridge, capacity, copy, springFatigue));
    }
}
