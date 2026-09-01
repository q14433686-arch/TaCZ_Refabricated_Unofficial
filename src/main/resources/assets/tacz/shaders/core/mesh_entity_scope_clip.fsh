#version 330

// TACZ mesh-GPU viewmodel clipping: verbatim clone of tacz:core/scope_flash_clip.fsh with the
// tacz_ScopeMaskMode uniform branch hardwired to mode 2 (outside-ocular discard). The uniform
// exists for the vanilla RenderType path, whose DepthCopyRenderType can rewrite it per draw; the
// mesh GPU hand list instead picks this pipeline variant ONLY when ScopeDepthCopyState reports a
// valid mask cycle for the current frame (Java-side gate, fail-open to the plain pipeline), so
// the branch can be constant. See PolyMeshGpuRenderer.LIT_PIPELINE_CLIP.


// TACZ viewmodel clipping shader: verbatim clone of minecraft:shaders/core/entity.fsh (26.1.2)
// with an outside-ocular screen-space mask prepended to main(). Each cloned source pipeline keeps
// its original defines and render states, so gun cutouts and both flash layers are unchanged outside
// the lens.
//
// Mode 2 is enabled only for a first-person gun submission that queued an ocular aperture. Pixels
// where apertureDepth < worldDepth - epsilon are inside that true projected aperture and discard;
// mode 0 is a fail-open path that renders the ordinary unmasked effect.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

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
    if (true) {
        vec2 taczWorldUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_WorldDepthSampler, 0)), vec2(1.0));
        vec2 taczApertureUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_ApertureDepthSampler, 0)), vec2(1.0));
        float taczWorldDepth = texture(tacz_WorldDepthSampler, taczWorldUv).r;
        float taczApertureDepth = texture(tacz_ApertureDepthSampler, taczApertureUv).r;
        bool taczInsideOcular = taczApertureDepth < taczWorldDepth - TACZ_MASK_EPSILON;
        if (taczInsideOcular) {
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

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
