package com.tacz.guns.item;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.industry.magazine.GunFeedDefinition;
import com.tacz.guns.industry.magazine.MagazineItemBuilder;
import com.tacz.guns.industry.magazine.MagazineItemDataAccessor;
import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A one-stack physical detachable magazine.
 *
 * <p>Loading/unloading is deliberately inventory-native: hold this item and
 * right-click a matching loose-ammo stack to load it, or right-click an empty
 * slot to take out a normal stack of rounds.  This mirrors the existing TACZ
 * ammo-box interaction without adding another screen for a simple operation.</p>
 */
public class MagazineItem extends Item implements MagazineItemDataAccessor {
    public MagazineItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        String nameKey = getDisplayNameKey(stack);
        return nameKey.isBlank() ? super.getName(stack) : Component.translatable(nameKey);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        return super.overrideOtherStackedOnMe(stack, other, slot, action, player, access);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack magazine, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !isConfigured(magazine)) {
            return false;
        }

        ItemStack target = slot.getItem();
        if (target.isEmpty()) {
            return unloadIntoEmptySlot(magazine, slot, player);
        }
        if (target.getItem() instanceof IAmmo ammo && ammo.getAmmoId(target).equals(getAmmoId(magazine))) {
            return loadFromAmmoStack(magazine, target, slot, player);
        }
        return false;
    }

    private boolean loadFromAmmoStack(ItemStack magazine, ItemStack ammoStack, Slot slot, Player player) {
        int free = getCapacity(magazine) - getAmmoCount(magazine);
        if (free <= 0) {
            return false;
        }
        ItemStack extracted = slot.safeTake(ammoStack.getCount(), free, player);
        if (extracted.isEmpty()) {
            return false;
        }
        setAmmoCount(magazine, getAmmoCount(magazine) + extracted.getCount());
        playInsertSound(player);
        return true;
    }

    private boolean unloadIntoEmptySlot(ItemStack magazine, Slot slot, Player player) {
        int stored = getAmmoCount(magazine);
        if (stored <= 0 || getAmmoId(magazine).equals(DefaultAssets.EMPTY_AMMO_ID)) {
            return false;
        }

        // One normal loose-ammo stack at a time keeps the inventory interaction
        // predictable and honours each pack's per-calibre stack-size setting.
        var ammoIndex = CommonAssetsManager.get().getAmmoIndex(getAmmoId(magazine));
        int take = ammoIndex == null
                ? Math.min(64, stored)
                : Math.min(ammoIndex.getStackSize(), stored);
        ItemStack looseAmmo = AmmoItemBuilder.create().setId(getAmmoId(magazine)).setCount(take).build();
        ItemStack remainder = slot.safeInsert(looseAmmo);
        int inserted = take - remainder.getCount();
        if (inserted <= 0) {
            return false;
        }
        setAmmoCount(magazine, stored - inserted);
        playRemoveSound(player);
        return true;
    }

    private static void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playRemoveSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return isConfigured(stack) && getAmmoCount(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int capacity = getCapacity(stack);
        if (capacity <= 0) {
            return 0;
        }
        return Math.clamp(1 + (12 * getAmmoCount(stack)) / capacity, 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(1 / 3f, 0.95F, 0.95F);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> adder, TooltipFlag advanced) {
        if (!isConfigured(stack)) {
            adder.accept(Component.translatable("tooltip.tacz.magazine.unconfigured")
                    .withStyle(style -> style.withColor(0xFF5555)));
            return;
        }
        adder.accept(Component.translatable("tooltip.tacz.magazine.rounds", getAmmoCount(stack), getCapacity(stack))
                .withStyle(style -> style.withColor(0xAAAAAA)));
        adder.accept(Component.translatable("tooltip.tacz.magazine.ammo", getAmmoId(stack).toString())
                .withStyle(style -> style.withColor(0xAAAAAA)));
        adder.accept(Component.translatable("tooltip.tacz.magazine.usage.load")
                .withStyle(style -> style.withColor(0x777777)));
        adder.accept(Component.translatable("tooltip.tacz.magazine.usage.unload")
                .withStyle(style -> style.withColor(0x777777)));
        if (advanced.isAdvanced()) {
            adder.accept(Component.translatable("tooltip.tacz.magazine.family", getMagazineFamily(stack))
                    .withStyle(style -> style.withColor(0x555555)));
        }
    }

    /** Build one empty sample per unique family/capacity/ammo combination. */
    public static NonNullList<ItemStack> fillItemCategory() {
        NonNullList<ItemStack> stacks = NonNullList.create();
        Set<String> seen = new HashSet<>();
        for (var entry : CommonAssetsManager.get().getAllGunFeedDefinitions()) {
            GunFeedDefinition definition = entry.getValue();
            if (!definition.isValidDetachableDefinition()) {
                continue;
            }
            String key = definition.getMagazineFamily() + "|" + definition.getMagazineCapacity() + "|" + definition.getAmmoId();
            if (seen.add(key)) {
                stacks.add(MagazineItemBuilder.create().fromDefinition(definition).build());
            }
        }
        return stacks;
    }

    public static void fillItemCategory(CreativeModeTab.Output output) {
        output.acceptAll(fillItemCategory());
    }
}
