#version 330

// TACZ masked scope text: line-for-line clone of minecraft:shaders/core/rendertype_text.fsh
// (1.21.11) with the ocular screen-space mask branch prepended to main() and the final-overlay
// flag. Fog is intentionally omitted: the same choice scope_reticle_final.fsh already makes,
// because this program runs either from the post-composite overlay flush (ScopeFinalOverlayState,
// where the world fog uniforms no longer describe the frozen hand transform) or from the scope
// body sequence, and in both cases the depth mask - not fog - decides which pixels survive.
//
// The vertex stage stays vanilla rendertype_text.vsh (cloned by ScopeRenderTypes.clonePipeline):
// it emits vertexColor/texCoord0 and reads the lightmap through Sampler2, which is why
// RenderSetup.useLightmap() is required on the Java side.
//
// The mask branch is dormant (tacz_ScopeMaskMode == 0) for every ordinary draw. A text draw that
// owns a fresh ocular aperture copy enables it and passes:
//   tacz_WorldDepthSampler    the pre-ocular world-depth backup (step 1)
//   tacz_ApertureDepthSampler the aperture depth copied before the body draw (step 3)
// Only pixels where apertureDepth < worldDepth - epsilon keep the text; everything else discards,
// clipping the glyphs to the true ocular footprint without any stencil attachment. This is the
// 1.21.11 depth-aperture counterpart of 26.2's 9d036594 in-scope text clipping, and of the
// 26.1.2 port in e1c550ee (their shader, our era's text fsh as the clone source).

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

uniform int tacz_ScopeMaskMode;
// Set by ScopeDepthCopyState whenever the active program declares it: presence marks the
// private-depth-copy path that bypasses destination-depth identity checks. Zero means the mask
// machinery is not driving this draw at all, so nothing is drawn rather than unclipped glyphs.
uniform int tacz_ScopeFinalOverlay;
uniform sampler2D tacz_WorldDepthSampler;
uniform sampler2D tacz_ApertureDepthSampler;

// Guard band for "strictly nearer": equal values mean no ocular fragment wrote that pixel.
const float TACZ_MASK_EPSILON = 1.0e-6;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
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

    // Vanilla text body (1.21.11 rendertype_text.fsh), minus the apply_fog() call.
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = color;
}
