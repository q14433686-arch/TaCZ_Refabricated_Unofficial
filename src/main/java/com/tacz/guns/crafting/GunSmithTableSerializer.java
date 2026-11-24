package com.tacz.guns.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.GunMod;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.ArrayList;
import java.util.List;

/** 26.1.2 codec for the legacy TACZ gun-smith recipe JSON format. */
public final class GunSmithTableSerializer {
    /**
     * 材料编解码器：{@code item} 字段以<b>原始 JSON</b> 读入，交给
     * {@link GunSmithTableIngredient#GunSmithTableIngredient(JsonElement, int)} 延迟解析。
     *
     * <h2>为什么不能直接 {@code Ingredient.CODEC.fieldOf("item")}（2026-09-21 实机日志）</h2>
     * <p>枪包里大量材料仍是旧写法：{@code {"tag":"c:ingots/iron"}}、
     * {@code {"type":"tacz:nbt","nbt":{...},"partial":true,"items":"tacz:attachment"}}。
     * 这些写法只有 {@link GunSmithTableIngredient#getIngredient()} 里的 {@code normalizeLegacy}
     * 会改写成 26.3 / Fabric 认得的形态；同一份 JSON 的 Gson 路径
     * （{@code GunSmithTableIngredientSerializer}，供 GUI/JEI）一直走那里，所以界面正常。
     * 但本 codec 是<b>配方注册表</b>加载路径（26.3 {@code RegistryDataLoader} 把
     * {@code minecraft:recipe} 当动态注册表加载），此前直接把原文喂给 {@code Ingredient.CODEC}：
     * {@code No key fabric:type ... Not a string ... Not a json array}。
     * 动态注册表任一元素解析失败 ⇒ {@code Failed to load registries due to errors} ⇒
     * <b>整个存档进不去</b>（只装默认枪包时恰好没有旧写法材料，所以此前没暴露）。</p>
     *
     * <p>{@code ExtraCodecs.JSON} 把任意值原样转成 {@code JsonElement}，后续解析、
     * 失败日志、判空语义全部与 Gson 路径统一；编码方向仍写出解析后的 Ingredient。</p>
     */
    private static final Codec<GunSmithTableIngredient> INGREDIENT_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    net.minecraft.util.ExtraCodecs.JSON.fieldOf("item").forGetter(GunSmithTableSerializer::encodeIngredient),
                    Codec.INT.optionalFieldOf("count", 1).forGetter(GunSmithTableIngredient::getCount)
            ).apply(instance, GunSmithTableIngredient::new)
    );

    /** 编码方向：能解析就写解析后的 Ingredient，否则回写原文（不让一条坏材料炸掉整表编码）。 */
    private static com.google.gson.JsonElement encodeIngredient(GunSmithTableIngredient ingredient) {
        Ingredient resolved = ingredient.getIngredient();
        if (resolved != null) {
            var result = Ingredient.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, resolved).result();
            if (result.isPresent()) {
                return result.get();
            }
        }
        com.google.gson.JsonElement raw = ingredient.getRawItem();
        return raw != null ? raw : com.google.gson.JsonNull.INSTANCE;
    }

    // Use the same lazy parser as the workbench cache. Keep the original result spec for encoding;
    // serializing an unresolved stack here would discard custom item components during registry loading.
    private static final Codec<GunSmithTableResult> RESULT_CODEC = net.minecraft.util.ExtraCodecs.JSON.comapFlatMap(
            element -> {
                try {
                    var result = com.tacz.guns.resource.CommonAssetsManager.GSON.fromJson(element, GunSmithTableResult.class);
                    result.setSourceJson(element.getAsJsonObject());
                    return com.mojang.serialization.DataResult.success(result);
                } catch (RuntimeException e) {
                    return com.mojang.serialization.DataResult.error(() -> "Invalid workbench result: " + e.getMessage());
                }
            }, GunSmithTableResult::encodeSpec);

    public static final MapCodec<GunSmithTableRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    RESULT_CODEC.fieldOf("result").forGetter(GunSmithTableRecipe::getResult),
                    INGREDIENT_CODEC.listOf().fieldOf("materials").forGetter(GunSmithTableRecipe::getInputs)
            ).apply(instance, (result, materials) -> new GunSmithTableRecipe(result.fallbackRecipeId(), result, materials))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, GunSmithTableRecipe> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public GunSmithTableRecipe decode(RegistryFriendlyByteBuf buffer) {
                    Identifier recipeId = buffer.readIdentifier();
                    int size = buffer.readInt();
                    List<GunSmithTableIngredient> ingredients = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        ingredients.add(new GunSmithTableIngredient(
                                Ingredient.CONTENTS_STREAM_CODEC.decode(buffer), buffer.readInt()));
                    }
                    ItemStack resultItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                    Identifier group = buffer.readIdentifier();
                    return new GunSmithTableRecipe(recipeId, new GunSmithTableResult(resultItem, group), ingredients);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, GunSmithTableRecipe recipe) {
                    Identifier recipeId = recipe.getId() != null
                            ? recipe.getId()
                            : Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "empty");
                    buffer.writeIdentifier(recipeId);
                    // 仅编码可解析且非空的 Ingredient，避免延迟解析失败（getIngredient() == null）
                    // 或空标签（CustomIngredientStreamCodec 抛 "Cannot serialize empty ingredient PacketByteBuf"）
                    // 触发 EncoderException 导致整包断连。
                    List<GunSmithTableIngredient> validInputs = new ArrayList<>(recipe.getInputs().size());
                    for (GunSmithTableIngredient ingredient : recipe.getInputs()) {
                        Ingredient resolved = ingredient.getIngredient();
                        if (resolved != null && !resolved.isEmpty() && resolved.items().findAny().isPresent()) {
                            validInputs.add(ingredient);
                        }
                    }
                    buffer.writeInt(validInputs.size());
                    for (GunSmithTableIngredient ingredient : validInputs) {
                        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient.getIngredient());
                        buffer.writeInt(ingredient.getCount());
                    }
                    ItemStack resultStack = recipe.getResult() != null && recipe.getResult().getResult() != null
                            ? recipe.getResult().getResult()
                            : ItemStack.EMPTY;
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, resultStack);
                    Identifier group = recipe.getResult() != null && recipe.getResult().getGroup() != null
                            ? recipe.getResult().getGroup()
                            : Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "empty");
                    buffer.writeIdentifier(group);
                }
            };

    private GunSmithTableSerializer() {
    }

    public static RecipeSerializer<GunSmithTableRecipe> create() {
        return new RecipeSerializer<>(CODEC, STREAM_CODEC);
    }
}
