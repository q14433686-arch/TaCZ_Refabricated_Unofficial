package cn.sh1rocu.tacz.industry.api.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

/**
 * TACZ-INDUSTRIAL 内部 Codec 助手。
 * 不依赖 Identifier.CODEC 是否存在（26.2 API 冻结前保守起见自实现）。
 */
public final class IndustryCodecs {
    private IndustryCodecs() {
    }

    /**
     * 字符串形态的 Identifier codec。非法值在解码期报 DataResult.error 而不是静默丢数据。
     */
    public static final Codec<Identifier> IDENTIFIER = Codec.STRING.comapFlatMap(str -> {
        Identifier id = Identifier.tryParse(str);
        return id != null
                ? DataResult.success(id)
                : DataResult.error(() -> "Invalid identifier: " + str);
    }, Identifier::toString);

    /**
     * 枚举 codec：按序列化名读写，非法值报错。避免序号存储（序号在插入枚举值后产生存档漂移）。
     */
    public static <E extends Enum<E>> Codec<E> enumByName(Class<E> type, E[] values, Function<E, String> nameGetter) {
        return Codec.STRING.comapFlatMap(str -> {
            for (E v : values) {
                if (nameGetter.apply(v).equals(str)) {
                    return DataResult.success(v);
                }
            }
            return DataResult.error(() -> "Unknown " + type.getSimpleName() + " value: " + str);
        }, nameGetter);
    }
}
