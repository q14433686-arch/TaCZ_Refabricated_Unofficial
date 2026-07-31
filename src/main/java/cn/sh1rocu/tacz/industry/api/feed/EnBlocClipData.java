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
 * 漏夹数据（随弹整体入仓、打空整体弹出的供弹机构）。
 *
 * <p><b>物理形状：固定容量 + 强制整体弹出标记。</b>
 * 漏夹与弹药一起进入枪的固定弹仓并参与供弹；最后一发入膛后漏夹自动弹出
 * （{@code ejected=true}，弹出动作触发 N 章著名的"叮"音效与 50% 可回收掉落）。
 * 中途不可整体更换——换弹被迫抛整夹，剩余弹药随夹浪费，这是该机构真实的战术争议点，
 * 刻意保留为玩法取舍。</p>
 *
 * <p><b>嵌套规则：</b>tryLoad 只在"完全打空前且未在枪内"才接受补弹——
 * 数据层面无法表达"是否已入枪"，该状态由枪侧 FeedSlot 管理，故本结构
 * 只允许空夹装填（预装漏夹玩法：后勤台上装好漏夹，中途不补）。</p>
 */
public record EnBlocClipData(
        Identifier cartridge,
        int capacity,
        List<LoadedRound> rounds,
        boolean ejected
) implements FeedDeviceData {

    // Q-21 编译教训（javac 死证）：绝不能写成 mapCodec(...).validate(...) 单链——
    // 链中段的泛型方法调用失去赋值目标类型流入（非 poly expression），
    // instance 的类型变量 O 将被推断为 Object，group(App<Mu<Object>,...>)
    // 与 RecordCodecBuilder<EnBlocClipData,...> 实参全系不匹配。
    // 原版惯例两步走：先单独语句直接赋值（O 由目标类型锚定），再链 validate。
    private static final MapCodec<EnBlocClipData> SHAPE_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    IndustryCodecs.IDENTIFIER.fieldOf("cartridge").forGetter(EnBlocClipData::cartridge),
                    Codec.INT.fieldOf("capacity").forGetter(EnBlocClipData::capacity),
                    LoadedRound.CODEC.listOf().optionalFieldOf("rounds", List.of()).forGetter(EnBlocClipData::rounds),
                    Codec.BOOL.optionalFieldOf("ejected", false).forGetter(EnBlocClipData::ejected)
            ).apply(instance, EnBlocClipData::new)
    );

    public static final MapCodec<EnBlocClipData> CODEC = SHAPE_CODEC.validate(EnBlocClipData::validateShape);

    public EnBlocClipData {
        rounds = List.copyOf(rounds);
    }

    private static DataResult<EnBlocClipData> validateShape(EnBlocClipData data) {
        if (data.capacity() <= 0) {
            return DataResult.error(() -> "EnBlocClip capacity must be positive");
        }
        if (data.rounds().size() > data.capacity()) {
            return DataResult.error(() -> "EnBlocClip rounds exceed capacity");
        }
        if (data.ejected() && !data.rounds().isEmpty()) {
            return DataResult.error(() -> "Ejected en-bloc clip must be empty");
        }
        return DataResult.success(data);
    }

    public static EnBlocClipData empty(Identifier cartridge, int capacity) {
        return new EnBlocClipData(cartridge, capacity, List.of(), false);
    }

    @Override
    public FeedSystemType feedSystem() {
        return FeedSystemType.EN_BLOC;
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
        // 强制整体弹出：最后一发离夹的瞬刻，漏夹标记弹出
        return new EnBlocClipData(cartridge, capacity, copy, copy.isEmpty());
    }

    @Override
    public Optional<FeedDeviceData> tryLoad(LoadedRound round) {
        // 已弹出=废夹；只允许对空夹预装填
        if (ejected || rejectsLoad(round) || (!rounds.isEmpty() && rounds.size() >= capacity)) {
            return Optional.empty();
        }
        List<LoadedRound> copy = new ArrayList<>(rounds);
        copy.add(round);
        return Optional.of(new EnBlocClipData(cartridge, capacity, copy, false));
    }

    /**
     * 判断"叮"弹出事件：供弹循环后处于弹出态即触发音效与掉落判定。
     */
    public boolean justEjected() {
        return ejected;
    }
}
