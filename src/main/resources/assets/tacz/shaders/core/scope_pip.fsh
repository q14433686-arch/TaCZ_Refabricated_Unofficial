#version 330

// Step 3: real scope PIP composite.
//
// The vertex stage (minecraft:core/screenquad) supplies texCoord in [0,1]. The aperture test is
// the same binary depth comparison used by scope_reticle_mask.fsh / scope_pip_debug.fsh:
//   wd = exact pre-ocular world depth
//   ad = world depth + ocular near-depth, copied before the scope body draw
//   keep only ad < wd - epsilon
//
// Inside the aperture we sample the captured pre-hand world color at
//   wideUV = center + (narrowUV - center) / M
// which is the exact screen-space equivalent of narrowing the FOV by M. M is the STEADY-STATE
// LENS zoom (total Z divided by the world-zoom share Z^share, full-ADS only), baked in at
// pipeline-build time as TACZ_PIP_ZOOM. With ScopePipWorldZoomShare=0, M == the scope zoom.

uniform sampler2D tacz_SceneColorSampler;
uniform sampler2D tacz_WorldDepthSampler;
uniform sampler2D tacz_ApertureDepthSampler;

in vec2 texCoord;

out vec4 fragColor;

#ifndef TACZ_PIP_ZOOM
#define TACZ_PIP_ZOOM 1.0
#endif
#ifndef TACZ_PIP_SHARPNESS
#define TACZ_PIP_SHARPNESS 0.0
#endif
#ifndef TACZ_PIP_PAINT_LENS
#define TACZ_PIP_PAINT_LENS 0.0
#endif

const float TACZ_MASK_EPSILON = 1.0e-6;

void main() {
    float wd = texture(tacz_WorldDepthSampler, texCoord).r;
    float ad = texture(tacz_ApertureDepthSampler, texCoord).r;
    if (!(ad < wd - TACZ_MASK_EPSILON)) {
        discard;
    }
    float zoom = max(1.0, TACZ_PIP_ZOOM);

    // 【诊断】合成覆盖区域涂纯品红：整屏变品红 = 合成漏出；只有镜片品红 = 覆盖范围正确。
    if (TACZ_PIP_PAINT_LENS > 0.5) {
        fragColor = vec4(1.0, 0.0, 1.0, 1.0);
        return;
    }

    vec2 centered = (texCoord - 0.5) / zoom + 0.5;
    vec3 color = texture(tacz_SceneColorSampler, centered).rgb;

    // 【锐化】钝化蒙版（unsharp mask），抽头取在源图的相邻像素上（先锐化再放大）。
    // 强度按倍率从 1× 的 0 线性升到 6× 的满值，超过 6× 保持满值。
    float sharpenAmount = clamp(TACZ_PIP_SHARPNESS, 0.0, 1.0)
            * clamp((zoom - 1.0) / 5.0, 0.0, 1.0);
    if (sharpenAmount > 0.001) {
        vec2 texel = 1.0 / vec2(textureSize(tacz_SceneColorSampler, 0));
        vec3 blur = texture(tacz_SceneColorSampler, centered + vec2( texel.x, 0.0)).rgb
                  + texture(tacz_SceneColorSampler, centered + vec2(-texel.x, 0.0)).rgb
                  + texture(tacz_SceneColorSampler, centered + vec2(0.0,  texel.y)).rgb
                  + texture(tacz_SceneColorSampler, centered + vec2(0.0, -texel.y)).rgb;
        blur *= 0.25;
        color = max(color + (color - blur) * sharpenAmount, vec3(0.0));
    }

    fragColor = vec4(color, 1.0);
}
