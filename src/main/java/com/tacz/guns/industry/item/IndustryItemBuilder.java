package com.tacz.guns.industry.item;

import com.tacz.guns.init.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Factory for NBT-identified gun components and reusable blueprints. */
public final class IndustryItemBuilder {
    private final Item item;
    private String platform = "";
    private String kind = "";
    private String displayName = "";

    private IndustryItemBuilder(Item item) {
        this.item = item;
    }

    public static IndustryItemBuilder component() {
        return new IndustryItemBuilder(ModItems.GUN_COMPONENT);
    }

    public static IndustryItemBuilder blueprint() {
        return new IndustryItemBuilder(ModItems.GUN_BLUEPRINT);
    }

    public IndustryItemBuilder platform(String platform) {
        this.platform = platform == null ? "" : platform;
        return this;
    }

    public IndustryItemBuilder kind(String kind) {
        this.kind = kind == null ? "" : kind;
        return this;
    }

    public IndustryItemBuilder displayNameKey(String displayName) {
        this.displayName = displayName == null ? "" : displayName;
        return this;
    }

    public ItemStack build() {
        ItemStack stack = new ItemStack(item);
        if (stack.getItem() instanceof IndustryItemDataAccessor accessor) {
            accessor.setPlatform(stack, platform);
            accessor.setPartKind(stack, kind);
            accessor.setDisplayNameKey(stack, displayName);
        }
        return stack;
    }
}
