#version 330
#extension GL_ARB_separate_shader_objects : require

// 瞄具镜身顶点着色器。
//
// 与 26.3 的 assets/minecraft/shaders/core/entity.vsh 【逐字节相同】,
// 仅因引擎要求 vsh/fsh 同名配对而单独存在一份。
// 所有裁剪逻辑都在 scope_body.fsh 里, 本文件不含任何自定义改动。
//
// 若将来 vanilla 改了 entity.vsh, 直接原样覆盖本文件即可(保留本段注释)。
//
// 【26.3 方言变更】着色器改由 shaderc 编译成 SPIR-V:
//   1. #moj_import 废除, 换成真正的 #include(带 include callback 解析);
//   2. 顶点属性与 varying 必须显式 layout(location = N);
//   3. 需要 GL_ARB_separate_shader_objects。
// 旧写法在 26.3 下的表现: #moj_import 整行被 shaderc 当未知预处理指令,
// fog.glsl 根本没被引入, 于是报 'fog_cylindrical_distance': no matching
// overloaded function found(2026-09-18 实机日志第 1 行)。

#if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
#include <minecraft:light.glsl>
#endif
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

#if !defined(NO_OVERLAY) && !defined(OIT_ALPHA_ONLY)
uniform sampler2D Sampler1;
#endif

#if !defined(EMISSIVE) && !defined(OIT_ALPHA_ONLY)
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
layout(location = 2) out vec4 vertexPerFaceColorBack;
layout(location = 3) out vec4 vertexPerFaceColorFront;
#else
layout(location = 2) out vec4 vertexColor;
#endif

#ifndef EMISSIVE
layout(location = 4) out vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
layout(location = 5) out vec4 overlayColor;
#endif

layout(location = 6) out vec2 texCoord0;
#ifdef GLINT
layout(location = 7) out vec2 texCoordGlint;
#endif
// 【2026-09-19 新增，偏离 vanilla entity.vsh 的唯一一处】
// 把本顶点的屏幕位置（NDC→[0,1]）传给片元，供掩码采样用。
// 原先 scope_body.fsh 用 gl_FragCoord.xy/ScreenSize 现算，实测在 26.3 下静默
// 失效（目镜不裁，case3：裁剪类型已选、掩码有内容）。两个可能成因都被这一改动
// 绕开：① 26.3 renderpearl 用 clip control，掩码 target 的纹素原点与 gl_FragCoord
//    的左下原点可能上下镜像；② Globals.ScreenSize 在手部 pass 可能未绑定而为 0，
//    gl_FragCoord/0 = inf，采样恒 0。
// NDC 坐标与掩码绘制走同一个 clip-space→纹素映射，与原点/分辨率约定都无关。
layout(location = 8) out vec2 scopeUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // 屏幕位置 → [0,1]。w 除算把齐次坐标拉回 NDC；对镜身几何 w 恒正。
    scopeUv = (gl_Position.xy / gl_Position.w) * 0.5 + 0.5;

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

#ifdef PER_FACE_LIGHTING
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, Normal);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-light, Color);
    vertexPerFaceColorFront = minecraft_mix_light_separate(light, Color);
#elif defined(NO_CARDINAL_LIGHTING)
    vertexColor = Color;
#else
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
#endif

#if !defined(EMISSIVE) && !defined(OIT_ALPHA_ONLY)
    lightMapColor = sample_lightmap(Sampler2, UV2);
#endif

#if !defined(NO_OVERLAY) && !defined(OIT_ALPHA_ONLY)
    overlayColor = texelFetch(Sampler1, UV1, 0);
#endif

    texCoord0 = UV0;

#ifdef APPLY_TEXTURE_MATRIX
    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif

#ifdef GLINT
    texCoordGlint = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif
}
