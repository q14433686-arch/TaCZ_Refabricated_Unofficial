package com.tacz.probe;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.probe.mixin.ImpactColorView;
import com.tacz.guns.client.particle.BulletHoleParticle;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.gun.GunAmmo;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.particles.BulletHoleOption;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Observe native particle/sound calls without replacing outputs; production impact provider remains unchanged. */
public final class PlatformImpacts {
    private static boolean active;
    private static int fragments,steps;
    public static void particle(ParticleOptions option){if(active && option.getType()==ParticleTypes.BLOCK)fragments++;}
    public static void sound(SoundInstance sound){if(active && sound.getSource()==SoundSource.BLOCKS && sound.getIdentifier().equals(Identifier.parse("minecraft:block.stone.step")))steps++;}
    static void verify(ClientGameTestContext ctx) {
        var provider=new BulletHoleParticle.Provider();
        ctx.runOnClient(mc->{
            var ammo=TimelessAPI.getClientAmmoIndex(Identifier.parse("tacz:762x39")).orElseThrow();
            var ammoDisplay=PlatformAds.read(ammo,"display");
            var gun=TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).orElseThrow();var display=PlatformAds.read(gun,"display");
            var oldAmmo=PlatformAds.read(ammoDisplay,"bulletHoleEnabled");var count=PlatformAds.read(ammoDisplay,"hitBlockParticleCount");var gunAmmo=PlatformAds.read(display,"gunAmmo");
            var oldAmmoColor=PlatformAds.read(ammo,"tracerColor");var oldGunColor=PlatformAds.read(gun,"tracerColor");
            int life=RenderConfig.BULLET_HOLE_PARTICLE_LIFE.get(),distance=RenderConfig.HIT_PARTICLE_MAX_DISTANCE.get();
            boolean hit=RenderConfig.HIT_PARTICLE_ENABLE.get(),extra=RenderConfig.HIT_EXTRA_PARTICLE_ENABLE.get();
            var pos=mc.player.blockPosition().offset(3,0,0);var state=mc.level.getBlockState(pos);
            try {
                RenderConfig.BULLET_HOLE_PARTICLE_LIFE.set(400);RenderConfig.HIT_PARTICLE_ENABLE.set(true);RenderConfig.HIT_EXTRA_PARTICLE_ENABLE.set(false);RenderConfig.HIT_PARTICLE_MAX_DISTANCE.set(64);
                mc.level.setBlock(pos,Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
                PlatformAds.write(ammoDisplay,"hitBlockParticleCount",2);PlatformAds.write(ammoDisplay,"bulletHoleEnabled",true);
                PlatformAds.write(display,"gunAmmo",ClientAssetsManager.GSON.fromJson("{enable_bullet_hole:false}",GunAmmo.class));
                active=true;fragments=steps=0;
                if(create(provider,mc,pos,false)!=null || fragments!=2 || steps!=1)throw new AssertionError("Disabled gun decal suppressed fragments/surface sound or ignored override: "+fragments+"/"+steps);
                PlatformAds.write(ammoDisplay,"bulletHoleEnabled",false);PlatformAds.write(display,"gunAmmo",ClientAssetsManager.GSON.fromJson("{enable_bullet_hole:true}",GunAmmo.class));
                var enabled=create(provider,mc,pos,false);
                if(enabled==null || !enabled.isAlive() || fragments!=4 || steps!=1)throw new AssertionError("Gun true did not override ammo false or same-tick step was duplicated");
                float offset=(Float)PlatformAds.read(enabled,"surfaceOffset");
                if(offset<.005f || offset>.01f)throw new AssertionError("Invalid frozen decal surface offset");
                PlatformAds.write(display,"gunAmmo",null);
                if(create(provider,mc,pos,false)!=null)throw new AssertionError("Ammo-level decal veto ignored");
                PlatformAds.write(ammoDisplay,"bulletHoleEnabled",true);
                PlatformAds.write(ammo,"tracerColor",new float[]{.2f,.4f,.6f});PlatformAds.write(gun,"tracerColor",null);
                color(create(provider,mc,pos,false),.2f,.4f,.6f);
                PlatformAds.write(gun,"tracerColor",new float[]{.8f,.6f,.4f});
                color(create(provider,mc,pos,false),.8f,.6f,.4f);
                int gated=fragments;RenderConfig.HIT_PARTICLE_ENABLE.set(false);
                if(create(provider,mc,pos,false)==null || fragments!=gated)throw new AssertionError("Fragment master switch incorrectly disabled the decal");
                RenderConfig.HIT_PARTICLE_ENABLE.set(true);RenderConfig.HIT_PARTICLE_MAX_DISTANCE.set(1);
                if(create(provider,mc,pos,false)==null || fragments!=gated)throw new AssertionError("Fragment distance gate incorrectly disabled the decal");
                RenderConfig.HIT_PARTICLE_MAX_DISTANCE.set(64);
                RenderConfig.BULLET_HOLE_PARTICLE_LIFE.set(0);int before=fragments;
                if(create(provider,mc,pos,false)!=null || fragments!=before+2)throw new AssertionError("Zero decal life suppressed independent fragments");
                RenderConfig.BULLET_HOLE_PARTICLE_LIFE.set(400);
                if(create(provider,mc,pos,true)==null)throw new AssertionError("Malformed IDs did not retain safe default decal");
                mc.level.setBlock(pos,ModBlocks.TARGET.defaultBlockState(),Block.UPDATE_ALL);before=fragments;
                if(create(provider,mc,pos,false)!=null || fragments!=before)throw new AssertionError("Target generated impact effects");
                mc.level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
                if(create(provider,mc,pos,false)!=null || fragments!=before)throw new AssertionError("Detached decal generated impact effects");
            } finally {
                active=false;mc.level.setBlock(pos,state,Block.UPDATE_ALL);
                PlatformAds.write(ammo,"tracerColor",oldAmmoColor);PlatformAds.write(gun,"tracerColor",oldGunColor);
                PlatformAds.write(ammoDisplay,"bulletHoleEnabled",oldAmmo);PlatformAds.write(ammoDisplay,"hitBlockParticleCount",count);PlatformAds.write(display,"gunAmmo",gunAmmo);
                RenderConfig.BULLET_HOLE_PARTICLE_LIFE.set(life);RenderConfig.HIT_PARTICLE_ENABLE.set(hit);RenderConfig.HIT_EXTRA_PARTICLE_ENABLE.set(extra);RenderConfig.HIT_PARTICLE_MAX_DISTANCE.set(distance);
            }
        });
        ctx.waitTick();
        ctx.runOnClient(mc->{
            var pos=mc.player.blockPosition().offset(3,0,0);var state=mc.level.getBlockState(pos);
            try {active=true;int before=steps;mc.level.setBlock(pos,Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);create(provider,mc,pos,true);if(steps!=before+1)throw new AssertionError("Next-tick surface sound stayed suppressed");}
            finally {active=false;mc.level.setBlock(pos,state,Block.UPDATE_ALL);}
        });
        System.out.println("PORT_PLATFORM_IMPACTS: actual provider gun/ammo decal precedence, zero-life fragments, native surface-step once per tick/rearmed next tick, malformed-ID fallback, gun/ammo color precedence, independent fragment config/distance, target/air veto and frozen offset range passed");
    }
    private static void color(BulletHoleParticle particle,float r,float g,float b) {
        if(particle==null || Math.abs(((ImpactColorView)particle).platform$red()-r)>.00001f
                || Math.abs(((ImpactColorView)particle).platform$green()-g)>.00001f
                || Math.abs(((ImpactColorView)particle).platform$blue()-b)>.00001f)throw new AssertionError("Impact gun/ammo tracer color precedence failed");
    }
    private static BulletHoleParticle create(BulletHoleParticle.Provider provider,Minecraft mc,net.minecraft.core.BlockPos pos,boolean invalid){return provider.createParticle(new BulletHoleOption(Direction.UP,pos,invalid?"INVALID ID":"tacz:762x39",invalid?"INVALID ID":"tacz:ak47",invalid?"INVALID ID":"tacz:ak47_display"),mc.level,pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,0,0,0,mc.level.getRandom());}
    private PlatformImpacts(){}
}
