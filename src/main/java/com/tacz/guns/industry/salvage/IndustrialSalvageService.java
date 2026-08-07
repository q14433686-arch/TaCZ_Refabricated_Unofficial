package com.tacz.guns.industry.salvage;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.industry.item.IndustryItemBuilder;
import com.tacz.guns.industry.magazine.EnBlocClipService;
import com.tacz.guns.industry.magazine.IMagazine;
import com.tacz.guns.industry.recipe.IndustryAssemblyDefinition;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Safe, server-side planning for the industrial recovery station.
 *
 * <p>No gun is consumed until this service has verified that its physical
 * external carrier and player-installed attachments have already been
 * extracted. Internal/chamber rounds are returned explicitly as loose ammo.
 * This is deliberately stricter than a generic crusher: recovery must never
 * silently erase a magazine full of cartridges or a mounted optic.</p>
 */
public final class IndustrialSalvageService {
    private static final String ASSEMBLY_PLATFORM_TAG = "IndustryAssemblyPlatform";
    private static final List<String> STRUCTURAL_ORDER = List.of("receiver", "bolt", "barrel", "trigger", "recoil");
    private static final float DEFAULT_GUN_WEIGHT = 3.0F;

    private IndustrialSalvageService() {
    }

    public static boolean isPotentialInput(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof IGun
                || stack.getItem() instanceof IMagazine
                || stack.is(ModItems.PRESS_DIE));
    }

    public static Plan plan(ItemStack input) {
        if (input == null || input.isEmpty()) {
            return Plan.failure(Failure.INVALID_INPUT);
        }
        if (input.getItem() instanceof IMagazine magazine) {
            return planMagazine(input, magazine);
        }
        if (input.is(ModItems.PRESS_DIE)) {
            return planDie(input);
        }
        if (input.getItem() instanceof IGun gun) {
            return planGun(input, gun);
        }
        return Plan.failure(Failure.INVALID_INPUT);
    }

    private static Plan planMagazine(ItemStack input, IMagazine magazine) {
        if (!magazine.isConfigured(input)) {
            return Plan.failure(Failure.INVALID_MAGAZINE);
        }
        if (magazine.getAmmoCount(input) > 0) {
            return Plan.failure(Failure.MAGAZINE_LOADED);
        }
        // A recovered empty shell returns as neutral body stock. The current
        // carrier line requires a separately calibrated specification gauge and
        // a named feed kit; it never stamps a finished carrier from a complete
        // gun or materialises a different compatibility family.
        return Plan.success(List.of(new ItemStack(ModItems.MAGAZINE_BLANK)));
    }

    private static Plan planDie(ItemStack input) {
        CompoundTag tag = ItemNbtUtils.getTag(input);
        String platform = tag.getStringOr("IndustryPlatform", "");
        String kind = tag.getStringOr("IndustryPartKind", "");
        if (platform.isBlank() || !(kind.endsWith("_die") || kind.endsWith("_die_blank")
                || kind.endsWith("_gauge") || kind.endsWith("_gauge_blank")
                || "acceptance_gauge_stock".equals(kind)
                || "die_blank".equals(kind) || "cartridge_gauge".equals(kind)
                || "action_jig".equals(kind))) {
            return Plan.failure(Failure.INVALID_DIE);
        }
        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(new ItemStack(ModItems.HIGH_CARBON_STEEL_PLATE));
        Item brassNugget = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("create", "brass_nugget"));
        if (brassNugget != null && brassNugget != Items.AIR) {
            outputs.add(new ItemStack(brassNugget, 2));
        }
        return Plan.success(outputs);
    }

    private static Plan planGun(ItemStack input, IGun gun) {
        if (gun.hasInstalledMagazine(input) || !EnBlocClipService.getInstalledClip(input).isEmpty()) {
            return Plan.failure(Failure.GUN_HAS_CARRIER);
        }
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            if (!gun.getAttachment(input, type).isEmpty()) {
                return Plan.failure(Failure.GUN_HAS_ATTACHMENT);
            }
        }

        CommonAssetsManager assets = CommonAssetsManager.getInstance();
        IndustryAssemblyDefinition assembly = assets == null ? null : assets.getIndustryAssemblyForGun(gun.getGunId(input));
        String assembledPlatform = ItemNbtUtils.getTag(input).getStringOr(ASSEMBLY_PLATFORM_TAG, "");
        if (assembly == null || assembledPlatform.isBlank() || !assembledPlatform.equals(assembly.getPlatform())) {
            // A legacy/loot/creative stack with the same GunId must not become
            // a back door around the industrial terminal route.
            return Plan.failure(Failure.GUN_NOT_INDUSTRIAL);
        }

        List<IndustryAssemblyDefinition.Component> components = assembly.getComponents();
        if (components.isEmpty()) {
            return Plan.failure(Failure.GUN_NOT_INDUSTRIAL);
        }
        List<ItemStack> outputs = new ArrayList<>();
        List<ItemStack> storedAmmo = extractInternalAmmo(input, gun);
        if (storedAmmo == null) {
            return Plan.failure(Failure.GUN_AMMO_UNKNOWN);
        }
        outputs.addAll(storedAmmo);
        // Recovery is now differentiated by actual GunData weight rather than
        // returning the same three blanks for a pocket pistol and a 15 kg
        // minigun. Light sidearms recover three structural blanks; ordinary
        // long guns four; heavy precision/MG platforms all five.
        float weight = TimelessAPI.getCommonGunIndex(gun.getGunId(input))
                .map(index -> index.getGunData().getWeight())
                .orElse(DEFAULT_GUN_WEIGHT);
        int recoveryCount = Math.min(components.size(), Math.clamp(
                3 + (int) Math.floor((Math.max(weight, 0.0F) + 1.0F) / 3.0F), 3, 5
        ));
        int offset = Math.floorMod(assembly.getPlatform().hashCode(), components.size());
        for (int recovered = 0; recovered < recoveryCount; recovered++) {
            int index = (offset + recovered) % components.size();
            IndustryAssemblyDefinition.Component component = components.get(index);
            String blankClass = blankClassFor(component, index);
            if (blankClass == null) {
                return Plan.failure(Failure.GUN_NOT_INDUSTRIAL);
            }
            outputs.add(IndustryItemBuilder.componentBlank()
                    .platform("machining")
                    .kind(blankClass + "_blank")
                    .displayNameKey("item.tacz.gun_component_blank")
                    .build());
        }
        int steelPlates = Math.clamp((int) Math.ceil(Math.max(weight, 0.0F) / 3.0F), 1, 4);
        outputs.add(new ItemStack(ModItems.HIGH_CARBON_STEEL_PLATE, steelPlates));
        appendExteriorMaterialRecovery(outputs, assembly);
        return Plan.success(outputs);
    }

    /**
     * Return 60% (rounded up) of the explicitly declared exterior materials.
     * These were consumed to make the calibrated furniture kit, so dropping
     * all of them would erase platform differentiation and make every rifle
     * salvage look identical.
     */
    private static void appendExteriorMaterialRecovery(List<ItemStack> outputs, IndustryAssemblyDefinition assembly) {
        for (IndustryAssemblyDefinition.Material material : assembly.getMaterials()) {
            Identifier id = Identifier.tryParse(material.itemId());
            Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
            if (item == null || item == Items.AIR) {
                continue;
            }
            int recovered = Math.max(1, (int) Math.ceil(material.count() * 0.60D));
            outputs.add(new ItemStack(item, recovered));
        }
    }

    /**
     * Internal/tube/revolver rounds and a chambered round have no removable
     * carrier. Preserve them as ordinary loose ammo in the station outputs;
     * an installed external carrier is rejected earlier so it can be ejected
     * intact with its own capacity/family data.
     */
    @Nullable
    private static List<ItemStack> extractInternalAmmo(ItemStack input, IGun gun) {
        int amount = Math.max(0, gun.getCurrentAmmoCount(input)) + (gun.hasBulletInBarrel(input) ? 1 : 0);
        if (amount <= 0) {
            return List.of();
        }
        // Virtual/dummy and direct-inventory systems do not represent stored
        // physical rounds in the gun stack. Never turn such counters into
        // free loose ammunition through a recovery operation.
        if (gun.useDummyAmmo(input) || gun.useInventoryAmmo(input)) {
            return null;
        }
        var gunIndex = TimelessAPI.getCommonGunIndex(gun.getGunId(input)).orElse(null);
        if (gunIndex == null || (gunIndex.getGunData().getReloadData() != null
                && gunIndex.getGunData().getReloadData().isInfinite())) {
            return null;
        }
        Identifier ammoId = gunIndex.getGunData().getAmmoId();
        if (ammoId == null || CommonAssetsManager.get().getAmmoIndex(ammoId) == null) {
            return null;
        }
        List<ItemStack> outputs = new ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = AmmoItemBuilder.create().setId(ammoId).setCount(remaining).build();
            int size = Math.min(remaining, stack.getMaxStackSize());
            stack.setCount(size);
            outputs.add(stack);
            remaining -= size;
        }
        return outputs;
    }

    @Nullable
    private static String blankClassFor(IndustryAssemblyDefinition.Component component, int index) {
        String blankClass = component.blankClass();
        if (STRUCTURAL_ORDER.contains(blankClass)) {
            return blankClass;
        }
        // Pre-tooling-schema declarations have no blank_class. Their component
        // list still follows receiver/bolt/barrel/trigger/recoil; only that
        // explicit legacy order is allowed as a closed fallback.
        String structural = component.structural();
        if (STRUCTURAL_ORDER.contains(structural)) {
            return structural;
        }
        return index >= 0 && index < STRUCTURAL_ORDER.size() ? STRUCTURAL_ORDER.get(index) : null;
    }

    public enum Failure {
        INVALID_INPUT("message.tacz.industrial_salvage.invalid"),
        INVALID_MAGAZINE("message.tacz.industrial_salvage.invalid_magazine"),
        MAGAZINE_LOADED("message.tacz.industrial_salvage.magazine_loaded"),
        INVALID_DIE("message.tacz.industrial_salvage.invalid_die"),
        GUN_HAS_CARRIER("message.tacz.industrial_salvage.gun_has_carrier"),
        GUN_AMMO_UNKNOWN("message.tacz.industrial_salvage.gun_ammo_unknown"),
        GUN_HAS_ATTACHMENT("message.tacz.industrial_salvage.gun_has_attachment"),
        GUN_NOT_INDUSTRIAL("message.tacz.industrial_salvage.gun_not_industrial");

        private final String translationKey;

        Failure(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public record Plan(List<ItemStack> outputs, @Nullable Failure failure) {
        public static Plan success(List<ItemStack> outputs) {
            return new Plan(outputs.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList(), null);
        }

        public static Plan failure(Failure failure) {
            return new Plan(List.of(), failure);
        }

        public boolean isSuccess() {
            return failure == null && !outputs.isEmpty();
        }
    }
}
