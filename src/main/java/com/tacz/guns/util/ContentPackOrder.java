package com.tacz.guns.util;

import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.Map;

public final class ContentPackOrder {
    public static final Comparator<String> NAMESPACE_ORDER = Comparator
            .comparingInt((String namespace) -> GunMod.MOD_ID.equals(namespace) ? 0 : 1)
            .thenComparing(Comparator.naturalOrder());

    private ContentPackOrder() {
    }

    public static <T> Comparator<Map.Entry<Identifier, T>> byNamespace() {
        return Comparator.comparing(entry -> entry.getKey().getNamespace(), NAMESPACE_ORDER);
    }
}
