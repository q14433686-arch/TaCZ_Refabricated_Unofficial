package com.tacz.probe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.renderer.other.HumanoidOffhandRender;
import com.tacz.guns.client.resource.pojo.display.gun.LayerGunShow;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Proxy;

/** Actual carried-gun entrypoint with real item-model resolution; intercepts native submissions, not pixels. */
final class PlatformCarriedGuns {
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            boolean disabled=RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.get();
            var player=mc.player;
            int selected=player.getInventory().getSelectedSlot(),slot=(selected+1)%9;
            var offhand=player.getOffhandItem().copy();
            var unselected=player.getInventory().getItem(slot).copy();
            var main=player.getMainHandItem().copy();
            var gun=GunItemBuilder.create().setId(Identifier.parse("tacz:ak47")).build();
            var display=TimelessAPI.getGunDisplay(gun).orElseThrow();
            var shows=display.getHotbarShow();
            if(shows==null)throw new AssertionError("AK47 fixture has no carried-gun slot map");
            var oldShows=new it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap<LayerGunShow>(shows);
            var state=new AvatarRenderState();state.id=player.getId();
            int[] calls={0};
            Object collector=Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                    new Class<?>[]{SubmitNodeCollector.class,OrderedSubmitNodeCollector.class},(proxy,method,args)->{
                        if(method.getReturnType().isInstance(proxy))return proxy;
                        if(method.getReturnType()==void.class){calls[0]++;return null;}
                        if(method.getReturnType()==boolean.class)return false;
                        if(method.getReturnType()==int.class)return 0;
                        if(method.getName().equals("toString"))return "carried-gun submission probe";
                        return null;
                    });
            try {
                shows.clear();shows.put(slot,new LayerGunShow());shows.put(selected,new LayerGunShow());
                player.setItemInHand(InteractionHand.OFF_HAND,gun.copy());player.getInventory().setItem(slot,ItemStack.EMPTY);
                RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.set(false);
                HumanoidOffhandRender.renderGun(state,new PoseStack(),(SubmitNodeCollector)collector,15728880);
                if(calls[0]==0)throw new AssertionError("Enabled offhand gun made no native submissions");
                player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.getInventory().setItem(slot,gun.copy());calls[0]=0;
                HumanoidOffhandRender.renderGun(state,new PoseStack(),(SubmitNodeCollector)collector,15728880);
                if(calls[0]==0)throw new AssertionError("Enabled unselected hotbar gun made no native submissions");
                RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.set(true);
                player.setItemInHand(InteractionHand.OFF_HAND,gun.copy());calls[0]=0;
                HumanoidOffhandRender.renderGun(state,new PoseStack(),(SubmitNodeCollector)collector,15728880);
                if(calls[0]!=0)throw new AssertionError("Hidden carried guns still submitted");
                RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.set(false);
                player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.getInventory().setItem(slot,ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND,gun.copy());calls[0]=0;
                HumanoidOffhandRender.renderGun(state,new PoseStack(),(SubmitNodeCollector)collector,15728880);
                if(calls[0]!=0)throw new AssertionError("Selected main-hand gun duplicated into carried-gun layer");
            } finally {
                shows.clear();shows.putAll(oldShows);
                player.setItemInHand(InteractionHand.OFF_HAND,offhand);player.getInventory().setItem(slot,unselected);player.setItemInHand(InteractionHand.MAIN_HAND,main);
                RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.set(disabled);
            }
        });
        System.out.println("PORT_PLATFORM_CARRIED_GUNS: native offhand and unselected-hotbar submissions enabled/hidden; selected main hand excluded; live config and fixture restored");
    }
    private PlatformCarriedGuns(){}
}
