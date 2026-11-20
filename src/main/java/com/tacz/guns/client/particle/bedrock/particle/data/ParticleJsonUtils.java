// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

/**
 * 基岩版粒子 JSON 解析工具方法集合。
 * <p>
 * 提供 Molang 表达式提取、数组解析、颜色解析、纹理路径转换等通用能力，
 * 供各组件的 {@code fromJson} 方法和 {@link ParticleEffectDeserializer} 复用。
 */
public final class ParticleJsonUtils {

    private ParticleJsonUtils() {}

    /**
     * 从 JsonObject 中提取 Molang 表达式字符串。
     * 支持数字和字符串两种 JSON 类型。
     */
    public static String getMolang(JsonObject obj, String key, String defaultValue) {
        if (!obj.has(key)) return defaultValue;
        return molangFromElement(obj.get(key), defaultValue);
    }

    /**
     * 从 JsonElement 中提取 Molang 表达式字符串。
     */
    public static String molangFromElement(@Nullable JsonElement elem, String defaultValue) {
        if (elem == null || elem.isJsonNull()) return defaultValue;
        if (elem.isJsonPrimitive()) {
            JsonPrimitive prim = elem.getAsJsonPrimitive();
            if (prim.isString()) return prim.getAsString();
            if (prim.isNumber()) return String.valueOf(prim.getAsNumber());
        }
        return defaultValue;
    }

    /**
     * 从 JsonObject 中提取字符串值。
     */
    public static String getString(JsonObject obj, String key, String defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonElement elem = obj.get(key);
        return elem.isJsonPrimitive() ? elem.getAsString() : defaultValue;
    }

    /**
     * 从 JsonObject 中提取布尔值。
     */
    public static boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        if (!obj.has(key)) return defaultValue;
        return obj.get(key).getAsBoolean();
    }

    /**
     * 从 JsonObject 中提取 3 元素 Molang 数组。
     */
    public static String[] getMolangArray3(JsonObject obj, String key, String d0, String d1, String d2) {
        if (!obj.has(key)) return new String[]{d0, d1, d2};
        JsonElement elem = obj.get(key);
        if (elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            return new String[]{
                    molangFromElement(arr.size() > 0 ? arr.get(0) : null, d0),
                    molangFromElement(arr.size() > 1 ? arr.get(1) : null, d1),
                    molangFromElement(arr.size() > 2 ? arr.get(2) : null, d2)
            };
        }
        return new String[]{d0, d1, d2};
    }

    /**
     * 从 JsonObject 中提取任意长度的 Molang 数组。
     */
    public static String[] getMolangArray(JsonObject obj, String key, int size, String... defaults) {
        if (!obj.has(key)) return defaults.clone();
        JsonElement elem = obj.get(key);
        if (elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            String[] result = new String[size];
            for (int i = 0; i < size; i++) {
                result[i] = molangFromElement(i < arr.size() ? arr.get(i) : null,
                        i < defaults.length ? defaults[i] : "0");
            }
            return result;
        }
        return defaults.clone();
    }

    /**
     * 解析十六进制颜色字符串。
     * <p>
     * 支持两种格式：
     * <ul>
     *   <li>{@code #AARRGGBB}（8 位，基岩版格式）</li>
     *   <li>{@code #RRGGBB}（6 位，无 alpha）</li>
     * </ul>
     *
     * @return float[4]：{r, g, b, a}，范围 0~1
     */
    public static float[] parseHexColor(String hex) {
        hex = hex.replace("#", "");
        // 基岩版颜色格式为 AARRGGBB
        if (hex.length() > 6) {
            float a = Integer.parseInt(hex.substring(0, 2), 16) / 255f;
            float r = Integer.parseInt(hex.substring(2, 4), 16) / 255f;
            float g = Integer.parseInt(hex.substring(4, 6), 16) / 255f;
            float b = Integer.parseInt(hex.substring(6, 8), 16) / 255f;
            return new float[]{r, g, b, a};
        }
        // RRGGBB（无 alpha）
        float r = Integer.parseInt(hex.substring(0, 2), 16) / 255f;
        float g = Integer.parseInt(hex.substring(2, 4), 16) / 255f;
        float b = Integer.parseInt(hex.substring(4, 6), 16) / 255f;
        return new float[]{r, g, b, 1f};
    }

    /**
     * 将基岩版纹理路径转换为 Java 版 Identifier。
     * <p>
     * 基岩版路径格式多样：
     * <ul>
     *   <li>{@code "textures/particle/particles"} — 无命名空间，已有 textures/ 前缀</li>
     *   <li>{@code "particlestorm:particle/bomb_spark"} — 有命名空间，缺少 textures/ 前缀</li>
     *   <li>{@code "textures/particle/particles.png"} — 偶尔带 .png 后缀</li>
     * </ul>
     * 统一转换为 {@code namespace:textures/xxx.png} 格式。
     */
    public static Identifier resolveTexturePath(String raw) {
        // 分离命名空间
        String namespace = "minecraft";
        String path = raw;
        int colonIdx = raw.indexOf(':');
        if (colonIdx >= 0) {
            namespace = raw.substring(0, colonIdx);
            path = raw.substring(colonIdx + 1);
        }

        // 确保 path 以 textures/ 开头
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }

        // 确保 .png 后缀
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }

        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
