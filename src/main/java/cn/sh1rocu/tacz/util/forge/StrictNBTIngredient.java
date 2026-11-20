package cn.sh1rocu.tacz.util.forge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 26.2: 迁移到 DataComponents 系统。
 * 原来的 NBT tag 比较改为使用 ItemStack.isSameItemSameComponents()。
 */
public class StrictNBTIngredient implements CustomIngredient {
    private final ItemStack stack;

    protected StrictNBTIngredient(ItemStack stack) {
        this.stack = stack;
    }

    /**
     * Creates a new ingredient matching the given stack and components
     */
    public static StrictNBTIngredient of(ItemStack stack) {
        return new StrictNBTIngredient(stack);
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        if (input == null)
            return false;
        return ItemStack.isSameItemSameComponents(this.stack, input);
    }

    @Override
    public Stream<Holder<Item>> items() {
        return stack.isEmpty() ? Stream.empty() : Stream.of(stack.typeHolder());
    }

    @Override
    public SlotDisplay display() {
        if (stack.isEmpty()) {
            return SlotDisplay.Empty.INSTANCE;
        }
        return new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack.copy()));
    }

    @Override
    public boolean requiresTesting() {
        return true;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static final Identifier ID = Identifier.fromNamespaceAndPath("forge", "nbt");

    public static class Serializer implements CustomIngredientSerializer<StrictNBTIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        private static final MapCodec<StrictNBTIngredient> LEGACY_ITEMS_CODEC = RecordCodecBuilder.mapCodec(codec -> codec.group(
                BuiltInRegistries.ITEM.holderByNameCodec().listOf().optionalFieldOf("items", List.of()).forGetter(ing ->
                        ing.stack.isEmpty() ? List.of() : List.of(ing.stack.typeHolder())),
                BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("id").forGetter(ing ->
                        ing.stack.isEmpty() ? Optional.empty() : Optional.of(ing.stack.typeHolder())),
                CustomData.COMPOUND_TAG_CODEC.optionalFieldOf("nbt").forGetter(ing -> {
                    CustomData data = ing.stack.get(DataComponents.CUSTOM_DATA);
                    return data != null ? Optional.of(data.copyTag()) : Optional.empty();
                })
        ).apply(codec, (holders, idOpt, tagOpt) -> {
            Holder<Item> holder = !holders.isEmpty() ? holders.getFirst() : idOpt.orElse(null);
            if (holder == null || holder.value() == Items.AIR) {
                throw new IllegalArgumentException("Cannot create a StrictNBTIngredient with no valid item");
            }
            ItemStack stack = new ItemStack(holder.value());
            tagOpt.ifPresent(tag -> stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)));
            return new StrictNBTIngredient(stack);
        }));

        @Override
        public Identifier getIdentifier() {
            return ID;
        }

        @Override
        public MapCodec<StrictNBTIngredient> getCodec() {
            return LEGACY_ITEMS_CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, StrictNBTIngredient> getStreamCodec() {
            return ItemStack.OPTIONAL_STREAM_CODEC.map(StrictNBTIngredient::new, ing -> ing.stack);
        }
    }
}
