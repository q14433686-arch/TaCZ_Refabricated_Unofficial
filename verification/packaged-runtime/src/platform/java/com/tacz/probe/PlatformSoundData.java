package com.tacz.probe;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.SoundData;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.client.sound.*;
import com.tacz.guns.config.client.SoundConfig;
import com.tacz.guns.sound.SoundManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/** Real parser -> gameplay sound entry -> native event resolution, not a second volume calculator. */
final class PlatformSoundData {
    @SuppressWarnings("unchecked")
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc->{
            boolean tracking=SoundConfig.FIRST_PERSON_ANIMATION_SOUND_TRACKING.get();
            var played=new ArrayList<GunSoundInstance>();
            try {
                var meta=parse("{id:'minecraft:block.note_block.harp',volume:0.35,pitch:1.25}");
                SoundConfig.FIRST_PERSON_ANIMATION_SOUND_TRACKING.set(false);
                var sound=SoundPlayManager.playClientSound(mc.player,meta,.9f,.9f,16);played.add(sound);
                require(sound!=null && sound.isRelative() && !(sound instanceof EntityTrackingGunSoundInstance),"Local nontracking audio was not listener-relative");
                sound.resolve(mc.getSoundManager());close(sound.getVolume(),.35f);close(sound.getPitch(),1.25f);
                require(!sound.getSound().getPath().toString().contains("tacz_sounds/"),"Vanilla fallback was redirected into a nonexistent gun-pack OGG");
                require(sound.getRegistryName().equals(meta.getSound()),"Fallback lost concurrency/stop resource identity");
                var legacy=SoundPlayManager.playClientSound(mc.player,Identifier.parse("tacz:svd/svd_dry_fire"),.6f,.8f,16);played.add(legacy);
                require(legacy!=null,"Default tacz_sounds clip was lost");legacy.resolve(mc.getSoundManager());
                require(legacy.getSound().getPath().toString().contains("tacz_sounds/"),"Gun-pack clip stopped using its direct resource");
                close(legacy.getVolume(),.6f);close(legacy.getPitch(),.8f);
                var random=parse("{id:'minecraft:block.note_block.harp',volume:0.5,pitch:1,volume_random_range:0.1,pitch_random_range:0.2}");
                float first=-1;boolean varied=false;
                for(int i=0;i<8;i++) {
                    var sample=SoundPlayManager.playClientSound(mc.player,random,9,9,16);played.add(sample);sample.resolve(mc.getSoundManager());
                    require(sample.getVolume()>=.39999f && sample.getVolume()<=.60001f && sample.getPitch()>=.79999f && sample.getPitch()<=1.20001f,
                            "Authored random ranges or fallback override were discarded");
                    if(first>=0 && first!=sample.getPitch())varied=true;first=sample.getPitch();
                }
                require(varied,"SoundData random pitch was frozen across actual playback instances");
                SoundConfig.FIRST_PERSON_ANIMATION_SOUND_TRACKING.set(true);
                var tracked=SoundPlayManager.playClientSound(mc.player,meta,9,9,16);played.add(tracked);
                require(tracked instanceof EntityTrackingGunSoundInstance && !tracked.isRelative(),"Tracking option did not select the entity-bound instance");
                SoundConfig.FIRST_PERSON_ANIMATION_SOUND_TRACKING.set(false);
                var display=TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).orElseThrow();
                var field=display.getClass().getDeclaredField("sounds");field.setAccessible(true);
                var original=(Map<String,SoundData>)field.get(display);var replacement=new java.util.HashMap<>(original);
                var pending=SoundPlayManager.class.getDeclaredField("tmpSoundInstance");pending.setAccessible(true);var oldPending=pending.get(null);
                var warned=SoundPlayManager.class.getDeclaredField("MISSING_SOUND_WARNED");warned.setAccessible(true);var missing=(Set<Identifier>)warned.get(null);
                var optional=Identifier.parse("tacz:r6_absent_optional_container");
                try {
                    replacement.put(SoundManager.DRAW_SOUND,meta);replacement.put(SoundManager.INSPECT_SOUND,SoundData.of(optional));field.set(display,replacement);
                    SoundPlayManager.playDrawSound(mc.player,display);
                    var draw=(GunSoundInstance)pending.get(null);played.add(draw);require(draw!=null,"Gameplay draw consumer lost metadata");draw.resolve(mc.getSoundManager());
                    close(draw.getVolume(),.35f);close(draw.getPitch(),1.25f);
                    SoundPlayManager.playInspectSound(mc.player,display,false);
                    require(pending.get(null)==null && !missing.contains(optional),"Optional animation aggregate produced a false missing-resource warning");
                } finally {field.set(display,original);pending.set(null,oldPending);}
                var attachment=ClientAssetsManager.GSON.fromJson("{sounds:{install:{id:'minecraft:block.note_block.harp',volume:0.35,pitch:1.25},uninstall:'tacz:dry_fire'}}",AttachmentDisplay.class);
                require(attachment.getSounds().get("install").equals(meta.getSound()) && attachment.getSoundData().get("install").hasVolume()
                        && !attachment.getSoundData().get("uninstall").hasVolume(),"Attachment parser no longer supports both object and legacy string forms");
                require(SoundPlayManager.playClientSound(null,meta,1,1,16)==null,"Null owner reached the audio engine");
            } catch(ReflectiveOperationException e){throw new AssertionError(e);}
            finally {SoundConfig.FIRST_PERSON_ANIMATION_SOUND_TRACKING.set(tracking);for(var sound:played)if(sound!=null)sound.setStop();}
        });
        System.out.println("PORT_PLATFORM_SOUND_DATA: actual authored volume/pitch/random playback, legacy direct OGG, native event fallback, relative/tracked routing, gameplay draw metadata and optional animation container preservation");
    }
    private static SoundData parse(String json){return ClientAssetsManager.GSON.fromJson(json,SoundData.class);}
    private static void close(float actual,float expected){require(Math.abs(actual-expected)<.0001f,"Sound value "+actual+" != "+expected);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private PlatformSoundData(){}
}
