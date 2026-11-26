package cn.sh1rocu.tacz.compat.rei;

import cn.sh1rocu.tacz.compat.rei.display.GunSmithTableDisplay;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import net.minecraft.network.chat.Component;

final class GunSmithTableTransferHandler implements TransferHandler {
    @Override public ApplicabilityResult checkApplicable(Context context) {
        return context.getContainerScreen() instanceof GunSmithTableScreen && context.getDisplay() instanceof GunSmithTableDisplay
                ? ApplicabilityResult.createApplicable() : ApplicabilityResult.createNotApplicable();
    }

    @Override public Result handle(Context context) {
        if (!(context.getContainerScreen() instanceof GunSmithTableScreen screen) || !(context.getDisplay() instanceof GunSmithTableDisplay display))
            return Result.createNotApplicable();
        if (!screen.getMenu().selectFromViewer(display.getRecipe().getId(), context.isActuallyCrafting()))
            return Result.createFailed(Component.translatable("jei.tacz.gun_smith_table.recipe_unavailable"));
        // REI restores the container screen when a blocking handler requests a return.
        return Result.createSuccessful().blocksFurtherHandling();
    }
}
