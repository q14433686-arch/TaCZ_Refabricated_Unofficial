package com.tacz.guns.item;

import cn.sh1rocu.tacz.api.extension.IItem;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.client.industry.icon.IndustryIconRenderer;
import com.tacz.guns.industry.ammo.AmmoProfileService;
import com.tacz.guns.industry.magazine.ExternalCarrierVariant;
import com.tacz.guns.industry.magazine.GunFeedDefinition;
import com.tacz.guns.industry.magazine.MagazineItemBuilder;
import com.tacz.guns.industry.magazine.MagazineItemDataAccessor;
import com.tacz.guns.resource.CommonAssetsManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A one-stack physical detachable magazine or physical loading device.
 *
 * <p>Loading and unloading use the usual inventory secondary-click workflow.
 * A compatible loose-ammo stack is transferred immediately into the ordered
 * carrier contents; unloading transfers the contiguous top run of one exact
 * AmmoId. The carrier therefore keeps its real mixed-round order without a
 * background inventory timer.</p>
 */
public class MagazineItem extends Item implements MagazineItemDataAccessor, IItem {
    public MagazineItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    @Environment(EnvType.CLIENT)
    public BuiltinItemRendererRegistry.DynamicItemRenderer getCustomRenderer() {
        return IndustryIconRenderer.INSTANCE.get();
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
        if (target.getItem() instanceof IAmmo ammo && canLoad(magazine, target, ammo)) {
            return loadFromAmmoStack(magazine, target, ammo, slot, player);
        }
        return false;
    }

    private boolean canLoad(ItemStack magazine, ItemStack ammoStack, IAmmo ammo) {
        Identifier roundAmmoId = ammo.getAmmoId(ammoStack);
        return getAmmoCount(magazine) < getCapacity(magazine)
                && AmmoProfileService.isLoadedAmmoIdentity(roundAmmoId)
                && AmmoProfileService.isSameCaliber(getAmmoId(magazine), roundAmmoId);
    }

    private boolean loadFromAmmoStack(ItemStack magazine, ItemStack ammoStack, IAmmo ammo, Slot slot, Player player) {
        int free = getCapacity(magazine) - getAmmoCount(magazine);
        if (free <= 0) {
            return false;
        }
        Identifier roundAmmoId = ammo.getAmmoId(ammoStack);
        ItemStack extracted = slot.safeTake(ammoStack.getCount(), free, player);
        if (extracted.isEmpty()) {
            return false;
        }
        int loaded = 0;
        for (int index = 0; index < extracted.getCount(); index++) {
            if (!pushRound(magazine, roundAmmoId)) {
                break;
            }
            loaded++;
        }
        if (loaded != extracted.getCount()) {
            // This is only reachable if another server-side mutation changed
            // the carrier between safeTake and pushRound. Restore the portion
            // that was not accepted instead of dropping or duplicating it.
            slot.safeInsert(extracted.copyWithCount(extracted.getCount() - loaded));
        }
        if (loaded <= 0) {
            return false;
        }
        playInsertSound(player);
        return true;
    }

