package com.tacz.guns.api.client.animation.interpolator;

import com.tacz.guns.api.client.animation.AnimationChannelContent;
import net.minecraft.util.Mth;

/**
 * UPSTREAM-INCOMPLETE[gltf-cubic-spline]: defensive linear fallback for an unfinished
 * upstream interpolator.
 *
 * <p>This is not a true glTF CUBICSPLINE implementation. The loader also maps the JSON
 * token with {@code Enum.valueOf} to an enum named {@code SPLINE}, and its value packing
 * does not preserve glTF's in-tangent/value/out-tangent triplets through rotation
 * conversion. Consequently bundled TACZ assets (all Bedrock animations) do not use this
 * path; third-party glTF CUBICSPLINE data remains unsupported. Keeping a finite linear
 * fallback is safer than upstream's identity/null stub, but must not be reported as a
 * completed spline implementation.</p>
 */
public class Spline implements Interpolator {
    private AnimationChannelContent content;

    @Override
    public void compile(AnimationChannelContent content) {
        this.content = content;
    }

    @Override
    public float[] interpolate(int indexFrom, int indexTo, float alpha) {
        if (content == null || content.values == null || content.values.length == 0) {
            return new float[]{0, 0, 0, 1};
        }
        
        // Explicit fallback only; see class-level audit note.
        float[] start = content.values[indexFrom];
        float[] end = content.values[indexTo];
        
        if (start.length != end.length) {
            return new float[]{0, 0, 0, 1};
        }
        
        float[] result = new float[start.length];
        for (int i = 0; i < start.length; i++) {
            result[i] = Mth.lerp(alpha, start[i], end[i]);
        }
        
        return result;
    }

    @Override
    public Interpolator clone() {
        Spline spline = new Spline();
        spline.content = this.content;
        return spline;
    }
}
