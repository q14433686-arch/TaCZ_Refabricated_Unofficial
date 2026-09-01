package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 会被目镜掩码裁剪的镜身 RenderType。
 *
 * <h2>它是什么</h2>
 * 与 {@code RenderTypes.entityCutout(texture)} <b>只差一件事</b>：
 * 片元着色器多一步「落在目镜投影内就 discard」。
 * 这是上游那句 {@code scope_body: stencilFunc(GL_EQUAL, 0)} 的等价物。
 *
 * <h2>管线配方（逐项对照 vanilla ENTITY_CUTOUT 的 &lt;clinit&gt; 反汇编）</h2>
 * vanilla 的 {@code ENTITY_CUTOUT}（偏移 1726-1774）是：
 * <pre>
 * builder(ENTITY_SNIPPET)
 *     .withLocation("pipeline/entity_cutout")
 *     .withShaderDefine("ALPHA_CUTOUT", 0.1F)
 *     .withShaderDefine("PER_FACE_LIGHTING")
 *     .withBindGroupLayout(SAMPLER1)
 *     .withCull(false)
 * </pre>
 * 本类<b>完全照抄</b>，只额外加三样：
 * <ul>
 *   <li>{@code withShaderDefine("SCOPE_MASK")} —— 打开 fsh 里的裁剪分支；</li>
 *   <li>自建一个只含 {@code ScopeMaskSampler} 的 bind group layout —— 声明掩码采样器
 *       （{@code BindGroupLayouts} 只到 SAMPLER2，没有 SAMPLER3，
 *       额外采样器要仿 vanilla {@code DissolveMaskSampler} 自建）；</li>
 *   <li>换成我们自己的 {@code scope_body} shader（vsh 与 vanilla entity.vsh 逐字节相同，
 *       fsh 只多了 SCOPE_MASK 那一段）。</li>
 * </ul>
 *
 * <p>为什么必须显式带上 {@code SAMPLER1}：{@code ENTITY_SNIPPET} 用的是
 * {@code SAMPLER0_SAMPLER2}（没有 Sampler1），而 entity.vsh 在
 * {@code !NO_OVERLAY} 时会用 {@code Sampler1} 取 overlay。
 * r52 就是漏了这类声明才崩在 {@code Missing sampler Sampler0}。
 *
 * <h2>失败时的退路</h2>
 * 掩码不可用（未开启/建不出来/绘制失败）时，调用方应当回退到
 * {@code RenderTypes.entityCutout}，也就是<b>当前已 PASS 的行为</b>。
 * 这样即便本特性整个坏掉，也只是回到"镜内能看到镜筒内壁"，不会更糟。
 */
@Environment(EnvType.CLIENT)
public final class ScopeBodyRenderTypes {

    /**
     * 掩码采样器的名字。
     *
     * <p>刻意<b>不</b>叫 {@code Sampler3}：{@code BindGroupLayouts} 里根本没有
     * {@code SAMPLER3} 常量（只到 SAMPLER2），编号槽位是 vanilla 自己预留的。
     * 额外采样器应当像 vanilla 的 {@code DissolveMaskSampler} 那样起个描述性名字，
     * 自建 layout 声明 —— 见 {@code BindGroupLayouts.<clinit>} 偏移 259-270。
     */
    private static final String MASK_SAMPLER = "ScopeMaskSampler";

    /** 掩码采样器的 bind group layout。仿 vanilla DISSOLVE_MASK_SAMPLER 的做法自建。 */
    private static final BindGroupLayout MASK_SAMPLER_LAYOUT =
            BindGroupLayout.builder().withSampler(MASK_SAMPLER).build();

    /** 供 meshloader 的 GPU 裁剪管线复用（同一个 layout 实例 = 同一个 sampler 名）。 */
    public static BindGroupLayout maskSamplerLayout() {
        return MASK_SAMPLER_LAYOUT;
    }

    /** 掩码采样器在 bind group 里的名字，供外部 pass 手动绑定时对齐。 */
    public static String maskSamplerName() {
        return MASK_SAMPLER;
    }

