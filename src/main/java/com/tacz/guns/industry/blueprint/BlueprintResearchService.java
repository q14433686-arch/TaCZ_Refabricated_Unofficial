package com.tacz.guns.industry.blueprint;

import com.tacz.guns.industry.item.IndustryItemDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The single server-side authority for archive -> factory-template conversion. */
public final class BlueprintResearchService {
    private BlueprintResearchService() {
    }

    public static boolean isArchive(ItemStack stack) {
        return stack.getItem() instanceof IndustryItemDataAccessor data
                && "blueprint".equals(data.getPartKind(stack))
                && IndustryItemDataAccessor.BLUEPRINT_ARCHIVE.equals(data.getBlueprintState(stack));
    }

    public static boolean isProductionTemplate(ItemStack stack) {
        return stack.getItem() instanceof IndustryItemDataAccessor data && data.isProductionBlueprint(stack);
    }

    /**
     * Returns an independently-owned production template. The archive is not
     * mutated in-place: the caller consumes it only after all machine output
     * checks pass, so a full output slot can never delete a discovery.
     */
    public static ItemStack study(ServerPlayer player, ItemStack archive) {
        if (!isArchive(archive) || !(archive.getItem() instanceof IndustryItemDataAccessor data)) {
            return ItemStack.EMPTY;
        }
        String platform = data.getPlatform(archive);
        if (platform.isBlank()) {
            return ItemStack.EMPTY;
        }
        BlueprintKnowledge.learn(player, platform);
        ItemStack template = archive.copyWithCount(1);
        data.setBlueprintState(template, IndustryItemDataAccessor.BLUEPRINT_PRODUCTION);
        return template;
    }
}
