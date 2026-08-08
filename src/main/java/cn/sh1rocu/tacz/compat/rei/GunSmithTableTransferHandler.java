package cn.sh1rocu.tacz.compat.rei;

import cn.sh1rocu.tacz.compat.rei.display.GunSmithTableDisplay;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.inventory.GunSmithTableMenu;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import net.minecraft.network.chat.Component;

/**
 * REI's + transfer action selects the exact displayed Gunsmith Table recipe.
 *
 * <p>The table has no ghost input grid. This handler therefore performs only
 * client-side recipe navigation and deliberately does not move stacks, craft,
 * or send a packet. The existing Gunsmith Table button still invokes the
 * server-authoritative recipe/filter/material transaction.</p>
 */
public final class GunSmithTableTransferHandler implements TransferHandler {
    @Override
    public double getPriority() {
        // Win over broad generic transfer handlers: this table has no recipe
        // slots that a generic handler could safely populate.
        return 100.0d;
    }

    @Override
    public ApplicabilityResult checkApplicable(Context context) {
        if (!(context.getContainerScreen() instanceof GunSmithTableScreen)
                || !(context.getMenu() instanceof GunSmithTableMenu menu)
                || !(context.getDisplay() instanceof GunSmithTableDisplay display)
                || !display.getBlockId().equals(menu.getBlockId())) {
            return ApplicabilityResult.createNotApplicable();
        }
        return ApplicabilityResult.createApplicable();
    }

    @Override
    public Result handle(Context context) {
        if (!(context.getContainerScreen() instanceof GunSmithTableScreen screen)
                || !(context.getMenu() instanceof GunSmithTableMenu menu)
                || !(context.getDisplay() instanceof GunSmithTableDisplay display)
                || !display.getBlockId().equals(menu.getBlockId())) {
            return Result.createNotApplicable();
        }
        if (!context.isActuallyCrafting()) {
            // Preview pass used to decide whether REI should enable its +
            // button. Do not mutate the table until the player clicks it.
            return Result.createSuccessful().tooltip(
                    Component.translatable("jei.tacz.gun_smith_table.transfer.lock"));
        }
        if (!screen.selectRecipeFromViewer(display.getRecipe().getId())) {
            return Result.createFailed(Component.translatable("jei.tacz.gun_smith_table.transfer.unavailable"))
                    .blocksFurtherHandling(false);
        }
        // Return to the table after selection so the player immediately sees
        // the exact result, materials, counts, and existing server craft button.
        return Result.createSuccessful()
                .tooltip(Component.translatable("jei.tacz.gun_smith_table.transfer.lock"))
                .blocksFurtherHandling(true);
    }
}
