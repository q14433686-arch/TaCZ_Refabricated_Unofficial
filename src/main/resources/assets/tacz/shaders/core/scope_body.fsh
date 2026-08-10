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
// 目镜掩码：R = 目镜投影(A)，G = 开镜进度(0~1)。
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
    // 有没有被目镜盖住，与几何自己的贴图 UV 无关。
    //
    // gl_FragCoord.xy 是以【左下】为原点的窗口像素坐标，掩码 target 的
    // 纹理原点同样在左下，两者一致，所以这里【不需要】翻 Y。
    // （调试预览里要翻 V，那是因为 GUI 坐标系原点在左上 —— 两回事，别混。）
    vec2 maskUv = gl_FragCoord.xy / ScreenSize;
    vec2 maskSample = texture(ScopeMaskSampler, maskUv).rg;
    // A = 目镜投影（掩码本体）。
    bool inMask = maskSample.r > 0.5;

    // 掩码距离场采样带宽（UV 单位，约 5.5% 屏高）。
    // 也是「窗口收缩带」的最大宽度：窗口 = A 向内收缩 progress×(1-FINAL_RING_FRACTION)×带宽。
    const float RING_BAND = 0.055;
    // 全开镜时保留的黑圈宽度占带宽的比例。0.65 → 全开时黑圈 ≈ 0.036 UV ≈ 3.6% 屏高。
    // 上游 1.21.1 的圆半径 = 80×modifier×progress，全开时同样保留黑圈 —— 本常量是其等价物。
    const float FINAL_RING_FRACTION = 0.65;

    // 【窗口 B = A 向内收缩】绿通道存的是开镜进度(由 ScopeMaskRenderer 写入
    // ColorModulator.g)。以掩码本身做距离场：采样周围若干环，数一数有多少落在
    // 掩码内。全在内部 -> depth≈1(处于中心深处)；贴着边缘 -> depth≈0。
    // 这样不需要知道圆心在哪，对任意形状的目镜投影都成立。
    //
    // 上游的做法是圆心固定、半径随进度增长（纯二维操作）；我们没有圆心，
    // 用「离边缘的距离」做等价物：窗口 = 掩码向内收缩 progress 比例的带宽。
    // 与上游不同的一点：全开时【不】把收缩带清零 —— 保留
    // FINAL_RING_FRACTION×带宽的黑圈（上游全开时半径停在 80×modifier，同样有黑圈）。
    bool inWindow = false;
    if (inMask) {
        float progress = clamp(maskSample.g, 0.0, 1.0);
        const int RINGS = 3;
        const int STEPS = 8;
        float inside = 0.0;
        float total = 0.0;
        for (int r = 1; r <= RINGS; r++) {
            float radius = RING_BAND * float(r) / float(RINGS);
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
        // 窗口阈值：progress 0→1 时从 1.0（只有最深处算窗口内）线性降到
        // 1 - 0.5×(1-FINAL_RING_FRACTION) ≈ 0.825（保留约 3.6% 屏高的黑圈带宽）。
        // 深度值在掩码边缘约 0.5、深入带宽后饱和到 1.0，故 0.5 系数把
        // 「收缩带宽」换算成「深度阈值」。
        float threshold = 1.0 - 0.5 * progress * (1.0 - FINAL_RING_FRACTION);
        inWindow = depth >= threshold;
    }
  #ifdef SCOPE_MASK_WINDOW
    // 【窗口裁切】只裁掉窗口内 —— 用于目镜黑圈、枪体与配件。
    // 目镜：落在窗口内(镜片中央)的部分不画，只保留窗口外的边缘带 = 黑圈
    //       （等价于上游 stencilFunc(EQUAL, i+1) 画的「圆外目镜遮罩」）。
    // 枪体/配件：镜内不该出现枪的任何部分（用户要求「镜内只剩世界+准星」）。
    if (inWindow) {
        discard;
    }
  #elif defined(SCOPE_MASK_INVERT)
    // 【反向】只保留窗口内 —— 用于准星（分划）。
    // 上游对准星用的是 stencilFunc(GL_EQUAL, ~(i+1))，即「只在窗口内绘制」
    // （renderDivisionOnly / renderOcularAndDivision 均如此）。
    // 少了这一步，准星就会溢出镜筒、贴在屏幕上不受镜框约束。
    if (!inWindow) {
        discard;
    }
  #else
    // 【镜身】整块目镜投影内都不画 —— 上游 stencilFunc(GL_EQUAL, 0)。
    // 落在目镜投影内属于「镜内」，镜身不该出现，让世界画面透出来；
    // 窗口外的边缘带（黑圈）由目镜单独绘制（SCOPE_MASK_WINDOW 那一支），
    // 不会露出镜筒内壁。
    if (inMask) {
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
