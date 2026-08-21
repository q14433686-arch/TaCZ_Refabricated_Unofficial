#version 330

// 瞄准镜「镜内画中画」合成片元着色器。
//
// 一个全屏三角形（顶点由 vanilla core/screenquad.vsh 用 gl_VertexID 造出来），
// 逐像素问两句：
//   1. 这个像素属不属于目镜孔径？不属于 -> discard，主画面原样保留。
//   2. 属于 -> 从「本帧世界画面的拷贝」里按倍率重采样，输出放大后的世界。
//
// 它是 scope_body.fsh 的镜像：那边在孔径内 discard 让路，这边在孔径内落笔。
// 两者的「孔径判定」必须【逐行一致】，否则会出现一圈既没被镜身画、
// 也没被 PIP 贴的裂缝。下面那一段就是从 scope_body.fsh 原样搬来的，
// 改动它时两个文件必须一起改。

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// 本帧世界画面的拷贝（未含枪与手 —— 拷贝卡在世界画完、视模开画之前）。
// 名字沿用 vanilla blit 系列的 InSampler，好直接复用 BindGroupLayouts.IN_SAMPLER。
uniform sampler2D InSampler;

// 目镜掩码：白 = 该像素属于镜内（目镜投影覆盖），黑 = 镜外。
// 绿通道存开镜进度，见下方收缩逻辑。
uniform sampler2D ScopeMaskSampler;

in vec2 texCoord;

out vec4 fragColor;

// ---------------------------------------------------------------------------
// Catmull-Rom 双三次重建（9 抽头版）
//
// 镜内画面的放大倍数【就是】瞄具倍率：屏幕上直径 D 的镜片，映射回原画面只有
// D/Z 个像素，却要铺满 D 个像素。6 倍镜 = 6× 放大。这种量级的放大下，
// 硬件双线性是最差的选择 —— 它在放大时等价于线性插值，边缘会糊成一片。
//
// Catmull-Rom 是插值型三次样条（过样本点、C¹ 连续），放大时明显比双线性锐利，
// 且不像最近邻那样出块。经典做法是 4×4=16 次点采样，这里用 Matt Pettineo 那套
// 把每个方向的中间两抽头合并成一次【硬件双线性抽头】的写法，降到 9 次采样 ——
// 前提是采样器必须是 LINEAR（合成阶段就是这么绑的）。
//
// 代价：三次样条会在高对比边缘轻微过冲（振铃），可能产生负值，
// 所以结果要 max(0.0) 夹一下。
// ---------------------------------------------------------------------------
vec3 sampleCatmullRom(sampler2D tex, vec2 uv, vec2 texSize) {
    vec2 samplePos = uv * texSize;
    vec2 texPos1 = floor(samplePos - 0.5) + 0.5;
    vec2 f = samplePos - texPos1;

    // Catmull-Rom 的四个基函数（张力 0.5）
    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);

    // 中间两抽头合并：用一次偏移过的双线性采样代替两次点采样
    vec2 w12 = w1 + w2;
    vec2 offset12 = w2 / max(w12, vec2(1.0e-5));

    vec2 texPos0 = (texPos1 - 1.0) / texSize;
    vec2 texPos3 = (texPos1 + 2.0) / texSize;
    vec2 texPos12 = (texPos1 + offset12) / texSize;

    vec3 result = vec3(0.0);
    result += texture(tex, vec2(texPos0.x,  texPos0.y)).rgb  * w0.x  * w0.y;
    result += texture(tex, vec2(texPos12.x, texPos0.y)).rgb  * w12.x * w0.y;
    result += texture(tex, vec2(texPos3.x,  texPos0.y)).rgb  * w3.x  * w0.y;

    result += texture(tex, vec2(texPos0.x,  texPos12.y)).rgb * w0.x  * w12.y;
    result += texture(tex, vec2(texPos12.x, texPos12.y)).rgb * w12.x * w12.y;
    result += texture(tex, vec2(texPos3.x,  texPos12.y)).rgb * w3.x  * w12.y;

    result += texture(tex, vec2(texPos0.x,  texPos3.y)).rgb  * w0.x  * w3.y;
    result += texture(tex, vec2(texPos12.x, texPos3.y)).rgb  * w12.x * w3.y;
    result += texture(tex, vec2(texPos3.x,  texPos3.y)).rgb  * w3.x  * w3.y;

    return max(result, vec3(0.0));
}

