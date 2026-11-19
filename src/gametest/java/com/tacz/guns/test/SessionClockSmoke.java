package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.util.time.GunTime;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

final class SessionClockSmoke {
    static void pauseAndResume(ClientGameTestContext context) {
        // Let Minecraft decide whether this screen pauses the world. Never toggle the TaCZ clock in the test.
        context.setScreen(() -> new Screen(Component.literal("GameTest pause")) {
            @Override public boolean isPauseScreen() { return true; }
        });
        long frozen;
        try {
            context.waitFor(mc -> mc.isPaused() && !GunTime.shouldTick());
            frozen = context.computeOnClient(mc -> GunTime.nowNanos());
            context.waitTicks(5);
            context.runOnClient(mc -> {
                if (GunTime.nowNanos() != frozen) throw new AssertionError("Gameplay clock advanced in a paused world");
                checkLuaClock(new GunAnimationStateContext(), frozen);
                checkLuaClock(new ModernKineticGunScriptAPI(), frozen);
            });
        } finally {
            context.setScreen(() -> null);
        }
        context.waitFor(mc -> !mc.isPaused() && GunTime.shouldTick() && GunTime.nowNanos() > frozen);
        GunMod.LOGGER.info("PORT_SMOKE_CLIENT: integrated pause/resume and both pack clocks passed");
    }

    // Invoke through the same LuaJ Java binding used by animation and logic scripts.
    private static void checkLuaClock(Object host, long frozenNs) {
        var api = CoerceJavaToLua.coerce(host);
        if (api.method("getCurrentTimestamp").checklong() != frozenNs / 1_000_000)
            throw new AssertionError("Pack script did not see the paused clock");
        long before = System.currentTimeMillis();
        long wall = api.method("getRealSystemTimestamp").checklong();
        if (wall < before || wall > System.currentTimeMillis()) throw new AssertionError("Expected Unix milliseconds, got " + wall);
    }
}