    private boolean unloadIntoEmptySlot(ItemStack magazine, Slot slot, Player player) {
        Identifier topRound = getNextRoundAmmoId(magazine);
        if (topRound.equals(DefaultAssets.EMPTY_AMMO_ID) || !AmmoProfileService.isLoadedAmmoIdentity(topRound)) {
            return false;
        }

        var ammoIndex = CommonAssetsManager.get().getAmmoIndex(topRound);
        int stackLimit = ammoIndex == null ? 64 : ammoIndex.getStackSize();
        int removable = 0;
        List<Identifier> rounds = getRoundAmmoIds(magazine);
        for (int index = rounds.size() - 1; index >= 0 && removable < stackLimit; index--) {
            if (!topRound.equals(rounds.get(index))) {
                break;
            }
            removable++;
        }
        if (removable <= 0) {
            return false;
        }

        ItemStack looseAmmo = AmmoItemBuilder.create().setId(topRound).setCount(removable).build();
        ItemStack remainder = slot.safeInsert(looseAmmo);
        int inserted = removable - remainder.getCount();
        if (inserted <= 0) {
            return false;
        }
        for (int index = 0; index < inserted; index++) {
            if (!topRound.equals(popNextRound(magazine))) {
                // The top run was calculated from this same stack and cannot
                // normally change during a menu click. Do not remove a
                // different profile if an external mutation ever races it.
                break;
            }
        }
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
        Identifier ammoId = getAmmoId(stack);
        Component ammoName = ammoDisplayName(ammoId);
        adder.accept(Component.translatable("tooltip.tacz.magazine.ammo", ammoName)
                .withStyle(style -> style.withColor(0xAAAAAA)));
        Identifier nextRound = getNextRoundAmmoId(stack);
        if (!nextRound.equals(DefaultAssets.EMPTY_AMMO_ID)) {
            adder.accept(Component.translatable("tooltip.tacz.magazine.next_round", ammoDisplayName(nextRound))
                    .withStyle(style -> style.withColor(0x8FD6C6)));
        }
        Map<Identifier, Integer> composition = new LinkedHashMap<>();
        for (Identifier round : getRoundAmmoIds(stack)) {
            composition.merge(round, 1, Integer::sum);
        }
        if (composition.size() > 1) {
            StringBuilder summary = new StringBuilder();
            int visible = 0;
            for (Map.Entry<Identifier, Integer> entry : composition.entrySet()) {
                if (visible++ >= 4) {
                    summary.append(" +");
                    break;
                }
                if (summary.length() > 0) {
                    summary.append(", ");
                }
                summary.append(ammoDisplayName(entry.getKey()).getString()).append(" ×").append(entry.getValue());
            }
            adder.accept(Component.translatable("tooltip.tacz.magazine.mixed", summary.toString())
                    .withStyle(style -> style.withColor(0xD6B46C)));
        }
        String feedDeviceKind = getFeedDeviceKind(stack);
        if (!feedDeviceKind.isBlank() && !"detachable_magazine".equals(feedDeviceKind)) {
            adder.accept(Component.translatable("tooltip.tacz.feed_device.kind." + feedDeviceKind)
                    .withStyle(style -> style.withColor(0x8FD6C6)));
            if ("stripper_clip".equals(feedDeviceKind) || "speedloader".equals(feedDeviceKind)
                    || "en_bloc_clip".equals(feedDeviceKind)) {
                adder.accept(Component.translatable("tooltip.tacz.feed_device.reusable")
                        .withStyle(style -> style.withColor(0x8FD6C6)));
            }
        }
        adder.accept(Component.translatable("tooltip.tacz.magazine.usage.load")
                .withStyle(style -> style.withColor(0x777777)));
        adder.accept(Component.translatable("tooltip.tacz.magazine.usage.unload")
                .withStyle(style -> style.withColor(0x777777)));
        if (advanced.isAdvanced()) {
            adder.accept(Component.translatable("tooltip.tacz.magazine.family", getMagazineFamily(stack))
                    .withStyle(style -> style.withColor(0x555555)));
        }
    }

    private static Component ammoDisplayName(Identifier ammoId) {
        var ammoIndex = CommonAssetsManager.get().getAmmoIndex(ammoId);
        return ammoIndex == null || ammoIndex.getPojo().getName() == null
                ? Component.literal(ammoId.toString())
                : Component.translatable(ammoIndex.getPojo().getName());
    }

    /** Build one empty sample per unique external-carrier or loading-device identity. */
    public static NonNullList<ItemStack> fillItemCategory() {
        NonNullList<ItemStack> stacks = NonNullList.create();
        Set<String> seen = new HashSet<>();
        for (var entry : CommonAssetsManager.get().getAllGunFeedDefinitions()) {
            // Compatibility data may ship a dormant feed declaration for an
            // optional gun pack. Do not expose a mysterious clip/magazine in
            // creative until its target GunIndex actually exists.
            if (CommonAssetsManager.get().getGunIndex(entry.getKey()) == null) {
                continue;
            }
            GunFeedDefinition definition = entry.getValue();
            if (definition.isValidExternalCarrierDefinition()) {
                // A belt box is just as physical as a detachable box magazine;
                // it used to be omitted here because the old check accepted
                // detachable_magazine only. Every explicit larger variant also
                // deserves its own empty creative sample rather than a fake
                // base-capacity clone.
                for (ExternalCarrierVariant variant : definition.getExternalCarrierVariants()) {
                    int capacity = variant.getCapacity();
                    String key = definition.getMechanism().serializedName() + "|" + definition.getMagazineFamily()
                            + "|" + capacity + "|" + AmmoProfileService.canonicalCaliber(definition.getAmmoId());
                    if (seen.add(key)) {
                        stacks.add(MagazineItemBuilder.create().fromExternalCarrier(definition, variant).build());
                    }
                }
                continue;
            }
            if (!definition.isValidLoadingDeviceDefinition() && !definition.isValidEnBlocClipDefinition()) {
                continue;
            }
            int capacity = definition.getFeedDeviceCapacity();
            String key = definition.getMechanism().serializedName() + "|" + definition.getMagazineFamily()
                    + "|" + capacity + "|" + definition.getAmmoId();
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
