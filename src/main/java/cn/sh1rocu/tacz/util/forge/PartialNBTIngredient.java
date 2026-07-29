package cn.sh1rocu.tacz.util.forge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 26.2: 迁移到 DataComponents 系统。
 * 原来的 NBT tag 匹配改为使用 DataComponents.CUSTOM_DATA + CustomData.matchedBy()。
 */
public class PartialNBTIngredient implements CustomIngredient {
    private final Set<Item> items;
    private final CompoundTag nbt;

    protected PartialNBTIngredient(Set<Item> items, CompoundTag nbt) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a PartialNBTIngredient with no items");
        }
        this.items = Collections.unmodifiableSet(items);
        this.nbt = nbt;
    }

    /**
     * Creates a new ingredient matching any item from the list, containing the given NBT
     */
    public static PartialNBTIngredient of(CompoundTag nbt, ItemLike... items) {
        return new PartialNBTIngredient(Arrays.stream(items).map(ItemLike::asItem).collect(Collectors.toSet()), nbt);
    }

    /**
     * Creates a new ingredient matching the given item, containing the given NBT
     */
    public static PartialNBTIngredient of(ItemLike item, CompoundTag nbt) {
        return new PartialNBTIngredient(Set.of(item.asItem()), nbt);
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        if (input == null)
            return false;
        if (!items.contains(input.getItem()))
            return false;
        // 26.2: 使用 CUSTOM_DATA component 进行 NBT 匹配
        CustomData customData = input.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.matchedBy(nbt);
    }

    @Override
    public Stream<Holder<Item>> items() {
        return items.stream().map(BuiltInRegistries.ITEM::wrapAsHolder);
    }

    /**
     * 让材料格显示<b>带上要求 NBT 的</b>物品，而不是光秃秃的基础物品。
     *
     * <p>不覆写的话，父接口默认实现只会拿 {@link #items()} 里的裸物品去画 ——
     * 对 TACZ 而言就是一把「空枪 ID」的 {@code tacz:modern_kinetic_gun}，
     * 图标是缺省模型、名字也不对，玩家根本看不出要交什么。
     *
     * <p>这里把 {@code nbt} 塞进 {@code CUSTOM_DATA} 再交给显示层，
     * 于是材料格会正确渲染成「柯尔特 M1892」本身。
     * 这只影响<b>显示</b>，匹配逻辑仍由 {@link #test} 负责，两者互不干扰。
     */
    @Override
    public SlotDisplay display() {
        return new SlotDisplay.Composite(items.stream()
                .map(item -> {
                    ItemStack stack = new ItemStack(item);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt.copy()));
                    return (SlotDisplay) new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack));
                })
                .toList());
    }

    @Override
    public boolean requiresTesting() {
        return true;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static final Identifier ID = Identifier.fromNamespaceAndPath("forge", "partial_nbt");

    public static class Serializer implements CustomIngredientSerializer<PartialNBTIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public Identifier getIdentifier() {
            return ID;
        }

        @Override
        public MapCodec<PartialNBTIngredient> getCodec() {
            return RecordCodecBuilder.mapCodec(codec -> codec.group(
                    BuiltInRegistries.ITEM.holderByNameCodec().listOf().fieldOf("items").forGetter(ing ->
                            ing.items.stream().map(BuiltInRegistries.ITEM::wrapAsHolder).toList()),
                    CustomData.COMPOUND_TAG_CODEC.fieldOf("nbt").forGetter(ing -> ing.nbt)
            ).apply(codec, (holders, tag) -> new PartialNBTIngredient(
                    holders.stream().map(Holder::value).collect(Collectors.toSet()), tag)));
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PartialNBTIngredient> getStreamCodec() {
            return StreamCodec.composite(
                    ByteBufCodecs.holderRegistry(Registries.ITEM).apply(ByteBufCodecs.list()),
                    ing -> ing.items.stream().map(BuiltInRegistries.ITEM::wrapAsHolder).toList(),
                    ByteBufCodecs.TRUSTED_COMPOUND_TAG,
                    ing -> ing.nbt,
                    (holders, tag) -> new PartialNBTIngredient(
                            holders.stream().map(Holder::value).collect(Collectors.toSet()), tag)
            );
        }
    }
}
