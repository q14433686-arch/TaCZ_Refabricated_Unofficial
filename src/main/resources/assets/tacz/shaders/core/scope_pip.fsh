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
//   wideUV = center + (narrowUV - center) / Z
// which is the exact screen-space equivalent of narrowing the FOV by Z. The magnification is
// baked in at pipeline-build time as TACZ_PIP_ZOOM (steady-state scope zoom; full-ADS only).

uniform sampler2D tacz_SceneColorSampler;
uniform sampler2D tacz_WorldDepthSampler;
uniform sampler2D tacz_ApertureDepthSampler;

in vec2 texCoord;

out vec4 fragColor;

#ifndef TACZ_PIP_ZOOM
#define TACZ_PIP_ZOOM 1.0
#endif

const float TACZ_MASK_EPSILON = 1.0e-6;

void main() {
    float wd = texture(tacz_WorldDepthSampler, texCoord).r;
    float ad = texture(tacz_ApertureDepthSampler, texCoord).r;
    if (!(ad < wd - TACZ_MASK_EPSILON)) {
        discard;
    }
    float zoom = max(1.0, TACZ_PIP_ZOOM);
    vec2 centered = (texCoord - 0.5) / zoom + 0.5;
    fragColor = vec4(texture(tacz_SceneColorSampler, centered).rgb, 1.0);
}
