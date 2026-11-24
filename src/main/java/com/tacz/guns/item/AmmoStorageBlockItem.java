package com.tacz.guns.item;

import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.inventory.tooltip.AmmoStorageTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AmmoStorageBlockItem extends BlockItem {
    private static final int MAX_TOOLTIP_ITEMS = 5;
    private static final String ENTRIES_TAG = "Entries";
    private static final String STACK_TAG = "Stack";
    private static final String COUNT_TAG = "Count";

    public AmmoStorageBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = super.getName(stack);
        if (getBlock() == ModBlocks.AMMO_STORAGE_GOLD) {
            return name.copy().withStyle(ChatFormatting.YELLOW);
        }
        if (getBlock() == ModBlocks.AMMO_STORAGE_DIAMOND) {
            return name.copy().withStyle(ChatFormatting.AQUA);
        }
        return name;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> components, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, components, flag);
        components.accept(Component.translatable("tooltip.tacz.ammo_storage.description").withStyle(ChatFormatting.GRAY));
        components.accept(Component.translatable("tooltip.tacz.ammo_storage.description.extra").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        CompoundTag tag = ItemNbtUtils.getTag(stack);
        if (!tag.contains(ENTRIES_TAG)) {
            // Also accept a vanilla pick-block containing the typed block-entity component.
            var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (data == null || data.type() != ModBlocks.AMMO_STORAGE_BE) return Optional.empty();
            tag = data.copyTagWithoutId();
        }
        ListTag entries = tag.getListOrEmpty(ENTRIES_TAG);
        List<AmmoStorageTooltip.Entry> mergedEntries = new ArrayList<>();
        for (int i = 0; i < Math.min(27, entries.size()); i++) {
            var entryTag = entries.getCompoundOrEmpty(i);
            ItemStack entryStack = ItemNbtUtils.loadItemStack(entryTag.getCompoundOrEmpty(STACK_TAG));
            long count = entryTag.getLongOr(COUNT_TAG, 0L);
            if (entryStack.isEmpty() || count <= 0) {
                continue;
            }
            mergeTooltipEntry(mergedEntries, entryStack, count);
        }
        if (mergedEntries.isEmpty()) {
            return Optional.empty();
        }
        int displayed = Math.min(MAX_TOOLTIP_ITEMS, mergedEntries.size());
        List<AmmoStorageTooltip.Entry> tooltipEntries = new ArrayList<>(mergedEntries.subList(0, displayed));
        return Optional.of(new AmmoStorageTooltip(tooltipEntries, mergedEntries.size() - displayed));
    }

    private static void mergeTooltipEntry(List<AmmoStorageTooltip.Entry> entries, ItemStack stack, long count) {
        for (int i = 0; i < entries.size(); i++) {
            AmmoStorageTooltip.Entry entry = entries.get(i);
            if (ItemStack.isSameItemSameComponents(entry.ammo(), stack)) {
                long mergedCount = saturatedAdd(entry.count(), count);
                entries.set(i, new AmmoStorageTooltip.Entry(entry.ammo(), mergedCount));
                return;
            }
        }
        ItemStack sample = stack.copy();
        sample.setCount(1);
        entries.add(new AmmoStorageTooltip.Entry(sample, count));
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
