package me.xjqsh.lrtactical.client.resource.display;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * 投掷物飞行实体的额外变换（官方 LR 0.4.3 {@code entity_transform}）。
 *
 * <p>不用 26.2 的 {@code ItemTransform#apply}：那个重载会自带
 * {@code translate(-0.5,-0.5,-0.5)} 的物品槽回中，飞行实体不需要。</p>
 *
 * <p>JSON 解析与官方 ItemTransform 反序列化一致：{@code translation} 乘 1/16。
 * 缺省值是官方默认：绕 Z 轴 90°，平移 {@code (-0.3, 0.15, 0)}（已是米）。</p>
 */
public record EntityExtraTransform(Vector3f rotation, Vector3f translation, Vector3f scale) {

    public static final EntityExtraTransform DEFAULT = new EntityExtraTransform(
            new Vector3f(0.0F, 0.0F, 90.0F),
            new Vector3f(-0.3F, 0.15F, 0.0F),
            new Vector3f(1.0F, 1.0F, 1.0F)
    );

    public void apply(PoseStack poseStack) {
        poseStack.translate(translation.x, translation.y, translation.z);
        if (rotation.z != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(rotation.z));
        }
        if (rotation.y != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation.y));
        }
        if (rotation.x != 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(rotation.x));
        }
        if (scale.x != 1.0F || scale.y != 1.0F || scale.z != 1.0F) {
            poseStack.scale(scale.x, scale.y, scale.z);
        }
    }

    public static EntityExtraTransform parse(@Nullable JsonObject json) {
        if (json == null) {
            return DEFAULT;
        }
        Vector3f rotation = vector(json, "rotation", new Vector3f());
        Vector3f translation = vector(json, "translation", new Vector3f());
        translation.mul(0.0625F);
        Vector3f scale = vector(json, "scale", new Vector3f(1.0F, 1.0F, 1.0F));
        return new EntityExtraTransform(rotation, translation, scale);
    }

    private static Vector3f vector(JsonObject object, String key, Vector3f fallback) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return new Vector3f(fallback);
        }
        JsonElement element = object.get(key);
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) {
            return new Vector3f(fallback);
        }
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }
}
