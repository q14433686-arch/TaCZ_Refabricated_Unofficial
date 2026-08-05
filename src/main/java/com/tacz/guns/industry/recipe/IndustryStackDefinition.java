package com.tacz.guns.industry.recipe;

import com.google.gson.JsonObject;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.industry.item.IndustryItemBuilder;
import com.tacz.guns.industry.magazine.MagazineItemBuilder;
import com.tacz.guns.init.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A serialisable item-stack description for industry displays.
 *
 * <p>It is parsed from the actual Create recipe JSON, then synchronised through
 * TACZ's existing common-data cache so REI has the same process graph as the
 * server even when Create Fly's own REI integration is absent.</p>
 */
public final class IndustryStackDefinition {
    private final Identifier itemId;
    private final boolean tag;
    private final int count;
    private final JsonObject components;

    public IndustryStackDefinition(Identifier itemId, boolean tag, int count, JsonObject components) {
        this.itemId = itemId;
        this.tag = tag;
        this.count = Math.clamp(count, 1, 99);
        this.components = components == null ? new JsonObject() : components.deepCopy();
    }

    public Identifier getItemId() {
        return itemId;
    }

    public boolean isTag() {
        return tag;
    }

    public int getCount() {
        return count;
    }

    public JsonObject getComponents() {
        return components.deepCopy();
    }

    public ItemStack createStack() {
        if (tag) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        JsonObject custom = components.has("minecraft:custom_data") && components.get("minecraft:custom_data").isJsonObject()
                ? components.getAsJsonObject("minecraft:custom_data") : new JsonObject();
        ItemStack stack;
        if (item == ModItems.GUN_COMPONENT || item == ModItems.GUN_BLUEPRINT) {
            stack = (item == ModItems.GUN_COMPONENT ? IndustryItemBuilder.component() : IndustryItemBuilder.blueprint())
                    .platform(string(custom, "IndustryPlatform"))
                    .kind(string(custom, "IndustryPartKind"))
                    .displayNameKey(string(custom, "IndustryDisplayName"))
                    .build();
        } else if (item == ModItems.AMMO) {
            stack = AmmoItemBuilder.create()
                    .setId(Identifier.tryParse(string(custom, "AmmoId")))
                    .setCount(count)
                    .build();
            return stack;
        } else if (item == ModItems.MAGAZINE) {
            Identifier ammoId = Identifier.tryParse(string(custom, "MagazineAmmoId"));
            stack = MagazineItemBuilder.create()
                    .setFamily(string(custom, "MagazineFamily"))
                    .setAmmoId(ammoId)
                    .setCapacity(integer(custom, "MagazineCapacity", 1))
                    .setAmmoCount(integer(custom, "MagazineAmmoCount", 0))
                    .setDisplayNameKey(string(custom, "MagazineDisplayName"))
                    .build();
        } else {
            stack = new ItemStack(item, count);
        }
        if (!stack.isEmpty() && item != ModItems.MAGAZINE) {
            stack.setCount(Math.min(count, stack.getMaxStackSize()));
        }
        return stack;
    }

    public static IndustryStackDefinition fromInput(String itemId) {
        boolean tag = itemId.startsWith("#");
        Identifier id = Identifier.tryParse(tag ? itemId.substring(1) : itemId);
        return id == null ? null : new IndustryStackDefinition(id, tag, 1, new JsonObject());
    }

    public static IndustryStackDefinition fromOutput(JsonObject output) {
        Identifier id = Identifier.tryParse(string(output, "id"));
        if (id == null) {
            return null;
        }
        int count = integer(output, "count", 1);
        JsonObject components = output.has("components") && output.get("components").isJsonObject()
                ? output.getAsJsonObject("components") : new JsonObject();
        return new IndustryStackDefinition(id, false, count, components);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
