package me.xjqsh.lrtactical.item.index;

import com.google.gson.JsonElement;
import me.xjqsh.lrtactical.api.index.ICustomItemIndex;
import me.xjqsh.lrtactical.api.item.IConsumable;
import me.xjqsh.lrtactical.item.consumable.ConsumableData;
import me.xjqsh.lrtactical.resource.CommonAssetsManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ConsumableIndex implements ICustomItemIndex {
    private final Item baseItem;
    private final ConsumableData data;
    private final Identifier id;
    private final String name;
    private final @Nullable String tooltip;

    private ConsumableIndex(ConsumableData data, String name, @Nullable String tooltip,
                            Identifier id, Item baseItem) {
        this.baseItem = baseItem;
        this.data = data;
        this.id = id;
        this.name = name;
        this.tooltip = tooltip;
    }

    @Nullable
    public static ConsumableIndex deserialize(JsonElement data, String name, @Nullable String tooltip,
                                              Identifier id, Item baseItem) {
        ConsumableData consumableData = CommonAssetsManager.GSON.fromJson(data, ConsumableData.class);
        if (consumableData == null) {
            return null;
        }
        return new ConsumableIndex(consumableData, name, tooltip, id, baseItem);
    }

    public ConsumableData getData() { return data; }
    @Nullable public String getTooltip() { return tooltip; }

    @Override
    public ItemStack createItemStack() {
        ItemStack stack = new ItemStack(baseItem);
        if (stack.getItem() instanceof IConsumable consumable) {
            consumable.setId(stack, this.getId());
        }
        applyDataComponents(stack, true);
        return stack;
    }

    public void applyDataComponents(ItemStack stack, boolean freshStack) {
        int maxStack = Math.clamp(getMaxStackSize(), 1, Item.ABSOLUTE_MAX_STACK_SIZE);
        if (stack.getMaxStackSize() != maxStack) {
            stack.set(DataComponents.MAX_STACK_SIZE, maxStack);
        }
        if (data.hasDurability()) {
            stack.set(DataComponents.MAX_DAMAGE, data.getMaxDurability());
            if (freshStack || stack.get(DataComponents.DAMAGE) == null) {
                stack.set(DataComponents.DAMAGE, 0);
            }
        }
    }

    @Override
    public int getMaxStackSize() {
        return data.hasDurability() ? 1 : data.getStackSize();
    }

    @Override public Identifier getId() { return id; }
    @Override public Item getBaseItem() { return baseItem; }
    @Override public String getDescriptionId() { return name; }
}
