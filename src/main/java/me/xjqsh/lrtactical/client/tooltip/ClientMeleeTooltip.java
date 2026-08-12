package me.xjqsh.lrtactical.client.tooltip;

import me.xjqsh.lrtactical.api.LrTacticalAPI;
import me.xjqsh.lrtactical.inventory.tooltip.MeleeTooltip;

public final class ClientMeleeTooltip extends AbstractClientItemTooltip {
    public ClientMeleeTooltip(MeleeTooltip tooltip) {
        LrTacticalAPI.getMeleeIndex(tooltip.stack()).ifPresent(index ->
                build(index.getTooltip(), index.getData().getTooltipLines()));
    }
}
