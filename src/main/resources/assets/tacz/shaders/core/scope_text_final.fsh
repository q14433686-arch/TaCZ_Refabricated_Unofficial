#version 330

// TACZ masked scope text: line-for-line clone of minecraft:shaders/core/rendertype_text.fsh
// (26.1.2) with the ocular screen-space mask branch prepended to main() and the
// final-overlay flag. Fog is intentionally omitted: this shader only ever runs in the
// post-composite overlay flush (ScopeFinalOverlayState), where the world fog uniforms no
// longer describe the frozen hand transform - the same choice as scope_reticle_final.fsh.
//
// The branch is dormant (tacz_ScopeMaskMode == 0) for every ordinary draw. A text draw that
// owns a fresh ocular aperture copy enables it and passes:
//   tacz_WorldDepthSampler    the pre-ocular world-depth backup (step 1)
//   tacz_ApertureDepthSampler the aperture depth copied before the body draw (step 3)
// Only pixels where apertureDepth < worldDepth - epsilon keep the text; everything else
// discards, clipping the glyphs to the true ocular footprint without any stencil attachment.
// This is the 26.1.2 depth-aperture counterpart of 26.2's 9d036594 in-scope text clipping.

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

uniform int tacz_ScopeMaskMode;
// Set by ScopeDepthCopyState only for the post-composite overlay path. Keeping this live
// lets that path bypass destination-depth identity checks and sample its private world copy.
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

    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = color;
}
