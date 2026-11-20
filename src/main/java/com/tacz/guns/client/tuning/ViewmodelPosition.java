package com.tacz.guns.client.tuning;

import com.tacz.guns.config.client.ViewTuningConfig.Axis;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** The positioning matrix is in the Z-flipped Bedrock basis, before any meshes or muzzle locators branch off. */
public final class ViewmodelPosition {
    public static void apply(Matrix4f positioning, float aiming, float refitting) {
        float hip = (1f - Mth.clamp(aiming, 0f, 1f)) * (1f - Mth.clamp(refitting, 0f, 1f));
        if (hip == 0) return;
        TuningValues values = TuningValues.visible();
        float x = offset(values, Axis.X), y = offset(values, Axis.Y), z = offset(values, Axis.Z);
        if (x != 0 || y != 0 || z != 0) {
            // Add in view axes, NOT along the pack's rotated sight node. Screen right/up are -X/-Y here.
            positioning.m30(positioning.m30() - x * hip);
            positioning.m31(positioning.m31() - y * hip);
            positioning.m32(positioning.m32() + z * hip);
        }
    }

    private static float offset(TuningValues values, Axis axis) {
        double value = values.get(axis.option);
        return Double.isFinite(value) ? (float) value : 0f;
    }

    private ViewmodelPosition() { }
}
