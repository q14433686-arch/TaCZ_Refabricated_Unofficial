#version 330

// Depth-based scope PIP diagnostic fragment shader.
//
// This is Step 2 only: paint the ocular aperture with pure magenta so the user can visually
// confirm that the "ad < wd - epsilon" criterion identifies exactly the lens. It does NOT
// re-project the world yet.
//
// It is intentionally a twin of scope_reticle_mask.fsh's aperture judgement:
//   wd = exact pre-ocular world depth
//   ad = world depth + ocular near-depth, copied before the scope body draw
//   keep only ad < wd - epsilon
//
// The fullscreen vertex stage supplies texCoord in [0,1]; both depth copies are the same
// dimensions as the main target, so a single normalized coordinate serves both samplers.

uniform sampler2D tacz_WorldDepthSampler;
uniform sampler2D tacz_ApertureDepthSampler;

in vec2 texCoord;

out vec4 fragColor;

const float TACZ_MASK_EPSILON = 1.0e-6;

void main() {
    float wd = texture(tacz_WorldDepthSampler, texCoord).r;
    float ad = texture(tacz_ApertureDepthSampler, texCoord).r;
    if (!(ad < wd - TACZ_MASK_EPSILON)) {
        discard;
    }
    fragColor = vec4(1.0, 0.0, 1.0, 1.0);
}
