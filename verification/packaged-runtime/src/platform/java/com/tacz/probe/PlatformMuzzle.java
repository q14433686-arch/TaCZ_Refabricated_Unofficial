package com.tacz.probe;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.client.particle.NativeMuzzleEffects;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Real physical shots must reach the posed muzzle locator, not merely allocate a pending Shot. */
final class PlatformMuzzle {
    static long flashes(NativeMuzzleEffects.Diagnostics d) { return d.firstFlashSubmits()+d.runtimeFirstFlashes(); }
    static long smoke(NativeMuzzleEffects.Diagnostics d) { return d.smokeBursts()+d.runtimeSmokeBursts(); }
    static void verify(ClientGameTestContext ctx, TestSingleplayerContext world, NativeMuzzleEffects.Diagnostics before) {
        ctx.waitFor(mc -> flashes(NativeMuzzleEffects.diagnostics()) > flashes(before)
                && smoke(NativeMuzzleEffects.diagnostics()) > smoke(before), 100);
        boolean flashDisabled=ctx.computeOnClient(mc -> RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.get());
        boolean smokeEnabled=ctx.computeOnClient(mc -> RenderConfig.RENDER_MUZZLE_SMOKE.get());
        try {
            ctx.waitTicks(12);
            var first=ctx.computeOnClient(mc -> {
                RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.set(true);RenderConfig.RENDER_MUZZLE_SMOKE.set(true);
                return NativeMuzzleEffects.diagnostics();
            });
            ctx.getInput().holdKey(ShootKey.SHOOT_KEY);
            try { ctx.waitFor(mc -> smoke(NativeMuzzleEffects.diagnostics()) > smoke(first), 100); }
            finally { ctx.getInput().releaseKey(ShootKey.SHOOT_KEY); }
            PlayerProbe.waitForServer(ctx,world,s -> {var item=s.getPlayerList().getPlayers().getFirst().getMainHandItem();return ((IGun)item.getItem()).getCurrentAmmoCount(item)==5;});
            ctx.runOnClient(mc -> {if(flashes(NativeMuzzleEffects.diagnostics())!=flashes(first))throw new AssertionError("Disabled renew flash still submitted at posed locator");});
            ctx.waitTicks(12);
            var second=ctx.computeOnClient(mc -> {
                RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.set(false);RenderConfig.RENDER_MUZZLE_SMOKE.set(false);
                return NativeMuzzleEffects.diagnostics();
            });
            ctx.getInput().holdKey(ShootKey.SHOOT_KEY);
            try { ctx.waitFor(mc -> flashes(NativeMuzzleEffects.diagnostics()) > flashes(second), 100); }
            finally { ctx.getInput().releaseKey(ShootKey.SHOOT_KEY); }
            PlayerProbe.waitForServer(ctx,world,s -> {var item=s.getPlayerList().getPlayers().getFirst().getMainHandItem();return ((IGun)item.getItem()).getCurrentAmmoCount(item)==4;});
            ctx.runOnClient(mc -> {if(smoke(NativeMuzzleEffects.diagnostics())!=smoke(second))throw new AssertionError("Disabled smoke still emitted at posed locator");});
            System.out.println("PORT_PLATFORM_MUZZLE: physical shots reached actual posed muzzle extraction; flash and smoke independently enabled/disabled with server ammo=6/5/4");
        } finally {
            ctx.getInput().releaseKey(ShootKey.SHOOT_KEY);
            ctx.runOnClient(mc -> {RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.set(flashDisabled);RenderConfig.RENDER_MUZZLE_SMOKE.set(smokeEnabled);});
        }
    }
    private PlatformMuzzle(){}
}
