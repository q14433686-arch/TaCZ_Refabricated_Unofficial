package cn.sh1rocu.tacz.util;

import com.google.gson.*;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 兼容旧版枪包的原版合成配方（minecraft:crafting_*）到 26.2 新格式。
 *
 * <p>26.2 变更（已字节码确认）：
 * <ul>
 *   <li>目录：data/&lt;ns&gt;/recipes/ -&gt; data/&lt;ns&gt;/recipe/（单数，常量，不可扩展）</li>
 *   <li>result.item -&gt; result.id</li>
 *   <li>result.nbt -&gt; result.components.minecraft:custom_data</li>
 *   <li>key: {"tag":"minecraft:logs"} -&gt; "#minecraft:logs"</li>
 *   <li>key: {"item":"minecraft:stick"} -&gt; "minecraft:stick"</li>
 *   <li>Ingredient.CODEC 只接受字符串/字符串数组</li>
 * </ul>
 *
 * <p>本类在 PackResources 层拦截：
 * <ul>
 *   <li>getResource：若请求 recipe/xxx 而实际在 recipes/xxx，尝试回退；并对旧格式做自动转换</li>
 *   <li>listResources：当查询 recipe 时，额外列出 recipes 目录并重映射为 recipe</li>
 * </ul>
 *
 * 仅对 type 以 minecraft: 开头的配方做转换，避免污染 tacz:gun_smith_table_crafting 等自定义配方。
 */
public final class RecipeCompat {
    private static final Gson GSON = new GsonBuilder().setLenient().setPrettyPrinting().create();

    private RecipeCompat() {}

    public static boolean isRecipePath(Identifier location) {
        String p = location.getPath();
        return p.startsWith("recipe/") || p.startsWith("recipes/");
    }

    public static boolean isVanillaRecipeType(JsonObject obj) {
        JsonElement typeEl = obj.get("type");
        if (typeEl == null || !typeEl.isJsonPrimitive()) return false;
        String t = typeEl.getAsString();
        // 只转换原版配方，自定义工作台配方走 TableRecipeManager，不在这里处理
        return t.startsWith("minecraft:");
    }

    public static JsonObject transformIfNeeded(JsonObject obj) {
        if (obj == null) return null;
        // 若已经是新格式（result.id 存在且无旧字段），直接返回
        // 但即使是新格式，key 仍可能是旧对象式，需要一并转
        boolean needTransform = false;

        // 快速判断是否需要转换
        if (obj.has("result")) {
            JsonElement resEl = obj.get("result");
            if (resEl.isJsonObject()) {
                JsonObject res = resEl.getAsJsonObject();
                if (res.has("item") || res.has("nbt")) {
                    needTransform = true;
                }
            }
        }
        if (obj.has("key") && obj.get("key").isJsonObject()) {
            JsonObject key = obj.getAsJsonObject("key");
            for (Map.Entry<String, JsonElement> e : key.entrySet()) {
                JsonElement v = e.getValue();
                if (v.isJsonObject() || (v.isJsonArray() && containsObject(v.getAsJsonArray()))) {
                    needTransform = true;
                    break;
                }
                // 兼容 forge: 标签写法也算需要转换（可选）
                if (v.isJsonPrimitive() && v.getAsString().startsWith("forge:")) {
                    needTransform = true;
                    break;
                }
            }
        }
        if (obj.has("ingredients") && obj.get("ingredients").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("ingredients");
            for (JsonElement el : arr) {
                if (el.isJsonObject() || (el.isJsonArray() && containsObject(el.getAsJsonArray()))) {
                    needTransform = true;
                    break;
                }
            }
        }
        if (obj.has("ingredient")) {
            JsonElement el = obj.get("ingredient");
            if (el.isJsonObject() || (el.isJsonArray() && containsObject(el.getAsJsonArray()))) {
                needTransform = true;
            }
        }

        // 如果不需要转换且是原版类型，仍尝试做 forge->c 的轻量转换（可选）
        // 为避免误伤，若已经判定无需转换，直接返回（但下层仍会检查 isVanilla 类型）
        if (!needTransform) {
            // 仍需检查 key 是否对象式，上面已覆盖；若完全无需，直接返回原对象（外层会判断是否原版类型再决定是否输出）
            // 但为了统一，我们在 isVanilla 的前提下才做转换，否则保持原样
            if (!isVanillaRecipeType(obj)) {
                return obj;
            }
            // 如果是原版但无需转换，仍然返回原对象
            // 调用方会自行决定是否需要序列化
            // 这里为了不重复解析，直接返回
            return obj;
        }

        // 只对原版配方做转换，自定义配方保持原样（避免把 gun_smith_table 的私有格式破坏）
        if (!isVanillaRecipeType(obj)) {
            return obj;
        }

        JsonObject copy = obj.deepCopy();

        // result
        if (copy.has("result") && copy.get("result").isJsonObject()) {
            JsonObject res = copy.getAsJsonObject("result");
            if (res.has("item")) {
                JsonElement item = res.get("item");
                res.add("id", item);
                res.remove("item");
            }
            if (res.has("nbt")) {
                JsonElement nbt = res.get("nbt");
                JsonObject components;
                if (res.has("components") && res.get("components").isJsonObject()) {
                    components = res.getAsJsonObject("components");
                } else {
                    components = new JsonObject();
                }
                // 26.2 要求 components.minecraft:custom_data
                components.add("minecraft:custom_data", nbt);
                res.add("components", components);
                res.remove("nbt");
            }
            // count 保持不变
        }

        // key
        if (copy.has("key") && copy.get("key").isJsonObject()) {
            JsonObject key = copy.getAsJsonObject("key");
            JsonObject newKey = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
                JsonElement transformed = transformIngredient(entry.getValue());
                newKey.add(entry.getKey(), transformed);
            }
            copy.add("key", newKey);
        }

