package me.xjqsh.lrtactical.client.tooltip;

import me.xjqsh.lrtactical.api.LrTacticalAPI;
import me.xjqsh.lrtactical.inventory.tooltip.ConsumableTooltip;

public final class ClientConsumableTooltip extends AbstractClientItemTooltip {
    public ClientConsumableTooltip(ConsumableTooltip tooltip) {
        LrTacticalAPI.getConsumableIndex(tooltip.stack()).ifPresent(index ->
                build(index.getTooltip(), index.getData().getTooltipLines()));
    }
}
