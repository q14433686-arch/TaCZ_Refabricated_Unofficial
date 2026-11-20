#version 330

uniform sampler2D Sampler0;
in vec2 texCoord0;
out vec4 fragColor;

#ifdef MATCH_SCENE_DEPTH
#moj_import <minecraft:projection.glsl>
uniform sampler2D SceneDepthSampler;

// The replay and the pack may round a rasterized plane differently or jitter its XY
// by a fraction of a pixel. Do not apply polygonOffset(factor,...): an edge-on face
// has unbounded slope and can be pulled in front of an unrelated, much nearer wall.
float nearestSurfaceDepth() {
    ivec2 p = ivec2(gl_FragCoord.xy), hi = textureSize(SceneDepthSampler, 0) - 1;
    float scene = texelFetch(SceneDepthSampler, p, 0).r, z = gl_FragCoord.z;
    vec2 slope = vec2(dFdx(z), dFdy(z));
    const float rounding = 4.0 / 16777215.0;
    float delta = abs(z - scene);
    if (delta <= rounding) return scene;
    // Window Z is affine across a rasterized triangle. An XY jitter of half a pixel
    // in each direction can account for at most this amount of depth disagreement.
    float halfPixelSpan = 0.5 * dot(abs(slope), vec2(1.0));
    // Cap grazing planes as well. One pixel is wider in the world at range, and
    // narrower under a zoom projection. A fixed fraction of a block isn't a pixel.
    float pixelDiagonalZ = 1.414214 * abs(ProjMat[3][2]) * gl_FragCoord.w /
                           (abs(ProjMat[1][1]) * float(hi.y + 1));
    if (delta > rounding + min(halfPixelSpan, pixelDiagonalZ)) return z;
    float left  = texelFetch(SceneDepthSampler, max(p-ivec2(1,0),ivec2(0)), 0).r;
    float right = texelFetch(SceneDepthSampler, min(p+ivec2(1,0),hi), 0).r;
    float below = texelFetch(SceneDepthSampler, max(p-ivec2(0,1),ivec2(0)), 0).r;
    float above = texelFetch(SceneDepthSampler, min(p+ivec2(0,1),hi), 0).r;
    // Require agreement of BOTH signed plane gradients with a real neighbour on
    // each axis. A model edge in front of a uniformly nearer wall cannot qualify.
    vec2 error = min(abs(vec2(scene-left, scene-below) - slope), abs(vec2(right-scene, above-scene) - slope));
    return all(lessThanEqual(error, vec2(2.0 * rounding))) ? scene : z;
}
#endif

void main() {
#ifdef MATCH_SCENE_DEPTH
    // Still depth-tested against a copy of the scene. The shift probe is EQUAL and
    // read-only; the ordinary coverage pass can also see translucent bodies in front.
    gl_FragDepth = nearestSurfaceDepth();
#endif
    if (texture(Sampler0, texCoord0).a < 0.1) discard;
    fragColor = vec4(1.0);
}
