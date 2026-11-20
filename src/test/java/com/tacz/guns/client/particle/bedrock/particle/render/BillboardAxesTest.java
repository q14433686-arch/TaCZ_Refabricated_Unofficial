package com.tacz.guns.client.particle.bedrock.particle.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BillboardAxesTest {
    @Test void uprightAcrossYawPitchRollIncludingVerticalView() {
        var up = new Vector3f(); var right = new Vector3f();
        for (float pitch : new float[]{0,.7f,-.45f,(float)Math.PI/2}) {
            for (float roll : new float[]{0,.35f}) {
                var view = new Matrix4f().rotationZ(roll).rotateX(pitch).rotateY(1.3f);
                BillboardHelper.uprightAxes(right, up, view);
                assertTrue(new Matrix4f(view).invert().transformDirection(new Vector3f(up)).equals(new Vector3f(0,1,0),1e-5f));
                assertEquals(0, right.dot(up), 1e-5);
                assertEquals(1, right.length(), 1e-5);
                assertEquals(0, right.z, 1e-5);
                assertEquals(Math.cos(pitch), Math.hypot(up.x, up.y), 1e-5);
            }
        }
    }
}
