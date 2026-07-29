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
                    JsonObject resultObject = jsonObject.has("item") ? GsonHelper.getAsJsonObject(jsonObject, "item") : jsonObject;
                    // 26.2: custom items must be constructed lazily. During reload, component binding is not
                    // guaranteed yet; ItemStack(item) can throw "Components not bound yet" for newly registered
                    // LRTactical items. GunSmithTableResult#init runs later, when recipes are actually used.
                    result = new GunSmithTableResult(resultObject.deepCopy(), tabOverride);
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
