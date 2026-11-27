#version 330
uniform sampler2D Sampler0;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) discard;
    // Test-only 24-bit depth readback through an RGBA8 attachment, NOT a heat shader.
    vec3 encoded = fract(gl_FragCoord.z * vec3(1.0, 255.0, 65025.0));
    encoded -= encoded.yzz * vec3(1.0 / 255.0, 1.0 / 255.0, 0.0);
    fragColor = vec4(encoded, 1.0);
}
