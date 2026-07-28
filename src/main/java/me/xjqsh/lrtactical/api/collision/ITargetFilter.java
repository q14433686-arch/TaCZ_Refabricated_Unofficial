package me.xjqsh.lrtactical.api.collision;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.List;

/**
 * 近战「命中区域」抽象 —— 决定一次挥击能打到哪些实体。
 *
 * <p>由数据包里的 {@code hitbox} 字段驱动，三种实现：
 * {@code cone}（锥形）/ {@code ray}（射线穿透）/ {@code obb}（有向包围盒）。
 *
 * <h2>26.2 移植说明</h2>
 * 本包是纯几何代码，所用 API（{@link Vec3} / {@link AABB} / {@link ClipContext} /
 * {@link HitResult}）在 26.2 <b>均无变化</b>（已逐个对字节码核实），
 * 因此与上游逐行一致，无需改写。
 *
 * <p><b>唯一需要注意的是 {@link Deserializer}</b>：它是按 {@code type} 字段分派的
 * 多态反序列化器，<b>必须注册到 Gson</b>，否则 {@code hitbox} 字段会解析失败
 * （而且是静默失败 —— Gson 会尝试把它当普通对象填进接口，抛的异常信息与真正的原因无关）。
 * 注册处见 {@code CommonAssetsManager.GSON}。
 */
@FunctionalInterface
public interface ITargetFilter {
    @NotNull
    List<Entity> filterTargets(LivingEntity attacker, Vec3 origin, Vec3 direction);

    default double getMaxRange() {
        return 2.5d;
    }

    /**
     * 攻击者能否「看见」目标（中间无方块阻隔）。
     *
     * <p>除了眼睛到眼睛，还会尝试目标包围盒的 8 个顶点 —— 只要有一条通路就算可见。
     * 这样贴着掩体边缘的目标不会因为中心被挡就完全无敌。
     */
    static boolean hasLineOfSight(LivingEntity attacker, Entity target) {
        if (target.level() != attacker.level()) {
            return false;
        }
        Vec3 eye = new Vec3(attacker.getX(), attacker.getEyeY(), attacker.getZ());
        if (clip(attacker, new Vec3(target.getX(), target.getEyeY(), target.getZ()), eye)) {
            return true;
        }
        AABB boundingBox = target.getBoundingBox();
        for (double x : new double[]{boundingBox.minX, boundingBox.maxX}) {
            for (double y : new double[]{boundingBox.minY, boundingBox.maxY}) {
                for (double z : new double[]{boundingBox.minZ, boundingBox.maxZ}) {
                    if (clip(attacker, new Vec3(x, y, z), eye)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** @return true 表示两点之间没有方块阻挡 */
    static boolean clip(LivingEntity attacker, Vec3 to, Vec3 from) {
        if (to.distanceTo(from) > 128.0D) {
            return false;
        }
        return attacker.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker)).getType() == HitResult.Type.MISS;
    }

    /**
     * 按 {@code type} 字段分派的多态反序列化器。
     *
     * <p>未知 type 直接抛异常而<b>不是</b>退回某个默认值 ——
     * 静默兜底会让内容包作者以为自己写对了，实际用的是完全不同的判定形状。
     */
    class Deserializer implements JsonDeserializer<ITargetFilter> {
        @Override
        public ITargetFilter deserialize(JsonElement element, Type type, JsonDeserializationContext ctx)
                throws JsonParseException {
            if (!element.isJsonObject()) {
                throw new JsonParseException("Expected a JsonObject, get " + element);
            }
            JsonObject jsonObject = element.getAsJsonObject();
            String typeName = GsonHelper.getAsString(jsonObject, "type");
            return switch (typeName) {
                case "cone" -> ctx.deserialize(jsonObject, ConeFilter.class);
                case "ray" -> ctx.deserialize(jsonObject, RayFilter.class);
                case "obb" -> ctx.deserialize(jsonObject, OBBFilter.class);
                default -> throw new JsonParseException("Unknown filter type: " + typeName);
            };
        }
    }
}