        // ingredients (shapeless)
        if (copy.has("ingredients") && copy.get("ingredients").isJsonArray()) {
            JsonArray arr = copy.getAsJsonArray("ingredients");
            JsonArray newArr = new JsonArray();
            for (JsonElement el : arr) {
                newArr.add(transformIngredient(el));
            }
            copy.add("ingredients", newArr);
        }

        // ingredient (single)
        if (copy.has("ingredient")) {
            copy.add("ingredient", transformIngredient(copy.get("ingredient")));
        }

        return copy;
    }

    private static boolean containsObject(JsonArray arr) {
        for (JsonElement el : arr) {
            if (el.isJsonObject()) return true;
            if (el.isJsonArray() && containsObject(el.getAsJsonArray())) return true;
        }
        return false;
    }

    public static JsonElement transformIngredient(JsonElement el) {
        if (el == null || el.isJsonNull()) return el;
        if (el.isJsonPrimitive()) {
            String s = el.getAsString();
            // forge: -> c: 兼容
            if (s.startsWith("forge:")) {
                s = "c:" + s.substring("forge:".length());
                return new JsonPrimitive(s);
            }
            return el;
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("tag")) {
                String tag = obj.get("tag").getAsString();
                if (tag.startsWith("forge:")) {
                    tag = "c:" + tag.substring("forge:".length());
                }
                // 新格式要求 "#tag"
                if (!tag.startsWith("#")) {
                    tag = "#" + tag;
                }
                return new JsonPrimitive(tag);
            }
            if (obj.has("item")) {
                String item = obj.get("item").getAsString();
                if (item.startsWith("forge:")) {
                    item = "c:" + item.substring("forge:".length());
                }
                return new JsonPrimitive(item);
            }
            // 未知对象，尝试保留（可能包含 count 等），但 Ingredient.CODEC 不接受对象，返回原样让上层报错
            return obj;
        }
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            JsonArray newArr = new JsonArray();
            for (JsonElement sub : arr) {
                newArr.add(transformIngredient(sub));
            }
            return newArr;
        }
        return el;
    }

    public static InputStream transformStreamIfNeeded(InputStream original) {
        try {
            byte[] bytes = original.readAllBytes();
            String text = new String(bytes, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return new ByteArrayInputStream(bytes);
            }
            JsonElement je;
            try {
                je = JsonParser.parseString(text);
            } catch (JsonSyntaxException ex) {
                // 不是 JSON，直接返回原流
                return new ByteArrayInputStream(bytes);
            }
            if (!je.isJsonObject()) {
                return new ByteArrayInputStream(bytes);
            }
            JsonObject obj = je.getAsJsonObject();
            // 仅对原版配方做转换
            if (!isVanillaRecipeType(obj)) {
                return new ByteArrayInputStream(bytes);
            }
            JsonObject transformed = transformIfNeeded(obj);
            if (transformed == obj) {
                // 引用相等说明无需转换（快速路径）
                return new ByteArrayInputStream(bytes);
            }
            String out = GSON.toJson(transformed);
            return new ByteArrayInputStream(out.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            GunMod.LOGGER.warn("[RecipeCompat] Failed to transform recipe, returning original", e);
            try {
                // 尝试重新读取原始
                return original;
            } catch (Exception ex) {
                return new ByteArrayInputStream(new byte[0]);
            }
        }
    }

    public static IoSupplier<InputStream> wrapSupplierForRecipe(Identifier requestedLocation, IoSupplier<InputStream> original) {
        if (original == null) return null;
        // 仅对 recipe/ 路径做转换
        if (!isRecipePath(requestedLocation)) {
            return original;
        }
        return () -> {
            try (InputStream in = original.get()) {
                return transformStreamIfNeeded(in);
            }
        };
    }

    public static Identifier remapLegacyToCurrent(Identifier legacy) {
        String path = legacy.getPath();
        if (path.startsWith("recipes/")) {
            String newPath = "recipe/" + path.substring("recipes/".length());
            return Identifier.fromNamespaceAndPath(legacy.getNamespace(), newPath);
        }
        return legacy;
    }

    public static Identifier remapCurrentToLegacy(Identifier current) {
        String path = current.getPath();
        if (path.startsWith("recipe/")) {
            String legacyPath = "recipes/" + path.substring("recipe/".length());
            return Identifier.fromNamespaceAndPath(current.getNamespace(), legacyPath);
        }
        return current;
    }
}
