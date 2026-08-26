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
 * 官方 0.4.3 display JSON 里 {@code display_offset} 与 {@code entity_transform} 的实现。
 *
 * <h2>两个字段各管什么</h2>
 * <ul>
 *   <li>{@code display_offset} —— 手持渲染时在 {@code transforms} 之后追加的一次平移。
 *       内容包用它把模型原点从「刀柄」挪到「手心」，不必重做模型。</li>
 *   <li>{@code entity_transform} —— <b>只</b>作用于飞行中的投掷物实体，
 *       决定手雷在空中的姿态（默认横躺，Z 轴转 90°）。</li>
 * </ul>
 *
 * <h2>量纲：为什么 JSON 的 translation 要乘 1/16 而默认值不乘</h2>
 * 官方 1.20.1 的 {@code entity_transform} 走 {@code ItemTransform} 的反序列化器，
 * 那条路径按<b>像素</b>读取 translation（×1/16 转成方块）、并 clamp 到 ±5；
 * 而它的<b>默认值</b>是在 Java 里直接构造的方块单位 {@code (-0.3, 0.15, 0)}。
 * 两者量纲本就不一致 —— 这是官方的既成契约，内容包是照着它调的参，
 * 这里如实保留，不「顺手统一」，否则所有现存内容包的手雷姿态都会偏。
 *
 * <h2>为什么不能直接复用 {@code ItemTransform#apply}</h2>
 * 本分支的 {@code ItemTransform#apply} 内部自带 {@code translate(-0.5, -0.5, -0.5)}
 * （{@code MeleeItemRenderer#renderByItem} 的注释就这一点已踩过坑，调用方要预先
 * {@code translate(0.5, 0.5, 0.5)} 抵消）。那个半格偏移是为「物品在方块格内居中」
 * 准备的，拿来摆飞行实体会整体偏半格，所以这里按 1.20.1
 * {@code ItemTransform#apply(false, poseStack)} 的语义手写平移 → 旋转 → 缩放三步。
 */
public final class DisplayTransform {
    /**
     * 官方默认姿态：Z 轴 90°（手雷横躺），并向左下偏一点。
     *
     * <p>这是<b>方块单位</b>的字面默认值，见类注释的量纲说明 —— 不要乘 1/16。
     */
    public static final EntityTransform DEFAULT_ENTITY = new EntityTransform(
            new Vector3f(0.0F, 0.0F, 90.0F),
            new Vector3f(-0.3F, 0.15F, 0.0F),
            new Vector3f(1.0F, 1.0F, 1.0F)
    );

    private static final Vector3f DEFAULT_ROTATION = new Vector3f();
    private static final Vector3f DEFAULT_TRANSLATION = new Vector3f();
    private static final Vector3f DEFAULT_SCALE = new Vector3f(1.0F, 1.0F, 1.0F);
    /** {@code ItemTransform} 的 clamp 上限，官方 1.20.1 同值。 */
    private static final float MAX_TRANSLATION = 5.0F;
    private static final float MAX_SCALE = 4.0F;

    private DisplayTransform() {
    }

    /**
     * 解析 {@code entity_transform}。
     *
     * <p>POJO 里存的是原始 {@link JsonObject} 而不是反序列化好的对象，
     * 与本包 {@code transforms} 字段同一处理方式，理由见
     * {@link MeleeDisplayInstance} 的类注释（{@code ItemTransform} 的 Gson
     * 反序列化器在本环境不可直接复用，统一走手写解析）。
     *
     * <p>字段缺失/类型不对一律回退默认值而不抛异常：内容包写错一个手雷的姿态，
     * 不该让整份 display 加载失败（{@code create()} 的 catch 会把整个文件丢掉）。
     *
     * @param json {@code null}（内容包没写这个字段）时返回 {@link #DEFAULT_ENTITY}
     */
    public static EntityTransform parseEntityTransform(@Nullable JsonObject json) {
        if (json == null) {
            return DEFAULT_ENTITY;
        }
        Vector3f rotation = getVector3f(json, "rotation", DEFAULT_ROTATION);

        // JSON 侧按【像素】书写（与 vanilla ItemTransform 一致），故 ×1/16；
        // 见类注释：DEFAULT_ENTITY 的字面值是方块单位，刻意不走这条路径。
        Vector3f translation = getVector3f(json, "translation", DEFAULT_TRANSLATION).mul(0.0625F);
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

    /** 读一个三元数组；缺字段/不是长度 3 的数组/元素不是数字时返回 {@code def} 的副本。 */
    private static Vector3f getVector3f(JsonObject object, String key, Vector3f def) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            return new Vector3f(def);
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) {
            return new Vector3f(def);
        }
        try {
            return new Vector3f(
                    array.get(0).getAsFloat(),
                    array.get(1).getAsFloat(),
                    array.get(2).getAsFloat()
            );
        } catch (ClassCastException | NumberFormatException | IllegalStateException e) {
            return new Vector3f(def);
        }
    }

    /**
     * 施加 {@code display_offset}。
     *
     * <p>{@code null} 与零向量都当作「没配」直接跳过 —— 后者能省掉一次矩阵乘法，
     * 而没有内容包提供该字段时正是零向量（{@code create()} 里的
     * {@code requireNonNullElseGet(..., Vector3f::new)}）。
     */
    public static void applyOffset(PoseStack poseStack, @Nullable Vector3f offset) {
        if (offset == null || (offset.x() == 0.0F && offset.y() == 0.0F && offset.z() == 0.0F)) {
            return;
        }
        poseStack.translate(offset.x(), offset.y(), offset.z());
    }

    /**
     * 飞行实体姿态。
     *
     * @param rotation    欧拉角，单位<b>度</b>，按 XYZ 顺序应用
     * @param translation <b>方块</b>单位（JSON 里写的是像素，解析时已乘 1/16）
     * @param scale       各轴缩放
     */
    public record EntityTransform(Vector3f rotation, Vector3f translation, Vector3f scale) {
        /** 平移 → 旋转 → 缩放，与 1.20.1 {@code ItemTransform#apply} 同序（但不含半格偏移）。 */
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
