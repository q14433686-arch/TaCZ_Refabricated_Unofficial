#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;
layout(location = 6) in vec2 texCoord0;
layout(location = 0) out vec4 fragColor;

void main() {
    // Texture holes remain holes, but a translucent living surface is still hot.
    // OIT accumulation and optical tint alpha must not become sensor coverage.
    if (texture(Sampler0, texCoord0).a < 0.1) discard;
    fragColor = vec4(1.0);
}
