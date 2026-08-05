package com.tacz.guns.industry.item;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.init.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Factory for NBT-identified gun components and reusable blueprints. */
public final class IndustryItemBuilder {
    private final Item item;
    private String platform = "";
    private String kind = "";
    private String displayName = "";
    private String caliber = "";
    private String cartridgeAmmoId = "";
    private String projectileType = "";
    private String dieTargetKind = "";

    private IndustryItemBuilder(Item item) {
        this.item = item;
    }

    public static IndustryItemBuilder componentBlank() {
        return new IndustryItemBuilder(ModItems.GUN_COMPONENT_BLANK);
    }

    public static IndustryItemBuilder component() {
        return new IndustryItemBuilder(ModItems.GUN_COMPONENT);
    }

    public static IndustryItemBuilder blueprint() {
        return new IndustryItemBuilder(ModItems.GUN_BLUEPRINT);
    }

    public static IndustryItemBuilder cartridgeCaseBlank() {
        return new IndustryItemBuilder(ModItems.CARTRIDGE_CASE_BLANK);
    }

    public static IndustryItemBuilder cartridgeCase() {
        return new IndustryItemBuilder(ModItems.CARTRIDGE_CASE);
    }

    public static IndustryItemBuilder projectileBlank() {
        return new IndustryItemBuilder(ModItems.PROJECTILE_BLANK);
    }

    public static IndustryItemBuilder projectileCore() {
        return new IndustryItemBuilder(ModItems.PROJECTILE_CORE);
    }

    public static IndustryItemBuilder pressDie() {
        return new IndustryItemBuilder(ModItems.PRESS_DIE);
    }

    public IndustryItemBuilder platform(String platform) {
        this.platform = platform == null ? "" : platform;
        return this;
    }

    public IndustryItemBuilder kind(String kind) {
        this.kind = kind == null ? "" : kind;
        return this;
    }

    public IndustryItemBuilder displayNameKey(String displayName) {
        this.displayName = displayName == null ? "" : displayName;
        return this;
    }

    public IndustryItemBuilder caliber(String caliber) {
        this.caliber = caliber == null ? "" : caliber;
        return this;
    }

    public IndustryItemBuilder cartridgeAmmoId(String ammoId) {
        this.cartridgeAmmoId = ammoId == null ? "" : ammoId;
        return this;
    }

    public IndustryItemBuilder projectileType(String projectileType) {
        this.projectileType = projectileType == null ? "" : projectileType;
        return this;
    }

    public IndustryItemBuilder dieTargetKind(String targetKind) {
        this.dieTargetKind = targetKind == null ? "" : targetKind;
        return this;
    }

    public ItemStack build() {
        ItemStack stack = new ItemStack(item);
        if (stack.getItem() instanceof IndustryItemDataAccessor accessor) {
            accessor.setPlatform(stack, platform);
            accessor.setPartKind(stack, kind);
            accessor.setDisplayNameKey(stack, displayName);
            accessor.setCartridgeCaliber(stack, caliber);
            accessor.setCartridgeAmmoId(stack, cartridgeAmmoId);
            accessor.setProjectileType(stack, projectileType);
            accessor.setDieTargetKind(stack, dieTargetKind);
        }
        // Recipe JSON writes this component directly. Builders are also used
        // for creative/REI samples, so give those configured samples the same
        // per-ammo slot limit whenever the synchronized index is available.
        Identifier ammoId = Identifier.tryParse(cartridgeAmmoId);
        if (ammoId != null && (item == ModItems.CARTRIDGE_CASE || item == ModItems.PROJECTILE_CORE)) {
            TimelessAPI.getCommonAmmoIndex(ammoId)
                    .ifPresent(index -> stack.set(DataComponents.MAX_STACK_SIZE,
                            Math.clamp(index.getStackSize(), 1, Item.ABSOLUTE_MAX_STACK_SIZE)));
        }
        return stack;
    }
}
