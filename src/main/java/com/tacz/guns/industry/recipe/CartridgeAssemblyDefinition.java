package com.tacz.guns.industry.recipe;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.TimelessAPI;
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
 * case, projectile, primer and propellant each have an explicit GUI slot.
 * Definitions may atomically consume/output balanced batches rather than
 * pretending every calibre has identical one-round material cost. It is not a
 * Create Basin recipe and therefore cannot be invalidated by Basin
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
    /** Finished loose-ammo rounds produced by one explicit machine operation. */
    private int count = 1;
    @SerializedName("case_count")
    private int caseCount = 1;
    @SerializedName("projectile_count")
    private int projectileCount = 1;
    @SerializedName("primer_count")
    private int primerCount = 1;
    @SerializedName("propellant_count")
    private int propellantCount = 1;
    /** Explicit opt-in: a fired round may produce a recoverable case item. */
    @SerializedName("eject_case")
    private boolean ejectCase;
    @SerializedName("spent_case_display_name")
    private String spentCaseDisplayName;
    /** Optional player-facing source ammo name for runtime surveyed profiles. */
    @SerializedName("survey_ammo_name")
    private String surveyAmmoName;

    public boolean isValid() {
        return valid(caseItem) && !blank(caseCaliber)
                && valid(projectileItem) && !blank(projectileCaliber) && !blank(projectileType)
                && valid(primerItem) && valid(propellantItem) && ammo != null
                && positive(count) && positive(caseCount) && positive(projectileCount)
                && positive(primerCount) && positive(propellantCount)
                && (!ejectCase || !blank(spentCaseDisplayName));
    }

    public boolean matches(ItemStack caseStack, ItemStack projectileStack, ItemStack primerStack, ItemStack propellantStack) {
        return matchesCase(caseStack) && matchesProjectile(projectileStack)
                && matchesPrimer(primerStack) && matchesPropellant(propellantStack)
                && caseStack.getCount() >= caseCount
                && projectileStack.getCount() >= projectileCount
                && primerStack.getCount() >= primerCount
                && propellantStack.getCount() >= propellantCount;
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
        Identifier projectileAmmo = Identifier.tryParse(tag.getStringOr("CartridgeAmmoId", ""));
        return "ammunition".equals(tag.getStringOr("IndustryPlatform", ""))
                && "projectile".equals(tag.getStringOr("IndustryPartKind", ""))
                && projectileCaliber.equals(tag.getStringOr("CartridgeCaliber", ""))
                && projectileType.equals(tag.getStringOr("ProjectileType", ""))
                && ammo != null && ammo.equals(projectileAmmo);
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

    /**
     * Builds the physical item that is spawned after one successfully consumed
     * round.  It deliberately has a different part kind from a ready case, so
     * it cannot bypass the matching-die reconditioning step before returning
     * to the dedicated four-slot loading machine.
     */
    public ItemStack createSpentCase() {
        if (!ejectsCase()) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("IndustryPlatform", "ammunition");
        tag.putString("IndustryPartKind", "spent_case");
        tag.putString("IndustryDisplayName", spentCaseDisplayName);
        tag.putString("CartridgeCaliber", caseCaliber);
        if (ammo != null) {
            tag.putString("CartridgeAmmoId", ammo.toString());
        }
        if (!blank(surveyAmmoName)) {
            tag.putString("IndustrySurveyAmmoName", surveyAmmoName);
        }
        tag.putBoolean("SpentCartridgeCase", true);
        return applyProductStackLimit(configuredPreview(caseItem, tag));
    }

    /** True only for a definition that explicitly declares a recoverable case. */
    public boolean ejectsCase() {
        return isValid() && ejectCase && valid(caseItem) && !blank(spentCaseDisplayName);
    }

    /**
     * The configured case/projectile is one physical part per final cartridge,
     * so its slot capacity follows the actual loose-ammo product cap rather
     * than the generic registry item's old intermediary cap.
     */
    public int getProductStackLimit() {
        if (ammo == null) {
            return Item.ABSOLUTE_MAX_STACK_SIZE;
        }
        return TimelessAPI.getCommonAmmoIndex(ammo)
                .map(index -> Math.clamp(index.getStackSize(), 1, Item.ABSOLUTE_MAX_STACK_SIZE))
                .orElse(Item.ABSOLUTE_MAX_STACK_SIZE);
    }

    public ItemStack applyProductStackLimit(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.set(DataComponents.MAX_STACK_SIZE, getProductStackLimit());
        }
        return stack;
    }

    public ItemStack createCasePreview() {
        return withCount(applyProductStackLimit(configuredPreview(caseItem, caseTag())), caseCount);
    }

    public ItemStack createProjectilePreview() {
        return withCount(applyProductStackLimit(configuredPreview(projectileItem, projectileTag())), projectileCount);
    }

    public ItemStack createPrimerPreview() {
        return withCount(plainPreview(primerItem), primerCount);
    }

    public ItemStack createPropellantPreview() {
        return withCount(plainPreview(propellantItem), propellantCount);
    }

    public int getCaseCount() {
        return caseCount;
    }

    public int getProjectileCount() {
        return projectileCount;
    }

    public int getPrimerCount() {
        return primerCount;
    }

    public int getPropellantCount() {
        return propellantCount;
    }

    public Identifier getAmmo() {
        return ammo;
    }

    public String getCaseCaliber() {
        return caseCaliber == null ? "" : caseCaliber;
    }

    public String getProjectileCaliber() {
        return projectileCaliber == null ? "" : projectileCaliber;
    }

    public String getProjectileType() {
        return projectileType == null ? "" : projectileType;
    }

    private CompoundTag caseTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("IndustryPlatform", "ammunition");
        tag.putString("IndustryPartKind", "case");
        tag.putString("CartridgeCaliber", caseCaliber);
        if (ammo != null) {
            tag.putString("CartridgeAmmoId", ammo.toString());
        }
        if (!blank(caseDisplayName)) {
            tag.putString("IndustryDisplayName", caseDisplayName);
        }
        if (!blank(surveyAmmoName)) {
            tag.putString("IndustrySurveyAmmoName", surveyAmmoName);
        }
        return tag;
    }

    private CompoundTag projectileTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("IndustryPlatform", "ammunition");
        tag.putString("IndustryPartKind", "projectile");
        tag.putString("CartridgeCaliber", projectileCaliber);
        if (ammo != null) {
            tag.putString("CartridgeAmmoId", ammo.toString());
        }
        tag.putString("ProjectileType", projectileType);
        if (!blank(projectileDisplayName)) {
            tag.putString("IndustryDisplayName", projectileDisplayName);
        }
        if (!blank(surveyAmmoName)) {
            tag.putString("IndustrySurveyAmmoName", surveyAmmoName);
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

    private static ItemStack withCount(ItemStack stack, int count) {
        if (!stack.isEmpty()) {
            stack.setCount(Math.min(Math.max(count, 1), stack.getMaxStackSize()));
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

    private static boolean positive(int value) {
        return value > 0 && value <= Item.ABSOLUTE_MAX_STACK_SIZE;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
