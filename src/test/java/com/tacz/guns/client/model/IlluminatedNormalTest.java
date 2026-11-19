package com.tacz.guns.client.model;

import com.tacz.guns.client.model.bedrock.UnshadedVertices.Normal;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IlluminatedNormalTest {
    @Test void keepTheSignedLightDirectionWithinThePackedNormalRange() {
        var direction = new Vector3f(-.7f, .2f, .1f).normalize();
        var n = Normal.fromLight(direction);
        assertEquals(-1, n.x());
        assertTrue(new Vector3f(n.x(), n.y(), n.z()).dot(direction) >= 1);
        assertTrue(Math.abs(n.y()) <= 1 && Math.abs(n.z()) <= 1);
        assertEquals(Normal.UP, Normal.fromLight(new Vector3f()));
        assertEquals(Normal.UP, Normal.fromLight(new Vector3f(Float.NaN,0,0)));
    }
}
