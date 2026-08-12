package me.xjqsh.lrtactical.client.tooltip;

import me.xjqsh.lrtactical.api.LrTacticalAPI;
import me.xjqsh.lrtactical.inventory.tooltip.ThrowableTooltip;

public final class ClientThrowableTooltip extends AbstractClientItemTooltip {
    public ClientThrowableTooltip(ThrowableTooltip tooltip) {
        LrTacticalAPI.getThrowableIndex(tooltip.stack()).ifPresent(index ->
                build(index.getTooltip(), index.getData().getTooltipLines()));
    }
}
