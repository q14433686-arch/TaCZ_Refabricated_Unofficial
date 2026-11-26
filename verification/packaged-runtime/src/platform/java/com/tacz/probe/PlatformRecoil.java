package com.tacz.probe;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.event.CameraSetupEvent;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.input.ShootKey;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import java.lang.reflect.Field;
import static cn.sh1rocu.tacz.TaCZFabric.LOWEST;

/** Last pre-listener veto must not create recoil or muzzle animation; actual successful fire must. */
final class PlatformRecoil {
    private static boolean cancel,active;
    private static int attempts,successes;
    static {
        GunFireEvent.CALLBACK.register(LOWEST,e->{if(active && e.getLogicalSide().isClient()){attempts++;if(cancel)e.setCanceled(true);}});
        GunFirePostEvent.EVENT.register(e->{if(active && e.getLogicalSide().isClient())successes++;});
    }
    static void verify(ClientGameTestContext ctx,TestSingleplayerContext world) {
        var old=world.getServer().computeOnServer(s->s.getPlayerList().getPlayers().getFirst().getMainHandItem().copy());
        world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();
            p.setItemInHand(InteractionHand.MAIN_HAND,GunItemBuilder.create().setId(Identifier.parse("tacz:ak47")).setAmmoCount(8).setAmmoInBarrel(true).setFireMode(FireMode.SEMI).build());p.inventoryMenu.broadcastChanges();});
        try {
            ctx.waitFor(mc->mc.player.getMainHandItem().getItem() instanceof IGun gun && gun.getCurrentAmmoCount(mc.player.getMainHandItem())==8);
            ctx.waitTicks(40);
            ctx.waitFor(mc->IGunOperator.fromLivingEntity(mc.player).getSynDrawCoolDown()==0 && IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder().clientBaseTimestamp>=0);
            ctx.runOnClient(mc->{timestamp(CameraSetupEvent.class,-1L);timestamp(FirstPersonRenderGunEvent.class,-1L);attempts=successes=0;active=cancel=true;});
            ctx.getInput().holdKey(ShootKey.SHOOT_KEY);
            try {ctx.waitFor(mc->attempts>0,80);}finally{ctx.getInput().releaseKey(ShootKey.SHOOT_KEY);}
            ctx.waitTicks(3);
            ctx.runOnClient(mc->{
                if(successes!=0 || timestamp(CameraSetupEvent.class,null)!=-1 || timestamp(FirstPersonRenderGunEvent.class,null)!=-1)
                    throw new AssertionError("A canceled final pre callback created recoil/muzzle success");
            });
            // This is the CLIENT visual veto, after the request packet. It must not pretend to
            // veto the independent server event: the server still settles its legitimate shot.
            PlayerProbe.waitForServer(ctx,world,s->{var stack=s.getPlayerList().getPlayers().getFirst().getMainHandItem();return ((IGun)stack.getItem()).getCurrentAmmoCount(stack)==7;});
            ctx.waitTicks(12);ctx.runOnClient(mc->cancel=false);
            ctx.getInput().holdKey(ShootKey.SHOOT_KEY);
            try{ctx.waitFor(mc->successes>0,80);}finally{ctx.getInput().releaseKey(ShootKey.SHOOT_KEY);}
            PlayerProbe.waitForServer(ctx,world,s->{var stack=s.getPlayerList().getPlayers().getFirst().getMainHandItem();return ((IGun)stack.getItem()).getCurrentAmmoCount(stack)==6;});
            ctx.runOnClient(mc->{if(successes!=1 || timestamp(CameraSetupEvent.class,null)<0 || timestamp(FirstPersonRenderGunEvent.class,null)<0)
                throw new AssertionError("Successful actual shot did not start both recoil consumers once");});
            System.out.println("PORT_PLATFORM_RECOIL: last client-pre veto kept both recoil clocks idle while server independently settled ammo=7; successful visual shot started both once and server ammo=6");
        } finally {
            ctx.getInput().releaseKey(ShootKey.SHOOT_KEY);ctx.runOnClient(mc->{active=cancel=false;});
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setItemInHand(InteractionHand.MAIN_HAND,old);p.inventoryMenu.broadcastChanges();});
        }
    }
    private static long timestamp(Class<?> type,Long value){try{Field f=type.getDeclaredField("shootTimeStamp");f.setAccessible(true);if(value!=null)f.setLong(null,value);return f.getLong(null);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private PlatformRecoil(){}
}
