#version 330
#extension GL_ARB_separate_shader_objects : require

// TACZ 光影后置目镜框片元 —— 26.3 的 assets/minecraft/shaders/core/entity.fsh
// 去掉 apply_fog() 的版本（除此之外逐行一致，vanilla 若改 entity.fsh 要跟着同步）。
//
// 为什么去雾：这条管线画在 Iris 全部 composite/final pass 与 PIP 合成【之后】，
// 是纯前景层。此刻 Fog uniform 块里挂的是世界那套雾参数，套上去目镜框会被
// 距离雾染色（1.21.11 母本 scope_ring_final.fsh 同一结论）。
// 顶点数据、lightmap、overlay 全部保持原版语义。
//
// 【26.3 方言变更】#moj_import -> #include、varying 显式 layout(location = N)。
// 本文件配对的顶点着色器是 core/scope_body（= vanilla entity.vsh 逐字拷贝），
// 所以这里的 in 编号必须与 entity.fsh 完全一致，否则会取错 varying。

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
layout(location = 2) in vec4 vertexPerFaceColorBack;
layout(location = 3) in vec4 vertexPerFaceColorFront;
#else
layout(location = 2) in vec4 vertexColor;
#endif

#ifndef EMISSIVE
layout(location = 4) in vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
layout(location = 5) in vec4 overlayColor;
#endif

layout(location = 6) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

#ifdef PER_FACE_LIGHTING
    vec4 faceVertexColor = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
#else
    vec4 faceVertexColor = vertexColor;
#endif

#ifdef DISSOLVE
    if (faceVertexColor.a < texture(DissolveMaskSampler, texCoord0).a) {
        discard;
    }
    faceVertexColor.a = 1.0;
#endif

    color *= faceVertexColor * ColorModulator;
#ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
#ifndef EMISSIVE
    color *= lightMapColor;
#endif

    fragColor = color;
}
