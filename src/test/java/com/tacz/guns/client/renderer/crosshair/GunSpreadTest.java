package com.tacz.guns.client.renderer.crosshair;

import com.google.gson.Gson;
import com.tacz.guns.resource.pojo.data.gun.GunFireModeAdjustData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.tacz.guns.resource.pojo.data.gun.InaccuracyType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GunSpreadTest {
    @Test void cacheMissUsesAvailableStanceAndItsFireModeAdjustment() {
        var adjust = new Gson().fromJson("{\"aim_inaccuracy\":-1.25,\"other_inaccuracy\":2}", GunFireModeAdjustData.class);
        var base = Map.of(STAND, 2f, MOVE, 4.5f, AIM, 0.5f);
        assertEquals(4f, GunSpread.baseSpread(base, STAND, adjust));
        assertEquals(6.5f, GunSpread.baseSpread(base, MOVE, adjust));
        assertEquals(0f, GunSpread.baseSpread(base, AIM, adjust));
        assertEquals(0.5f, GunSpread.baseSpread(base, AIM, null));
        assertEquals(0f, GunSpread.baseSpread(base, SNEAK, adjust));
        assertEquals(0f, GunSpread.baseSpread(null, SNEAK, adjust));
    }

    @Test void heatScalesTheCurrentModifiedValueAndInvalidProjectionDoesNotDrawAnUnboundedReticle() {
        var heat = new Gson().fromJson("{\"max\":40,\"min_inaccuracy\":1,\"max_inaccuracy\":3}", GunHeatData.class);
        assertEquals(2f, GunSpread.withHeat(2, heat, -1));
        assertEquals(4f, GunSpread.withHeat(2, heat, 20));
        assertEquals(6f, GunSpread.withHeat(2, heat, 80));
        assertEquals(2f, GunSpread.withHeat(2, new Gson().fromJson("{\"max\":0}", GunHeatData.class), 20));
        assertEquals(0, GunSpread.pixels(Float.NaN, 1, 240, 100));
        assertEquals(0, GunSpread.pixels(5, Float.POSITIVE_INFINITY, 240, 100));
        assertEquals(0, GunSpread.pixels(-2, 1, 240, 100));
        assertEquals(80, GunSpread.pixels(Float.MAX_VALUE, 3, 240, 200));
        assertEquals(4, GunSpread.pixels(2, 1, 240, 100));
        assertEquals(8, GunSpread.pixels(2, 2, 240, 100));
        assertEquals(12, GunSpread.pixels(2, 2, 240, 150));
    }
}
