package com.tacz.guns.client.resource;

import com.google.gson.Gson;
import com.tacz.guns.client.resource.pojo.display.ammo.HitSurfaceParticle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HitSurfaceParticleTest {
    @Test void boundsPackInputsBeforeSpawning() {
        var effect = new Gson().fromJson("""
                {"count":999,"count_range":[-3,91],"life_time":0,"surface_offset":"NaN", "spread_angle":120,
                 "alpha":2,"scale":20,"normal_speed":-8,"random_speed":15}
                """, HitSurfaceParticle.class);
        assertEquals(32, effect.getCount());
        assertEquals(0, effect.getCountMin());
        assertEquals(32, effect.getCountMax());
        assertEquals(1, effect.getLifeTime());
        assertEquals(0f, effect.getSurfaceOffset());
        assertEquals(89f, effect.getSpreadAngle());
        assertEquals(1f, effect.getAlpha().floatValue());
        assertEquals(4f, effect.getScale().floatValue());
        assertEquals(0f, effect.getNormalSpeed());
        assertEquals(2f, effect.getRandomSpeed());
    }
}
