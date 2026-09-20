package cn.sh1rocu.tacz.util;

import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * {@code FriendlyByteBuf#readMap/writeMap} 的替代实现。
 *
 * <h2>为什么需要它</h2>
 * <p>26.3 从 {@link FriendlyByteBuf} 上移除了 {@code readMap} / {@code writeMap}
 * 这两个便利方法（{@code readCollection}/{@code writeCollection} 同批移除）。
 * 上游的做法是让每个包自己用 {@code StreamCodec} 组合，但本仓库的几个同步包
 * 走的是手写 {@code FriendlyByteBuf} 构造器 + {@code write} 的老式写法，
 * 全面改造成 StreamCodec 属于超出移植范围的重构。</p>
 *
 * <h2>线格式</h2>
 * <p>与 26.2 的 {@code readMap}/{@code writeMap} <b>完全一致</b>：
 * 先一个 varint 表示条目数，然后逐条写 key、value。
 * 因此本类只是把被删掉的方法原样搬过来，不改变任何协议行为。</p>
 */
public final class BufMapCodec {
    private BufMapCodec() {
    }

    /** 等价于 26.2 的 {@code buf.readMap(keyReader, valueReader)}。 */
    public static <K, V> Map<K, V> readMap(FriendlyByteBuf buf,
                                           Function<FriendlyByteBuf, K> keyReader,
                                           Function<FriendlyByteBuf, V> valueReader) {
        int size = buf.readVarInt();
        Map<K, V> map = new HashMap<>(Math.max(16, size));
        for (int i = 0; i < size; i++) {
            K key = keyReader.apply(buf);
            map.put(key, valueReader.apply(buf));
        }
        return map;
    }

    /** 等价于 26.2 的 {@code buf.writeMap(map, keyWriter, valueWriter)}。 */
    public static <K, V> void writeMap(FriendlyByteBuf buf, Map<K, V> map,
                                       BiConsumer<FriendlyByteBuf, K> keyWriter,
                                       BiConsumer<FriendlyByteBuf, V> valueWriter) {
        buf.writeVarInt(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            keyWriter.accept(buf, entry.getKey());
            valueWriter.accept(buf, entry.getValue());
        }
    }
}
