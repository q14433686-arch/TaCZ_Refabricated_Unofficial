#version 330
#extension GL_ARB_separate_shader_objects : require

// 【镜内文字】顶点着色器 —— 26.3 assets/minecraft/shaders/core/text.vsh 的
// 逐行拷贝，零改动。裁剪完全发生在片元侧（屏幕空间掩码采样），顶点侧
// 不需要任何额外数据。保留 IS_GUI/IS_SEE_THROUGH 分支是为了保持与母本
// 逐行同构，便于 vanilla 更新时 diff 对照 —— 我们的管线从不定义这两个宏，
// 恒走世界文字路径（fog + lightmap）。
//
// 【26.3 方言变更】#moj_import -> #include、属性/varying 显式
// layout(location = N)、需 GL_ARB_separate_shader_objects。

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#include <minecraft:sample_lightmap.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 3) in ivec2 UV2;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif

layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
// 同 scope_body.vsh：屏幕位置（NDC→[0,1]）供掩码采样，绕开 gl_FragCoord 原点/ScreenSize 问题。
layout(location = 4) out vec2 scopeUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    scopeUv = (gl_Position.xy / gl_Position.w) * 0.5 + 0.5;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
#else
    vertexColor = Color;
#endif
    texCoord0 = UV0;
}
