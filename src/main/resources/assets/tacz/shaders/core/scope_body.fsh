#version 330

// Scope body fragment shader — vanilla entity.fsh clone with ocular mask clipping.
//
// When SCOPE_MASK is defined, pixels inside the ocular projection are discarded
// (for scope body) or pixels outside are discarded (for reticle, with SCOPE_MASK_INVERT).
//
// This replaces the old depth-manipulation approach (scope_depth_cleanup.fsh and
// scope_reticle_mask.fsh). The mask is an offscreen RGBA8 texture rendered at the
// phase boundary — the depth buffer is never touched.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

#ifdef SCOPE_MASK
// Ocular mask: R = inside ocular (1=covered), G = aiming progress (0-1).
// Rendered to offscreen FBO by ScopeMaskRenderer at the phase boundary.
uniform sampler2D ScopeMaskSampler;
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
#ifdef SCOPE_MASK
    // Use screen-space position to sample the mask — we want to know
    // "is the ocular lens covering THIS pixel on screen", not the model UV.
    vec2 maskUv = gl_FragCoord.xy / ScreenSize;
    vec2 maskSample = texture(ScopeMaskSampler, maskUv).rg;
    bool insideOcular = maskSample.r > 0.5;

    // ADS edge softening: as the player aims down sights, the mask boundary
    // smoothly contracts from the edges toward the center. The green channel
    // carries the current aiming progress (0 = hipfire, 1 = fully aimed).
    if (insideOcular) {
        float progress = maskSample.g;
        if (progress < 0.999) {
            // Ring-based distance field: sample N rings around the current pixel,
            // count how many are inside the mask. Pixels near the edge have lower
            // "depth" values, pixels deep inside have depth near 1.0.
            const int RINGS = 3;
            const int STEPS = 8;
            float inside = 0.0;
            float total = 0.0;
            float unit = 0.055; // ~5.5% of screen height
            for (int r = 1; r <= RINGS; r++) {
                float radius = unit * float(r) / float(RINGS);
                for (int i = 0; i < STEPS; i++) {
                    float a = 6.2831853 * float(i) / float(STEPS);
                    vec2 off = vec2(cos(a), sin(a)) * radius;
                    // Aspect ratio correction
                    off.x *= ScreenSize.y / max(ScreenSize.x, 1.0);
                    total += 1.0;
                    inside += texture(ScopeMaskSampler, maskUv + off).r > 0.5 ? 1.0 : 0.0;
                }
            }
            float depth = total > 0.0 ? inside / total : 1.0;
            // Fade from edge: pixels near boundary are temporarily not "inside"
            if (depth < 1.0 - progress) {
                insideOcular = false;
            }
        }
    }

#ifdef SCOPE_MASK_INVERT
    // Reticle: only draw INSIDE the ocular projection
    if (!insideOcular) {
        discard;
    }
#else
    // Scope body: only draw OUTSIDE the ocular projection
    if (insideOcular) {
        discard;
    }
#endif
#endif // SCOPE_MASK

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

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
