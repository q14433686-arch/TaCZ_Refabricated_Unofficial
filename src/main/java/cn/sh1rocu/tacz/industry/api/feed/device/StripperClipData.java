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
 * 桥夹数据（为固定弹仓装弹的一次性工具，而非枪内供弹具）。
 *
 * <p><b>物理形状：固定容量 + 一次性消耗标记。</b>
 * 桥夹的价值是"预排好的一夹弹"：装填动作把整夹弹一次性压入枪的固定仓，
 * 之后桥夹自身成为消耗件（{@code consumed=true}，回收铜材或丢弃）——
 * 桥夹缺失时逐发压仓的装填耗时 ×3（N 章节奏惩罚）。</p>
 *
 * <p>注意：桥夹本身不直接给枪膛供弹，peek/eject 仅服务"压仓"动作的内部遍历。</p>
 */
public record StripperClipData(
        Identifier cartridge,
        int capacity,
        List<LoadedRound> rounds,
        boolean consumed
) implements FeedDeviceData {

    public static final MapCodec<StripperClipData> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    IndustryCodecs.IDENTIFIER.fieldOf("cartridge").forGetter(StripperClipData::cartridge),
                    Codec.INT.fieldOf("capacity").forGetter(StripperClipData::capacity),
                    LoadedRound.CODEC.listOf().optionalFieldOf("rounds", List.of()).forGetter(StripperClipData::rounds),
                    Codec.BOOL.optionalFieldOf("consumed", false).forGetter(StripperClipData::consumed)
            ).apply(instance, StripperClipData::new)
    ).validate(StripperClipData::validateShape);

    public StripperClipData {
        rounds = List.copyOf(rounds);
    }

    private static DataResult<StripperClipData> validateShape(StripperClipData data) {
        if (data.capacity() <= 0) {
            return DataResult.error(() -> "StripperClip capacity must be positive");
        }
        if (data.rounds().size() > data.capacity()) {
            return DataResult.error(() -> "StripperClip rounds exceed capacity");
        }
        if (data.consumed() && !data.rounds().isEmpty()) {
            return DataResult.error(() -> "Consumed stripper clip must be empty");
        }
        return DataResult.success(data);
    }

    public static StripperClipData empty(Identifier cartridge, int capacity) {
        return new StripperClipData(cartridge, capacity, List.of(), false);
    }

    @Override
    public FeedSystemType feedSystem() {
        return FeedSystemType.STRIPPER_CLIP;
    }

    @Override
    public int loadedCount() {
        return rounds.size();
    }

    @Override
    public Optional<LoadedRound> peekNext() {
        return rounds.isEmpty() ? Optional.empty() : Optional.of(rounds.get(0));
    }

    @Override
    public FeedDeviceData ejectNext() {
        if (rounds.isEmpty()) {
            return this;
        }
        List<LoadedRound> copy = new ArrayList<>(rounds);
        copy.remove(0);
        // 弹尽即消耗（最后一颗压入枪仓，桥夹完成使命）
        return new StripperClipData(cartridge, capacity, copy, copy.isEmpty());
    }

    @Override
    public Optional<FeedDeviceData> tryLoad(LoadedRound round) {
        // 一次性消耗品：已消耗或余夹都不接受中间补弹（预排的工业弹夹，保持语义纯粹）
        if (consumed || rejectsLoad(round) || !rounds.isEmpty()) {
            return Optional.empty();
        }
        List<LoadedRound> copy = new ArrayList<>(rounds);
        copy.add(round);
        return Optional.of(new StripperClipData(cartridge, capacity, copy, false));
    }

    /**
     * 整夹弹药（压仓动作批量读取）。
     */
    public List<LoadedRound> stripAll() {
        return rounds;
    }
}
