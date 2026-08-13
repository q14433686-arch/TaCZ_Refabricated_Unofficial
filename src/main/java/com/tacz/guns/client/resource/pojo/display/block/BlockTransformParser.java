package com.tacz.guns.client.resource.pojo.display.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * 把枪包 {@code display/blocks/*.json} 里的 {@code transforms} 段解析成 26.2 的
 * {@link ItemTransforms}。
 *
 * <p><b>为什么需要这个类</b></p>
 *
 * <p>1.21.1 里 {@code BlockDisplay#getTransforms()} 直接返回 {@code ItemTransforms}，
 * 由 Minecraft 自己的 Gson 适配器（注册在 {@code BlockModel.GSON} 上）反序列化。
 * 26.2 把这套模型 JSON 解析迁走了，{@code ItemTransform.Deserializer} 变成
 * {@code protected static} 内部类、{@code ItemTransforms} 也不再暴露公开 Codec，
 * 外部无法再直接复用。</p>
 *
 * <p>移植时的处理是：把 {@code BlockDisplay.transforms} 的类型退化成裸
 * {@code JsonObject}，并把 {@code ClientBlockIndex} 里的 {@code checkTransforms(...)}
 * 与 {@code getTransforms()} <b>整段删掉</b>，于是 {@code GunSmithTableItemRenderer}
 * 中原本"应用 transforms"的那一段也一并消失 —— 结果就是三种工作台/装配台的手持模型
 * <b>完全不缩放</b>，按方块原始尺寸（1×1×1 米）渲染，看起来巨大。
 * 默认包里 {@code gun_smith_table.json} 声明的是 {@code scale: [0.25, 0.25, 0.25]}，
 * 也就是说实际显示尺寸是应有尺寸的 <b>4 倍</b>。</p>
 *
 * <p>本类按 26.2 反编译源 {@code ItemTransform.Deserializer} 的<b>逐行语义</b>重实现解析：</p>
 * <ul>
 *   <li>{@code translation} 乘 {@code 0.0625}（1/16，像素→米），再 clamp 到 ±5</li>
 *   <li>{@code scale} clamp 到 ±4</li>
 *   <li>{@code rotation} 原样保留（角度制）</li>
 *   <li>缺省值：rotation/translation 为 0，scale 为 1</li>
 * </ul>
 *
 * <p>注意 {@link ItemTransform#apply(boolean, com.mojang.blaze3d.vertex.PoseStack.Pose)}
 * 在 26.2 里接收的是 {@code PoseStack.Pose} 而不是 {@code PoseStack}，且它自带
 * {@code translate(-0.5, -0.5, -0.5)} 的回中操作 —— 调用方无需再手动补。</p>
 */
public final class BlockTransformParser {
    private static final Vector3f DEFAULT_ROTATION = new Vector3f();
    private static final Vector3f DEFAULT_TRANSLATION = new Vector3f();
    private static final Vector3f DEFAULT_SCALE = new Vector3f(1.0F, 1.0F, 1.0F);
    private static final float MAX_TRANSLATION = 5.0F;
    private static final float MAX_SCALE = 4.0F;

    private BlockTransformParser() {
    }

    /**
     * @param json 枪包中的 {@code transforms} 对象，可为 null
     * @return 解析结果；json 为 null 时返回 {@link ItemTransforms#NO_TRANSFORMS}
     */
    public static ItemTransforms parse(@Nullable JsonObject json) {
        if (json == null) {
            return ItemTransforms.NO_TRANSFORMS;
        }
        return new ItemTransforms(
                get(json, "thirdperson_lefthand"),
                get(json, "thirdperson_righthand"),
                get(json, "firstperson_lefthand"),
                get(json, "firstperson_righthand"),
                get(json, "head"),
                get(json, "gui"),
                get(json, "ground"),
                get(json, "fixed"),
                // 26.2 新增的 fixedFromBottom：枪包 JSON 里不存在该键，沿用 fixed 的值，
                // 与 vanilla 模型解析对缺失键回退到 NO_TRANSFORM 的行为保持一致。
                get(json, "fixed")
        );
    }

    private static ItemTransform get(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return ItemTransform.NO_TRANSFORM;
        }
        JsonObject object = root.getAsJsonObject(key);

        Vector3f rotation = getVector3f(object, "rotation", DEFAULT_ROTATION);

        Vector3f translation = getVector3f(object, "translation", DEFAULT_TRANSLATION);
        translation.mul(0.0625F);
        translation.set(
                Mth.clamp(translation.x, -MAX_TRANSLATION, MAX_TRANSLATION),
                Mth.clamp(translation.y, -MAX_TRANSLATION, MAX_TRANSLATION),
                Mth.clamp(translation.z, -MAX_TRANSLATION, MAX_TRANSLATION)
        );

        Vector3f scale = getVector3f(object, "scale", DEFAULT_SCALE);
        scale.set(
                Mth.clamp(scale.x, -MAX_SCALE, MAX_SCALE),
                Mth.clamp(scale.y, -MAX_SCALE, MAX_SCALE),
                Mth.clamp(scale.z, -MAX_SCALE, MAX_SCALE)
        );

        return new ItemTransform(rotation, translation, scale);
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

    /** 便于调用方判断上下文是否为左手（{@code ItemTransform#apply} 的第一个参数）。 */
    public static boolean isLeftHand(ItemDisplayContext context) {
        return context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }
}
