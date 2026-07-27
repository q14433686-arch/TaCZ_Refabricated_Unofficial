#version 330

// 瞄具镜身片元着色器 —— 在 vanilla core/entity.fsh 之上只加一件事：
// 被目镜盖到的像素 discard。
//
// 这是上游 1.21.1 那句 stencil 的等价物：
//     scope_body: stencilFunc(GL_EQUAL, 0)   // 只在目镜【没盖到】处画镜身
// 26.2 没有模板缓冲，改为采样一张离屏掩码纹理（ScopeMaskSampler）来做同样的二分。
//
// 为什么整份抄一遍 entity.fsh 而不是想办法「继承」：
// GLSL 没有继承，而 vanilla 也不提供可插拔的片元钩子。要在 entity 的
// 渲染语义上加一步 discard，只能复制一份再改。除下面 SCOPE_MASK 那一段外，
// 本文件与 26.2 的 assets/minecraft/shaders/core/entity.fsh 逐行一致 ——
// 如果将来 vanilla 改了 entity.fsh，这里要跟着同步。

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

#ifdef SCOPE_MASK
// 目镜掩码：白 = 该像素属于镜内（目镜投影覆盖），黑 = 镜外。
// 由 ScopeMaskRenderer 在阶段边界渲染到离屏 target。
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
    // 用 gl_FragCoord 而不是 texCoord0：我们要问的是「屏幕上这个位置」
    // 有没有被目镜盖住，与镜身自己的贴图 UV 无关。
    //
    // gl_FragCoord.xy 是以【左下】为原点的窗口像素坐标，掩码 target 的
    // 纹理原点同样在左下，两者一致，所以这里【不需要】翻 Y。
    // （调试预览里要翻 V，那是因为 GUI 坐标系原点在左上 —— 两回事，别混。）
    vec2 maskUv = gl_FragCoord.xy / ScreenSize;
    vec2 maskSample = texture(ScopeMaskSampler, maskUv).rg;
    bool insideOcular = maskSample.r > 0.5;

    // 【开镜渐进】绿通道存的是开镜进度(由 ScopeMaskRenderer 写入 ColorModulator.g)。
    //
    // 上游的做法是: 圆心固定在目镜投影中心, 只让半径随进度增长 ——
    //     centerX/centerY = getBedrockPartCenter(...)  // 固定不动
    //     rad = 80 * modifier * aimingProgress         // 只有半径在变
    // 关键在于这是【纯二维】操作: 位置不动, 只有覆盖范围在变。
    //
    // 早前的实现是按进度缩放 3D 目镜几何, 那在透视投影下会连带改变投影【位置】,
    // 观感就是镜内区域从画面外"飞"进来 —— 用户实测到的第 2 个问题。
    //
    // 这里改成等价的二维操作: 沿掩码边缘向内收缩。progress 小时只保留
    // 深处的像素(离边缘远的), progress=1 时保留全部。位置始终不动。
    if (insideOcular) {
        float progress = maskSample.g;
        if (progress < 0.999) {
            // 以掩码本身做距离场: 采样周围若干环, 数一数有多少落在掩码内。
            // 全在内部 -> depth≈1(处于中心深处); 贴着边缘 -> depth≈0。
            // 这样不需要知道圆心在哪, 对任意形状的目镜投影都成立
            // (我们的掩码是多边形投影, 不是正圆)。
            const int RINGS = 3;
            const int STEPS = 8;
            float inside = 0.0;
            float total = 0.0;
            // 收缩带宽度, 以 UV 为单位。取 0.055 约等于屏幕高度的 5.5%,
            // 对默认枪包里最大的目镜投影也够覆盖到中心。
            float unit = 0.055;
            for (int r = 1; r <= RINGS; r++) {
                float radius = unit * float(r) / float(RINGS);
                for (int i = 0; i < STEPS; i++) {
                    float a = 6.2831853 * float(i) / float(STEPS);
                    vec2 off = vec2(cos(a), sin(a)) * radius;
                    // 纵横比修正: UV 空间里同样的数值在 x/y 上对应不同像素数
                    off.x *= ScreenSize.y / max(ScreenSize.x, 1.0);
                    total += 1.0;
                    inside += texture(ScopeMaskSampler, maskUv + off).r > 0.5 ? 1.0 : 0.0;
                }
            }
            float depth = total > 0.0 ? inside / total : 1.0;
            // depth < 1-progress 的像素(靠近边缘的)暂时不算"镜内"
            if (depth < 1.0 - progress) {
                insideOcular = false;
            }
        }
    }
  #ifdef SCOPE_MASK_INVERT
    // 【反向】只保留镜内 —— 用于准星（分划）。
    // 上游对准星用的是 stencilFunc(GL_EQUAL, i+1)，即「只在第 i 个目镜的
    // 投影区内绘制」（renderDivisionOnly / renderOcularAndDivision 均如此）。
    // 少了这一步，准星就会溢出镜筒、贴在屏幕上不受镜框约束。
    if (!insideOcular) {
        discard;
    }
  #else
    // 落在目镜投影内 —— 这里属于「镜内」，镜身不该出现，
    // 让后面的世界画面透出来。等价于上游 stencilFunc(GL_EQUAL, 0)。
    if (insideOcular) {
        discard;
    }
  #endif
#endif

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
