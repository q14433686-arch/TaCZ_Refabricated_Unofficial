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
 * 官方 0.4.3 display JSON 里的 {@code display_offset} / {@code entity_transform}。
 *
 * <p>{@code entity_transform} 在 1.20.1 上走 {@code ItemTransform} 反序列化：
 * {@code translation} 乘 1/16、再 clamp。默认值却是构造出来的方块单位
 * {@code (-0.3, 0.15, 0) + Z 90°}，和 JSON 路径的量纲并不一致。这里按官方契约逐条保留。
 *
 * <p>26.2 的 {@code ItemTransform#apply} 会额外 {@code translate(-0.5,-0.5,-0.5)}，
 * 不能拿来摆飞行实体，所以旋转/平移/缩放按 1.20.1 {@code ItemTransform#apply(false, pose)}
 * 的语义手写一遍。
 */
public final class DisplayTransform {
    public static final EntityTransform DEFAULT_ENTITY = new EntityTransform(
            new Vector3f(0.0F, 0.0F, 90.0F),
            new Vector3f(-0.3F, 0.15F, 0.0F),
            new Vector3f(1.0F, 1.0F, 1.0F)
    );

    private static final Vector3f DEFAULT_ROTATION = new Vector3f();
    private static final Vector3f DEFAULT_TRANSLATION = new Vector3f();
    private static final Vector3f DEFAULT_SCALE = new Vector3f(1.0F, 1.0F, 1.0F);
    private static final float MAX_TRANSLATION = 5.0F;
    private static final float MAX_SCALE = 4.0F;

    private DisplayTransform() {
    }

    public static EntityTransform parseEntityTransform(@Nullable JsonObject json) {
        if (json == null) {
            return DEFAULT_ENTITY;
        }
        Vector3f rotation = getVector3f(json, "rotation", DEFAULT_ROTATION);
        Vector3f translation = getVector3f(json, "translation", DEFAULT_TRANSLATION);
        translation.mul(0.0625F);
        translation.set(
                Mth.clamp(translation.x, -MAX_TRANSLATION, MAX_TRANSLATION),
                Mth.clamp(translation.y, -MAX_TRANSLATION, MAX_TRANSLATION),
                Mth.clamp(translation.z, -MAX_TRANSLATION, MAX_TRANSLATION)
        );
        Vector3f scale = getVector3f(json, "scale", DEFAULT_SCALE);
        scale.set(
                Mth.clamp(scale.x, -MAX_SCALE, MAX_SCALE),
                Mth.clamp(scale.y, -MAX_SCALE, MAX_SCALE),
                Mth.clamp(scale.z, -MAX_SCALE, MAX_SCALE)
        );
        return new EntityTransform(rotation, translation, scale);
    }

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

    public record EntityTransform(Vector3f rotation, Vector3f translation, Vector3f scale) {
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
