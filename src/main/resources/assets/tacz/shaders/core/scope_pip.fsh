#version 330

// Step 3: real scope PIP composite.
//
// The vertex stage (minecraft:core/screenquad) supplies texCoord in [0,1]. The aperture test is
// the same binary depth comparison used by scope_reticle_mask.fsh / scope_pip_debug.fsh:
//   wd = exact pre-ocular world depth
//   ad = world depth + ocular near-depth, copied before the scope body draw
//   keep only ad < wd - epsilon

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D tacz_SceneColorSampler;
uniform sampler2D tacz_WorldDepthSampler;
uniform sampler2D tacz_ApertureDepthSampler;

in vec2 texCoord;

out vec4 fragColor;

#define Aperture ModelViewMat[3] // measured UV centre and half extents, plain and authored lenses

bool insideOcular(vec2 uv) {
    return texture(tacz_ApertureDepthSampler, uv).r < texture(tacz_WorldDepthSampler, uv).r - 1e-6;
}

#ifdef TACZ_SCOPE_LENS
#moj_import <tacz:scope_lens.glsl>
#endif

// Iris reprojects a finished frame containing the hand outside the moving ocular. Don't
// magnify a rim texel, including one from a linear/sharpen/aberration tap. The destination
// itself has already passed the depth test and is a clean 1x fallback for irregular cutouts.
vec2 sourceUV(vec2 uv) {
    if (ColorModulator.a < 0.5) return uv; // clean pre-hand/narrow scene, or settled ADS
    vec2 size = vec2(textureSize(tacz_SceneColorSampler, 0));
    vec2 lo = (floor(uv * size - 0.5) + 0.5) / size;
    vec2 hi = lo + 1.0 / size;
    return insideOcular(lo) && insideOcular(hi) && insideOcular(vec2(lo.x, hi.y))
            && insideOcular(vec2(hi.x, lo.y)) ? uv : texCoord;
}

void main() {
    if (!insideOcular(texCoord)) discard;
    float zoom = max(1.0, ColorModulator.r);

    // 【诊断】合成覆盖区域涂纯品红：整屏变品红 = 合成漏出；只有镜片品红 = 覆盖范围正确。
    if (ColorModulator.b > 0.5) {
        fragColor = vec4(1.0, 0.0, 1.0, 1.0);
        return;
    }

    // Off-axis, contract around this frame's ocular, not the middle of the screen (gun/rim).
    // Once the sight straddles the camera axis, return smoothly to its calibrated full-ADS view.
    float offAxis = length((Aperture.xy - 0.5) / max(Aperture.zw, vec2(1e-5)));
    vec2 pivot = mix(vec2(0.5), Aperture.xy, smoothstep(0.25, 1.0, offAxis));
    vec2 centered = mix(texCoord, pivot, 1.0 - 1.0 / zoom);
#ifdef TACZ_SCOPE_LENS
    vec2 relative = (texCoord - Aperture.xy) / zoom;
    float ramp = 0.0, edge = 0.0;
    float reach = 0.465 * max(Optics.z, Sensor.x);
    if (reach > 0.0) {
        float distance = edgeDistance(texCoord, reach);
        if (Optics.z > 0.0) ramp = 1.0 - smoothstep(0.0, min(0.465 * Optics.z, 1.0), distance);
        if (Sensor.x > 0.0) { float f = distance / (0.155 * Sensor.x); edge = exp(-f*f) * Optics.w; }
        centered -= relative * Optics.x * ramp;
    }
#endif
    centered = sourceUV(centered);
    vec3 color = texture(tacz_SceneColorSampler, centered).rgb;

    // 【锐化】钝化蒙版（unsharp mask），抽头取在源图的相邻像素上（先锐化再放大）。
    // 强度按倍率从 1× 的 0 线性升到 6× 的满值，超过 6× 保持满值。
    float sharpenAmount = clamp(ColorModulator.g, 0.0, 1.0)
            * clamp((zoom - 1.0) / 5.0, 0.0, 1.0);
    if (sharpenAmount > 0.001) {
        vec2 texel = 1.0 / vec2(textureSize(tacz_SceneColorSampler, 0));
        vec3 blur = texture(tacz_SceneColorSampler, sourceUV(centered + vec2( texel.x, 0.0))).rgb
                  + texture(tacz_SceneColorSampler, sourceUV(centered + vec2(-texel.x, 0.0))).rgb
                  + texture(tacz_SceneColorSampler, sourceUV(centered + vec2(0.0,  texel.y))).rgb
                  + texture(tacz_SceneColorSampler, sourceUV(centered + vec2(0.0, -texel.y))).rgb;
        blur *= 0.25;
        color = max(color + (color - blur) * sharpenAmount, vec3(0.0));
    }

#ifdef TACZ_SCOPE_LENS
    if (TintMode.w == 0.0 && Optics.y * ramp > 0.0) {
        vec2 split = relative * Optics.y * ramp;
        color.r = texture(tacz_SceneColorSampler, sourceUV(centered - split)).r;
        color.b = texture(tacz_SceneColorSampler, sourceUV(centered + split)).b;
    }
    color = lensColor(color, centered, edge);
#endif
    fragColor = vec4(color, 1.0);
}
