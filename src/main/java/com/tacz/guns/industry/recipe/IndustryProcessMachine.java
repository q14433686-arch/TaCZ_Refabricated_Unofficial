package com.tacz.guns.industry.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Stable TACZ-side descriptions of the Create Fly operations used by industry
 * recipes.  No Create Java class is referenced: this remains compatible with
 * the optional integration model while still giving REI useful categories.
 */
public enum IndustryProcessMachine {
    MILLING("milling", "rei.tacz.industry.milling", "create:millstone", Items.GRINDSTONE),
    CRUSHING("crushing", "rei.tacz.industry.crushing", "create:crushing_wheel", Items.STONECUTTER),
    HEATED_MIXING("heated_mixing", "rei.tacz.industry.heated_mixing", "create:mechanical_mixer", Items.BLAZE_POWDER),
    SUPERHEATED_MIXING("superheated_mixing", "rei.tacz.industry.superheated_mixing", "create:mechanical_mixer", Items.BLAZE_ROD),
    PRESSING("pressing", "rei.tacz.industry.pressing", "create:mechanical_press", Items.ANVIL),
    /** Multi-input compacting is performed in a Basin, never on a Depot. */
    COMPACTING("compacting", "rei.tacz.industry.compacting", "create:basin", Items.CAULDRON),
    /** One target workpiece plus one item held by a Deployer. */
    DEPLOYING("deploying", "rei.tacz.industry.deploying", "create:deployer", Items.DISPENSER),
    /** One workpiece receives fluid through a Create Spout. */
    FILLING("filling", "rei.tacz.industry.filling", "create:spout", Items.WATER_BUCKET),
    /** A real multi-slot Create mechanical-crafter layout, used for calibrated gauge tooling. */
    MECHANICAL_CRAFTING("mechanical_crafting", "rei.tacz.industry.mechanical_crafting", "create:mechanical_crafter", Items.CRAFTING_TABLE),
    /** One transitional workpiece travels through multiple single-input stations. */
    SEQUENCED_ASSEMBLY("sequenced_assembly", "rei.tacz.industry.sequenced_assembly", "create:deployer", Items.DISPENSER);

    private final String id;
    private final String translationKey;
    private final Identifier workstationId;
    private final Item fallbackIcon;

    IndustryProcessMachine(String id, String translationKey, String workstationId, Item fallbackIcon) {
        this.id = id;
        this.translationKey = translationKey;
        this.workstationId = Identifier.parse(workstationId);
        this.fallbackIcon = fallbackIcon;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return translationKey;
    }

    public ItemStack workstationStack() {
        Item item = BuiltInRegistries.ITEM.getValue(workstationId);
        if (item == null || item == Items.AIR) {
            item = fallbackIcon;
        }
        return new ItemStack(item);
    }

    public static IndustryProcessMachine fromCreateRecipe(String type, String heatRequirement) {
        return switch (type) {
            case "create:milling" -> MILLING;
            case "create:crushing" -> CRUSHING;
            case "create:pressing" -> PRESSING;
            case "create:compacting" -> COMPACTING;
            case "create:deploying" -> DEPLOYING;
            case "create:filling" -> FILLING;
            case "create:mechanical_crafting" -> MECHANICAL_CRAFTING;
            case "create:sequenced_assembly" -> SEQUENCED_ASSEMBLY;
            case "create:mixing" -> "superheated".equals(heatRequirement)
                    ? SUPERHEATED_MIXING
                    : "heated".equals(heatRequirement) ? HEATED_MIXING : HEATED_MIXING;
            default -> null;
        };
    }
}
