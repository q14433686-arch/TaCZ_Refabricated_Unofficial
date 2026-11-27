package com.tacz.guns.client.renderer.item;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FirstPersonRenderMathTest {
    @Test void preservesDepthAndProjectedMuzzleAcrossDifferentFovs() {
        for (double[] fov : new double[][]{{70, 80}, {80, 70}, {45, 100}, {70, 70}}) {
            var result = new Vector3f();
            assertSame(result, FirstPersonRenderMath.muzzleOffset(.237f, -.113f, -.330f, fov[0], fov[1], result));
            double itemTan = Math.tan(Math.toRadians(fov[0]) / 2);
            double worldTan = Math.tan(Math.toRadians(fov[1]) / 2);
            assertEquals(-.330f, result.z);
            assertEquals(.237 / itemTan, result.x / worldTan, 1e-6);
            assertEquals(-.113 / itemTan, result.y / worldTan, 1e-6);
        }
    }

    @Test void tinyOrNonPositiveItemTangentUsesIdentityCompensation() {
        for (double fov : new double[]{0, -1, .00001}) {
            assertEquals(1, FirstPersonRenderMath.lateralFovScale(fov, 80));
            assertEquals(new Vector3f(1, -2, -3),
                    FirstPersonRenderMath.muzzleOffset(1, -2, -3, fov, 80, new Vector3f()));
        }
    }
}
