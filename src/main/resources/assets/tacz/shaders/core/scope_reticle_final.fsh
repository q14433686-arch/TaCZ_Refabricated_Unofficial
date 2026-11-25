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

#ifdef RETICLE_COVERAGE
in vec2 reticleFace;

// A point sample can miss every stroke of a minified transparent atlas tile. Integrate
// premultiplied colour AND coverage over the pixel, restricted to this authored face.
vec4 coveredInk() {
    vec2 dx = dFdx(texCoord0), dy = dFdy(texCoord0);
    vec2 sx = dFdx(reticleFace), sy = dFdy(reticleFace);
    vec2 edge = 0.5 * (abs(sx) + abs(sy));
    vec2 texels = (abs(dx) + abs(dy)) * vec2(textureSize(Sampler0, 0));
    if (all(greaterThan(reticleFace, edge)) && all(lessThan(reticleFace, 1.0-edge)) && max(texels.x,texels.y) <= 1.0)
        return textureLod(Sampler0, texCoord0, 0.0);
    vec4 sum = vec4(0.0);
    for (int j=0; j<4; ++j) for (int i=0; i<4; ++i) {
        vec2 offset = (vec2(i,j)+0.5)*0.25-0.5;
        vec2 face = reticleFace + offset.x*sx + offset.y*sy;
        if (any(lessThan(face, vec2(0.0))) || any(greaterThanEqual(face, vec2(1.0)))) continue;
        vec4 sampleColor = textureLod(Sampler0, texCoord0 + offset.x*dx + offset.y*dy, 0.0);
        sum += vec4(sampleColor.rgb * sampleColor.a, sampleColor.a);
    }
    return vec4(sum.rgb / max(sum.a, 1e-6), sum.a / 16.0);
}
#endif

out vec4 fragColor;

void main() {
#ifdef RETICLE_COVERAGE
    vec4 color = coveredInk();
#else
    vec4 color = texture(Sampler0, texCoord0);
#endif
    if (color.a == 0.0) discard;
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
