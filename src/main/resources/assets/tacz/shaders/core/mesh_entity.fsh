#version 330

// TacZ Mesh Loader: poly_mesh GPU 烘焙用的片元着色器。
// 与 26.2 的 assets/minecraft/shaders/core/entity.fsh 逐字节相同，
// 仅因 26.2 要求 vsh/fsh 同名配对而单独存在一份。

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif

#ifndef EMISSIVE
in vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
in vec4 overlayColor;
#endif

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

#ifdef DISSOLVE
    float dissolve = texture(DissolveMaskSampler, texCoord0).r;
    if (dissolve < 0.5) {
        discard;
    }
#endif

#ifdef PER_FACE_LIGHTING
    vec4 vertexColor = (gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack) * color;
#else
    vec4 vertexColor = color;
#endif

#ifndef EMISSIVE
    vertexColor *= lightMapColor;
#endif

#ifndef NO_OVERLAY
    vertexColor.rgb = mix(overlayColor.rgb, vertexColor.rgb, overlayColor.a);
#endif

    fragColor = vertexColor * ColorModulator;

#ifdef ENABLE_FOG
    fragColor = linear_fog(fragColor, sphericalVertexDistance, cylindricalVertexDistance, FogStart, FogEnd, FogColor, FogDensity);
#endif
}
