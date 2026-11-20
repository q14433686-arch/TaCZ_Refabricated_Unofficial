#version 330

// TACZ 光影后置目镜框片元 —— 26.2 的 assets/minecraft/shaders/core/entity.fsh
// 去掉 apply_fog() 的版本（除此之外逐行一致，vanilla 若改 entity.fsh 要跟着同步）。
//
// 为什么去雾：这条管线画在 Iris 全部 composite/final pass 与 PIP 合成【之后】，
// 是纯前景层。此刻 Fog uniform 块里挂的是世界那套雾参数，套上去目镜框会被
// 距离雾染色（1.21.11 母本 scope_ring_final.fsh 同一结论）。
// 顶点数据、lightmap、overlay 全部保持原版语义。

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
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

#ifdef SCOPE_MASK
uniform sampler2D ScopeMaskSampler;
#endif
void main() {
#ifdef RETICLE_COVERAGE
    vec4 color = coveredInk(); // evaluate derivatives before any nonuniform discard
    if (color.a == 0.0) discard;
#endif
#ifdef SCOPE_MASK
    if (texture(ScopeMaskSampler, gl_FragCoord.xy / vec2(textureSize(ScopeMaskSampler, 0))).r < 0.5) discard;
#endif
#ifndef RETICLE_COVERAGE
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

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

    fragColor = color;
}
