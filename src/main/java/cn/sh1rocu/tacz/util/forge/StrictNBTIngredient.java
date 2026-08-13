package cn.sh1rocu.tacz.util.forge;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
    public Stream<Holder<Item>> getMatchingItems() {
        return Stream.of(stack.getItemHolder());
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

        @Override
        public Identifier getIdentifier() {
            return ID;
        }

        @Override
        public MapCodec<StrictNBTIngredient> getCodec() {
            return ItemStack.MAP_CODEC.xmap(StrictNBTIngredient::new, ing -> ing.stack);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, StrictNBTIngredient> getPacketCodec() {
            return ItemStack.STREAM_CODEC.map(StrictNBTIngredient::new, ing -> ing.stack);
        }
    }
}
