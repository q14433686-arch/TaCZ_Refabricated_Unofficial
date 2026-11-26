package com.tacz.guns.client.sound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.tacz.guns.client.resource.pojo.display.SoundData;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.resource.serialize.IdentifierSerializer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SoundDataTest {
    private static final Gson GSON=new GsonBuilder().registerTypeAdapter(Identifier.class,new IdentifierSerializer())
            .registerTypeAdapter(SoundData.class,new SoundData.Deserializer()).create();
    @Test void legacyIdsKeepFallbacksAndAttachmentObjectsKeepAuthoredParameters() {
        var display=GSON.fromJson("{sounds:{install:{id:'minecraft:block.note_block.harp',volume:0.25,pitch:1.25},remove:'tacz:dry_fire'}}",AttachmentDisplay.class);
        var data=display.getSoundData().get("install");var random=RandomSource.create(123);
        assertEquals(Identifier.parse("minecraft:block.note_block.harp"),display.getSounds().get("install"));
        assertEquals(.25f,data.resolveVolume(random,.9f));assertEquals(1.25f,data.resolvePitch(random,.9f));
        var legacy=display.getSoundData().get("remove");
        assertEquals(.9f,legacy.resolveVolume(random,.9f));assertEquals(.8f,legacy.resolvePitch(random,.8f));
    }
    @Test void randomRangesVaryAndClampWithoutAcceptingMalformedObjects() {
        var data=GSON.fromJson("{id:'tacz:a',volume:0,pitch:0.01,volume_random_range:0.2,pitch_random_range:0.2}",SoundData.class);
        var random=RandomSource.create(123);boolean positive=false,clamped=false;
        for(int i=0;i<100;i++) {
            float volume=data.resolveVolume(random,9),pitch=data.resolvePitch(random,9);
            assertTrue(volume>=0 && volume<=.2f);assertTrue(pitch>=.0001f && pitch<=.21f);
            positive|=volume>0;clamped|=pitch==.0001f;
        }
        assertTrue(positive && clamped);
        for(String json:new String[]{"42","{}","{id:'tacz:a',volume:-1}","{id:'tacz:a',pitch:0}","{id:'tacz:a',pitch_random_range:-1}"})
            assertThrows(JsonParseException.class,()->GSON.fromJson(json,SoundData.class),json);
    }
}
