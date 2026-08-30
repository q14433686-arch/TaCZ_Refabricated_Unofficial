package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 会被目镜掩码裁剪的<b>文字</b> RenderType（镜内文字一案）。
 *
 * <h2>它解决什么</h2>
 * 瞄具上的文字（MK5HD 的弹药计数等）此前走 {@code SubmitNodeCollector#submitText}
 * 的 vanilla 字体管线 —— {@code TextFeatureRenderer} 内部用
 * {@code TextRenderable#renderType(displayMode)} 从 {@code GlyphRenderTypes}
 * 三件套（normal/seeThrough/polygonOffset，字节码实读）里挑 RenderType，
 * <b>调用方没有任何注入点</b>可以换成裁剪版。于是文字会溢出目镜圆孔
 * （现状用「开镜到 0.35 才显示」的门禁兜着，治标不治本）。
 *
 * <p>26.2 重做 GUI 时留了后门：{@code Font#prepareText} 返回的
 * {@code PreparedText} 可用 {@code GlyphVisitor} 遍历出每个
 * {@code TextRenderable}，其 {@code render(Matrix4fc, VertexConsumer, int, boolean)}
 * 直接把字形顶点写进<b>任意</b> VertexConsumer（字节码实读：标准
 * addVertex/setColor/setUv/setLight 四件套 × 4 顶点）。也就是说文字几何
 * 可以徒手拿到，塞进我们自己的裁剪 RenderType —— 本类就是那个 RenderType。</p>
 *
 * <h2>管线配方（逐项对照 26.2 RenderPipelines.&lt;clinit&gt; 的 TEXT 反汇编）</h2>
 * vanilla {@code TEXT} 是：
 * <pre>
 * builder(WORLD_TEXT_SNIPPET)          // = GLOBALS + FOG + SAMPLER2 +
 *                                      //   POSITION_TEX_LIGHTMAP_COLOR + QUADS
 *     .withLocation("pipeline/text")
 *     .withVertexShader("core/text")
 *     .withFragmentShader("core/text")
 * </pre>
 * 本类完全照抄，只额外加三样（与 {@link ScopeBodyRenderTypes} 同一套路）：
 * <ul>
 *   <li>{@code withShaderDefine("SCOPE_MASK")} —— 打开 fsh 里的镜内裁剪；</li>
 *   <li>掩码采样器 bind group layout（与 scope_body 同名 {@code ScopeMaskSampler}）；</li>
 *   <li>换成我们的 {@code scope_text} 着色器（vsh 与 vanilla text.vsh 逐行相同，
 *       fsh 只多 SCOPE_MASK 一段 —— 语义同准星的 SCOPE_MASK_INVERT：
 *       <b>只保留镜内</b>）。</li>
 * </ul>
 *
 * <p>注意与镜身相反：镜身是「镜内 discard」（挖洞给 PIP 透），文字是
 * 「镜<b>外</b> discard」（约束在圆孔内）—— 文字属于「浮在镜内画面之上」
 * 的一族，跟准星同侧。</p>
 *
 * <h2>为什么按 GpuTextureView 缓存而不是按 Identifier</h2>
 * 字形顶点的 UV 是相对<b>某一页字体图集</b>的（{@code BakedSheetGlyph.textureView}，
 * 字节码实读），多页图集必须逐页分组绑定。但 RenderSetup 只认 Identifier
 * （{@code prepareTextures} 走 TextureManager 查表），所以这里复用
 * {@link ScopeMaskTextureHandle} 的「空壳纹理」思路：给每页图集注册一个
 * 壳 Identifier，每帧把该页的 view 塞进壳里。字体图集页数有限（默认字体
 * 常驻 1-2 页），HashMap 不会膨胀。
 *
 * <h2>失败时的退路</h2>
 * 掩码不可用时调用方（{@code BedrockAttachmentModel}）回退到现状 ——
 * vanilla submitText + 开镜门禁。文字特性坏掉最多回到「开镜才显示、
 * 边缘可能溢出」的已验证状态，不会画错。
 */
@Environment(EnvType.CLIENT)
public final class ScopeTextRenderTypes {

    /** 与 {@code ScopeBodyRenderTypes.MASK_SAMPLER} 同名同义：掩码采样器。 */
    private static final String MASK_SAMPLER = "ScopeMaskSampler";

    private static final BindGroupLayout MASK_SAMPLER_LAYOUT =
            BindGroupLayout.builder().withSampler(MASK_SAMPLER).build();

    /**
     * 裁剪文字管线 = vanilla TEXT 配方 + SCOPE_MASK 三件套。
     *
     * <p>不需要 IS_GRAYSCALE 变体：grayscale 只用于 TrueType/unihex 的
     * 灰度图集（{@code GlyphRenderTypes.createForGrayscaleTexture}），
     * 默认位图字体走彩色路径。第三方资源包用 ttf 字体时文字会回退
     * vanilla 管线（见 {@code ScopeTextSubmitter} 的 renderType 嗅探），
     * 属于可接受降级 —— 不为极小众场景翻倍管线数量。</p>
     */
    private static final RenderPipeline CLIPPED_TEXT_PIPELINE =
            RenderPipeline.builder(RenderPipelines.WORLD_TEXT_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_text_clipped"))
                    .withVertexShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_text"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_text"))
                    .withShaderDefine("SCOPE_MASK")
                    .withBindGroupLayout(MASK_SAMPLER_LAYOUT)
                    .build();

    private static boolean irisAssignmentAttempted = false;

    /**
     * 与 {@code ScopeBodyRenderTypes#ensureIrisCompatibility} 同一约定：
     * 让 Iris 把这条管线归到第一人称手部程序，避免光影下被判为未知管线。
     * （实际上光影包激活时掩码整体禁用、走不到这里，这是双保险。）
     */
    private static void ensureIrisCompatibility() {
        if (irisAssignmentAttempted) {
            return;
        }
        irisAssignmentAttempted = true;
        com.tacz.guns.compat.iris.IrisCompat.assignScopePipelineToHand(CLIPPED_TEXT_PIPELINE, "scope_text_clipped");
    }

    /** 按字体图集<b>页</b>缓存（键是壳 Identifier；一页一个 RenderType）。 */
    private static final Map<Identifier, RenderType> PAGE_CACHE = new HashMap<>();

    /**
     * 拿到绑定了指定字体图集页的裁剪文字 RenderType。
     *
     * @param pageId 该图集页的壳 Identifier（由 {@code ScopeTextSubmitter}
     *               的 view 登记流程生成并每帧刷新指向）
     */
    public static RenderType clippedText(Identifier pageId) {
        ensureIrisCompatibility();
        return PAGE_CACHE.computeIfAbsent(pageId,
                id -> RenderType.create("tacz_scope_text_clipped",
                        RenderSetup.builder(CLIPPED_TEXT_PIPELINE)
                                .withTexture("Sampler0", id)
                                .withTexture(MASK_SAMPLER, ScopeMaskTextureHandle.ID)
                                // useLightmap 提供 Sampler2 —— vanilla text
                                // 的 RenderSetup（lambda$static$20 字节码实读）
                                // 就这一项，别多也别少。
                                .useLightmap()
                                .createRenderSetup()));
    }

    private ScopeTextRenderTypes() {
    }
}
