// Pack lens columns written by ScopeLensFrame.uniforms. Coordinates are normalized in
// the current output (mask) and source (world/heat) images, not the hand projection.
uniform sampler2D HeatSampler;
#define Optics    ModelViewMat[0]  // distortion, aberration, edge width, vignette strength
#define Sensor    ModelViewMat[1]  // vignette width, gain, noise, seconds
#define TintMode  ModelViewMat[2]  // tint RGB, vision mode

// Bounded local estimate of distance to the actual cutout, not a full-screen radial
// vignette. Never treat the window frame as a visible lens boundary.
float edgeDistance(vec2 uv, float reach) {
    vec2 size = vec2(textureSize(tacz_WorldDepthSampler, 0));
    float radius = max(min(Aperture.z * size.x, Aperture.w * size.y), 1.0);
    float nearest = reach;
    for (int i = 0; i < 8; ++i) {
        float a = float(i) * 0.78539816;
        vec2 stepUv = radius * vec2(cos(a), sin(a)) / size;
        if (insideOcular(uv + nearest * stepUv)) continue;
        float lo = 0.0, hi = nearest;
        for (int j = 0; j < 4; ++j) {
            float mid = (lo + hi) * 0.5;
            if (insideOcular(uv + mid * stepUv)) lo = mid;
            else hi = mid;
        }
        nearest = hi;
    }
    return nearest;
}

vec3 ironbow(float heat) {
    vec3 cold = vec3(0.015, 0.01, 0.06), purple = vec3(0.30, 0.025, 0.48);
    vec3 red = vec3(0.90, 0.10, 0.035), yellow = vec3(1.0, 0.82, 0.12);
    if (heat < 0.35) return mix(cold, purple, heat / 0.35);
    if (heat < 0.65) return mix(purple, red, (heat - 0.35) / 0.30);
    if (heat < 0.85) return mix(red, yellow, (heat - 0.65) / 0.20);
    return mix(yellow, vec3(1.0), (heat - 0.85) / 0.15);
}

vec3 lensColor(vec3 scene, vec2 sceneUv, float edge) {
    int mode = int(TintMode.w);
    vec3 color = scene * TintMode.rgb;
    float luma = clamp(dot(scene, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
    if (mode != 0) {
        float grain = fract(sin(dot(floor(gl_FragCoord.xy), vec2(12.9898, 78.233))
                + floor(Sensor.w * 30.0) * 37.719) * 43758.5453) - 0.5;
        if (mode < 3) {
            float signal = clamp(1.0 - exp(-pow(luma, 0.65) * Sensor.y) + grain * Sensor.z, 0.0, 1.0);
            color = signal * (mode == 1 ? vec3(0.18, 1.0, 0.28) : vec3(0.82, 0.94, 1.0));
        } else {
            float heat = mix(0.06 + luma * 0.24, 0.78 + luma * 0.20, texture(HeatSampler, sceneUv).a);
            heat = clamp(heat + grain * Sensor.z, 0.0, 1.0);
            color = mode == 3 ? vec3(heat) : (mode == 4 ? vec3(1.0 - heat) : ironbow(heat));
        }
    }
    return color * (1.0 - edge);
}
