#version 330

// TACZ final reticle shader: no-fog entity clone with the
// ocular screen-space mask branch prepended to main(). It is drawn after Iris final compositing;
// shader defines (EMISSIVE / ALPHA_CUTOUT / NO_OVERLAY / PER_FACE_LIGHTING / DISSOLVE) and
// uniform blocks remain compatible with the source pipeline, except fog is intentionally omitted.
//
// The branch is dormant (tacz_ScopeMaskMode == 0) for every ordinary draw. A reticle draw that
// owns a fresh ocular aperture copy enables it and passes:
//   tacz_WorldDepthSampler    the pre-ocular world-depth backup (step 1)
//   tacz_ApertureDepthSampler the aperture depth copied before the body draw (step 3)
// Only pixels where apertureDepth < worldDepth - epsilon keep the reticle; everything else
// discards, clipping the reticle to the true ocular footprint without any stencil attachment.

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

uniform int tacz_ScopeMaskMode;
// Set by ScopeDepthCopyState only for the Iris post-composite overlay path. Keeping this live
// lets that path bypass destination-depth identity checks and sample its private world copy.
uniform int tacz_ScopeFinalOverlay;
uniform sampler2D tacz_WorldDepthSampler;
uniform sampler2D tacz_ApertureDepthSampler;

// Guard band for "strictly nearer": equal values mean no ocular fragment wrote that pixel.
const float TACZ_MASK_EPSILON = 1.0e-6;

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
    if (tacz_ScopeFinalOverlay == 0) {
        discard;
    }
    if (tacz_ScopeMaskMode != 0) {
        vec2 taczWorldUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_WorldDepthSampler, 0)), vec2(1.0));
        vec2 taczApertureUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_ApertureDepthSampler, 0)), vec2(1.0));
        float taczWorldDepth = texture(tacz_WorldDepthSampler, taczWorldUv).r;
        float taczApertureDepth = texture(tacz_ApertureDepthSampler, taczApertureUv).r;
        if (!(taczApertureDepth < taczWorldDepth - TACZ_MASK_EPSILON)) {
            discard;
        }
    }

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
    // The dissolve effect entirely replaces translucency
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