    private static RenderPipeline buildPipeline(String name, boolean mask, boolean invert, boolean emissive) {
        var builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/" + name))
                .withVertexShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"))
                .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                .withCull(false);
        if (emissive) {
            // 发光准星不应受面法线/方向光影响；否则会随玩家朝向变亮变暗。
            builder = builder.withShaderDefine("EMISSIVE")
                    .withShaderDefine("NO_CARDINAL_LIGHTING");
        } else {
            // 以下与 vanilla ENTITY_CUTOUT 一致，用于镜身和蚀刻分划。
            builder = builder.withShaderDefine("PER_FACE_LIGHTING");
        }
        if (mask) {
            builder = builder.withShaderDefine("SCOPE_MASK")
                    .withBindGroupLayout(MASK_SAMPLER_LAYOUT);
            if (invert) {
                // 准星版：只保留镜内（上游 stencilFunc(EQUAL, i+1)）
                builder = builder.withShaderDefine("SCOPE_MASK_INVERT");
            }
        }
        return builder.build();
    }

    /** 镜身：只在目镜<b>没盖到</b>处绘制。 */
    private static final RenderPipeline CLIPPED_PIPELINE =
            buildPipeline("scope_body_clipped", true, false, false);

    /**
     * 枪口火光（大面片层）的掩码裁剪管线。
     *
     * <p>配方逐项对照 26.2 {@code RenderPipelines.<clinit>} 里 {@code ENTITY_TRANSLUCENT}
     * 的字节码（putstatic 前 24 条指令实读）：<pre>
     * builder(ENTITY_SNIPPET)
     *     .withLocation("pipeline/entity_translucent")
     *     .withShaderDefine("ALPHA_CUTOUT", 0.1F)
     *     .withShaderDefine("PER_FACE_LIGHTING")
     *     .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
     *     .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
     *     .withCull(false)</pre>
     * 注意 26.2 里 {@code RenderPipeline.builder} 只接受 {@code Snippet...}，
     * 而 {@code RenderPipelines.ENTITY_TRANSLUCENT} 是<b>完整管线不是 Snippet</b>
     * （26.2 暴露的 Snippet 只有 {@code ENTITY_SNIPPET} 等基础款），所以不能
     * 「以它为底」，必须把整条配方照抄出来，再叠加我们那三样
     * （SCOPE_MASK + 掩码 layout + scope_body 着色器）。除这三样外与 vanilla
     * entity_translucent 逐状态一致 —— 火光在镜外的观感不变，镜内被 discard
     * （透视口径契约：口径内一切视模像素都不出现）。
     *
     * <p>只覆盖火光的【大面片】层。辉光涡旋层（{@code energySwirl}）在 26.2 里
     * 的 shader 已被折叠进共享实现（jar 内无独立 rendertype_energy_swirl.fsh），
     * 未逆向确认前不动它 —— 残余效果至多是镜内仍见一倍缩小后的柔光，
     * 可后补，不属于回归风险。
     */
    private static final RenderPipeline FLASH_TRANSLUCENT_CLIPPED_PIPELINE =
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_flash_translucent_clipped"))
                    .withVertexShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withShaderDefine("SCOPE_MASK")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                    .withBindGroupLayout(MASK_SAMPLER_LAYOUT)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .build();

