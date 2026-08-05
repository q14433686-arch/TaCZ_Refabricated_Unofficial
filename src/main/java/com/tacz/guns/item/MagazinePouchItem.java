package com.tacz.guns.item;

import com.tacz.guns.industry.magazine.IMagazine;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Compact four-magazine carrier. It stores complete physical magazine stacks,
 * including remaining rounds and compatibility family, rather than flattening
 * them into an integer counter.
 */
public final class MagazinePouchItem extends Item {
    public static final int CAPACITY = 4;
    private static final String CONTENTS_TAG = "MagazinePouchContents";

    public MagazinePouchItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack pouch, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) {
            return false;
        }
        ItemStack target = slot.getItem();
        if (target.isEmpty()) {
            return takeIntoSlot(pouch, slot, player);
        }
        if (target.getItem() instanceof IMagazine magazine && magazine.isConfigured(target)) {
            return storeMagazine(pouch, slot, player);
        }
        return false;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack pouch = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return hasContents(pouch) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        List<ItemStack> contents = readContents(pouch);
        if (contents.isEmpty()) {
            return InteractionResult.PASS;
        }
        ItemStack magazine = contents.removeFirst();
        if (!player.getInventory().add(magazine)) {
            return InteractionResult.FAIL;
        }
        writeContents(pouch, contents);
        player.getInventory().setChanged();
        player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        return InteractionResult.CONSUME;
    }

    private static boolean storeMagazine(ItemStack pouch, Slot slot, Player player) {
        if (player.level().isClientSide()) {
            return true;
        }
        List<ItemStack> contents = readContents(pouch);
        if (contents.size() >= CAPACITY) {
            return false;
        }
        ItemStack extracted = slot.safeTake(1, 1, player);
        if (extracted.isEmpty() || !(extracted.getItem() instanceof IMagazine magazine) || !magazine.isConfigured(extracted)) {
            if (!extracted.isEmpty()) {
                slot.safeInsert(extracted);
            }
            return false;
        }
        contents.add(extracted.copyWithCount(1));
        writeContents(pouch, contents);
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.9F + player.level().getRandom().nextFloat() * 0.2F);
        return true;
    }

    private static boolean takeIntoSlot(ItemStack pouch, Slot slot, Player player) {
        if (player.level().isClientSide()) {
            return hasContents(pouch);
        }
        List<ItemStack> contents = readContents(pouch);
        if (contents.isEmpty()) {
            return false;
        }
        ItemStack magazine = contents.removeFirst();
        ItemStack remainder = slot.safeInsert(magazine);
        if (!remainder.isEmpty()) {
            contents.add(0, magazine);
            return false;
        }
        writeContents(pouch, contents);
        player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.9F + player.level().getRandom().nextFloat() * 0.2F);
        return true;
    }

    public static int getStoredMagazineCount(ItemStack pouch) {
        return readContents(pouch).size();
    }

    private static boolean hasContents(ItemStack pouch) {
        return !readContents(pouch).isEmpty();
    }

    private static List<ItemStack> readContents(ItemStack pouch) {
        List<ItemStack> contents = new ArrayList<>();
        ListTag list = ItemNbtUtils.getTag(pouch).getListOrEmpty(CONTENTS_TAG);
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag compound)) {
                continue;
            }
            ItemStack stack = ItemNbtUtils.loadItemStack(compound);
            if (stack.getItem() instanceof IMagazine magazine && magazine.isConfigured(stack)) {
                contents.add(stack.copyWithCount(1));
                if (contents.size() >= CAPACITY) {
                    break;
                }
            }
        }
        return contents;
    }

    private static void writeContents(ItemStack pouch, List<ItemStack> contents) {
        ItemNbtUtils.updateTag(pouch, tag -> {
            ListTag list = new ListTag();
            for (ItemStack magazine : contents) {
                if (!magazine.isEmpty()) {
                    list.add(ItemNbtUtils.saveItemStack(magazine.copyWithCount(1)));
                }
            }
            tag.put(CONTENTS_TAG, list);
        });
    }

    @Override
    public boolean isBarVisible(ItemStack pouch) {
        return getStoredMagazineCount(pouch) > 0;
    }

    @Override
    public int getBarWidth(ItemStack pouch) {
        return Math.clamp(1 + 12 * getStoredMagazineCount(pouch) / CAPACITY, 0, 13);
    }

    @Override
    public int getBarColor(ItemStack pouch) {
        return 0xA06A38;
    }

    @Override
    public void appendHoverText(ItemStack pouch, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> adder, TooltipFlag advanced) {
        int count = getStoredMagazineCount(pouch);
        adder.accept(Component.translatable("tooltip.tacz.magazine_pouch.count", count, CAPACITY)
                .withStyle(style -> style.withColor(0xAAAAAA)));
        adder.accept(Component.translatable("tooltip.tacz.magazine_pouch.usage.store")
                .withStyle(style -> style.withColor(0x777777)));
        adder.accept(Component.translatable("tooltip.tacz.magazine_pouch.usage.take")
                .withStyle(style -> style.withColor(0x777777)));
    }
}
