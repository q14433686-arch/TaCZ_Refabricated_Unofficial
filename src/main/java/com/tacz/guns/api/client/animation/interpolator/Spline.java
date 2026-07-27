package com.tacz.guns.api.client.animation.interpolator;

import com.tacz.guns.api.client.animation.AnimationChannelContent;
import net.minecraft.util.Mth;

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
        
        // 简单线性插值作为基础实现
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