    /**
     * 枪口火光【辉光涡旋层】的掩码裁剪管线。
     *
     * <p>配方逐项对照 26.2 {@code RenderPipelines.<clinit>} 里 {@code ENERGY_SWIRL}
     * 的字节码（clinit ins 914-956 实读）：<pre>
     * builder(MATRICES_FOG_SNIPPET)
     *     .withLocation("pipeline/energy_swirl")
     *     .withVertexShader("core/entity")      // 26.2 把 swirl 折叠回通用 entity shader
     *     .withFragmentShader("core/entity")    //   （jar 内已无独立 energy_swirl.fsh）
     *     .withShaderDefine("ALPHA_CUTOUT", 0.1F)
     *     .withShaderDefine("EMISSIVE")
     *     .withShaderDefine("NO_OVERLAY")
     *     .withShaderDefine("NO_CARDINAL_LIGHTING")
     *     .withShaderDefine("APPLY_TEXTURE_MATRIX")  // UV 动画全靠它 + OffsetTextureTransform
     *     .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
     *     .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
     *     .withCull(false)
     *     .withVertexBinding(0, DefaultVertexFormat.ENTITY)
     *     .withPrimitiveTopology(QUADS)
     *     .withDepthStencilState(DepthStencilState.DEFAULT)</pre>
     * 看到 {@code core/entity} 的那一刻一切都顺了：我们的 {@code scope_body} 着色器
     * 就是 entity 的逐字节拷贝 + SCOPE_MASK 段，把上面配方原样复刻、换上我们的
     * 着色器再加 SCOPE_MASK/掩码 layout，即可得到「观感逐像素一致的 swirl、
     * 镜内被 discard」的辉光层 —— 之前悬而未决的「辉光层镜内残余」由此件正式收口
     * （LayerAssignment 裁决实验结论：残余一直是这层，大面片从未失手）。
     *
     * <p>与 flash 大面片管线的区别只是底子和混合：MATRICES_FOG_SNIPPET + ADDITIVE
     * （swirl 是叠加发光），顶点绑定/拓扑/深度状态需要显式给出
     * （ENTITY_SNIPPET 才有这些默认值，MATRICES_FOG_SNIPPET 没有）。
     */
    private static final RenderPipeline FLASH_SWIRL_CLIPPED_PIPELINE =
            RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_flash_swirl_clipped"))
                    .withVertexShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("EMISSIVE")
                    .withShaderDefine("NO_OVERLAY")
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withShaderDefine("APPLY_TEXTURE_MATRIX")
                    .withShaderDefine("SCOPE_MASK")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withBindGroupLayout(MASK_SAMPLER_LAYOUT)
                    .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                    .withCull(false)
                    .withVertexBinding(0, com.mojang.blaze3d.vertex.DefaultVertexFormat.ENTITY)
                    .withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .build();

    /** 蚀刻准星：只在目镜<b>盖到</b>处绘制，保留受光。 */
    private static final RenderPipeline RETICLE_PIPELINE =
            buildPipeline("scope_reticle_clipped", true, true, false);

    /** 发光准星：只在目镜<b>盖到</b>处绘制，满亮且不受方向光影响。 */
    private static final RenderPipeline RETICLE_EMISSIVE_PIPELINE =
            buildPipeline("scope_reticle_emissive_clipped", true, true, true);

    /** 发光准星无掩码回退：满亮且不受方向光影响。 */
    private static final RenderPipeline EMISSIVE_PIPELINE =
            buildPipeline("scope_reticle_emissive", false, false, true);

