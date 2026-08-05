package com.tacz.guns.industry.recipe;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Server-authoritative definition for one dedicated cartridge-assembly-machine
 * operation.
 *
 * <p>The dedicated machine deliberately owns the final four-input operation:
 * case, projectile, primer and propellant each have an explicit GUI slot. It
 * is not a Create Basin recipe and therefore cannot be invalidated by Basin
 * recipe-trie selection or Depot's one-workpiece limitation. New calibres and
 * projectile types are supplied as data files rather than Java branches.</p>
 */
public final class CartridgeAssemblyDefinition {
    @SerializedName("case_item")
    private Identifier caseItem;
    @SerializedName("case_caliber")
    private String caseCaliber;
    @SerializedName("case_display_name")
    private String caseDisplayName;
    @SerializedName("projectile_item")
    private Identifier projectileItem;
    @SerializedName("projectile_caliber")
    private String projectileCaliber;
    @SerializedName("projectile_type")
    private String projectileType;
    @SerializedName("projectile_display_name")
    private String projectileDisplayName;
    @SerializedName("primer_item")
    private Identifier primerItem;
    @SerializedName("propellant_item")
    private Identifier propellantItem;
    private Identifier ammo;
    private int count = 1;

    public boolean isValid() {
        return valid(caseItem) && !blank(caseCaliber)
                && valid(projectileItem) && !blank(projectileCaliber) && !blank(projectileType)
                && valid(primerItem) && valid(propellantItem) && ammo != null
                && count > 0 && count <= 99;
    }

    public boolean matches(ItemStack caseStack, ItemStack projectileStack, ItemStack primerStack, ItemStack propellantStack) {
        return matchesCase(caseStack) && matchesProjectile(projectileStack)
                && matchesPrimer(primerStack) && matchesPropellant(propellantStack);
    }

    public boolean matchesCase(ItemStack stack) {
        if (!isValid() || !matchesItem(stack, caseItem)) {
            return false;
        }
        CompoundTag tag = ItemNbtUtils.getTag(stack);
        return "ammunition".equals(tag.getStringOr("IndustryPlatform", ""))
                && "case".equals(tag.getStringOr("IndustryPartKind", ""))
                && caseCaliber.equals(tag.getStringOr("CartridgeCaliber", ""));
    }

    public boolean matchesProjectile(ItemStack stack) {
        if (!isValid() || !matchesItem(stack, projectileItem)) {
            return false;
        }
        CompoundTag tag = ItemNbtUtils.getTag(stack);
        return "ammunition".equals(tag.getStringOr("IndustryPlatform", ""))
                && "projectile".equals(tag.getStringOr("IndustryPartKind", ""))
                && projectileCaliber.equals(tag.getStringOr("CartridgeCaliber", ""))
                && projectileType.equals(tag.getStringOr("ProjectileType", ""));
    }

    public boolean matchesPrimer(ItemStack stack) {
        return isValid() && matchesItem(stack, primerItem);
    }

    public boolean matchesPropellant(ItemStack stack) {
        return isValid() && matchesItem(stack, propellantItem);
    }

    public ItemStack createResult() {
        return AmmoItemBuilder.create().setId(ammo).setCount(count).build();
    }

    public ItemStack createCasePreview() {
        return configuredPreview(caseItem, caseTag());
    }

    public ItemStack createProjectilePreview() {
        return configuredPreview(projectileItem, projectileTag());
    }

    public ItemStack createPrimerPreview() {
        return plainPreview(primerItem);
    }

    public ItemStack createPropellantPreview() {
        return plainPreview(propellantItem);
    }

    public Identifier getAmmo() {
        return ammo;
    }

    private CompoundTag caseTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("IndustryPlatform", "ammunition");
        tag.putString("IndustryPartKind", "case");
        tag.putString("CartridgeCaliber", caseCaliber);
        if (!blank(caseDisplayName)) {
            tag.putString("IndustryDisplayName", caseDisplayName);
        }
        return tag;
    }

    private CompoundTag projectileTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("IndustryPlatform", "ammunition");
        tag.putString("IndustryPartKind", "projectile");
        tag.putString("CartridgeCaliber", projectileCaliber);
        tag.putString("ProjectileType", projectileType);
        if (!blank(projectileDisplayName)) {
            tag.putString("IndustryDisplayName", projectileDisplayName);
        }
        return tag;
    }

    private static ItemStack configuredPreview(Identifier id, CompoundTag tag) {
        ItemStack stack = plainPreview(id);
        if (!stack.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }

    private static ItemStack plainPreview(Identifier id) {
        Item item = valid(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static boolean matchesItem(ItemStack stack, Identifier id) {
        Item item = valid(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
        return item != null && item != Items.AIR && !stack.isEmpty() && stack.getItem() == item;
    }

    private static boolean valid(Identifier id) {
        return id != null && BuiltInRegistries.ITEM.getValue(id) != null && BuiltInRegistries.ITEM.getValue(id) != Items.AIR;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
