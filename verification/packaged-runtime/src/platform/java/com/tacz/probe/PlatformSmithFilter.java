package com.tacz.probe;

import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.gui.components.GunPackList;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/** Real synchronized default recipes and screen/widget callbacks; does not craft or alter server inventory. */
final class PlatformSmithFilter {
    static void verify(ClientGameTestContext ctx) {
        var item=ctx.computeOnClient(mc->mc.player.getMainHandItem().copy());
        boolean auto=ctx.computeOnClient(mc->RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER.get());
        try {
            ctx.runOnClient(mc->{
                RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER.set(true);
                // An attachment can filter gun results, but an ammo-only table has none.
                mc.player.setItemInHand(InteractionHand.MAIN_HAND,AttachmentItemBuilder.create().setId(Identifier.parse("tacz:sight_sro")).build());
                var menu=new GunSmithTableMenu(0,mc.player.getInventory(),Identifier.parse("tacz:ammo_workbench"));
                var screen=new GunSmithTableScreen(menu,mc.player.getInventory(),Component.literal("Port filter probe"));
                mc.setScreen(screen);
                if(!((GunPackList)field(screen,"filterList")).isByHandSelected() || ((List<?>)field(screen,"emptyByHandFilterMessage")).isEmpty()
                        || !((Map<?,?>)field(screen,"recipes")).isEmpty())throw new AssertionError("Empty held-item filter did not show its recovery hint");
                if(clearButton(screen)==null)throw new AssertionError("Missing held-filter recovery widget");
            });
            ctx.waitTick();ctx.takeScreenshot("tacz_platform_smith_filter_empty");
            ctx.runOnClient(mc->{
                var screen=(GunSmithTableScreen)mc.screen;
                clearButton(screen).onPress(new KeyEvent(257,0,0));
                if(((GunPackList)field(screen,"filterList")).isByHandSelected() || !((List<?>)field(screen,"emptyByHandFilterMessage")).isEmpty()
                        || ((Map<?,?>)field(screen,"recipes")).isEmpty() || field(screen,"selectedRecipe")==null || clearButton(screen)!=null)
                    throw new AssertionError("Recovery widget failed to clear held filter and restore synchronized recipes");
            });
            ctx.waitTick();ctx.takeScreenshot("tacz_platform_smith_filter_cleared");
            System.out.println("PORT_PLATFORM_SMITH_FILTER: actual ammo workbench with held attachment showed empty-state hint and clear widget; native button callback cleared filter and restored synchronized recipes/selection");
        } finally {
            ctx.runOnClient(mc->{mc.setScreen(null);mc.player.setItemInHand(InteractionHand.MAIN_HAND,item);RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER.set(auto);});
        }
    }
    private static Button clearButton(GunSmithTableScreen screen){return screen.children().stream().filter(e->e instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals("gui.tacz.gun_smith_table.filter.hand.clear")).map(e->(Button)e).findFirst().orElse(null);}
    private static Object field(Object instance,String name){try{Field f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(instance);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private PlatformSmithFilter(){}
}