void main() {
    // texCoord 与掩码/场景拷贝同为左下原点、同一尺寸比例，
    // 三者可以直接共用一套归一化坐标，不需要任何翻转或纵横比换算。
    vec2 maskSample = texture(ScopeMaskSampler, texCoord).rg;
    bool insideOcular = maskSample.r > 0.5;

    // 【开镜渐进】与 scope_body.fsh 同一份逻辑：沿掩码边缘向内收缩。
    // 圆心不动、只有覆盖范围随进度增长 —— 这是上游
    //     rad = 80 * modifier * aimingProgress
    // 的二维等价物。用掩码自身当距离场，因此对任意形状的目镜投影都成立。
    if (insideOcular) {
        float progress = maskSample.g;
        if (progress < 0.999) {
            const int RINGS = 3;
            const int STEPS = 8;
            float inside = 0.0;
            float total = 0.0;
            float unit = 0.055;
            for (int r = 1; r <= RINGS; r++) {
                float radius = unit * float(r) / float(RINGS);
                for (int i = 0; i < STEPS; i++) {
                    float a = 6.2831853 * float(i) / float(STEPS);
                    vec2 off = vec2(cos(a), sin(a)) * radius;
                    off.x *= ScreenSize.y / max(ScreenSize.x, 1.0);
                    total += 1.0;
                    inside += texture(ScopeMaskSampler, texCoord + off).r > 0.5 ? 1.0 : 0.0;
                }
            }
            float depth = total > 0.0 ? inside / total : 1.0;
            if (depth < 1.0 - progress) {
                insideOcular = false;
            }
        }
    }

    if (!insideOcular) {
        // 镜外：主画面（未变焦的世界）原样保留。
        discard;
    }

    // 【诊断 · ScopePipDebugPaintLens】把合成覆盖到的区域涂成纯品红。
    //
    // 「放大画面溢出到镜外」有两种完全不同的成因，成品画面上分不出来：
    //   ① 合成没被掩码约束住，整屏都画了 —— 那么整个屏幕会变品红；
    //   ② 合成是对的，溢出来自别处 —— 那么只有镜片是品红。
    // 涂纯色能一眼看出合成到底盖了多大范围，比「关掉合成看还漏不漏」更直接：
    // 后者只能告诉你「不是合成」，前者直接把合成的真实覆盖范围画出来。
    //
    // 标志走 ColorModulator.b（0 = 诊断，1 = 正常）——
    // 那个通道本来就是常量 1.0 的空闲载体，不必再动 bind group layout。
    if (ColorModulator.b < 0.5) {
        fragColor = vec4(1.0, 0.0, 1.0, 1.0);
        return;
    }

    // 【镜内重投影】倍率由 ColorModulator.r 送进来（见 ScopePipRenderer 的合成阶段）。
    //
    // 透视投影的恒等关系：把 FOV 压窄 Z 倍，等价于绕光轴把画面放大 Z 倍。
    //     窄FOV下的 NDC = 宽FOV下的 NDC × Z
    // 反过来，要知道「窄FOV画面在这个像素上是什么」，就到宽FOV画面的
    //     center + (uv − center) / Z
    // 处去取。这不是近似 —— 它与「用窄 FOV 把世界重画一遍」逐像素等价。
    //
    // 因为 |uv − center| <= 0.5 且 Z >= 1，采样点必然落在 [0,1] 内，
    // 不会碰到纹理边界，无需额外 clamp。
    float magnification = max(ColorModulator.r, 1.0);
    vec2 center = vec2(0.5);
    vec2 sceneUv = center + (texCoord - center) / magnification;

    // 重建：双三次而不是双线性，理由见 sampleCatmullRom 的注释。
    vec3 color = sampleCatmullRom(InSampler, sceneUv, ScreenSize);

    // 【锐化】按倍率加权的钝化蒙版（unsharp mask）。
    //
    // 放大倍数就是瞄具倍率，所以低倍镜几乎不需要锐化、高倍镜很需要 ——
    // 强度随倍率从 1× 的 0 线性升到 6× 的满值，超过 6× 保持满值。
    // 这样 ACOG 不会被过度处理，8 倍镜也不会锐化不足。
    //
    // 抽头取在【源图】的相邻像素上（1/ScreenSize），即「先锐化再放大」：
    // 高频细节本来就只存在于源图的采样率上，在放大后的坐标系里做邻域
    // 只会放大插值产生的伪细节，而且每个抽头都要再跑一遍 Catmull-Rom，不划算。
    float sharpenAmount = ColorModulator.g * clamp((magnification - 1.0) / 5.0, 0.0, 1.0);
    if (sharpenAmount > 0.001) {
        vec2 texel = 1.0 / ScreenSize;
        vec3 blur = texture(InSampler, sceneUv + vec2( texel.x, 0.0)).rgb
                  + texture(InSampler, sceneUv + vec2(-texel.x, 0.0)).rgb
                  + texture(InSampler, sceneUv + vec2(0.0,  texel.y)).rgb
                  + texture(InSampler, sceneUv + vec2(0.0, -texel.y)).rgb;
        blur *= 0.25;
        // 经典 unsharp：原图 + k ×（原图 − 模糊）。夹到 0 以上，
        // 避免在高对比边缘的暗侧被推成负值（叠加 Catmull-Rom 自身的过冲会更明显）。
        color = max(color + (color - blur) * sharpenAmount, vec3(0.0));
    }

    // alpha 固定 1.0：管线的写掩码是 WRITE_COLOR，alpha 通道压根不会被写入，
    // 这里给什么都不影响结果；写 1.0 只是让「这是一块不透明的实心画面」在源码里读得出来。
    fragColor = vec4(color, 1.0);
}
