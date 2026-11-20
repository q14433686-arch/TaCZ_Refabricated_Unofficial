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
    float dx = abs(dFdx(z)), dy = abs(dFdy(z));
    // A few depth-buffer quantization steps, not a distance-dependent silhouette dilation.
    const float rounding = 4.0 / 16777215.0;
    float delta = abs(z - scene);
    if (delta <= rounding) return scene;
    // A pixel of XY mismatch on a slope is meaningful only if the *scene* has that
    // slope too. Bound the match by 1/64 block along eye Z. At discontinuities use
    // the smaller one-sided difference, not the depth jump to the background.
    float cap = 0.5 * abs(ProjMat[3][2]) * gl_FragCoord.w * gl_FragCoord.w / 64.0;
    if (delta > rounding + min(cap, dx + dy)) return z;
    float left  = texelFetch(SceneDepthSampler, max(p-ivec2(1,0),ivec2(0)), 0).r;
    float right = texelFetch(SceneDepthSampler, min(p+ivec2(1,0),hi), 0).r;
    float below = texelFetch(SceneDepthSampler, max(p-ivec2(0,1),ivec2(0)), 0).r;
    float above = texelFetch(SceneDepthSampler, min(p+ivec2(0,1),hi), 0).r;
    float agreed = min(dx, min(abs(scene-left), abs(right-scene))) + min(dy, min(abs(scene-below), abs(above-scene)));
    return delta <= rounding + agreed ? scene : z;
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
