package com.tacz.guns.event;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunSoundAngerTest {
    @Test void queryRadiusIsFiniteAndBounded() {
        assertEquals(0, GunSoundAngerEvent.getCappedRadius(24, Float.NaN));
        assertEquals(0, GunSoundAngerEvent.getCappedRadius(32, -1));
        assertEquals(12, GunSoundAngerEvent.getCappedRadius(24, 0.5f));
        assertEquals(64, GunSoundAngerEvent.getCappedRadius(32, 1000));
    }
}
