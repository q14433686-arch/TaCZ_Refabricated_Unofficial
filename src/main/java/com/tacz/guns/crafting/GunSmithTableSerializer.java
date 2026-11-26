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

/** 1.21.11 codec for the legacy TACZ gun-smith recipe JSON format. */
public final class GunSmithTableSerializer {
    // The vanilla recipe-registry path sees raw legacy gun-pack JSON as well as our own
    // Gson loader. Decode the item field as JSON and defer its normalized Ingredient parse
    // until tags are available. Eager Ingredient.CODEC rejects old {tag:...}/{item:...}
    // syntax before the custom loader has a chance to normalize it (26.3 c8837d2).
    private static final Codec<GunSmithTableIngredient> INGREDIENT_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    net.minecraft.util.ExtraCodecs.JSON.fieldOf("item").forGetter(GunSmithTableSerializer::encodeIngredient),
                    Codec.INT.optionalFieldOf("count", 1).forGetter(GunSmithTableIngredient::getCount)
            ).apply(instance, GunSmithTableIngredient::new)
    );

    private static com.google.gson.JsonElement encodeIngredient(GunSmithTableIngredient ingredient) {
        Ingredient resolved = ingredient.getIngredient();
        if (resolved != null) {
            // Keep the codec's encode side usable for data generation as well as decoding.
            var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                    net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
            var encoded = Ingredient.CODEC.encodeStart(ops, resolved).result();
            if (encoded.isPresent()) {
                return encoded.get();
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
                    // Recipe results may be empty when a third-party resource pack has malformed data.
                    // The mandatory codec rejects such stacks and aborts the entire recipe sync.
                    ItemStack resultItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                    Identifier group = buffer.readIdentifier();
                    return new GunSmithTableRecipe(recipeId, new GunSmithTableResult(resultItem, group), ingredients);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, GunSmithTableRecipe recipe) {
                    buffer.writeIdentifier(recipe.getId());
                    buffer.writeInt(recipe.getInputs().size());
                    for (GunSmithTableIngredient ingredient : recipe.getInputs()) {
                        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient.getIngredientOrThrow());
                        buffer.writeInt(ingredient.getCount());
                    }
                    // Keep this paired with decode: OPTIONAL_STREAM_CODEC represents ItemStack.EMPTY on the wire.
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, recipe.getResult().getResult());
                    buffer.writeIdentifier(recipe.getResult().getGroup());
                }
            };

    private GunSmithTableSerializer() {
    }

    /**
     * 1.21.11 的 {@code RecipeSerializer} 是<b>接口</b>（只有 codec() / streamCodec() 两个抽象方法），
     * 26.1 才把它变成可直接 new 的 record/类。这里用匿名实现回退，行为完全一致。
     */
    public static RecipeSerializer<GunSmithTableRecipe> create() {
        return new RecipeSerializer<>() {
            @Override
            public MapCodec<GunSmithTableRecipe> codec() {
                return CODEC;
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, GunSmithTableRecipe> streamCodec() {
                return STREAM_CODEC;
            }
        };
    }
}
