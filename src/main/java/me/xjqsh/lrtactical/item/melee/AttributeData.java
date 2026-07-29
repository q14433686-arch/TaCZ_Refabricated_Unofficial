package me.xjqsh.lrtactical.item.melee;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 近战武器附带的属性修饰（攻击力、攻速等）。
 *
 * <p>数据包写法（两种都支持）：
 * <pre>
 * "attributes": {
 *   "minecraft:attack_damage": 7.0,
 *   "minecraft:attack_speed": { "amount": -2.4, "operation": "addition" }
 * }
 * </pre>
 *
 * <p>26.2 变更：{@code ResourceLocation} → {@link Identifier}（全仓统一）。
 */
public class AttributeData {
    private final List<AttributeInfo> attributes = new ArrayList<>();

    public List<AttributeInfo> getAttributes() {
        return attributes;
    }

    public record AttributeInfo(Identifier id, double amount, AttributeModifier.Operation operation) {
    }

    /**
     * 兼容 1.20/1.21 刀包常见的旧属性 id。
     *
     * <p>真实 LRTactical 刀包仍写 {@code generic.attack_damage} 与
     * {@code minecraft:generic.movement_speed}。26.2 的属性注册名已变为
     * {@code minecraft:attack_damage}/{@code minecraft:movement_speed}；若不归一化，解析能过，
     * 但 {@code BuiltInRegistries.ATTRIBUTE.get(id)} 查不到，表现就是“除了测试刀外都没伤害”。</p>
     */
    private static Identifier normalizeAttributeId(String raw) {
        return switch (raw) {
            case "generic.attack_damage", "minecraft:generic.attack_damage" ->
                    Identifier.fromNamespaceAndPath("minecraft", "attack_damage");
            case "generic.attack_speed", "minecraft:generic.attack_speed" ->
                    Identifier.fromNamespaceAndPath("minecraft", "attack_speed");
            case "generic.movement_speed", "minecraft:generic.movement_speed" ->
                    Identifier.fromNamespaceAndPath("minecraft", "movement_speed");
            case "generic.max_health", "minecraft:generic.max_health" ->
                    Identifier.fromNamespaceAndPath("minecraft", "max_health");
            case "generic.attack_knockback", "minecraft:generic.attack_knockback" ->
                    Identifier.fromNamespaceAndPath("minecraft", "attack_knockback");
            case "generic.knockback_resistance", "minecraft:generic.knockback_resistance" ->
                    Identifier.fromNamespaceAndPath("minecraft", "knockback_resistance");
            case "generic.armor", "minecraft:generic.armor" ->
                    Identifier.fromNamespaceAndPath("minecraft", "armor");
            case "generic.armor_toughness", "minecraft:generic.armor_toughness" ->
                    Identifier.fromNamespaceAndPath("minecraft", "armor_toughness");
            case "generic.luck", "minecraft:generic.luck" ->
                    Identifier.fromNamespaceAndPath("minecraft", "luck");
            default -> Identifier.tryParse(raw);
        };
    }

    public static class Deserializer implements JsonDeserializer<AttributeData> {
        @Override
        public AttributeData deserialize(JsonElement element, Type type, JsonDeserializationContext ctx)
                throws JsonParseException {
            if (!element.isJsonObject()) {
                throw new JsonParseException("Expected a JsonObject, get " + element);
            }
            JsonObject jsonObject = element.getAsJsonObject();
            AttributeData data = new AttributeData();

            for (var entry : jsonObject.entrySet()) {
                // 属性 id 是原版命名空间；这里只做“旧 generic.* 名称 -> 26.2 名称”的兼容，
                // 不做“裸名补 lrtactical:”那类内容包资源归一化。
                Identifier id = normalizeAttributeId(entry.getKey());
                if (id == null) {
                    continue;
                }
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive()) {
                    JsonPrimitive primitive = value.getAsJsonPrimitive();
                    if (primitive.isNumber()) {
                        data.attributes.add(new AttributeInfo(
                                id, primitive.getAsDouble(), AttributeModifier.Operation.ADD_VALUE));
                    }
                } else if (value.isJsonObject()) {
                    JsonObject obj = value.getAsJsonObject();
                    double amount = GsonHelper.getAsDouble(obj, "amount", 0);
                    String operationId = GsonHelper.getAsString(obj, "operation", "addition");
                    AttributeModifier.Operation operation = switch (operationId) {
                        case "percent" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                        case "multiply" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                        default -> AttributeModifier.Operation.ADD_VALUE;
                    };
                    data.attributes.add(new AttributeInfo(id, amount, operation));
                }
            }
            return data;
        }
    }
}
