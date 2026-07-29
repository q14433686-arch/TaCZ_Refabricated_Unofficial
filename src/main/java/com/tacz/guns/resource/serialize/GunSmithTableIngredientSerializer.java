package com.tacz.guns.resource.serialize;

import com.google.gson.*;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import net.minecraft.util.GsonHelper;

import java.lang.reflect.Type;

/**
 * 第 14 轮：不再在此处调用 {@code Ingredient.CODEC.parse}。
 *
 * <p>本序列化器由 {@code CommonDataManager}（一个 server reload listener）驱动，
 * 而 26.2 的 item tag 要等 {@code MinecraftServer#reloadResources} 里的
 * {@code updateComponentsAndStaticRegistryTags()} 才绑定，<b>晚于所有 reload listener</b>。
 * 在这里解析 {@code "#c:ingots/copper"} 必然拿到 {@code Missing tag}，
 * 异常再被 {@code JsonDataManager#apply} 吞掉，导致整条配方静默消失。
 * 详见 {@link GunSmithTableIngredient} 的类注释。
 *
 * <p>因此这里只做结构校验 + 原样保存 JSON，真正解析推迟到首次取用时。
 */
public class GunSmithTableIngredientSerializer implements JsonDeserializer<GunSmithTableIngredient> {
    @Override
    public GunSmithTableIngredient deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            if (!jsonObject.has("item")) {
                throw new JsonSyntaxException("Expected " + jsonObject + " must has a item member");
            }
            int count = 1;
            if (jsonObject.has("count")) {
                count = Math.max(GsonHelper.getAsInt(jsonObject, "count"), 1);
            }
            // 延迟解析：此刻 tag 尚未绑定，存原文即可。
            return new GunSmithTableIngredient(jsonObject.get("item"), count);
        } else {
            throw new JsonSyntaxException("Expected " + json + " to be a Pair because it's not an object");
        }
    }
}
