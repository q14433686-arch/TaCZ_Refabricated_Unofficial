package com.tacz.guns.item;

import cn.sh1rocu.tacz.api.extension.IItem;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.tacz.guns.client.industry.icon.IndustryIconRenderer;
import com.tacz.guns.industry.magazine.ExternalCarrierVariant;
import com.tacz.guns.industry.magazine.GunFeedDefinition;
import com.tacz.guns.industry.magazine.InventoryRoundHandlingService;
import com.tacz.guns.industry.magazine.MagazineItemBuilder;
import com.tacz.guns.industry.magazine.MagazineItemDataAccessor;
import com.tacz.guns.resource.CommonAssetsManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A one-stack physical detachable magazine or physical loading device.
 *
 * <p>Right-clicking a loose round stack or an empty inventory slot keeps the
 * familiar inventory workflow, but now starts a server-timed one-round
 * transaction. There is no instant bulk-loading shortcut: ordered mixed-round
 * handling happens in the player's real inventory slots.</p>
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
        // Consume the client click too, so vanilla never swaps the stacks while
        // the server is counting down the selected physical round.
        if (player.level().isClientSide()) {
            return slot.getItem().isEmpty() || slot.getItem().getItem() instanceof com.tacz.guns.api.item.IAmmo;
        }
        return InventoryRoundHandlingService.beginMagazineInteraction(player, magazine, slot);
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
        if (!nextRound.equals(com.tacz.guns.api.DefaultAssets.EMPTY_AMMO_ID)) {
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
                            + "|" + capacity + "|" + definition.getAmmoId();
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
