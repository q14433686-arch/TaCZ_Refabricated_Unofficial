package com.tacz.guns.resource.pojo.data.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record LootTableInjection(List<Identifier> lootTables, LootTable lootTable, Identifier id) {

    public static LootTableInjection fromJson(Identifier fileId, JsonElement element) {
        // 26.2 兼容层：minecraft:set_nbt 已被移除。set_custom_data 与 set_nbt 完全等价
        // （同样接收 tag 字段），且写入 minecraft:custom_data 组件——而 TACZ 的 GunId/AmmoId
        // 本就存在该组件的 NBT 中（ItemNbtUtils），因此旧枪包的 set_nbt 迁移后枪/弹药变体可正确生成。
        JsonElement migrated = LegacyLootCompat.migrateSetNbt(element);
        JsonObject object = GsonHelper.convertToJsonObject(migrated, "loot injection");
        List<Identifier> lootTables = readLootTables(fileId, object);
        if (!object.has("pools")) {
            throw new JsonParseException("Loot injection " + fileId + " must define pools");
        }

        var lootTable = LootTable.DIRECT_CODEC.parse(JsonOps.INSTANCE, object)
                .result().orElseThrow(() -> new JsonParseException("Failed to parse loot table: " + fileId));
        return new LootTableInjection(lootTables, lootTable, fileId);
    }

    private static List<Identifier> readLootTables(Identifier fileId, JsonObject object) {
        List<Identifier> lootTables = new ArrayList<>();
        if (object.has("loot_tables")) {
            for (JsonElement table : GsonHelper.getAsJsonArray(object, "loot_tables")) {
                lootTables.add(Identifier.parse(GsonHelper.convertToString(table, "loot table")));
            }
        } else if (object.has("loot_table")) {
            lootTables.add(Identifier.parse(GsonHelper.getAsString(object, "loot_table")));
        } else {
            throw new JsonParseException("Loot injection " + fileId + " must define loot_table or loot_tables");
        }
        return List.copyOf(lootTables);
    }

    public List<ItemStack> createStacks(LootContext context) {
        List<ItemStack> stacks = new ArrayList<>();
        lootTable.getRandomItemsRaw(context, stacks::add);
        return stacks;
    }

    /**
     * 兼容层：递归遍历 loot JSON，把 {function:"minecraft:set_nbt", tag:"<SNBT>"}
     * 改名成 {function:"minecraft:set_custom_data", tag:"<SNBT>"}。
     * 26.2 的 set_custom_data 与旧 set_nbt 行为等价（写入 custom_data 组件）。
     */
    static final class LegacyLootCompat {
        static JsonElement migrateSetNbt(JsonElement element) {
            if (element == null || element.isJsonNull()) {
                return element;
            }
            if (element.isJsonObject()) {
                JsonObject o = element.getAsJsonObject();
                if (o.has("function") && o.get("function").isJsonPrimitive()
                        && "minecraft:set_nbt".equals(o.get("function").getAsString()) && o.has("tag")) {
                    JsonObject replacement = new JsonObject();
                    replacement.addProperty("function", "minecraft:set_custom_data");
                    replacement.add("tag", o.get("tag"));
                    if (o.has("conditions")) {
                        replacement.add("conditions", o.get("conditions"));
                    }
                    return replacement;
                }
                JsonObject out = new JsonObject();
                for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                    out.add(en.getKey(), migrateSetNbt(en.getValue()));
                }
                return out;
            }
            if (element.isJsonArray()) {
                JsonArray out = new JsonArray();
                for (JsonElement el : element.getAsJsonArray()) {
                    out.add(migrateSetNbt(el));
                }
                return out;
            }
            return element;
        }
    }
}
