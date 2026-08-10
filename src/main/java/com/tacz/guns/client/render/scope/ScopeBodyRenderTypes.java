package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
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

    private static RenderPipeline buildPipeline(String name, boolean mask, boolean invert, boolean window, boolean emissive, boolean translucent) {
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
            // 以下与 vanilla ENTITY_CUTOUT 一致，用于镜身、蚀刻分划、枪体与配件。
            builder = builder.withShaderDefine("PER_FACE_LIGHTING");
        }
        if (mask) {
            builder = builder.withShaderDefine("SCOPE_MASK")
                    .withBindGroupLayout(MASK_SAMPLER_LAYOUT);
            if (invert) {
                // 准星版：只保留镜内窗口（上游 stencilFunc(EQUAL, ~(i+1))）
                builder = builder.withShaderDefine("SCOPE_MASK_INVERT");
            }
            if (window) {
                // 窗口裁切版：只裁掉镜内窗口（目镜黑圈 / 枪体 / 配件）
                builder = builder.withShaderDefine("SCOPE_MASK_WINDOW");
            }
        }
        if (translucent) {
            // 与 vanilla ENTITY_TRANSLUCENT 的混合状态一致（含透明枪械的枪体）。
            builder = builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
        }
        return builder.build();
    }

    /** 镜身：整块目镜投影内都不画（上游 stencilFunc(GL_EQUAL, 0)）。 */
    private static final RenderPipeline CLIPPED_PIPELINE =
            buildPipeline("scope_body_clipped", true, false, false, false, false);

    /** 蚀刻准星：只在窗口内绘制，保留受光。 */
    private static final RenderPipeline RETICLE_PIPELINE =
            buildPipeline("scope_reticle_clipped", true, true, false, false, false);

    /** 发光准星：只在窗口内绘制，满亮且不受方向光影响。 */
    private static final RenderPipeline RETICLE_EMISSIVE_PIPELINE =
            buildPipeline("scope_reticle_emissive_clipped", true, true, false, true, false);

    /** 发光准星无掩码回退：满亮且不受方向光影响。 */
    private static final RenderPipeline EMISSIVE_PIPELINE =
            buildPipeline("scope_reticle_emissive", false, false, false, true, false);

    /** 窗口裁切（目镜黑圈 / 非瞄具配件）：只裁掉镜内窗口，不透明。 */
    private static final RenderPipeline WINDOW_PIPELINE =
            buildPipeline("scope_window_clipped", true, false, true, false, false);

    /** 窗口裁切 + 混合（含透明枪械的枪体，等价 entityTranslucent + 窗口裁切）。 */
    private static final RenderPipeline WINDOW_TRANSLUCENT_PIPELINE =
            buildPipeline("scope_window_clipped_translucent", true, false, true, false, true);

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
        IrisCompat.assignScopePipelineToHand(WINDOW_PIPELINE, "scope_window_clipped");
        IrisCompat.assignScopePipelineToHand(WINDOW_TRANSLUCENT_PIPELINE, "scope_window_clipped_translucent");
    }

    /**
     * 按贴图缓存。
     *
     * <p>RenderType 参与批次合并，同一贴图必须复用同一实例，否则每次调用
     * 都产生新对象 → 批次爆炸 → 掉帧。瞄具贴图种类有限（个位数），
     * 用无上限的 HashMap 不会有内存问题。
     */
    private static final Map<Identifier, RenderType> BODY_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> RETICLE_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> RETICLE_EMISSIVE_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> EMISSIVE_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> WINDOW_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> GUN_BODY_CACHE = new HashMap<>();
    private static final Map<Identifier, RenderType> GUN_BODY_TRANSLUCENT_CACHE = new HashMap<>();

    private ScopeBodyRenderTypes() {
    }

    /**
     * 镜身：只在目镜<b>没盖到</b>处绘制。
     *
     * <p>等价于上游 {@code scope_body: stencilFunc(GL_EQUAL, 0)}。
     */
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
     * 窗口裁切（不透明）：只裁掉镜内窗口。
     *
     * <p>两个用途：</p>
     * <ul>
     *   <li><b>目镜黑圈</b>：筒镜目镜用本类型单独提交后，窗口内(镜片中央)被裁掉，
     *       只保留窗口外的边缘带 —— 等价于上游 {@code stencilFunc(EQUAL, i+1)}
     *       画的「圆外目镜黑色遮罩」；</li>
     *   <li><b>非瞄具配件</b>（前瞄、制退器等）：开镜时镜内不该出现它们。</li>
     * </ul>
     */
    public static RenderType window(Identifier texture) {
        ensureIrisCompatibility();
        return WINDOW_CACHE.computeIfAbsent(texture,
                tex -> create("tacz_scope_window_clipped", WINDOW_PIPELINE, tex, true));
    }

    /**
     * 枪体窗口裁切：开镜时枪体在镜内窗口中的部分不可见（「镜内只剩世界+准星」）。
     *
     * @param translucent 该枪贴图是否启用透明（对应 entityTranslucent 的混合状态）
     */
    public static RenderType gunBody(Identifier texture, boolean translucent) {
        ensureIrisCompatibility();
        if (translucent) {
            return GUN_BODY_TRANSLUCENT_CACHE.computeIfAbsent(texture,
                    tex -> create("tacz_scope_gun_body_window_translucent", WINDOW_TRANSLUCENT_PIPELINE, tex, true));
        }
        return GUN_BODY_CACHE.computeIfAbsent(texture,
                tex -> create("tacz_scope_gun_body_window", WINDOW_PIPELINE, tex, true));
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
