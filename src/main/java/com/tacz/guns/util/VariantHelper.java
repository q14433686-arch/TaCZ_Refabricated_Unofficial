package com.tacz.guns.util;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class VariantHelper {
    private static final String KEY = "Variant";
    private VariantHelper() {}

    public static void setVariant(ItemStack part, Identifier id) {
        if (!part.isEmpty() && id != null) ItemNbtUtils.updateTag(part, tag -> tag.putString(KEY, id.toString()));
    }

    public static @Nullable Identifier getVariant(ItemStack part) {
        return part.isEmpty() ? null : ItemNbtUtils.getTag(part).getString(KEY).map(Identifier::tryParse).orElse(null);
    }

    public static boolean hasVariant(ItemStack part) { return getVariant(part) != null; }

    public static void removeVariant(ItemStack part) {
        if (!part.isEmpty()) ItemNbtUtils.updateTag(part, tag -> tag.remove(KEY));
    }
}
