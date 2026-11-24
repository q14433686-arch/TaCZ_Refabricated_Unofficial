package com.tacz.guns.client.resource;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.tacz.guns.client.resource.pojo.display.SoundData;
import com.tacz.guns.resource.serialize.IdentifierSerializer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoundDataTest {
    @Test void stringKeepsFallbackAndObjectResolvesPerPlayback() {
        var gson = new GsonBuilder().registerTypeAdapter(SoundData.class, new SoundData.Deserializer())
                .registerTypeAdapter(Identifier.class, new IdentifierSerializer()).create();
        var legacy = gson.fromJson("\"sound:test\"", SoundData.class);
        var random = RandomSource.create(1);
        assertEquals(.625f, legacy.resolveVolume(random, .625f));
        assertEquals(.75f, legacy.resolvePitch(random, .75f));
        var object = gson.fromJson("""
            {"id":"sound:test","volume":2,"volume_random_range":0.5,"pitch":0.8}
            """, SoundData.class);
        assertEquals(legacy.getSound(), object.getSound());
        assertEquals(.8f, object.resolvePitch(random, 1));
        float a = object.resolveVolume(random, 1), b = object.resolveVolume(random, 1);
        assertTrue(a >= 1.5f && a <= 2.5f && b >= 1.5f && b <= 2.5f);
        assertNotEquals(a, b);
        assertThrows(JsonParseException.class, () -> gson.fromJson("{\"id\":\"sound:test\",\"pitch\":0}", SoundData.class));
    }
}
