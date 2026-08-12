package com.tacz.guns.resource.serialize;

import cn.sh1rocu.tacz.util.forge.CraftingHelper;
import com.google.gson.*;
import com.tacz.guns.GunMod;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.crafting.result.RawGunTableResult;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.resource.pojo.data.recipe.GunResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;


public class GunSmithTableResultSerializer implements JsonDeserializer<GunSmithTableResult> {
    @Override
    public GunSmithTableResult deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            String typeName = GsonHelper.getAsString(jsonObject, "type");
            int count = 1;
            CompoundTag extraTag = null;
            Identifier tabOverride = null;
            if (jsonObject.has("count")) {
                count = Math.max(GsonHelper.getAsInt(jsonObject, "count"), 1);
            }
            if (jsonObject.has("nbt")) {
                extraTag = CraftingHelper.getNBT(jsonObject.get("nbt"));
            }
            if (jsonObject.has("group")) {
                String raw = GsonHelper.getAsString(jsonObject, "group");
                if (!raw.contains(":")) {
                    raw = GunMod.MOD_ID + ":" + raw;
                }
                tabOverride = Identifier.tryParse(raw);
            }

            GunSmithTableResult result;
            switch (typeName) {
                case GunSmithTableResult.GUN, GunSmithTableResult.AMMO, GunSmithTableResult.ATTACHMENT -> {
                    RawGunTableResult raw = new RawGunTableResult(typeName, getId(jsonObject), count);
                    if (extraTag != null) {
                        raw.setNbt(extraTag);
                    }
                    if (typeName.equals(GunSmithTableResult.GUN)) {
                        GunResult gunResult = CommonAssetsManager.GSON.fromJson(jsonObject, GunResult.class);
                        if (gunResult != null) {
                            raw.setExtraData(gunResult);
                        }
                    }

                    result = new GunSmithTableResult(raw, tabOverride);
                }
                case GunSmithTableResult.CUSTOM -> {
                    // 26.2: custom items must be constructed lazily. During reload, component binding is not
                    // guaranteed yet; ItemStack(item) can throw "Components not bound yet" for newly registered
                    // LRTactical items. GunSmithTableResult#init runs later, when recipes are actually used.
                    // 形状归一见 normalizeCustomResultJson:第三方包常按 26.x 配方风格写 {"id": ...},
                    // 旧实现直接存下原始 JSON,到开界面时才在 CraftingHelper.getItemStack 炸出
                    // "Missing item, expected to find a string",玩家被当作「网络协议错误」踢出。
                    result = new GunSmithTableResult(normalizeCustomResultJson(jsonObject).deepCopy(), tabOverride);
                }
                default -> {
                    return new GunSmithTableResult(ItemStack.EMPTY, TabConfig.TAB_EMPTY);
                }
            }
            return result;
        }
        return new GunSmithTableResult(ItemStack.EMPTY, TabConfig.TAB_EMPTY);
    }

    private Identifier getId(JsonObject jsonObject) {
        return Identifier.parse(GsonHelper.getAsString(jsonObject, "id"));
    }

    /**
     * 把合法与常见变体的自定义结果统一成 {@code CraftingHelper.getItemStack} 期待的形状
     * ({@code {"item": "ns:path", "count": n, "nbt": ...}}):
     *
     * <ul>
     *   <li>上游标准形 {@code {"type":"custom","item":{"item":"ns:x","count":2,"nbt":{...}}}} 原样透传;</li>
     *   <li>26.x 配方风格 {@code {"type":"custom","id":"ns:x"}} 以及嵌套
     *       {@code {"type":"custom","item":{"id":"ns:x"}}},把 {@code id} 归一到 {@code item};</li>
     *   <li>字符串简写 {@code {"type":"custom","item":"ns:x"}} 包装成对象;</li>
     *   <li>{@code count}/{@code nbt} 允许写在内层或外层,内层优先。</li>
     * </ul>
     * 真正的残缺数据(两者皆无)不在这里抛 —— 交给 {@code GunSmithTableResult#init} 的
     * 降级守卫记 WARN 并产出 EMPTY,避免坏配方在包加载时打断整个同步。
     */
    private static JsonObject normalizeCustomResultJson(JsonObject jsonObject) {
        JsonElement itemElement = jsonObject.get("item");
        JsonObject inner = itemElement != null && itemElement.isJsonObject()
                ? itemElement.getAsJsonObject() : jsonObject;

        if (inner.has("id") && inner.has("components")) {
            JsonObject modern = inner.deepCopy();
            if (!modern.has("count") && jsonObject.has("count")) {
                modern.add("count", jsonObject.get("count"));
            }
            return modern;
        }

        JsonElement itemId = null;
        if (inner.has("item") && inner.get("item").isJsonPrimitive()) {
            itemId = inner.get("item");
        } else if (itemElement != null && itemElement.isJsonPrimitive()) {
            itemId = itemElement;
        } else if (inner.has("id") && inner.get("id").isJsonPrimitive()) {
            itemId = inner.get("id");
        }

        JsonObject normalized = new JsonObject();
        if (itemId != null) {
            normalized.add("item", itemId);
        }
        JsonElement count = inner.has("count") ? inner.get("count") : jsonObject.get("count");
        if (count != null) {
            normalized.add("count", count);
        }
        JsonElement nbt = inner.has("nbt") ? inner.get("nbt") : jsonObject.get("nbt");
        if (nbt != null) {
            normalized.add("nbt", nbt);
        }
        return normalized;
    }
}
