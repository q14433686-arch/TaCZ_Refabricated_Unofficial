package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.test.mixin.InventoryOrigin;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import java.util.*;

/** Move the real pointer onto inventory slots. The observer only records lines from the tooltip
 * being drawn; calling addText or constructing a tooltip directly is not a substitute for a hover. */
public final class TooltipSmoke {
    private record Sample(String id, List<String> expected) {
        ItemStack stack() { return id.equals("tacz:gun_display_case") ? new ItemStack(ModItems.GUN_DISPLAY_CASE)
                : AttachmentItemBuilder.create().setId(Identifier.parse(id)).build(); }
    }
    private static final List<Sample> SAMPLES = List.of(
            new Sample("tacz:scope_xm157", List.of("attachment.rangefinder","attachment.fire_control")),
            new Sample("tacz:scope_mk5hd", List.of("attachment.ammo_counter")),
            new Sample("tacz:scope_standard_8x", List.of()),
            new Sample("port_test:less_flash", List.of("attachment.flash_hider.decrease","attachment.smoke_hider.increase")),
            new Sample("port_test:more_flash", List.of("attachment.flash_hider.increase","attachment.smoke_hider.decrease")),
            new Sample("tacz:gun_display_case", List.of("gun_display_case")));
    private static Sample watching;
    private static List<String> lines;
    private static boolean registered;
    public static void drawn(Identifier id, List<Component> components) {
        if (watching != null && watching.id.equals(id.toString())) lines=components.stream().map(Component::getString).toList();
    }
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        String originalLanguage=ctx.computeOnClient(mc->mc.getLanguageManager().getSelected());
        var originalMode=world.getServer().computeOnServer(s->s.getPlayerList().getPlayers().getFirst().gameMode.getGameModeForPlayer());
        var original=world.getServer().computeOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();var inv=p.getInventory();
            p.setGameMode(GameType.SURVIVAL);var old=new ArrayList<ItemStack>();
            for(int i=0;i<SAMPLES.size();i++){old.add(inv.getItem(9+i).copy());inv.setItem(9+i,SAMPLES.get(i).stack());}p.inventoryMenu.broadcastChanges();return old;});
        try {
            ctx.runOnClient(mc->{if (!registered) {
                registered=true; ItemTooltipCallback.EVENT.register((stack,context,flag,components)->{
                    if (watching!=null && stack.is(ModItems.GUN_DISPLAY_CASE) && watching.id.equals("tacz:gun_display_case"))
                        lines=components.stream().map(Component::getString).toList();
                });
            }});
            ctx.waitFor(mc->!mc.player.isCreative() && mc.player.getInventory().getItem(14).is(ModItems.GUN_DISPLAY_CASE));
            for (String lang : List.of("en_us","zh_cn")) {
                language(ctx,lang); ctx.getInput().pressKey(o->o.keyInventory);ctx.waitForScreen(InventoryScreen.class);
                for(int i=0;i<SAMPLES.size();++i) {
                    final int index=i; var sample=SAMPLES.get(i); ctx.getInput().setCursorPos(2,2);ctx.waitTicks(1);
                    var point=ctx.computeOnClient(mc->{var screen=(InventoryScreen)mc.screen;var origin=(InventoryOrigin)screen;
                        var slot=screen.getMenu().getSlot(9+index);var w=mc.getWindow();
                        watching=sample;lines=null;
                        return new double[]{(origin.tacz$left()+slot.x+8)*w.getWidth()/(double)screen.width,
                                (origin.tacz$top()+slot.y+8)*w.getHeight()/(double)screen.height};});
                    ctx.getInput().setCursorPos(point[0],point[1]); ctx.waitFor(mc->lines!=null);
                    ctx.runOnClient(mc->{for(String key:SAMPLES.stream().flatMap(it->it.expected.stream()).distinct().toList()) {
                        String full="tooltip.tacz."+key, localized=Component.translatable(full).getString();long count=lines.stream().filter(localized::equals).count();
                        if (localized.equals(full) || count!=(sample.expected.contains(key)?1:0)) throw new AssertionError(lang+" "+sample.id+": "+key+" in "+lines);
                    }});
                    ctx.takeScreenshot("tacz_hover_"+lang+"_"+Identifier.parse(sample.id).getPath());
                }
                ctx.runOnClient(mc->watching=null);ctx.setScreen(()->null);
            }
            GunMod.LOGGER.info("PORT_HOVER: six inventory slots, both en_us and zh_cn, observed expected conditional tooltip lines");
        } finally {
            ctx.runOnClient(mc->watching=null);ctx.setScreen(()->null);language(ctx,originalLanguage);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();for(int i=0;i<original.size();i++) p.getInventory().setItem(9+i,original.get(i));
                p.setGameMode(originalMode);p.inventoryMenu.broadcastChanges();});
        }
    }
    private static void language(ClientGameTestContext ctx,String code) {
        if(ctx.computeOnClient(mc->code.equals(mc.getLanguageManager().getSelected())))return;
        var done=ctx.computeOnClient(mc->{mc.getLanguageManager().setSelected(code);return mc.reloadResourcePacks();});
        ctx.waitFor(mc->done.isDone() && mc.getOverlay()==null,600);done.join();
    }
}
