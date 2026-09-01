package com.tacz.guns.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.GunMod;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

/**
 * 工作台配方的一项材料。
 *
 * <h2>第 14 轮：为什么这里要「延迟解析」</h2>
 *
 * 原先 {@code GunSmithTableIngredientSerializer} 在 Gson 反序列化的当场就调用
 * {@code Ingredient.CODEC.parse(...)}。这在 26.2 上是<b>错误时机</b>，原因经反编译逐级确认：
 *
 * <ol>
 *   <li>{@code Ingredient.CODEC = ExtraCodecs.nonEmptyHolderSet(NON_AIR_HOLDER_SET_CODEC)}，
 *       而 {@code NON_AIR_HOLDER_SET_CODEC = HolderSetCodec.create(Registries.ITEM, ...)}；</li>
 *   <li>{@code HolderSetCodec#decode} 碰到 {@code "#c:ingots/copper"} 这种 tag 写法时，
 *       会走 {@code lookupTag(registry, tag)}；该方法在 tag 尚未绑定时直接返回
 *       {@code DataResult.error("Missing tag: ...")}；</li>
 *   <li>{@code MappedRegistry#get(TagKey)} 读的是 {@code allTags}，而 {@code allTags}
 *       要等 {@code Registry.PendingTags#apply()} 之后才有内容；</li>
 *   <li>{@code ReloadableServerResources#loadResources} 把 {@code postponedTags} 存起来，
 *       真正的 {@code updateComponentsAndStaticRegistryTags()}
 *       （内部 {@code postponedTags.forEach(PendingTags::apply)}）
 *       是在 {@code MinecraftServer#reloadResources} 的 {@code thenAcceptAsync} 里执行的，
 *       <b>晚于所有 reload listener 跑完</b>。</li>
 * </ol>
 *
 * 我们的 {@code CommonDataManager}（配方加载器）正是一个 reload listener，
 * 所以它 {@code apply()} 的时候 item tag 一律查不到 → 每个含 {@code #tag} 的材料都抛异常 →
 * {@code JsonDataManager#apply} 把 {@code JsonParseException} catch 住只打一行 error →
 * <b>整条配方被静默丢弃</b>。
 *
 * <p>实测 172 个默认配方里只有 {@code attachments/ammo_mod_he.json}（高爆弹）不含 {@code #tag}，
 * 于是「有且仅有高爆弹能被查到」——与用户观察完全吻合。运行时实验亦已证实：
 * {@code "#c:ingots/copper"} → {@code FAIL: Missing tag: 'c:ingots/copper' in 'minecraft:item'}，
 * 而 {@code "minecraft:crying_obsidian"} → OK。
 *
 * <p>对照上游 1.21.1：上游同样在反序列化当场解析，但上游配方走 vanilla {@code RecipeManager} 通道，
 * 其 ops 来自 {@code ReloadableServerResources} 的
 * {@code loadingContext = fullRegistries.lookupWithUpdatedTags()}——
 * <b>那是一份已经带上新 tag 的 lookup</b>，所以上游不触发本问题。
 * 我们在第 12 轮把配方改走自己的 {@code DataType.RECIPES} 同步通道后就失去了这份 lookup，
 * 必须自己把解析推迟到 tag 绑定之后。
 */
public class GunSmithTableIngredient {
    private final int count;

    @Nullable
    private Ingredient ingredient;
    /** 尚未解析的 {@code "item"} 字段原文；解析成功后置空。 */
    @Nullable
    private JsonElement rawItem;
    /** 只记录「是否已打过日志」，不缓存失败结果——避免过早的一次调用把材料永久毒化。 */
    private boolean loggedFailure;

    public GunSmithTableIngredient(Ingredient ingredient, int count) {
        this.ingredient = ingredient;
        this.count = count;
    }

