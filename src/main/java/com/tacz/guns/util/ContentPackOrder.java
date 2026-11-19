package com.tacz.guns.util;

import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.Map;
import java.util.function.ToIntFunction;

public final class ContentPackOrder {
    public static final Comparator<String> NAMESPACE_ORDER = Comparator
            .comparingInt((String namespace) -> GunMod.MOD_ID.equals(namespace) ? 0 : 1)
            .thenComparing(Comparator.naturalOrder());

    private ContentPackOrder() {
    }

    public static <T> Comparator<Map.Entry<Identifier, T>> byNamespace() {
        return Comparator.comparing(entry -> entry.getKey().getNamespace(), NAMESPACE_ORDER);
    }
    /** Same tie-breaker for creative items and console-generated samples, including external packs. */
    public static <T> Comparator<Map.Entry<Identifier, T>> byPackThenSort(ToIntFunction<T> sort) {
        return ContentPackOrder.<T>byNamespace()
                .thenComparingInt(entry -> sort.applyAsInt(entry.getValue())).thenComparing(Map.Entry::getKey);
    }
}
