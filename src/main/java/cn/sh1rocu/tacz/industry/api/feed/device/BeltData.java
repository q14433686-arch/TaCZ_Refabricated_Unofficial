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
 * 弹链数据（机枪供弹）。
 *
 * <p><b>物理形状：FIFO 队列</b>（供弹机从链头逐发勾弹）+ 链节类型 + 弹药箱对接标记。
 * 与弹匣的本质差异：供弹机构多一级"勾弹"动作，天生故障率最高（E 章勾弹失败掷骰
 * 仅对 BELT 开放）；弹链可由多条续接成"无限"后勤流（L 章弹药箱联动）。</p>
 *
 * @param linkType     链节类型（可散/不可散）
 * @param hasLinkTail  链尾是否带对接环：true = 可在弹药箱内与下一条弹链首尾相接
 *                     （"是否可对接下一条弹药箱"——任务要求字段）
 */
public record BeltData(
        Identifier cartridge,
        int capacity,
        List<LoadedRound> rounds,
        BeltLinkType linkType,
        boolean hasLinkTail
) implements FeedDeviceData {

    /**
     * 链节类型：可散链击发时链节随之抛散；不可散链整条回收。
     */
    public enum BeltLinkType {
        DISINTEGRATING("disintegrating"),
        NON_DISINTEGRATING("non_disintegrating");

        public static final Codec<BeltLinkType> CODEC = IndustryCodecs.enumByName(BeltLinkType.class, values(), BeltLinkType::getSerializedName);

        private final String serializedName;

        BeltLinkType(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return serializedName;
        }
    }

    public static final MapCodec<BeltData> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    IndustryCodecs.IDENTIFIER.fieldOf("cartridge").forGetter(BeltData::cartridge),
                    Codec.INT.fieldOf("capacity").forGetter(BeltData::capacity),
                    LoadedRound.CODEC.listOf().optionalFieldOf("rounds", List.of()).forGetter(BeltData::rounds),
                    BeltLinkType.CODEC.optionalFieldOf("link_type", BeltLinkType.DISINTEGRATING).forGetter(BeltData::linkType),
                    Codec.BOOL.optionalFieldOf("has_link_tail", false).forGetter(BeltData::hasLinkTail)
            ).apply(instance, BeltData::new)
    ).validate(BeltData::validateShape);

    public BeltData {
        rounds = List.copyOf(rounds);
    }

    private static DataResult<BeltData> validateShape(BeltData data) {
        if (data.capacity() <= 0) {
            return DataResult.error(() -> "Belt capacity must be positive");
        }
        if (data.rounds().size() > data.capacity()) {
            return DataResult.error(() -> "Belt rounds exceed capacity");
        }
        return DataResult.success(data);
    }

    public static BeltData empty(Identifier cartridge, int capacity, BeltLinkType linkType, boolean hasLinkTail) {
        return new BeltData(cartridge, capacity, List.of(), linkType, hasLinkTail);
    }

    @Override
    public FeedSystemType feedSystem() {
        return FeedSystemType.BELT;
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
        return new BeltData(cartridge, capacity, copy, linkType, hasLinkTail);
    }

    @Override
    public Optional<FeedDeviceData> tryLoad(LoadedRound round) {
        if (rejectsLoad(round) || rounds.size() >= capacity) {
            return Optional.empty();
        }
        List<LoadedRound> copy = new ArrayList<>(rounds);
        copy.add(round);
        return Optional.of(new BeltData(cartridge, capacity, copy, linkType, hasLinkTail));
    }

    /**
     * 弹药箱续接：本条链尾带对接环且下一条同口径同链型时，拼为一条链。
     * 嵌套规则：续接后保留"下一条"的链尾标记（是否还能再接由 next 决定）。
     */
    public Optional<BeltData> joinWith(BeltData next) {
        if (!this.hasLinkTail) {
            return Optional.empty();
        }
        if (!this.cartridge.equals(next.cartridge) || this.linkType != next.linkType) {
            return Optional.empty();
        }
        if (this.rounds.size() + next.rounds.size() > this.capacity) {
            return Optional.empty();
        }
        List<LoadedRound> copy = new ArrayList<>(this.rounds);
        copy.addAll(next.rounds);
        return Optional.of(new BeltData(cartridge, capacity, copy, linkType, next.hasLinkTail));
    }
}
