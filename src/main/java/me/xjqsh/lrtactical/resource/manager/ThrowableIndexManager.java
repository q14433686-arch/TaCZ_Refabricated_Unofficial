package me.xjqsh.lrtactical.resource.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.init.ModRegistries;
import me.xjqsh.lrtactical.item.index.ThrowableIndex;
import me.xjqsh.lrtactical.item.throwable.ThrowableType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * 扫描并解析 {@code data/<ns>/index/throwable/*.json}。
 *
 * <p>直接复用 TACZ 的 {@code JsonDataManager}（它已完成 26.2 适配：
 * {@code Identifier}、{@code IdentifiableResourceReloadListener} 等）。
 *
 * <h2>为什么覆写 {@code apply} 而不用父类默认实现</h2>
 * 父类默认走 {@code gson.fromJson(element, dataClass)} 直接反序列化成目标类型；
 * 但本索引<b>不能</b>这么做 —— 它要先读 {@code "type"} 字段查出对应的
 * {@link ThrowableType}，再用该类型自带的 serializer 去解析 {@code "data"} 段。
 * 这正是数据驱动的关键：同一个 index 文件，{@code type} 不同则 {@code data} 的结构也不同。
 * 故构造时把 {@code dataClass} 传 {@code null} 并自行实现 {@code apply}，与上游一致。
 *
 * <h2>26.2 差异</h2>
 * <ul>
 *   <li>{@code ResourceLocation} → {@link Identifier}；</li>
 *   <li>{@code Registry#get(Identifier)} → {@code getValue(Identifier)}
 *       （本仓库此前已踩过同一处改名）；</li>
 *   <li>类型注册表在 Fabric 上是普通 Map（见 {@link ModRegistries} 的说明），
 *       故这里查表走 {@code ModRegistries.getThrowableType}。</li>
 * </ul>
 *
 * <h2>网络同步（已实现）</h2>
 * 原始 JSON 会写入 {@link #networkCache}，由
 * {@code ServerMessageSyncLrPack} 在登录及数据包重载时发给客户端；客户端再经
 * {@link #fromNetwork(Map)} 调用同一份 {@link #parse(JsonObject, Identifier)}
 * 重建索引。旧注释曾写“暂未实现网络同步”，但那在同步包落地后已经过时。
 */
public class ThrowableIndexManager extends com.tacz.guns.resource.manager.JsonDataManager<ThrowableIndex<?, ?>> {
    public ThrowableIndexManager(Gson gson) {
        // dataClass 传 null：本类自行解析，不走父类的 gson.fromJson，见类注释
        super(null, gson, "index/throwable", "LrThrowableIndex");
    }

    /**
     * 原始 JSON 缓存，专供发给客户端。
     *
     * <p>为什么要留原文而不是把解析后的 {@link ThrowableIndex} 序列化：
     * 索引里含 {@code ThrowableType}（行为 + 实体工厂），那是<b>代码对象</b>，
     * 根本无法序列化。客户端只能拿到同一份 JSON 后<b>用同一个解析器再解析一遍</b>，
     * 这也是 TACZ 侧 {@code CommonDataManager#networkCache} 的既有做法。
     */
    private Map<Identifier, String> networkCache = java.util.Collections.emptyMap();

    @Override
    protected void apply(Map<Identifier, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        this.getAllData().clear();
        com.google.common.collect.ImmutableMap.Builder<Identifier, String> rawBuilder =
                com.google.common.collect.ImmutableMap.builder();
        for (Map.Entry<Identifier, JsonElement> entry : object.entrySet()) {
            Identifier id = entry.getKey();
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                EquipmentMod.LOGGER.error("Failed to load throwable index {}: expected object, got {}", id, element);
                continue;
            }
            try {
                ThrowableIndex<?, ?> index = parse(element.getAsJsonObject(), id);
                if (index != null) {
                    this.getAllData().put(id, index);
                    // 只缓存解析成功的，避免把坏数据发给客户端再报一次同样的错
                    rawBuilder.put(id, element.toString());
                }
            } catch (JsonParseException | IllegalArgumentException e) {
                EquipmentMod.LOGGER.error("Failed to load throwable index {}", id, e);
            }
        }
        this.networkCache = rawBuilder.build();
        EquipmentMod.LOGGER.info("Loaded {} throwable index(es)", this.getAllData().size());
    }

    /** 供 {@code ServerMessageSyncLrPack} 打包发送。 */
    public Map<Identifier, String> getNetworkCache() {
        return this.networkCache;
    }

    /**
     * 客户端收到同步包后，用<b>同一个解析器</b>重建索引。
     *
     * <p>刻意复用 {@link #parse}，保证两端解析逻辑<b>只有一份</b> ——
     * PORTING_NOTES 6.7 的教训：同一份数据有两条读取路径时必然分叉。
     */
    public void fromNetwork(Map<Identifier, String> raw) {
        this.getAllData().clear();
        for (Map.Entry<Identifier, String> entry : raw.entrySet()) {
            Identifier id = entry.getKey();
            try {
                JsonObject json = com.google.gson.JsonParser.parseString(entry.getValue()).getAsJsonObject();
                ThrowableIndex<?, ?> index = parse(json, id);
                if (index != null) {
                    this.getAllData().put(id, index);
                }
            } catch (JsonParseException | IllegalStateException | IllegalArgumentException e) {
                EquipmentMod.LOGGER.error("Failed to parse throwable index {} from network", id, e);
            }
        }
        EquipmentMod.LOGGER.info("Received {} throwable index(es) from server", this.getAllData().size());
    }

    public static ThrowableIndex<?, ?> parse(JsonObject json, Identifier id) throws JsonParseException {
        String name = GsonHelper.getAsString(json, "name", "unknown.lrtactical.name");
        String tooltip = GsonHelper.getAsString(json, "tooltip", null);

        String typeName = GsonHelper.getAsString(json, "type");
        Identifier typeId = Identifier.tryParse(typeName);
        if (typeId == null) {
            throw new JsonParseException("Malformed type id \"" + typeName + "\"");
        }
        Object rawType = ModRegistries.getThrowableType(typeId);
        if (!(rawType instanceof ThrowableType<?, ?> type)) {
            throw new JsonParseException("Unknown throwable type \"" + typeName + "\"");
        }

        String baseItemName = GsonHelper.getAsString(json, "base_item", "lrtactical:throwable");
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
     * 单独抽出泛型辅助方法。
     *
     * <p>{@code ThrowableIndex.deserialize} 要求 {@code ThrowableType<T,E>} 与其
     * serializer 的类型参数一致；从注册表取出的是通配符类型
     * {@code ThrowableType<?,?>}，直接传会因捕获类型不匹配而编译失败。
     * 借助方法级类型参数完成一次捕获转换，是处理这种情况的标准做法。
     */
    private static <T extends me.xjqsh.lrtactical.item.throwable.ThrowableData,
            E extends me.xjqsh.lrtactical.entity.ThrowableItemEntity>
    ThrowableIndex<T, E> deserialize(ThrowableType<T, E> type, JsonElement data,
                                     String name, String tooltip, Identifier id, Item baseItem) {
        return ThrowableIndex.deserialize(type, data, name, tooltip, id, baseItem);
    }
}