    /**
     * 【光影后置目镜框】PIP 合成之后重画 ocular_ring 的专用管线。
     *
     * <p>配方 = vanilla ENTITY_CUTOUT（ENTITY_SNIPPET + ALPHA_CUTOUT 0.1 +
     * PER_FACE_LIGHTING + SAMPLER1 + Cull(false)，与 {@code buildPipeline}
     * 的非 emissive 分支同一份抄本），只differ两处：
     * <ul>
     *   <li>片元换成 {@code core/scope_ring_final}（entity.fsh 去掉 apply_fog —
     *       此刻画的是 Iris 收工后的纯前景层，世界雾参数会把框染色）；</li>
     *   <li>深度测试 ALWAYS_PASS、不写深度 —— 主 target 的深度缓冲经过整条
     *       Iris 管线后内容不可预期，被判「遮挡」整个框就消失；它是最后一层
     *       前景，无条件通过即可（1.21.11 母本 FINAL_OCULAR_RING_PIPELINE
     *       的 NO_DEPTH_TEST + depthWrite=false 同义）。</li>
     * </ul>
     *
     * <p><b>刻意不注册给 Iris HAND 程序</b>（对比 {@link #ensureIrisCompatibility}
     * 里的其他管线）：绘制发生在 isRenderingWorld=false 之后，注册了反而被塞回
     * 光影管线，既失去无雾语义又会被后置 pass 再盖一次。</p>
     */
    private static final RenderPipeline FINAL_OCULAR_RING_PIPELINE =
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_final_ocular_ring"))
                    .withVertexShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_ring_final"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(
                            com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS, false))
                    .build();

    /**
     * 第一人称视模（枪身/非瞄具配件）的「镜内 discard」总入口。
     *
     * <h2>它补的是哪个洞（目镜内未裁切枪体、配件 一案）</h2>
     * 「透视瞄具」的成立条件是：目镜投影区内<b>一切</b>属于视模的像素都必须
     * discard —— 不仅不写颜色，也不写深度，后面的世界画面才能透过镜片露出来。
     * 而裁剪版 RenderType 此前只发给了瞄具镜身（{@code BedrockAttachmentModel}），
     * 枪身（{@code GunItemRendererWrapper}）与其他配件（{@code AttachmentRender}）
     * 用的还是原版 {@code entityCutout} —— 镜内区域照常写颜色+深度，
     * 于是镜片中看得见护木/激光盒等穿过镜面，世界画面透不过来。
     *
     * <h2>前置条件为什么这么多</h2>
     * 与 {@code BedrockAttachmentModel#resolveBodyRenderType} 的哲学一致：
     * 任何一环不满足就原样退回 —— 最坏情况只是回到「镜内见镜筒」的已验证状态，
     * 绝不能因为裁剪特性坏掉而画错模型。
     *
     * @param original    调用方原本的 RenderType（不满足条件时原样返回）
     * @param texture     该模型/配件的贴图；为 {@code null} 无法构造裁剪版，退回
     * @param applies     调用点是否属于「第一人称手持视模」（手持渲染路径才需要，
     *                    GUI 预览/第三人称/掉落物一律退回）
     */
    public static RenderType clipForViewmodel(RenderType original,
                                              @javax.annotation.Nullable Identifier texture,
                                              boolean applies) {
        if (texture == null) {
            return original;
        }
        if (!maskReadyForViewmodel(applies)) {
            return original;
        }
        return clipped(texture);
    }

    private static boolean irisAssignmentAttempted = false;

    /**
     * 让 Iris 知道这两个自定义 pipeline 属于第一人称手部渲染。
     *
     * <p>调用方会在判断 shader fallback 之前先调用这里。静态初始化时也可以直接尝试，
     * 但显式方法能保证未来若 class 加载时机变化，仍可在第一次使用前补做 assignment。</p>
     */
    public static void ensureIrisCompatibility() {
        if (irisAssignmentAttempted) {
            return;
        }
        irisAssignmentAttempted = true;
        IrisCompat.assignScopePipelineToHand(CLIPPED_PIPELINE, "scope_body_clipped");
        IrisCompat.assignScopePipelineToHand(RETICLE_PIPELINE, "scope_reticle_clipped");
        IrisCompat.assignScopePipelineToHand(RETICLE_EMISSIVE_PIPELINE, "scope_reticle_emissive_clipped");
        IrisCompat.assignScopePipelineToHand(EMISSIVE_PIPELINE, "scope_reticle_emissive");
        IrisCompat.assignScopePipelineToHand(FLASH_TRANSLUCENT_CLIPPED_PIPELINE, "scope_flash_translucent_clipped");
        IrisCompat.assignScopePipelineToHand(FLASH_SWIRL_CLIPPED_PIPELINE, "scope_flash_swirl_clipped");
    }

    /**
     * 按贴图缓存。
     *
     * <p>RenderType 参与批次合并，同一贴图必须复用同一实例，否则每次调用
     * 都产生新对象 → 批次爆炸 → 掉帧。瞄具贴图种类有限（个位数），
     * 用无上限的 HashMap 不会有内存问题。
     */
    private static final Map<Identifier, RenderType> BODY_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> ARM_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> RETICLE_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> RETICLE_EMISSIVE_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> EMISSIVE_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> FLASH_TRANSLUCENT_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> FLASH_SWIRL_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> FINAL_RING_CACHE = new HashMap<>();

    private ScopeBodyRenderTypes() {
    }

    /**
     * 镜身：只在目镜<b>没盖到</b>处绘制。
     *
     * <p>等价于上游 {@code scope_body: stencilFunc(GL_EQUAL, 0)}。
     */
    /**
     * 【光影后置目镜框】的 RenderType。用于 {@code ScopeFinalOverlayState} 在
     * PIP 合成之后重画 ocular_ring。<b>不</b>调 ensureIrisCompatibility ——
     * 这条管线刻意不进 Iris（见 FINAL_OCULAR_RING_PIPELINE 注释）。
     */
    public static RenderType finalOcularRing(Identifier texture) {
        return FINAL_RING_CACHE.computeIfAbsent(texture,
                tex -> create("tacz_scope_final_ocular_ring", FINAL_OCULAR_RING_PIPELINE, tex, false));
    }

    public static RenderType clipped(Identifier texture) {
        ensureIrisCompatibility();
        return BODY_CACHE.computeIfAbsent(texture,
                tex -> create("tacz_scope_body_clipped", CLIPPED_PIPELINE, tex, true));
    }

    /**
     * 准星：只在目镜<b>盖到</b>处绘制。
     *
     * <p>等价于上游 {@code renderDivisionOnly: stencilFunc(GL_EQUAL, i+1)} ——
     * 准星被约束在目镜投影内，不会溢出镜筒贴到屏幕上。
     */
    public static RenderType reticle(Identifier texture) {
        ensureIrisCompatibility();
        return RETICLE_CACHE.computeIfAbsent(texture,
                tex -> create("tacz_scope_reticle_clipped", RETICLE_PIPELINE, tex, true));
    }

    /** 发光准星：反向裁剪 + 满亮/无方向光。 */
    public static RenderType reticleEmissive(Identifier texture) {
        ensureIrisCompatibility();
        return RETICLE_EMISSIVE_CACHE.computeIfAbsent(texture,
                tex -> create("tacz_scope_reticle_emissive_clipped", RETICLE_EMISSIVE_PIPELINE, tex, true));
    }

    /** 发光准星：无裁剪回退 + 满亮/无方向光。 */
    public static RenderType emissive(Identifier texture) {
        ensureIrisCompatibility();
        return EMISSIVE_CACHE.computeIfAbsent(texture,
                tex -> create("tacz_scope_reticle_emissive", EMISSIVE_PIPELINE, tex, false));
    }

    /**
     * 枪口火光（大面片层）：只在目镜<b>没盖到</b>处绘制。
     *
     * <p>调用方须先过 {@link #maskReadyForViewmodel} —— 掩码没就绪时必须
     * 继续用 vanilla {@code entityTranslucent}，否则火光整层消失。</p>
     */
    public static RenderType flashTranslucent(Identifier texture) {
        ensureIrisCompatibility();
        return FLASH_TRANSLUCENT_CACHE.computeIfAbsent(texture,
                tex -> create("tacz_scope_flash_translucent_clipped", FLASH_TRANSLUCENT_CLIPPED_PIPELINE, tex, true));
    }

    /**
     * 第一人称手臂的「镜内 discard」版 {@code entityTranslucent}。
     *
     * <h2>为什么手臂要单独一个入口（镜内裁手一案）</h2>
     * 手臂的提交发生在 {@code AvatarRenderer#renderHand} <b>内部</b>——字节码实读：
     * <pre>collector.submitModelPart(arm, pose, RenderTypes.entityTranslucent(skin), light, NO_OVERLAY, null)</pre>
     * RenderType 是 vanilla 自己挑的（玩家皮肤含半透明二层袖，必须 translucent），
     * 我们无法像枪身那样在调用点直接换 —— 由 {@code RenderHelper} 用
     * collector 代理把这次提交的 RenderType 原地替换成本方法的返回值。
     *
     * <p><b>管线复用</b> {@link #FLASH_TRANSLUCENT_CLIPPED_PIPELINE}：它就是
     * vanilla {@code ENTITY_TRANSLUCENT} 管线的逐项抄本 + SCOPE_MASK 三件套，
     * 火光与手臂在管线状态上无差别（同 blend、同 snippet、同 define）。
     * 但 <b>RenderSetup 不能复用</b> {@code create(...)} 助手：vanilla
     * {@code entityTranslucent} 的 setup（lambda$static$12 字节码实读）比
     * entityCutout 多 {@code affectsCrumbling() + sortOnUpload()} 两项 ——
     * 半透明批次不 sortOnUpload 会出现二层袖压一层臂的错序。照抄补齐。</p>
     *
     * <p>调用方须先过 {@link #maskReadyForViewmodel}，任一不满足退回
     * vanilla {@code entityTranslucent} —— 与枪身/火光同一失败哲学：
     * 特性坏掉最多回到「镜内见手臂」的现状，绝不画错模型。</p>
     */
    public static RenderType armClipped(Identifier skinTexture) {
        ensureIrisCompatibility();
        return ARM_CACHE.computeIfAbsent(skinTexture,
                tex -> RenderType.create("tacz_scope_arm_clipped",
                        RenderSetup.builder(FLASH_TRANSLUCENT_CLIPPED_PIPELINE)
                                .withTexture("Sampler0", tex)
                                .withTexture(MASK_SAMPLER, ScopeMaskTextureHandle.ID)
                                .useLightmap()
                                .useOverlay()
                                .affectsCrumbling()
                                .sortOnUpload()
                                .createRenderSetup()));
    }

    /**
     * 枪口火光【辉光涡旋层】：只在目镜<b>没盖到</b>处绘制，叠加发光观感与
     * vanilla {@code RenderTypes.energySwirl(tex, 1, 1)} 逐状态一致。
     *
     * <p>RenderSetup 显式对照 vanilla energySwirl 的方法体字节码：
     * Sampler0 + OffsetTextureTransform(1,1)（UV 滚动）+ useLightmap + useOverlay
     * + sortOnUpload，再补一个掩码采样器绑定（管线里声明了就必须绑，r52 的教训）。</p>
     *
     * <p>调用方须先过 {@link #maskReadyForViewmodel}。 </p>
     */
    public static RenderType flashSwirlClipped(Identifier texture) {
        ensureIrisCompatibility();
        return FLASH_SWIRL_CACHE.computeIfAbsent(texture,
                tex -> RenderType.create("tacz_scope_flash_swirl_clipped",
                        RenderSetup.builder(FLASH_SWIRL_CLIPPED_PIPELINE)
                                .withTexture("Sampler0", tex)
                                .withTexture(MASK_SAMPLER, ScopeMaskTextureHandle.ID)
                                .setTextureTransform(new TextureTransform.OffsetTextureTransform(1.0F, 1.0F))
                                .useLightmap()
                                .useOverlay()
                                .sortOnUpload()
                                .createRenderSetup()));
    }

    /**
     * 「本帧第一人称视模上，目镜掩码是否就绪<b>且允许裁视模</b>」的统一判定。
     *
     * <p>供火光这类【不想换镜像身贴图管线、只想换个同源裁剪版】的调用点使用。
     * 与 {@link #clipForViewmodel} 的前置条件完全同一份：任一不满足务必退回
     * 原版渲染类型。</p>
     *
     * <p><b>「有几何」不等于「可以裁视模」</b>（案例⑨ 第四轮）：低倍 sight 通道
     * 会生成 reticle-only 掩码（只为准星约束服务），而上游 {@code renderSight}
     * 不裁镜身/枪身/配件/火光。因此这里必须同时看到
     * {@link ScopeMaskGeometry#isViewmodelClipEnabled()} 才允许换 outside-mask 类型 ——
     * 否则枪身会在低倍镜片投影内被啃出一个洞。</p>
     */
    public static boolean maskReadyForViewmodel(boolean appliesToFirstPersonViewmodel) {
        if (!appliesToFirstPersonViewmodel) {
            return false;
        }
        if (!com.tacz.guns.config.client.RenderConfig.SCOPE_MASK_ENABLE.get()) {
            return false;
        }
        if (IrisCompat.shouldDisableScopeMaskUnderShaderPack()) {
            return false;
        }
        if (com.tacz.guns.client.render.scope.ScopeMaskGeometry.isEmpty()
                || !com.tacz.guns.client.render.scope.ScopeMaskGeometry.isViewmodelClipEnabled()) {
            return false;
        }
        return ScopeMaskTextureHandle.syncToMaskTarget();
    }

    private static RenderType create(String name, RenderPipeline pipeline, Identifier tex, boolean bindMask) {
        var builder = RenderSetup.builder(pipeline)
                // Sampler0 = 瞄具自身贴图。r52 教训：管线声明的每个 sampler
                // 都必须在这里绑定，少一个就在 drawIndexed 时抛 Missing sampler。
                .withTexture("Sampler0", tex);
        if (bindMask) {
            // 掩码采样器 = 目镜掩码。指向 ScopeMaskTextureHandle 注册的那张，
            // 它每帧被刷新为当前掩码 target 的 view。
            builder = builder.withTexture(MASK_SAMPLER, ScopeMaskTextureHandle.ID);
        }
        return RenderType.create(name,
                builder
                        // useLightmap/useOverlay 提供 Sampler2/Sampler1，
                        // 与 vanilla entityCutout 的 RenderSetup 一致。
                        .useLightmap()
                        .useOverlay()
                        .createRenderSetup());
    }
}
