package me.xjqsh.lrtactical.client.resource.display;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 官方 0.4.3 display JSON 里的 {@code display_offset} 与 {@code entity_transform}。
 *
 * <h2>两个字段各管一件事</h2>
 * <ul>
 *   <li>{@code display_offset}：一个纯平移（方块单位），在<b>物品</b>渲染里施加 ——
 *       位置在 {@code transforms} 之后、模型原点平移之前
 *       （见 {@code MeleeItemRenderer#renderByItem} / {@code ThrowableItemRendererWrapper}）。
 *       近战、投掷物、消耗品三类 display 都有它。</li>
 *   <li>{@code entity_transform}：<b>只有投掷物</b>有。手雷飞行实体的姿态
 *       （旋转 + 平移 + 缩放），在 {@code ThrowableEntityRenderer} 里套在飞行朝向之后。</li>
 * </ul>
 *
 * <h2>为什么 {@code entity_transform} 的默认值与 JSON 量纲不一致（照抄官方契约）</h2>
 * JSON 里的 {@code translation} 走 1.20.1 {@code ItemTransform} 的反序列化规则：
 * 先乘 {@code 1/16}（像素 → 方块），再按 {@code ±5} 夹取；{@code scale} 按 {@code ±4} 夹取。
 * 但<b>默认值</b>（JSON 里没写 {@code entity_transform} 时）是官方直接构造出来的
 * 方块单位 {@code (-0.3, 0.15, 0) + Z 90°}，没有经过 {@code 1/16}。
 * 这不是笔误 —— 官方 0.4.3 就是这么写的，内容包作者也按这个约定写 JSON，
 * 所以这里逐条保留，不做「统一量纲」的自作主张。
 *
 * <h2>为什么不用 26.2 的 {@code ItemTransform#apply}</h2>
 * 26.2 的 {@code apply} 内部会额外 {@code translate(-0.5,-0.5,-0.5)}
 * （本仓 {@code MeleeItemRenderer#renderByItem} 的注释里有同一条结论），
 * 那是给方块/物品槽位准备的重新居中；拿来摆飞行实体会整体偏半个方块。
 * 因此这里按 1.20.1 {@code ItemTransform#apply(false, pose)} 的语义手写一遍：
 * 平移 → 旋转（{@code rotationXYZ}，与 26.2 {@code ItemTransform} 内部用的同一个
 * joml 方法，已在本地 26.2 jar 常量池核对存在）→ 缩放。
 */
public final class DisplayTransform {
    /** 官方 0.4.3 的默认实体姿态：Z 轴 90° + 方块单位偏移 {@code (-0.3, 0.15, 0)}。 */
    public static final EntityTransform DEFAULT_ENTITY = new EntityTransform(
            new Vector3f(0.0F, 0.0F, 90.0F),
            new Vector3f(-0.3F, 0.15F, 0.0F),
            new Vector3f(1.0F, 1.0F, 1.0F)
    );

    private static final Vector3f DEFAULT_ROTATION = new Vector3f();
    private static final Vector3f DEFAULT_TRANSLATION = new Vector3f();
    private static final Vector3f DEFAULT_SCALE = new Vector3f(1.0F, 1.0F, 1.0F);
    /** 1.20.1 {@code ItemTransform.Deserializer} 的平移上限（乘 1/16 之后）。 */
    private static final float MAX_TRANSLATION = 5.0F;
    /** 1.20.1 {@code ItemTransform.Deserializer} 的缩放上限。 */
    private static final float MAX_SCALE = 4.0F;

    private DisplayTransform() {
    }

    /**
     * 解析 {@code entity_transform}。
     *
     * <p>缺字段 / 类型不对 / 数组长度不是 3 时，逐项回退到默认值而不是整段丢弃 ——
     * 内容包只写了 {@code rotation} 却不写 {@code scale} 是常见情况，
     * 整段丢弃会让缩放悄悄变成 0（模型消失），逐项回退不会。</p>
     *
     * @param json {@code entity_transform} 对象，可为 {@code null}（此时整体用默认姿态）
     */
    public static EntityTransform parseEntityTransform(@Nullable JsonObject json) {
        if (json == null) {
            return DEFAULT_ENTITY;
        }
        Vector3f rotation = getVector3f(json, "rotation", DEFAULT_ROTATION);
        Vector3f translation = getVector3f(json, "translation", DEFAULT_TRANSLATION);
        // 1.20.1 契约：JSON 写的是像素，乘 1/16 换算成方块单位。
        translation.mul(0.0625F);
        translation.set(
                Mth.clamp(translation.x(), -MAX_TRANSLATION, MAX_TRANSLATION),
                Mth.clamp(translation.y(), -MAX_TRANSLATION, MAX_TRANSLATION),
                Mth.clamp(translation.z(), -MAX_TRANSLATION, MAX_TRANSLATION)
        );
        Vector3f scale = getVector3f(json, "scale", DEFAULT_SCALE);
        scale.set(
                Mth.clamp(scale.x(), -MAX_SCALE, MAX_SCALE),
                Mth.clamp(scale.y(), -MAX_SCALE, MAX_SCALE),
                Mth.clamp(scale.z(), -MAX_SCALE, MAX_SCALE)
        );
        return new EntityTransform(rotation, translation, scale);
    }

    /**
     * 施加 {@code display_offset}。{@code null} 时什么都不做
     * （display 实例里已保证非 null，这里的判空只为让调用点少一层顾虑）。
     */
    public static void applyOffset(PoseStack poseStack, @Nullable Vector3f offset) {
        if (offset != null) {
            poseStack.translate(offset.x(), offset.y(), offset.z());
        }
    }

    private static Vector3f getVector3f(JsonObject object, String key, Vector3f def) {
        if (!object.has(key)) {
            return new Vector3f(def);
        }
        JsonElement element = object.get(key);
        if (!element.isJsonArray()) {
            return new Vector3f(def);
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) {
            return new Vector3f(def);
        }
        return new Vector3f(
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat()
        );
    }

    /** 一份解析完成的实体姿态。字段都是方块单位 / 角度 / 倍率。 */
    public record EntityTransform(Vector3f rotation, Vector3f translation, Vector3f scale) {
        /** 按 1.20.1 {@code ItemTransform#apply(false, pose)} 的顺序施加：平移 → 旋转 → 缩放。 */
        public void apply(PoseStack poseStack) {
            poseStack.translate(translation.x(), translation.y(), translation.z());
            poseStack.mulPose(new Quaternionf().rotationXYZ(
                    rotation.x() * Mth.DEG_TO_RAD,
                    rotation.y() * Mth.DEG_TO_RAD,
                    rotation.z() * Mth.DEG_TO_RAD));
            poseStack.scale(scale.x(), scale.y(), scale.z());
        }
    }
}
