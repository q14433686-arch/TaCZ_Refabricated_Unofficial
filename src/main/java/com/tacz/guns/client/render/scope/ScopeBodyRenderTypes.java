package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
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

    private static RenderPipeline buildPipeline(String name, boolean invert) {
        var builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/" + name))
                .withVertexShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"))
                // 以下四项与 vanilla ENTITY_CUTOUT 完全一致，缺一不可
                .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                .withShaderDefine("PER_FACE_LIGHTING")
                .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                .withCull(false)
                // 本特性专属：打开裁剪分支 + 声明掩码采样器
                .withShaderDefine("SCOPE_MASK")
                .withBindGroupLayout(MASK_SAMPLER_LAYOUT);
        if (invert) {
            // 准星版：只保留镜内（上游 stencilFunc(EQUAL, i+1)）
            builder = builder.withShaderDefine("SCOPE_MASK_INVERT");
        }
        return builder.build();
    }

    /** 镜身：只在目镜<b>没盖到</b>处绘制。 */
    private static final RenderPipeline CLIPPED_PIPELINE =
            buildPipeline("scope_body_clipped", false);

    /** 准星：只在目镜<b>盖到</b>处绘制。 */
    private static final RenderPipeline RETICLE_PIPELINE =
            buildPipeline("scope_reticle_clipped", true);

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

    private ScopeBodyRenderTypes() {
    }

    /**
     * 镜身：只在目镜<b>没盖到</b>处绘制。
     *
     * <p>等价于上游 {@code scope_body: stencilFunc(GL_EQUAL, 0)}。
     */
    public static RenderType clipped(Identifier texture) {
        return BODY_CACHE.computeIfAbsent(texture,
                tex -> create("tacz_scope_body_clipped", CLIPPED_PIPELINE, tex));
    }

    /**
     * 准星：只在目镜<b>盖到</b>处绘制。
     *
     * <p>等价于上游 {@code renderDivisionOnly: stencilFunc(GL_EQUAL, i+1)} ——
     * 准星被约束在目镜投影内，不会溢出镜筒贴到屏幕上。
     */
    public static RenderType reticle(Identifier texture) {
        return RETICLE_CACHE.computeIfAbsent(texture,
                tex -> create("tacz_scope_reticle_clipped", RETICLE_PIPELINE, tex));
    }

    private static RenderType create(String name, RenderPipeline pipeline, Identifier tex) {
        return RenderType.create(name,
                RenderSetup.builder(pipeline)
                        // Sampler0 = 瞄具自身贴图。r52 教训：管线声明的每个 sampler
                        // 都必须在这里绑定，少一个就在 drawIndexed 时抛 Missing sampler。
                        .withTexture("Sampler0", tex)
                        // 掩码采样器 = 目镜掩码。指向 ScopeMaskTextureHandle 注册的那张，
                        // 它每帧被刷新为当前掩码 target 的 view。
                        .withTexture(MASK_SAMPLER, ScopeMaskTextureHandle.ID)
                        // useLightmap/useOverlay 提供 Sampler2/Sampler1，
                        // 与 vanilla entityCutout 的 RenderSetup 一致。
                        .useLightmap()
                        .useOverlay()
                        .createRenderSetup());
    }
}
