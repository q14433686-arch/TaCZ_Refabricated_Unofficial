package com.tacz.guns.client.resource;

import com.google.gson.Gson;
import com.tacz.guns.client.resource.pojo.display.gun.AdsPreset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdsPresetTest {
    private static final Gson GSON = new Gson();

    @Test void independentWindowsEndpointsAndOvershoot() {
        var preset = GSON.fromJson("""
            {"ads_path":{"x":{"start":0.5,"end":1},"z":{"ease":"easeOutBack"}},
             "ads_fov":{"model":{"ease":"ease_in_circ","clamp":false,"smooth":false},
                        "world":{"ease":"not-a-curve"}}}
            """, AdsPreset.class);
        var path = preset.getPath();
        assertTrue(path.isValid());
        assertEquals(0, path.x(.4f));
        assertEquals(.5f, path.x(.75f));
        assertEquals(.75f, path.rotation(.75f));
        assertTrue(path.z(.65f) > 1); // Back can overshoot even with clamped input
        assertEquals(1f, path.z(1f));
        assertFalse(preset.getFov().getModel().isSmooth());
        assertEquals(1f, preset.getFov().getModel().evaluate(1.2f));
        assertNull(preset.getFov().getWorld()); // invalid world channel doesn't suppress the model channel
    }

    @Test void invalidWindowCannotDrivePositioning() {
        assertFalse(GSON.fromJson("{\"ads_path\":{\"y\":{\"start\":0.5,\"end\":0.5}}}", AdsPreset.class).getPath().isValid());
    }
}
