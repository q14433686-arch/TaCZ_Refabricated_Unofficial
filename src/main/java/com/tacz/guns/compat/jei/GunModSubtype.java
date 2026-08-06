package com.tacz.guns.compat.jei;

import com.tacz.guns.api.item.*;
import com.tacz.guns.industry.item.IndustryItemDataAccessor;
import com.tacz.guns.industry.magazine.IMagazine;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import net.minecraft.world.item.ItemStack;

public class GunModSubtype {
    public static ISubtypeInterpreter<ItemStack> getAmmoSubtype() {
        return (stack, context) -> {
            if (stack.getItem() instanceof IAmmo iAmmo) {
                return iAmmo.getAmmoId(stack).toString();
            }
            return null;
        };
    }

    public static ISubtypeInterpreter<ItemStack> getGunSubtype() {
        return (stack, context) -> {
            if (stack.getItem() instanceof IGun iGun) {
                return iGun.getGunId(stack).toString();
            }
            return null;
        };
    }

    public static ISubtypeInterpreter<ItemStack> getAttachmentSubtype() {
        return (stack, context) -> {
            if (stack.getItem() instanceof IAttachment iAttachment) {
                return iAttachment.getAttachmentId(stack).toString();
            }
            return null;
        };
    }

    public static ISubtypeInterpreter<ItemStack> getTableSubType() {
        return (stack, context) -> {
            if (stack.getItem() instanceof IBlock iBlock) {
                return iBlock.getBlockId(stack).toString();
            }
            return null;
        };
    }

    public static ISubtypeInterpreter<ItemStack> getMagazineSubtype() {
        return (stack, context) -> {
            if (stack.getItem() instanceof IMagazine magazine) {
                return magazine.getMagazineFamily(stack) + "|" + magazine.getAmmoId(stack)
                        + "|" + magazine.getCapacity(stack) + "|" + magazine.getAmmoCount(stack);
            }
            return null;
        };
    }

    public static ISubtypeInterpreter<ItemStack> getIndustrySubtype() {
        return (stack, context) -> {
            if (stack.getItem() instanceof IndustryItemDataAccessor part) {
                return part.getPlatform(stack) + "|" + part.getPartKind(stack)
                        + "|" + part.getCartridgeCaliber(stack) + "|" + part.getCartridgeAmmoId(stack)
                        + "|" + part.getProjectileType(stack) + "|" + part.getDieTargetKind(stack)
                        + "|" + part.getBlueprintTier(stack) + "|" + part.getBlueprintRole(stack)
                        + "|" + part.getActionProfile(stack) + "|" + part.getToolingScope(stack);
            }
            return null;
        };
    }

    public static ISubtypeInterpreter<ItemStack> getAmmoBoxSubtype() {
        return (stack, context) -> {
            if (stack.getItem() instanceof IAmmoBox iAmmoBox) {
                if (iAmmoBox.isAllTypeCreative(stack)) {
                    return "all_type_creative";
                }
                if (iAmmoBox.isCreative(stack)) {
                    return "creative";
                }
                return String.format("level_%d", iAmmoBox.getAmmoLevel(stack));
            }
            return null;
        };
    }
}
