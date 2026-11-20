package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.animation.AnimationParticleEvent;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import com.tacz.guns.network.message.AnimationParticleMessages.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;

/** Real S2C delivery to a separately rendered tracked owner, not a local shared animation runner. */
final class RemoteParticleSmoke {
    static void run(ClientGameTestContext ctx,TestSingleplayerContext world){
        Identifier gun=Identifier.parse("port_acceptance:inventory");
        var actor=world.getServer().computeOnServer(s->{
            var player=s.getPlayerList().getPlayers().getFirst();
            var entity=EntityTypes.HUSK.create(player.level(),EntitySpawnReason.COMMAND);
            if(entity==null)throw new AssertionError("Tracked particle owner creation failed");
            entity.setNoAi(true);entity.snapTo(player.position().add(0,0,3));
            entity.setItemInHand(InteractionHand.MAIN_HAND,GunItemBuilder.create().setId(gun).setAmmoCount(3).build());
            player.level().addFreshEntity(entity);return entity;
        });
        try{
            ctx.waitFor(mc -> mc.level.getEntity(actor.getId()) != null);
            ctx.getInput().lookAt(net.minecraft.core.BlockPos.containing(actor.position().add(0,1,0)));
            ctx.waitTicks(5);
            long before=ctx.computeOnClient(mc->WorldEmitterManager.getInstance().spawned());
            // Well-formed but undeclared token must not introduce any executable effect.
            world.getServer().runOnServer(s->ServerPlayNetworking.send(s.getPlayerList().getPlayers().getFirst(),new Delivery(actor.getId(),gun,"0".repeat(64))));
            ctx.waitTicks(3);
            if(ctx.computeOnClient(mc->WorldEmitterManager.getInstance().spawned())!=before)throw new AssertionError("Unknown remote particle token accepted");
            var event=new AnimationParticleEvent(Identifier.parse("port_acceptance:runtime_probe"),"muzzle_flash","variable.count = 3;");
            world.getServer().runOnServer(s->ServerPlayNetworking.send(s.getPlayerList().getPlayers().getFirst(),new Delivery(actor.getId(),gun,event.token())));
            ctx.waitFor(mc->WorldEmitterManager.getInstance().spawned()>before);
            long geometry=ctx.computeOnClient(mc->WorldEmitterManager.getInstance().rendered());
            ctx.waitFor(mc->WorldEmitterManager.getInstance().rendered()>geometry);
            ctx.takeScreenshot("tacz_snowstorm_tracked");
            ctx.waitTicks(20);
            if(ctx.computeOnClient(mc->WorldEmitterManager.getInstance().emitterCount())!=0)throw new AssertionError("Tracked owner retained expired emitter");
            GunMod.LOGGER.info("PORT_SNOWSTORM_REMOTE: real S2C, trusted-token resolution, tracked main-hand geometry and expiry passed; unknown token rejected");
        }finally{world.getServer().runOnServer(s->actor.discard());}
    }
}
