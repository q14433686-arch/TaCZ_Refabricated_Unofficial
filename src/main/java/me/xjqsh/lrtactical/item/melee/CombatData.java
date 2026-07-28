package me.xjqsh.lrtactical.item.melee;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import me.xjqsh.lrtactical.api.collision.ConeFilter;
import me.xjqsh.lrtactical.api.collision.ITargetFilter;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * 一把近战武器的「攻击配置」——每个动作（左/右键）可以配一串连招。
 *
 * <p>数据包写法示例：
 * <pre>
 * "attack": {
 *   "attack_left":  [ {...第一段...}, {...第二段...} ],
 *   "attack_right": {...只有一段时可直接写对象...}
 * }
 * </pre>
 *
 * <p>与上游逐字对应，仅把 {@code ResourceLocation} 相关引用换成 26.2 的类型。
 */
public class CombatData {
    /** 未配置 hitbox 时的兜底：3.5 格、60 度锥形。 */
    public static final ITargetFilter DEFAULT_HITBOX = new ConeFilter(3.5, 60);

    public EnumMap<MeleeAction, List<MeleeAttackInfo>> attackInfo = new EnumMap<>(MeleeAction.class);

    @Nullable
    public MeleeAttackInfo getAttackInfo(MeleeAction action) {
        return getAttackInfo(action, 0);
    }

    /**
     * 取某个动作的第 {@code index} 段连招。
     *
     * @return 越界或未配置时返回 {@code null}（调用方据此判定「该动作不可用」）
     */
    @Nullable
    public MeleeAttackInfo getAttackInfo(MeleeAction action, int index) {
        List<MeleeAttackInfo> list = attackInfo.get(action);
        if (list != null && index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return null;
    }

    /** 某个动作共有几段连招。 */
    public int getComboLength(MeleeAction action) {
        List<MeleeAttackInfo> list = attackInfo.get(action);
        return list == null ? 0 : list.size();
    }

    /** 攻击时的位移（前冲）。 */
    public static class MeleeMovement {
        @SerializedName("delay")
        private int delay = 0;

        @SerializedName("speed")
        private float speed = 0.5f;

        public int getDelay() {
            return delay;
        }

        public float getSpeed() {
            return speed;
        }
    }

    /** 一段攻击的全部参数。 */
    public static class MeleeAttackInfo {
        /** 伤害倍率（乘在武器基础攻击力上）。 */
        @SerializedName("factor")
        private float factor = 1.0f;

        @SerializedName("knockback")
        private float knockback = 0.02f;

        /** 本段攻击后的冷却（tick）。 */
        @SerializedName("cooldown")
        private int cooldown = 20;

        /** 从按下到真正判定的延迟（tick），用于对齐挥击动画。 */
        @SerializedName("delay")
        private int delay = 0;

        @SerializedName("hitbox")
        private ITargetFilter hitbox = DEFAULT_HITBOX;

        @SerializedName("durability_damage")
        private int durabilityDamage = 1;

        @SerializedName("movement")
        private MeleeMovement movement = null;

        public float getFactor() {
            return factor;
        }

        public float getKnockback() {
            return knockback;
        }

        public int getCooldown() {
            return cooldown;
        }

        public int getDelay() {
            return delay;
        }

        public ITargetFilter getHitbox() {
            // Gson 对「JSON 里没写该字段」的情况会保留初始值，
            // 但如果写了 null 就会变成 null，这里兜一层
            return hitbox == null ? DEFAULT_HITBOX : hitbox;
        }

        public int getDurabilityDamage() {
            return durabilityDamage;
        }

        @Nullable
        public MeleeMovement getMovement() {
            return movement;
        }
    }

    /**
     * 按动作名分派，且兼容「单个对象」与「数组」两种写法。
     */
    public static class Deserializer implements JsonDeserializer<CombatData> {
        @Override
        public CombatData deserialize(JsonElement element, Type type, JsonDeserializationContext ctx)
                throws JsonParseException {
            if (!element.isJsonObject()) {
                throw new JsonParseException("Expected a JsonObject, get " + element);
            }
            JsonObject jsonObject = element.getAsJsonObject();
            CombatData combatData = new CombatData();

            for (MeleeAction action : MeleeAction.values()) {
                if (!jsonObject.has(action.getId())) {
                    continue;
                }
                List<MeleeAttackInfo> attackInfos = new ArrayList<>();
                parseAttackInfo(ctx, action, jsonObject.get(action.getId()), attackInfos);
                if (!attackInfos.isEmpty()) {
                    combatData.attackInfo.put(action, attackInfos);
                }
            }
            return combatData;
        }

        private void parseAttackInfo(JsonDeserializationContext ctx, MeleeAction action,
                                     JsonElement actionElement, List<MeleeAttackInfo> out)
                throws JsonParseException {
            if (actionElement.isJsonArray()) {
                JsonArray array = actionElement.getAsJsonArray();
                for (JsonElement ele : array) {
                    MeleeAttackInfo info = ctx.deserialize(ele, MeleeAttackInfo.class);
                    if (info != null) {
                        out.add(info);
                    }
                }
            } else if (actionElement.isJsonObject()) {
                MeleeAttackInfo info = ctx.deserialize(actionElement, MeleeAttackInfo.class);
                if (info != null) {
                    out.add(info);
                }
            } else {
                throw new JsonParseException("Expected a JsonArray or JsonObject for action "
                        + action.getId() + ", get " + actionElement);
            }
        }
    }
}
