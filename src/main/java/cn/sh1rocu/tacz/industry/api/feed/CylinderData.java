package cn.sh1rocu.tacz.industry.api.feed;

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
 * 转轮弹巢数据。
 *
 * <p><b>物理形状：固定长度槽位数组</b>——每个槽位独立记录三态
 * （空 / 实弹 / 待抛壳），外加"当前对齐枪管的槽位索引"。
 * 与弹匣的本质区别：击发后空壳不出外，格位状态记忆让"数弹靠记、排哑弹靠逐膛检查"
 * 成为真实玩法（E 章转轮故障签名）。</p>
 *
 * <p><b>嵌套规则：</b></p>
 * <ul>
 *   <li>ejectNext = 对齐格位由 LOADED 转 SPENT，随后索引前进一格（模拟转轮旋转）</li>
 *   <li>peekNext 只看对齐格位——空对齐位 = "咔嚓空击"，这是真实的转轮体验</li>
 *   <li>装填只能入 EMPTY 格；退壳杆动作（{@link #ejectAllSpent()}）清除全部 SPENT 格</li>
 *   <li>无 double feed / FTF 类故障——E 章故障表对 CYLINDER 自动屏蔽该类掷骰</li>
 * </ul>
 */
public record CylinderData(
        Identifier cartridge,
        List<CylinderSlot> slots,
        int alignedIndex
) implements FeedDeviceData {

    /**
     * 单个弹巢槽位（嵌套数据形状）。
     * 不变量：LOADED 必须携带 round；EMPTY/SPENT 的 round 必须为空（空壳不占数据）。
     */
    public record CylinderSlot(SlotState state, Optional<LoadedRound> round) {
        public static final Codec<CylinderSlot> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                SlotState.CODEC.fieldOf("state").forGetter(CylinderSlot::state),
                LoadedRound.CODEC.optionalFieldOf("round").forGetter(CylinderSlot::round)
        ).apply(instance, CylinderSlot::new));

        public static CylinderSlot empty() {
            return new CylinderSlot(SlotState.EMPTY, Optional.empty());
        }

        public static CylinderSlot loaded(LoadedRound round) {
            return new CylinderSlot(SlotState.LOADED, Optional.of(round));
        }

        /**
         * 击发后的槽位：实弹化身空壳占位（保留个体数据供逐膛排查哑弹展示）。
         */
        public CylinderSlot asSpent() {
            return new CylinderSlot(SlotState.SPENT, round.map(LoadedRound::asSpent));
        }
    }

    public enum SlotState {
        EMPTY("empty"),
        LOADED("loaded"),
        SPENT("spent");

        public static final Codec<SlotState> CODEC = IndustryCodecs.enumByName(SlotState.class, values(), SlotState::getSerializedName);

        private final String serializedName;

        SlotState(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return serializedName;
        }
    }

    // Q-21 编译教训（javac 死证）：绝不能写成 mapCodec(...).validate(...) 单链——
    // 链中段的泛型方法调用失去赋值目标类型流入（非 poly expression），
    // instance 的类型变量 O 将被推断为 Object，group(App<Mu<Object>,...>)
    // 与 RecordCodecBuilder<CylinderData,...> 实参全系不匹配。
    // 原版惯例两步走：先单独语句直接赋值（O 由目标类型锚定），再链 validate。
    private static final MapCodec<CylinderData> SHAPE_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    IndustryCodecs.IDENTIFIER.fieldOf("cartridge").forGetter(CylinderData::cartridge),
                    CylinderSlot.CODEC.listOf().fieldOf("slots").forGetter(CylinderData::slots),
                    Codec.INT.optionalFieldOf("aligned_index", 0).forGetter(CylinderData::alignedIndex)
            ).apply(instance, CylinderData::new)
    );

    public static final MapCodec<CylinderData> CODEC = SHAPE_CODEC.validate(CylinderData::validateShape);

    public CylinderData {
        slots = List.copyOf(slots);
    }

    private static DataResult<CylinderData> validateShape(CylinderData data) {
        if (data.slots().isEmpty()) {
            return DataResult.error(() -> "Cylinder must have at least one slot");
        }
        if (data.alignedIndex() < 0 || data.alignedIndex() >= data.slots().size()) {
            return DataResult.error(() -> "Cylinder aligned_index out of range");
        }
        for (CylinderSlot slot : data.slots()) {
            boolean hasRound = slot.round().isPresent();
            if ((slot.state() == SlotState.LOADED) != hasRound) {
                return DataResult.error(() -> "Cylinder slot state/round mismatch");
            }
        }
        return DataResult.success(data);
    }

    public static CylinderData empty(Identifier cartridge, int slotCount) {
        List<CylinderSlot> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(CylinderSlot.empty());
        }
        return new CylinderData(cartridge, slots, 0);
    }

    @Override
    public FeedSystemType feedSystem() {
        return FeedSystemType.CYLINDER;
    }

    @Override
    public int capacity() {
        return slots.size();
    }

    @Override
    public int loadedCount() {
        int count = 0;
        for (CylinderSlot slot : slots) {
            if (slot.state() == SlotState.LOADED) {
                count++;
            }
        }
        return count;
    }

    /**
     * 待抛壳格数（决定退壳杆动作的动画/时间）。
     */
    public int spentCount() {
        int count = 0;
        for (CylinderSlot slot : slots) {
            if (slot.state() == SlotState.SPENT) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Optional<LoadedRound> peekNext() {
        // 只看对齐格位；对齐空位 = 空击（转轮特性，由 E 章成"咔嚓"干响而非故障）
        CylinderSlot aligned = slots.get(alignedIndex);
        if (aligned.state() == SlotState.SPENT) {
            return Optional.empty();
        }
        return aligned.round();
    }

    @Override
    public FeedDeviceData ejectNext() {
        CylinderSlot aligned = slots.get(alignedIndex);
        List<CylinderSlot> copy = new ArrayList<>(slots);
        if (aligned.state() == SlotState.LOADED) {
            copy.set(alignedIndex, aligned.asSpent());
        }
        int next = (alignedIndex + 1) % slots.size();
        return new CylinderData(cartridge, copy, next);
    }

    @Override
    public Optional<FeedDeviceData> tryLoad(LoadedRound round) {
        if (rejectsLoad(round)) {
            return Optional.empty();
        }
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).state() == SlotState.EMPTY) {
                List<CylinderSlot> copy = new ArrayList<>(slots);
                copy.set(i, CylinderSlot.loaded(round));
                return Optional.of(new CylinderData(cartridge, copy, alignedIndex));
            }
        }
        return Optional.empty(); // 全格占用
    }

    /**
     * 退壳杆动作：全部 SPENT 格清空（空壳落地由调用方生成拾取物）。
     */
    public CylinderData ejectAllSpent() {
        List<CylinderSlot> copy = new ArrayList<>(slots.size());
        for (CylinderSlot slot : slots) {
            copy.add(slot.state() == SlotState.SPENT ? CylinderSlot.empty() : slot);
        }
        return new CylinderData(cartridge, copy, alignedIndex);
    }
}
