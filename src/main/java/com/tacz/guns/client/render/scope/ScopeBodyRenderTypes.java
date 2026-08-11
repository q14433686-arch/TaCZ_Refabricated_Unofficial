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
     * <p>等价于 vanilla {@code pipeline/entity_translucent} 之上叠加
     * {@code SCOPE_MASK} 分支 —— 直接以 {@code RenderPipelines.ENTITY_TRANSLUCENT}
     * 为底拷贝整套状态（blend/深度/cull/布局全继承），只替换 shader 为我们那份
     * 「entity 逐字节一致 + SCOPE_MASK 段」的 scope_body 着色器，并声明掩码采样器。
     * 因此火光在镜外的观感与 vanilla 逐像素一致，镜内被 discard（透视口径契约：
     * 口径内一切视模像素都不出现）。
     *
     * <p>只覆盖火光的【大面片】层。辉光涡旋层（{@code energySwirl}）在 26.2 里
     * 的 shader 已被折叠进共享实现（jar 内无独立 rendertype_energy_swirl.fsh），
     * 未逆向确认前不动它 —— 残余效果至多是镜内仍见一倍缩小后的柔光，
     * 可后补，不属于回归风险。
     */
    private static final RenderPipeline FLASH_TRANSLUCENT_CLIPPED_PIPELINE =
            RenderPipeline.builder(RenderPipelines.ENTITY_TRANSLUCENT)
                    .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_flash_translucent_clipped"))
                    .withVertexShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_body"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("SCOPE_MASK")
                    .withBindGroupLayout(MASK_SAMPLER_LAYOUT)
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
    private static final Map<Identifier, RenderType> FLASH_TRANSLUCENT_CACHE = new HashMap<>();

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
     * 「本帧第一人称视模上，目镜掩码是否就绪可裁」的统一判定。
     *
     * <p>供火光这类【不想换镜像身贴图管线、只想换个同源裁剪版】的调用点使用。
     * 与 {@link #clipForViewmodel} 的前置条件完全同一份：任一不满足务必退回
     * 原版渲染类型。</p>
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
        if (com.tacz.guns.client.render.scope.ScopeMaskGeometry.isEmpty()) {
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
