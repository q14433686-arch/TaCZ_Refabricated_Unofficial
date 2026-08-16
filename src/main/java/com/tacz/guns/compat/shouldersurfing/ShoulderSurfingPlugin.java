package com.tacz.guns.compat.shouldersurfing;

import com.github.exopandora.shouldersurfing.api.client.event.ComputePlayerAimStateEvent;
import com.github.exopandora.shouldersurfing.api.event.IEventBus;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingPlugin;
import com.tacz.guns.api.item.IGun;

/**
 * Shoulder Surfing Reloaded 5.x plugin.
 *
 * <p>The 1.21.1 integration used {@code IShoulderSurfingRegistrar} and an
 * adaptive-item callback. API 5.x replaced that registrar with an event bus;
 * marking a held TACZ gun as an aiming-capable item is the direct equivalent.</p>
 */
public final class ShoulderSurfingPlugin implements IShoulderSurfingPlugin {
    @Override
    public void register(IEventBus eventBus) {
        eventBus.register((ComputePlayerAimStateEvent event) -> {
            if (event.getEntity().getMainHandItem().getItem() instanceof IGun) {
                event.setResult(true);
            }
        });
    }
}
