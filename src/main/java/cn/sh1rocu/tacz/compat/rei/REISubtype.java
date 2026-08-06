package cn.sh1rocu.tacz.compat.rei;

import com.tacz.guns.api.item.*;
import com.tacz.guns.industry.item.IndustryItemDataAccessor;
import com.tacz.guns.industry.magazine.IMagazine;
import me.shedaniel.rei.api.common.entry.comparison.EntryComparator;
import net.minecraft.world.item.ItemStack;

public class REISubtype {
    public static EntryComparator<ItemStack> getAmmoSubtype() {
        return (context, stack) -> {
            if (stack.getItem() instanceof IAmmo iAmmo) {
                return iAmmo.getAmmoId(stack).hashCode();
            }
            return 0;
        };
    }

    public static EntryComparator<ItemStack> getGunSubtype() {
        return (context, stack) -> {
            if (stack.getItem() instanceof IGun iGun) {
                return iGun.getGunId(stack).hashCode();
            }
            return 0;
        };
    }

    public static EntryComparator<ItemStack> getAttachmentSubtype() {
        return (context, stack) -> {
            if (stack.getItem() instanceof IAttachment iAttachment) {
                return iAttachment.getAttachmentId(stack).hashCode();
            }
            return 0;
        };
    }

    public static EntryComparator<ItemStack> getTableSubType() {
        return (context, stack) -> {
            if (stack.getItem() instanceof IBlock iBlock) {
                return iBlock.getBlockId(stack).hashCode();
            }
            return 0;
        };
    }

    public static EntryComparator<ItemStack> getMagazineSubtype() {
        return (context, stack) -> {
            if (stack.getItem() instanceof IMagazine magazine) {
                return java.util.Objects.hash(magazine.getMagazineFamily(stack), magazine.getAmmoId(stack),
                        magazine.getCapacity(stack), magazine.getAmmoCount(stack));
            }
            return 0;
        };
    }

    public static EntryComparator<ItemStack> getIndustrySubtype() {
        return (context, stack) -> {
            if (stack.getItem() instanceof IndustryItemDataAccessor part) {
                String partKind = part.getPartKind(stack);
                // Keep this exactly aligned with JEI: action profile/scope are
                // descriptive provenance, while a blueprint's tier/role are
                // actual document identity. This lets a legacy die/component
                // and its current recipe representation share one REI edge.
                boolean blueprint = "blueprint".equals(partKind);
                return java.util.Objects.hash(part.getPlatform(stack), partKind,
                        part.getCartridgeCaliber(stack), part.getCartridgeAmmoId(stack),
                        part.getProjectileType(stack), part.getDieTargetKind(stack),
                        blueprint ? part.getBlueprintTier(stack) : "",
                        blueprint ? part.getBlueprintRole(stack) : "");
            }
            return 0;
        };
    }

    public static EntryComparator<ItemStack> getAmmoBoxSubtype() {
        return (context, stack) -> {
            if (stack.getItem() instanceof IAmmoBox iAmmoBox) {
                if (iAmmoBox.isAllTypeCreative(stack)) {
                    return "all_type_creative".hashCode();
                }
                if (iAmmoBox.isCreative(stack)) {
                    return "creative".hashCode();
                }
                return String.format("level_%d", iAmmoBox.getAmmoLevel(stack)).hashCode();
            }
            return 0;
        };
    }
}
