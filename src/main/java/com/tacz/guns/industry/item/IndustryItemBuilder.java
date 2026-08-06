package com.tacz.guns.industry.item;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.industry.magazine.MagazineItemDataAccessor;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Factory for NBT-identified industrial components, carrier tooling, and reusable blueprints. */
public final class IndustryItemBuilder {
    private final Item item;
    private String platform = "";
    private String kind = "";
    private String displayName = "";
    private String caliber = "";
    private String cartridgeAmmoId = "";
    private String projectileType = "";
    private String dieTargetKind = "";
    private String blueprintTier = "";
    private String blueprintRole = "";
    private String actionProfile = "";
    private String toolingScope = "";
    // Carrier tooling/components reuse the magazine compatibility contract so
    // their real family/ammo/capacity identity survives recipe-viewer samples.
    private String magazineFamily = "";
    private String magazineAmmoId = "";
    private int magazineCapacity;

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

    public IndustryItemBuilder blueprintTier(String tier) {
        this.blueprintTier = tier == null ? "" : tier;
        return this;
    }

    public IndustryItemBuilder blueprintRole(String role) {
        this.blueprintRole = role == null ? "" : role;
        return this;
    }

    public IndustryItemBuilder actionProfile(String profile) {
        this.actionProfile = profile == null ? "" : profile;
        return this;
    }

    public IndustryItemBuilder toolingScope(String scope) {
        this.toolingScope = scope == null ? "" : scope;
        return this;
    }

    /**
     * Preserve the physical carrier specification on a generic industry stack.
     * This is not a second magazine implementation: it is the same stable
     * compatibility data later copied onto the finished {@code tacz:magazine}.
     */
    public IndustryItemBuilder magazineFamily(String family) {
        this.magazineFamily = family == null ? "" : family;
        return this;
    }

    public IndustryItemBuilder magazineAmmoId(String ammoId) {
        this.magazineAmmoId = ammoId == null ? "" : ammoId;
        return this;
    }

    public IndustryItemBuilder magazineCapacity(int capacity) {
        this.magazineCapacity = Math.max(0, capacity);
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
            accessor.setBlueprintTier(stack, blueprintTier);
            accessor.setBlueprintRole(stack, blueprintRole);
            accessor.setActionProfile(stack, actionProfile);
            accessor.setToolingScope(stack, toolingScope);
        }
        if (!magazineFamily.isBlank() || !magazineAmmoId.isBlank() || magazineCapacity > 0) {
            ItemNbtUtils.updateTag(stack, tag -> {
                tag.putString(MagazineItemDataAccessor.MAGAZINE_FAMILY_TAG, magazineFamily);
                tag.putString(MagazineItemDataAccessor.MAGAZINE_AMMO_ID_TAG, magazineAmmoId);
                tag.putInt(MagazineItemDataAccessor.MAGAZINE_CAPACITY_TAG, magazineCapacity);
            });
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
