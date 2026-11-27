package com.tacz.guns.client.renderer.item;

import org.joml.Vector3f;

/** Projection conversion after removing the hand pose's base transform. */
public final class FirstPersonRenderMath {
    private FirstPersonRenderMath() {}

    public static double lateralFovScale(double itemFov, double worldFov) {
        double itemTan = Math.tan(Math.toRadians(itemFov) / 2);
        return itemTan <= 1e-6 ? 1 : Math.tan(Math.toRadians(worldFov) / 2) / itemTan;
    }

    /** Keep view depth; scale x/y to preserve the item's screen-space muzzle position. */
    public static Vector3f muzzleOffset(float viewX, float viewY, float viewZ,
                                        double itemFov, double worldFov, Vector3f destination) {
        double lateral = lateralFovScale(itemFov, worldFov);
        return destination.set((float) (viewX * lateral), (float) (viewY * lateral), viewZ);
    }
}
