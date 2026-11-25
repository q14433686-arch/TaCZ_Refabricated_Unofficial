package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.builder.*;
import com.tacz.guns.client.model.papi.AmmoCountPapi;
import com.tacz.guns.client.gameplay.ClientAmmoCount;
import com.tacz.guns.client.particle.AnimationParticleDispatcher;
import com.tacz.guns.client.input.InspectKey;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.network.message.ServerMessagePackWarnings;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.CameraType;
import java.util.*;

/** Authored acceptance-pack resources, real inventory sync, inspect input and render submissions. */
final class CoreCompletionSmoke {
    private static Identifier id(String path){return Identifier.parse(path);}
    static void run(ClientGameTestContext ctx,TestSingleplayerContext world){
        var saved=world.getServer().computeOnServer(s->{var inv=s.getPlayerList().getPlayers().getFirst().getInventory();
            var items=new ArrayList<ItemStack>();for(int i=0;i<inv.getContainerSize();i++)items.add(inv.getItem(i).copy());return items;});
        var camera=ctx.computeOnClient(mc->mc.options.getCameraType());
        boolean hidden=ctx.computeOnClient(mc->OtherConfig.HIDE_GUN_PACK_COMPATIBILITY_WARNING.get());
        var inspectBinding=ctx.computeOnClient(mc->net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(InspectKey.INSPECT_KEY));
        try{
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();var inv=p.getInventory();
                for(int i=0;i<inv.getContainerSize();i++)inv.setItem(i,ItemStack.EMPTY);
                inv.setItem(inv.getSelectedSlot(),GunItemBuilder.create().setId(id("port_acceptance:inventory")).setAmmoCount(3).build());
                inv.setItem(10,AmmoItemBuilder.create().setId(id("tacz:762x39")).setCount(17).build());p.inventoryMenu.broadcastChanges();});
            ctx.runOnClient(mc->mc.options.setCameraType(CameraType.FIRST_PERSON));
            awaitCount(ctx,"port_acceptance:inventory",17);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();var box=new ItemStack(ModItems.AMMO_BOX);
                var api=(IAmmoBox)box.getItem();api.setAmmoId(box,id("tacz:762x39"));api.setAmmoCount(box,9);p.getInventory().setItem(11,box);p.inventoryMenu.broadcastChanges();});
            awaitCount(ctx,"port_acceptance:inventory",26);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.getInventory().setItem(10,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();});
            awaitCount(ctx,"port_acceptance:inventory",9);
            ctx.waitTicks(30); // complete draw animation before the real inspect input
            long before=ctx.computeOnClient(mc->AnimationParticleDispatcher.submitted());
            long customBefore=ctx.computeOnClient(mc->com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager.getInstance().spawned());
            ctx.runOnClient(mc -> { InspectKey.INSPECT_KEY.setKey(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(4)); net.minecraft.client.KeyMapping.resetMapping(); });
            ctx.getInput().holdKey(InspectKey.INSPECT_KEY);ctx.waitTicks(1);ctx.getInput().releaseKey(InspectKey.INSPECT_KEY);
            ctx.waitFor(mc->AnimationParticleDispatcher.submitted()>=before+5);
            ctx.waitFor(mc->com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager.getInstance().spawned()>customBefore);
            PlayerSuites.frame(ctx,"tacz_acceptance_inventory");
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.getInventory().setItem(p.getInventory().getSelectedSlot(),
                    GunItemBuilder.create().setId(id("port_acceptance:magazine")).setAmmoCount(3).build());p.inventoryMenu.broadcastChanges();});
            awaitCount(ctx,"port_acceptance:magazine",3);
            ctx.runOnClient(mc->OtherConfig.HIDE_GUN_PACK_COMPATIBILITY_WARNING.set(false));
            world.getServer().runOnServer(s->ServerPlayNetworking.send(s.getPlayerList().getPlayers().getFirst(),new ServerMessagePackWarnings(List.of("legacy_acceptance.zip"))));
            ctx.waitFor(mc->com.tacz.guns.client.event.PackCompatibilityNotice.announcedPacks().contains("legacy_acceptance.zip"));
            ctx.runOnClient(mc->mc.player.connection.sendCommand("tacz_pack_compat dismiss"));
            ctx.waitFor(mc->OtherConfig.HIDE_GUN_PACK_COMPATIBILITY_WARNING.get());
            GunMod.LOGGER.info("PORT_CORE: inventory/model counts 17/26/9, magazine 3, inspect particle render dispatch and warning packet/client dismissal passed");
        }finally{
            ctx.getInput().releaseKey(InspectKey.INSPECT_KEY);
            ctx.runOnClient(mc -> { InspectKey.INSPECT_KEY.setKey(inspectBinding); net.minecraft.client.KeyMapping.resetMapping(); });
            ctx.runOnClient(mc->{mc.options.setCameraType(camera);OtherConfig.HIDE_GUN_PACK_COMPATIBILITY_WARNING.set(hidden);com.tacz.guns.config.ConfigPersist.saveAll();});
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();for(int i=0;i<saved.size();i++)p.getInventory().setItem(i,saved.get(i));p.inventoryMenu.broadcastChanges();});
        }
    }
    private static void awaitCount(ClientGameTestContext ctx,String gunId,int expected){
        ctx.waitFor(mc->{var stack=mc.player.getMainHandItem();if(!(stack.getItem() instanceof IGun gun)||!gun.getGunId(stack).equals(id(gunId)))return false;
            String value=new AmmoCountPapi().apply(stack);
            if(!value.equals(Integer.toString(expected)))return false;
            if(gun.useInventoryAmmo(stack)&&ClientAmmoCount.getCached(mc.player,stack,gun)!=expected)throw new AssertionError("PAPI differs from HUD cache");
            return true;});
    }
}
