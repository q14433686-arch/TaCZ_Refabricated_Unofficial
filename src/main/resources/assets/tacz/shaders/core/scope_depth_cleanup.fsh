#version 330

// Vanilla cleanup shader. Iris replaces this program, but receives an equivalent dormant branch through
// IrisDepthRestoreShaderMixin. Color writes are disabled by the pipeline; only gl_FragDepth matters.
uniform int tacz_DepthRestoreMode;
uniform sampler2D tacz_DepthBackupSampler;
uniform sampler2D tacz_ApertureDepthSampler;
uniform sampler2D tacz_PostBodyDepthSampler;

out vec4 fragColor;

void main() {
    if (tacz_DepthRestoreMode != 0) {
        vec2 size = max(vec2(textureSize(tacz_DepthBackupSampler, 0)), vec2(1.0));
        vec2 uv = gl_FragCoord.xy / size;
        if (tacz_DepthRestoreMode == 2) {
            vec2 apertureSize = max(vec2(textureSize(tacz_ApertureDepthSampler, 0)), vec2(1.0));
            vec2 postBodySize = max(vec2(textureSize(tacz_PostBodyDepthSampler, 0)), vec2(1.0));
            float apertureDepth = texture(tacz_ApertureDepthSampler, gl_FragCoord.xy / apertureSize).r;
            float postBodyDepth = texture(tacz_PostBodyDepthSampler, gl_FragCoord.xy / postBodySize).r;
            // Equal means the invisible ocular is still the nearest hand fragment and may be
            // restored to world depth. A different value means visible scope geometry survived;
            // keep its depth so later water/particles/clouds cannot composite over its color.
            if (postBodyDepth != apertureDepth) {
                discard;
            }
        }
        gl_FragDepth = texture(tacz_DepthBackupSampler, uv).r;
    }
    fragColor = vec4(0.0);
}
