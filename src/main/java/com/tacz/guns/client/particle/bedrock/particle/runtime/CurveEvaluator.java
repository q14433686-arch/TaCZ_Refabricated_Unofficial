// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.runtime;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.particle.data.curve.CurveNode;
import com.tacz.guns.client.particle.bedrock.particle.data.curve.CurveType;
import com.tacz.guns.client.particle.bedrock.particle.data.curve.ParticleCurve;

/**
 * 粒子曲线求值器。
 */
public final class CurveEvaluator {
    private static final CurveNode FIRST_CHAIN_NODE = new CurveNode(0f, 0f);
    private static final CurveNode LAST_CHAIN_NODE = new CurveNode(0f, 0f);
    private static final float ONE_THIRD = 1f / 3f;

    private CurveEvaluator() {
    }

    public static float evaluate(ParticleCurve curve, MolangContext<?> ctx, String curveName) {
        float input = (float) curve.input().evaluate(ctx);
        float horizontalRange = (float) curve.horizontalRange().evaluate(ctx);
        if (curve.type() == CurveType.BEZIER_CHAIN) {
            horizontalRange = 1f;
        }

        float t = horizontalRange == 0f ? 0f : input / horizontalRange;
        t = clamp01(t);

        return switch (curve.type()) {
            case LINEAR -> evaluateLinear(curve.nodes(), t);
            case BEZIER -> evaluateBezier(curve.nodes(), t);
            case CATMULL_ROM -> evaluateCatmullRom(curve.nodes(), t);
            case BEZIER_CHAIN -> evaluateBezierChain(curve.nodes(), curve.chainNodes(), t);
        };
    }

    private static float evaluateLinear(float[] nodes, float t) {
        if (nodes == null || nodes.length == 0) return 0f;
        if (nodes.length == 1) return nodes[0];

        float scaled = t * (nodes.length - 1);
        int index = Math.min((int) Math.floor(scaled), nodes.length - 2);
        float fraction = scaled - index;
        float start = nodes[index];
        float end = nodes[index + 1];
        return start + (end - start) * fraction;
    }

    private static float evaluateBezier(float[] nodes, float t) {
        if (nodes == null || nodes.length == 0) return 0f;
        if (nodes.length == 1) return nodes[0];

        float[] work = nodes.clone();
        for (int level = work.length - 1; level > 0; level--) {
            for (int i = 0; i < level; i++) {
                work[i] = work[i] + (work[i + 1] - work[i]) * t;
            }
        }
        return work[0];
    }

    private static float evaluateCatmullRom(float[] nodes, float t) {
        if (nodes == null || nodes.length == 0) return 0f;
        if (nodes.length < 4) return evaluateLinear(nodes, t);

        int segmentCount = nodes.length - 3;
        if (segmentCount <= 0) return nodes[0];

        float scaled = t * segmentCount;
        int segment = Math.min((int) Math.floor(scaled), segmentCount - 1);
        float localT = scaled - segment;

        float p0 = nodes[segment];
        float p1 = nodes[segment + 1];
        float p2 = nodes[segment + 2];
        float p3 = nodes[segment + 3];

        float tt = localT * localT;
        float ttt = tt * localT;

        return 0.5f * ((2f * p1)
                + (-p0 + p2) * localT
                + (2f * p0 - 5f * p1 + 4f * p2 - p3) * tt
                + (-p0 + 3f * p1 - 3f * p2 + p3) * ttt);
    }

    private static float evaluateBezierChain(float[] keys, CurveNode[] nodes, float t) {
        if (keys == null || nodes == null || keys.length == 0 || nodes.length == 0) return 0f;

        int rightIndex = 0;
        while (rightIndex < keys.length && keys[rightIndex] <= t) {
            rightIndex++;
        }

        float leftTime = rightIndex == 0 ? 0f : keys[rightIndex - 1];
        float rightTime = rightIndex == keys.length ? 1f : keys[rightIndex];
        CurveNode leftNode = rightIndex == 0 ? FIRST_CHAIN_NODE : nodes[rightIndex - 1];
        CurveNode rightNode = rightIndex == keys.length ? LAST_CHAIN_NODE : nodes[rightIndex];

        float duration = rightTime - leftTime;
        float localT = duration <= 0f ? 0f : (t - leftTime) / duration;
        localT = clamp01(localT);

        float v0 = leftNode.value();
        float v1 = leftNode.value() + leftNode.slope() * ONE_THIRD;
        float v2 = rightNode.value() - rightNode.slope() * ONE_THIRD;
        float v3 = rightNode.value();
        return cubicBezier(localT, v0, v1, v2, v3);
    }

    private static float cubicBezier(float t, float v0, float v1, float v2, float v3) {
        float inv = 1f - t;
        float inv2 = inv * inv;
        float t2 = t * t;
        return inv2 * inv * v0
                + 3f * inv2 * t * v1
                + 3f * inv * t2 * v2
                + t2 * t * v3;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
