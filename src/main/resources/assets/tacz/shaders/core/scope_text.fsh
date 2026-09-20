#version 330
#extension GL_ARB_separate_shader_objects : require

// 【镜内文字】片元着色器。
//
// 母本：26.3 assets/minecraft/shaders/core/text.fsh（逐行拷贝），
// 只多一段 SCOPE_MASK —— 与 scope_body.fsh 的 SCOPE_MASK_INVERT 分支
// 同一语义：只保留目镜投影【内】的像素（准星的约束方式）。
// 简化说明：文字与准星一样是小几何，永远不可能是遮光板，所以这里
// 不需要 scope_body.fsh 里那套「开镜渐进收缩带」距离场（那是为
// 镜身/遮光板的渐进开镜观感服务的）——文字的淡入交给顶点色 alpha
// （Java 侧按 aimingProgress 算好塞进 Color），掩码只做硬裁剪。
//
// 如果将来 vanilla 改了 text.fsh，这里要跟着同步（同 scope_body 的约定）。
//
// 【26.3 方言变更】同上：#moj_import -> #include、varying 显式 location。

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#ifdef SCOPE_MASK
// globals.glsl 提供 ScreenSize（scope_body.fsh 同款用法）。
#include <minecraft:globals.glsl>
#endif
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#ifdef SCOPE_MASK
// 目镜掩码：白 = 该像素属于镜内（目镜投影覆盖），黑 = 镜外。
// 由 ScopeMaskRenderer 在阶段边界渲染到离屏 target。
uniform sampler2D ScopeMaskSampler;
// mode 2 标记采样器：同 scope_body.fsh，文字属「镜外 discard」一族，
// 供 Java 侧按 draw 判别 mode，GLSL 从不采样。
uniform sampler2D ScopeMaskMode2Sampler;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif

layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    #endif

    #if !defined(IS_SEE_THROUGH) && !defined(IS_GUI)

    #ifdef OIT_ACCUMULATE
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    color = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #endif

    return color;
}

void main() {
#ifdef SCOPE_MASK
    // 与 scope_body.fsh 完全一致的采样约定：gl_FragCoord 左下原点，
    // 掩码 target 纹理原点也在左下，不翻 Y。
    vec2 maskUv = gl_FragCoord.xy / ScreenSize;
    if (texture(ScopeMaskSampler, maskUv).r <= 0.5) {
        // 目镜投影之外 —— 文字被镜筒挡住，不可见。
        // 这正是「MK5HD 弹药计数穿出目镜」一案的裁剪点。
        discard;
    }
#endif

    #ifdef IS_GRAYSCALE
    vec4 texColor = texture(Sampler0, texCoord0).rrrr;
    #else
    vec4 texColor = texture(Sampler0, texCoord0);
    #endif

    vec4 color = texColor * vertexColor * ColorModulator;

    if (color.a < 0.1) {
        discard;
    }

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