    /** 延迟解析用的构造器，见类注释。 */
    public GunSmithTableIngredient(JsonElement rawItem, int count) {
        this.rawItem = rawItem;
        this.count = count;
    }

    /**
     * @return 解析后的 {@link Ingredient}；若原始 JSON 至今仍无法解析（例如 tag 真的不存在）则返回 {@code null}。
     *         调用方必须判空——这正是「配方整条消失」与「材料格空着但配方还在」的区别。
     */
    @Nullable
    public Ingredient getIngredient() {
        if (this.ingredient == null && this.rawItem != null) {
            JsonElement raw = this.rawItem;
            try {
                this.ingredient = Ingredient.CODEC.parse(
                        RegistryOps.create(JsonOps.INSTANCE,
                                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)),
                        normalizeLegacy(raw)
                ).getOrThrow();
                this.rawItem = null;
            } catch (RuntimeException | LinkageError e) {
                if (!this.loggedFailure) {
                    this.loggedFailure = true;
                    // ERROR + 原文 + 规范化形态 + 完整异常：跨包合成排查的第一现场。
                    // 材料格空白/配方点不动时，先在 latest.log 搜这一行。
                    // （同步自 26.2 线 7227ff9：LinkageError 也要接 —— 自定义材料
                    //  serializer 缺失在部分 Fabric 版本上以 NoClassDefFoundError
                    //  形态抛出，只接 RuntimeException 会让它消失得无声无息。）
                    GunMod.LOGGER.error("Failed to resolve gun smith table ingredient {} "
                            + "(normalized form: {})", raw, normalizeLegacy(raw), e);
                }
            }
        }
        return this.ingredient;
    }

    /**
     * 把 1.20 及以前的<b>对象式</b> Ingredient 写法规范化为 26.2 的字符串写法。
     *
     * <pre>
     * {"tag":  "forge:ingots/iron"}  ->  "#forge:ingots/iron"
     * {"item": "minecraft:flint"}    ->  "minecraft:flint"
     * </pre>
     *
     * <h2>为什么必须转换</h2>
     * 26.2 的 {@code Ingredient.CODEC} 是
     * {@code ExtraCodecs.nonEmptyHolderSet(HolderSetCodec.create(Registries.ITEM, ...))}
     * （字节码确认），{@code HolderSetCodec} <b>只接受字符串或字符串数组</b> ——
     * {@code "#tag"} 表示 tag、裸 id 表示单个物品。
     * 它<b>不认</b> {@code {"tag": ...}} / {@code {"item": ...}} 这种对象形式，
     * 遇到对象会直接返回 {@code DataResult.error}。
     *
     * <p>而 1.20 及以前的枪包写的正是对象形式。实测第三方包
     * GunpowderRevolution v1.2.7 的 68 个配方<b>全部</b>使用
     * {@code {"tag": "forge:ingots/iron"}} 这种写法，于是每一项材料都解析失败，
     * 表现为「配方条目在、材料数量也在，但材料格没有图标、且无法合成」
     * —— 因为 {@code getIngredient()} 返回 null，走的是
     * 「材料格空着但配方还在」那条分支（见类注释）。
     *
     * <p>注意这与上一轮修的 {@code forge/tags/items → item} 是<b>两个独立问题</b>：
     * 那次修的是「tag 定义文件加载不到」，这次修的是「引用 tag 的写法不被识别」。
     * 两者都修好，旧枪包才能真正工作。
     *
     * <h2>为什么放在这里而不是转换器里</h2>
     * 该包已经是<b>新版布局</b>（自带 {@code gunpack.meta.json}），
     * 直接放进 {@code tacz/} 即可加载，根本不会经过 {@code PackConvertor}。
     * 也就是说这条路径必须自己兼容旧写法，不能指望「先转换一遍」。
     *
     * <p>数组形式（{@code [{"tag":...},{"item":...}]}）同样逐项处理 ——
     * 旧格式允许用数组表达「多选一」，新格式则是字符串数组。
     *
     * @return 规范化后的元素；本就是新写法时<b>原样返回</b>，不做任何改动
     */
    private static JsonElement normalizeLegacy(JsonElement raw) {
        if (raw.isJsonObject()) {
            JsonObject obj = raw.getAsJsonObject();
            // 【带 type 的自定义 Ingredient】改写成 Fabric 的自定义 Ingredient 格式。
            //
            // 旧 Forge 生态有一批自定义 Ingredient 类型，本项目在 util/forge 下已实现了
            // 对应的 Fabric 版（PartialNBTIngredient / StrictNBTIngredient），
            // 并已在 TaCZFabric#onInitialize 注册。这里只负责把【JSON 写法】对齐：
            //
            //   Forge : {"type":"forge:partial_nbt","item":"tacz:modern_kinetic_gun",
            //            "nbt":{"GunId":"hamster:coltm1892"}}
            //   Fabric: {"fabric:type":"forge:partial_nbt","items":["tacz:modern_kinetic_gun"],
            //            "nbt":{"GunId":"hamster:coltm1892"}}
            //
            // 两处差异都是硬性的（均经源码确认）：
            //   1. 判别键必须是 "fabric:type"（CustomIngredientImpl.TYPE_KEY 常量），
            //      Ingredient.CODEC 由 Fabric 的 IngredientMixin 用
            //      CustomIngredientImpl.CODEC.dispatch(TYPE_KEY, ...) 接管；
            //   2. 我们的 Serializer 声明的字段名是 "items" 且为【列表】
            //      （holderByNameCodec().listOf().fieldOf("items")），而 Forge 写的是
            //      单数 "item" 字符串。
            //
            // 语义完全保持不变：仍然是「必须是带该 NBT 的那件物品」，
            // 不放宽成「任意 TACZ 枪械」—— 这正是之前刻意不转换的理由，
            // 现在既然有了等价实现，就能在不改变游戏行为的前提下真正支持它。
            //
            // 只改写我们【确实注册了】的类型；其余 type 一律原样返回，
            // 让 CODEC 自己报错，避免我们猜出一个错误的等价写法掩盖真问题。
            if (obj.has("type")) {
                return normalizeCustomIngredient(obj);
            }
            // 只认这两个键，且必须是字符串；其余情况原样返回，交给 CODEC 自己报错，
            // 避免我们「猜」出一个错误的等价写法而掩盖真正的问题。
            JsonElement tag = obj.get("tag");
            if (tag != null && tag.isJsonPrimitive() && tag.getAsJsonPrimitive().isString()) {
                return new JsonPrimitive("#" + tag.getAsString());
            }
            JsonElement item = obj.get("item");
            if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                // 【隐式 NBT 材料 —— 跨包合成 bug 追查轮】旧写法允许不带 "type"
                // 直接写 {"item": "tacz:modern_kinetic_gun", "nbt": {"GunId": ...}}。
                // 本方法此前把 "nbt" 【静默丢弃】、只留物品 id —— 后果双重：
                //   ① 材料格显示一把没有 GunId 的裸 modern_kinetic_gun
                //     （缺省模型/名字不对，玩家看不出要交哪把枪）；
                //   ② 匹配退化成「任意一把同物品枪都行」，语义悄悄放宽。
                // 带 nbt 的对象改写成 partial_nbt 语义（宽松子集匹配 ——
                // 枪械物品必然带着弹药数/开火模式等额外字段，strict 永远不可能
                // 命中一把用过的枪，partial 是唯一可用语义，与 wiki 建议一致）。
                // （同步自 26.2 线 7227ff9。）
                if (obj.has("nbt") && obj.get("nbt").isJsonObject()) {
                    JsonObject out = new JsonObject();
                    out.add("fabric:type", new JsonPrimitive("forge:partial_nbt"));
                    JsonArray items = new JsonArray(1);
                    items.add(new JsonPrimitive(item.getAsString()));
                    out.add("items", items);
                    out.add("nbt", obj.get("nbt"));
                    GunMod.LOGGER.info("Rewrote a legacy no-type NBT ingredient (item={} + nbt) to forge:partial_nbt "
                            + "semantics; previously the nbt was silently dropped.", item.getAsString());
                    return out;
                }
                return new JsonPrimitive(item.getAsString());
            }
            return raw;
        }
        if (raw.isJsonArray()) {
            JsonArray src = raw.getAsJsonArray();
            JsonArray out = new JsonArray(src.size());
            boolean changed = false;
            for (JsonElement e : src) {
                JsonElement n = normalizeLegacy(e);
                changed |= n != e;
                out.add(n);
            }
            return changed ? out : raw;
        }
        return raw;
    }

    /**
     * 我们已注册 Fabric 版实现的 Forge 自定义 Ingredient 类型。
     *
     * <p>与 {@code util/forge} 下两个 Serializer 的 {@code ID} 一一对应。
     * 不在此集合中的 type 不做任何改写 —— 宁可让它明确失败，
     * 也不要「猜」一个近似语义悄悄改变配方要求。
     */
    private static final java.util.Set<String> SUPPORTED_CUSTOM_INGREDIENTS =
            java.util.Set.of("forge:partial_nbt", "forge:nbt", "tacz:nbt");

    /**
     * 把 Forge 写法的自定义 Ingredient 改写为 Fabric 写法。见 {@link #normalizeLegacy} 中的说明。
     *
     * @return 改写后的对象；类型不受支持时<b>原样返回</b>
     */
    private static JsonElement normalizeCustomIngredient(JsonObject obj) {
        JsonElement typeElement = obj.get("type");
        if (typeElement == null || !typeElement.isJsonPrimitive() || !typeElement.getAsJsonPrimitive().isString()) {
            return obj;
        }
        String type = typeElement.getAsString();
        if (!SUPPORTED_CUSTOM_INGREDIENTS.contains(type)) {
            return obj;
        }

        JsonObject out = new JsonObject();
        // Fabric 的判别键，见 CustomIngredientImpl.TYPE_KEY。
        out.add("fabric:type", new JsonPrimitive(type));
        for (java.util.Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if ("type".equals(key)) {
                continue;
            }
            // 单数 item(字符串) -> 复数 items(列表)，对齐 Serializer 声明的字段。
            // 若原文已经写了 items，则原样保留，不重复包装。
            if ("item".equals(key) && !obj.has("items")) {
                JsonArray items = new JsonArray(1);
                items.add(entry.getValue());
                out.add("items", items);
                continue;
            }
            // 【tacz:nbt · TaCZPackUpgrader 形态】"items" 写成单个字符串而非数组
            // （Upgrader.kt upgradeIngredient：obj.add("items", item) —— item 是
            // JsonPrimitive）。我们的 codec 是 listOf().fieldOf("items")，只认数组，
            // 这里把字符串包成单元素数组（实机日志的 "Not a json array" 正是这一步缺失）。
            // （同步自 26.2 线 61345c5。）
            if ("items".equals(key) && entry.getValue().isJsonPrimitive()) {
                JsonArray items = new JsonArray(1);
                items.add(entry.getValue());
                out.add("items", items);
                continue;
            }
            out.add(key, entry.getValue());
        }
        return out;
    }

    /** 供必须拿到非空值的场合（如网络编码）使用。 */
    public Ingredient getIngredientOrThrow() {
        Ingredient resolved = this.getIngredient();
        if (resolved == null) {
            throw new IllegalStateException("Unresolved gun smith table ingredient: " + this.rawItem);
        }
        return resolved;
    }

    /** 材料是否可用（tag 已绑定且解析成功）。 */
    public boolean isResolved() {
        return this.getIngredient() != null;
    }

    public int getCount() {
        return count;
    }
}
