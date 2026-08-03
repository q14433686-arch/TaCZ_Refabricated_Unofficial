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
                    // CUSTOM result 的 "item" 字段历史上有两种写法，必须都兼容：
                    //
                    // 1) Forge / 旧枪包写法（LRTactical 9 条配方、绝大多数第三方包都这么写）：
                    //      "item": { "item": "<id>", "count": n, "nbt": {...} }
                    //    内层的 "item" 是物品 id 字符串，由 CraftingHelper.getItemStack 读取。
                    //
                    // 2) 26.2 codec 新写法（GunSmithTableSerializer.ResultSpec.CODEC 写出的格式，
                    //    PR #17 把 blood_strike_1 迁到这个格式）：
                    //      "item": { "id": "<id>", "count": n, "components": {...} }
                    //    内层用 "id" 而不是 "item"，数据放在 "components"（DataComponentPatch）。
                    //
                    // 上一轮把 blood_strike_1 从顶层 "id"/"count" 迁进 "item" 时，直接按 codec
                    // 新格式写成了 id/components，但本分支仍然把 rawCustomItem 原样交给
                    // CraftingHelper.getItemStack —— 后者第一行就是
                    // GsonHelper.getAsString(json, "item")，找不到字符串 "item" 就抛
                    // JsonSyntaxException，异常在 JEI/REI 的 registerRecipes 循环里未被捕获，
                    // 直接打断整个注册流程，表现为「枪械工作台分类一个配方都没有」。
                    //
                    // 修复：进入 CUSTOM 分支时把两种写法规范化成 CraftingHelper 能读懂的
                    // 旧格式（字符串 "item" + 可选 "nbt"）。26.2 的 components 在惰性 init()
                    // 阶段目前仍由 CraftingHelper 走 nbt -> CustomData 这条兼容路径处理，
                    // 足以覆盖 painting 等无自定义数据的物品；后续若真有带 components 的
                    // CUSTOM 配方再扩展 CraftingHelper。
                    JsonObject resultObject = GsonHelper.getAsJsonObject(jsonObject, "item").deepCopy();
                    JsonElement idEl = resultObject.get("id");
                    if (idEl != null && idEl.isJsonPrimitive() && !resultObject.has("item")) {
                        // codec 新写法 -> 旧写法：把 "id" 改写成 "item" 字符串字段。
                        resultObject.add("item", idEl);
                        resultObject.remove("id");
                        // "components" 如果存在，保留给后续 CraftingHelper 扩展；
                        // 目前 CraftingHelper 只认 "nbt"，但也不会因 components 多一个字段而报错。
                    }
                    result = new GunSmithTableResult(resultObject, tabOverride);
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
}
