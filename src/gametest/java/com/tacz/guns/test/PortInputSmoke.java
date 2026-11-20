package com.tacz.guns.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.builder.*;
import com.tacz.guns.client.gui.*;
import com.tacz.guns.client.input.*;
import com.tacz.guns.config.client.*;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Drive the real keyboard/mouse callbacks, not the actions they are supposed to reach. */
final class PortInputSmoke {
    private static IClientPlayerGunOperator op(Minecraft mc) { return IClientPlayerGunOperator.fromLocalPlayer(mc.player); }
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var mappings = List.of(AimKey.AIM_KEY, RefitKey.REFIT_KEY, ReloadKey.RELOAD_KEY, GunInputHandler.UNLOAD_KEY);
        var old = ctx.computeOnClient(mc -> mappings.stream().map(KeyMappingHelper::getBoundKeyOf).toList());
        boolean hold = ctx.computeOnClient(mc -> KeyConfig.HOLD_TO_AIM.get());
        boolean filter = ctx.computeOnClient(mc -> RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER.get());
        var saved = world.getServer().computeOnServer(s -> {
            var inv = s.getPlayerList().getPlayers().getFirst().getInventory();
            var slots = new ArrayList<ItemStack>();
            for (int i = 0; i < inv.getContainerSize(); i++) slots.add(inv.getItem(i).copy());
            return slots;
        });
        try {
            ctx.runOnClient(mc -> {
                AimKey.AIM_KEY.setKey(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_J));
                RefitKey.REFIT_KEY.setKey(InputConstants.Type.MOUSE.getOrCreate(4));
                ReloadKey.RELOAD_KEY.setKey(InputConstants.Type.MOUSE.getOrCreate(5));
                GunInputHandler.UNLOAD_KEY.setKey(InputConstants.Type.MOUSE.getOrCreate(5));
                KeyMapping.resetMapping(); KeyConfig.HOLD_TO_AIM.set(false); op(mc).aim(false);
            });
            equip(world, GunItemBuilder.create().setId(Identifier.parse("port_test:scripted")).setAmmoCount(3).setAmmoInBarrel(true).build());
            ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun g && g.getGunId(mc.player.getMainHandItem()).equals(Identifier.parse("port_test:scripted")));
            ctx.waitTicks(40);
            ctx.getInput().pressKey(AimKey.AIM_KEY); ctx.waitFor(mc -> op(mc).isAim());
            ctx.getInput().pressKey(AimKey.AIM_KEY); ctx.waitFor(mc -> !op(mc).isAim());
            ctx.runOnClient(mc -> KeyConfig.HOLD_TO_AIM.set(true));
            ctx.getInput().holdKey(AimKey.AIM_KEY); ctx.waitFor(mc -> op(mc).isAim());
            ctx.getInput().releaseKey(AimKey.AIM_KEY); ctx.waitFor(mc -> !op(mc).isAim());
            ctx.getInput().pressMouse(4); ctx.waitForScreen(GunRefitScreen.class);
            ctx.getInput().pressMouse(4); ctx.waitFor(mc -> mc.screen == null);
            // Client ADS can already be zero while the server is still lowering the gun or drawing it.
            ClientProbe.waitForServer(ctx, world, s -> {
                var p = s.getPlayerList().getPlayers().getFirst(); var state = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(p);
                var data = state.getDataHolder();
                return !data.isAiming && data.aimingProgress == 0 && data.currentGunItem != null && data.currentGunItem.get() == p.getMainHandItem()
                        && state.getSynDrawCoolDown() == 0;
            });
            ctx.waitFor(mc -> !op(mc).getDataHolder().clientStateLock && op(mc).getDataHolder().clientAimingProgress == 0
                    && com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(mc.player).getSynDrawCoolDown() == 0 && com.tacz.guns.util.InputExtraCheck.isInGame());
            ctx.getInput().pressMouse(5);
            try { ctx.waitFor(mc -> count(mc)==2 && op(mc).getDataHolder().autoReloadSuppressed); }
            catch(AssertionError failure){throw new AssertionError(ctx.computeOnClient(mc -> "Rebound unload: client magazine="+count(mc)+", suppressed="+op(mc).getDataHolder().autoReloadSuppressed+", lock="+op(mc).getDataHolder().clientStateLock),failure);}
            world.getServer().runOnServer(s -> {
                var stack = s.getPlayerList().getPlayers().getFirst().getMainHandItem();
                if (!(stack.getItem() instanceof IGun gun) || gun.getCurrentAmmoCount(stack) != 2)
                    throw new AssertionError("Client and authoritative magazine disagree after unload");
            });
            ctx.runOnClient(mc -> { GunInputHandler.UNLOAD_KEY.setKey(InputConstants.UNKNOWN); KeyMapping.resetMapping(); });
            ctx.waitTicks(30);
            ctx.getInput().holdAlt(); ctx.getInput().pressMouse(5); ctx.getInput().releaseAlt(); ctx.waitFor(mc -> count(mc)==1);
            world.getServer().runOnServer(s -> {
                var stack = s.getPlayerList().getPlayers().getFirst().getMainHandItem();
                if (!(stack.getItem() instanceof IGun gun) || gun.getCurrentAmmoCount(stack) != 1)
                    throw new AssertionError("Client and authoritative magazine disagree after unload");
            });
            equip(world, GunItemBuilder.create().setId(Identifier.parse("tacz:ak47")).setAmmoCount(1).setAmmoInBarrel(true).build());
            world.getServer().runOnServer(s -> {
                var p = s.getPlayerList().getPlayers().getFirst();
                p.getInventory().setItem(12, AmmoItemBuilder.create().setId(Identifier.parse("tacz:762x39")).setCount(8).build());
                p.inventoryMenu.broadcastChanges();
            });
            ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun g && g.getGunId(mc.player.getMainHandItem()).equals(Identifier.parse("tacz:ak47")));
            ctx.waitTicks(40); ctx.getInput().pressMouse(5); ctx.waitFor(mc -> count(mc) > 1, 200);
            // The hand filter applies only to TaCZ items. Use a genuine ammo stack with no matching recipe.
            equip(world, AmmoItemBuilder.create().setId(Identifier.parse("port_test:no_matching_ammo")).build());
            ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IAmmo);
            ctx.runOnClient(mc -> RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER.set(true));
            ctx.setScreen(() -> { var inv = Minecraft.getInstance().player.getInventory();
                return new GunSmithTableScreen(new GunSmithTableMenu(0, inv, DefaultAssets.DEFAULT_BLOCK_ID), inv, Component.literal("Filter test")); });
            ctx.waitForScreen(GunSmithTableScreen.class); ctx.waitTicks(2);
            PackClientSmoke.saveFrame(ctx, "tacz_hand_filter_empty");
            ctx.clickScreenButton("gui.tacz.gun_smith_table.filter.hand.clear");
            ctx.waitTicks(2); PackClientSmoke.saveFrame(ctx,"tacz_hand_filter_cleared");
            ctx.runOnClient(mc -> { var screen=(GunSmithTableScreen)mc.screen;
                if (screen.children().stream().noneMatch(w -> w instanceof com.tacz.guns.client.gui.components.smith.ResultButton))
                    throw new AssertionError("Clearing the hand filter left the real recipe list empty"); });
        } finally {
            ctx.getInput().releaseKey(AimKey.AIM_KEY); ctx.getInput().releaseAlt(); ctx.setScreen(() -> null);
            ctx.runOnClient(mc -> { for(int i=0;i<mappings.size();i++) mappings.get(i).setKey(old.get(i)); KeyMapping.resetMapping();
                KeyConfig.HOLD_TO_AIM.set(hold); RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER.set(filter); });
            world.getServer().runOnServer(s -> {var p = s.getPlayerList().getPlayers().getFirst();
                for (int i = 0; i < saved.size(); ++i) p.getInventory().setItem(i,saved.get(i));
                p.inventoryMenu.broadcastChanges(); });
        }
    }
    private static void equip(TestSingleplayerContext world, ItemStack stack) {
        world.getServer().runOnServer(s -> {var p=s.getPlayerList().getPlayers().getFirst(); p.setItemInHand(InteractionHand.MAIN_HAND,stack); p.inventoryMenu.broadcastChanges();});
    }
    private static int count(Minecraft mc) {
        var stack=mc.player.getMainHandItem(); return stack.getItem() instanceof IGun g ? g.getCurrentAmmoCount(stack) : -1;
    }
}
