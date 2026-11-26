#version 330

uniform sampler2D SceneDepthSampler;
out vec4 fragColor;

void main() {
    // Canonicalize DEPTH32's hidden fixed-point bits through the float representation used
    // by thermal_coverage.fsh. Source and destination are distinct depth attachments.
    gl_FragDepth = texelFetch(SceneDepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    fragColor = vec4(0.0); // color writes are disabled; the heat attachment remains clear
}
