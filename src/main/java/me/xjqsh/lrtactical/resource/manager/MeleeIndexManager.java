package me.xjqsh.lrtactical.resource.manager;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.init.ModRegistries;
import me.xjqsh.lrtactical.item.index.MeleeWeaponIndex;
import me.xjqsh.lrtactical.item.melee.MeleeWeaponData;
import me.xjqsh.lrtactical.item.melee.MeleeWeaponType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.Map;

/**
 * 扫描并解析 {@code data/<ns>/index/melee/*.json}。
 *
 * <p>结构与已完成的 {@code ThrowableIndexManager} 完全对称 ——
 * 同样继承 TACZ 的 {@code JsonDataManager}、同样自带 {@code networkCache}
 * 与 {@code fromNetwork}（联机时客机拿不到数据包，必须同步；
 * 这个坑投掷物已经踩过一次，见 {@code ServerMessageSyncLrPack} 的注释）。
 *
 * <p>{@code dataClass} 传 {@code null} 并自行实现 {@code apply}：
 * 索引要先读 {@code type} 字段查出对应的 {@link MeleeWeaponType}，
 * 再用该类型的 serializer 解析 {@code data} 段，无法走父类的通用反序列化。
 */
public class MeleeIndexManager extends com.tacz.guns.resource.manager.JsonDataManager<MeleeWeaponIndex<?>> {
    private Map<Identifier, String> networkCache = Collections.emptyMap();

    public MeleeIndexManager(Gson gson) {
        super(null, gson, "index/melee", "LrMeleeIndex");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        this.getAllData().clear();
        ImmutableMap.Builder<Identifier, String> rawBuilder = ImmutableMap.builder();
        for (Map.Entry<Identifier, JsonElement> entry : object.entrySet()) {
            Identifier id = entry.getKey();
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                EquipmentMod.LOGGER.error("Failed to load melee index {}: expected object, got {}", id, element);
                continue;
            }
            try {
                MeleeWeaponIndex<?> index = parse(element.getAsJsonObject(), id);
                if (index != null) {
                    this.getAllData().put(id, index);
                    rawBuilder.put(id, element.toString());
                }
            } catch (JsonParseException | IllegalArgumentException e) {
                EquipmentMod.LOGGER.error("Failed to load melee index {}", id, e);
            }
        }
        this.networkCache = rawBuilder.build();
        EquipmentMod.LOGGER.info("Loaded {} melee index(es)", this.getAllData().size());
    }

    /** 供同步包打包发送。 */
    public Map<Identifier, String> getNetworkCache() {
        return this.networkCache;
    }

    /** 客户端收到同步包后，用<b>同一个</b> {@link #parse} 重建，保证两端逻辑单一来源。 */
    public void fromNetwork(Map<Identifier, String> raw) {
        this.getAllData().clear();
        for (Map.Entry<Identifier, String> entry : raw.entrySet()) {
            Identifier id = entry.getKey();
            try {
                JsonObject json = JsonParser.parseString(entry.getValue()).getAsJsonObject();
                MeleeWeaponIndex<?> index = parse(json, id);
                if (index != null) {
                    this.getAllData().put(id, index);
                }
            } catch (JsonParseException | IllegalStateException | IllegalArgumentException e) {
                EquipmentMod.LOGGER.error("Failed to parse melee index {} from network", id, e);
            }
        }
        EquipmentMod.LOGGER.info("Received {} melee index(es) from server", this.getAllData().size());
    }

    public static MeleeWeaponIndex<?> parse(JsonObject json, Identifier id) throws JsonParseException {
        String name = GsonHelper.getAsString(json, "name", "unknown.lrtactical.name");
        String tooltip = GsonHelper.getAsString(json, "tooltip", null);

        String typeName = GsonHelper.getAsString(json, "type", "lrtactical:normal");
        Identifier typeId = Identifier.tryParse(typeName);
        if (typeId == null) {
            throw new JsonParseException("Malformed type id \"" + typeName + "\"");
        }
        Object rawType = ModRegistries.getMeleeType(typeId);
        if (!(rawType instanceof MeleeWeaponType<?> type)) {
            throw new JsonParseException("Unknown melee weapon type \"" + typeName + "\"");
        }

        String baseItemName = GsonHelper.getAsString(json, "base_item", "lrtactical:melee");
        Identifier baseItemId = Identifier.tryParse(baseItemName);
        if (baseItemId == null) {
            throw new JsonParseException("Malformed base_item id \"" + baseItemName + "\"");
        }
        // 26.2: Registry#get(Identifier) 已改名为 getValue(Identifier)
        Item item = BuiltInRegistries.ITEM.getValue(baseItemId);
        if (item == null) {
            throw new JsonParseException("Unknown base_item \"" + baseItemName + "\"");
        }

        JsonObject data = GsonHelper.getAsJsonObject(json, "data");
        return deserialize(type, data, name, tooltip, id, item);
    }

    /**
     * 泛型捕获辅助 —— 与 {@code ThrowableIndexManager} 同一手法。
     *
     * <p>注册表里存的是通配符类型 {@code MeleeWeaponType<?>}，
     * 直接传给要求 {@code <T>} 一致的 {@code deserialize} 会因捕获类型不匹配而编译失败。
     */
    private static <T extends MeleeWeaponData> MeleeWeaponIndex<T> deserialize(
            MeleeWeaponType<T> type, JsonElement data, String name,
            String tooltip, Identifier id, Item baseItem) {
        return MeleeWeaponIndex.deserialize(type, data, name, tooltip, id, baseItem);
    }
}
