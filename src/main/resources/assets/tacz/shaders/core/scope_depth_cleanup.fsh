#version 330

// Vanilla cleanup shader. Iris replaces this program, but receives an equivalent dormant branch through
// IrisDepthRestoreShaderMixin. Color writes are disabled by the pipeline; only gl_FragDepth matters.
uniform int tacz_DepthRestoreMode;
uniform sampler2D tacz_DepthBackupSampler;

out vec4 fragColor;

void main() {
    if (tacz_DepthRestoreMode != 0) {
        vec2 size = max(vec2(textureSize(tacz_DepthBackupSampler, 0)), vec2(1.0));
        vec2 uv = gl_FragCoord.xy / size;
        gl_FragDepth = texture(tacz_DepthBackupSampler, uv).r;
    }
    fragColor = vec4(0.0);
}
