package me.xjqsh.lrtactical.resource.manager;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.item.index.ConsumableIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.Map;

/** 扫描并解析 {@code data/<ns>/index/consumable/*.json}。 */
public class ConsumableIndexManager extends com.tacz.guns.resource.manager.JsonDataManager<ConsumableIndex> {
    private Map<Identifier, String> networkCache = Collections.emptyMap();

    public ConsumableIndexManager(Gson gson) {
        super(null, gson, "index/consumable", "LrConsumableIndex");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        this.getAllData().clear();
        ImmutableMap.Builder<Identifier, String> rawBuilder = ImmutableMap.builder();
        for (Map.Entry<Identifier, JsonElement> entry : object.entrySet()) {
            Identifier id = entry.getKey();
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                EquipmentMod.LOGGER.error("Failed to load consumable index {}: expected object, got {}", id, element);
                continue;
            }
            try {
                ConsumableIndex index = parse(element.getAsJsonObject(), id);
                if (index != null) {
                    this.getAllData().put(id, index);
                    rawBuilder.put(id, element.toString());
                }
            } catch (JsonParseException | IllegalArgumentException e) {
                EquipmentMod.LOGGER.error("Failed to load consumable index {}", id, e);
            }
        }
        this.networkCache = rawBuilder.build();
        EquipmentMod.LOGGER.info("Loaded {} consumable index(es)", this.getAllData().size());
    }

    public Map<Identifier, String> getNetworkCache() { return networkCache; }

    public void fromNetwork(Map<Identifier, String> raw) {
        this.getAllData().clear();
        for (Map.Entry<Identifier, String> entry : raw.entrySet()) {
            Identifier id = entry.getKey();
            try {
                JsonObject json = JsonParser.parseString(entry.getValue()).getAsJsonObject();
                ConsumableIndex index = parse(json, id);
                if (index != null) {
                    this.getAllData().put(id, index);
                }
            } catch (JsonParseException | IllegalStateException | IllegalArgumentException e) {
                EquipmentMod.LOGGER.error("Failed to parse consumable index {} from network", id, e);
            }
        }
        EquipmentMod.LOGGER.info("Received {} consumable index(es) from server", this.getAllData().size());
    }

    public static ConsumableIndex parse(JsonObject json, Identifier id) throws JsonParseException {
        String name = GsonHelper.getAsString(json, "name", "unknown.lrtactical.name");
        String tooltip = GsonHelper.getAsString(json, "tooltip", null);
        String baseItemName = GsonHelper.getAsString(json, "base_item", "lrtactical:consumable");
        Identifier baseItemId = Identifier.tryParse(baseItemName);
        if (baseItemId == null) {
            throw new JsonParseException("Malformed base_item id \"" + baseItemName + "\"");
        }
        Item item = BuiltInRegistries.ITEM.getValue(baseItemId);
        if (item == null) {
            throw new JsonParseException("Unknown base_item \"" + baseItemName + "\"");
        }
        JsonObject data = GsonHelper.getAsJsonObject(json, "data");
        return ConsumableIndex.deserialize(data, name, tooltip, id, item);
    }
}
