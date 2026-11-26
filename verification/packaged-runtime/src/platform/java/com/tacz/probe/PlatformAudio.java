package com.tacz.probe;

import com.tacz.probe.mixin.PlatformVolume;
import me.xjqsh.lrtactical.client.audio.StunRingingSound;
import me.xjqsh.lrtactical.init.ModEffects;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.Arrays;

final class PlatformAudio {
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            try {
                var field=Arrays.stream(mc.getSoundManager().getClass().getDeclaredFields()).filter(f -> f.getType()==SoundEngine.class).findFirst().orElseThrow();
                field.setAccessible(true);var engine=(PlatformVolume)field.get(mc.getSoundManager());
                var previous=mc.player.getEffect(ModEffects.DEAFENED);
                SoundInstance[] sounds={sound(SoundSource.PLAYERS),sound(SoundSource.BLOCKS),sound(SoundSource.UI),sound(SoundSource.MUSIC),sound(SoundSource.MASTER),new StunRingingSound()};
                try {
                    mc.player.removeEffect(ModEffects.DEAFENED);
                    float[] base=read(engine,sounds);
                    if(base[0]<=0 || base[5]<=0) throw new AssertionError("No nonzero native volume for audio comparison");
                    for(int duration:new int[]{200,50}) {
                        mc.player.removeEffect(ModEffects.DEAFENED);
                        mc.player.addEffect(new MobEffectInstance(ModEffects.DEAFENED,duration));
                        float[] values=read(engine,sounds);
                        float factor=duration>=100 ? .01f : .505f;
                        for(int i=0;i<values.length;i++) close(values[i],base[i]*(i<2?factor:1));
                    }
                    mc.player.removeEffect(ModEffects.DEAFENED);
                    var recovered=read(engine,sounds);for(int i=0;i<base.length;i++) close(recovered[i],base[i]);
                } finally { mc.player.removeEffect(ModEffects.DEAFENED);if(previous!=null) mc.player.addEffect(previous); }
            } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        System.out.println("PORT_PLATFORM_AUDIO: actual SoundEngine volume: players/blocks 1%, half-expiry 50.5%, UI/music/master/ringing exempt, removal restored without double multiplication");
    }
    private static SoundInstance sound(SoundSource source) {
        return new AbstractSoundInstance(SoundEvent.createVariableRangeEvent(Identifier.parse("tacz:platform_volume_probe")),source,RandomSource.create()) {};
    }
    private static float[] read(PlatformVolume engine,SoundInstance[] sounds) {
        float[] a=new float[sounds.length];for(int i=0;i<a.length;i++) a[i]=engine.platform$volume(sounds[i]);return a;
    }
    private static void close(float a,float b) { if(Math.abs(a-b)>.00001f) throw new AssertionError("Native volume "+a+" != "+b); }
    private PlatformAudio() {}
}
