package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把一段文字用【镜内裁剪】管线提交 —— 绕开 vanilla 字体管线的徒手路径（镜内文字一案的正解）。
 *
 * <p>这是 26.2 {@code 9d036594}（in-scope text clipping）经 26.1.2 {@code e1c550ee} 移植后的
 * <b>1.21.11 版</b>：语义逐条对齐，API 全部换成<b>我们这一代 javap 实测过的对应物</b>（对照表与
 * 取证过程见 {@code docs/lineage/SYNC_REVIEW_2612_PIP_BACKPORT_20260901.md} §2/§2.1）。</p>
 *
 * <h2>为什么必须绕开 vanilla 管线</h2>
 * {@code submitText} 的下游是 {@code TextFeatureRenderer → GlyphRenderTypes}，RenderType 由那三件套
 * <b>写死</b>，调用方没有注入点 ⇒ 文字不吃孔径深度，镜孔外的像素照画（本分支曾经的"深度剔除≈掩码"
 * 论断已被 26.1.2 的实机否证，故有本类）。但 {@code Font#prepareText → PreparedText#visit} 是公开
 * API，{@code TextRenderable#render} 接受<b>任意</b> VertexConsumer —— 本类把 vanilla 那两步搬过来，
 * 唯独把 RenderType 换成 {@link ScopeRenderTypes#maskedText}（掩码裁剪版）。
 *
 * <h2>相对 26.1.2/26.2 的 API 映射（1.21.11 javap 逐符号核实）</h2>
 * <ul>
 *   <li>26.1.2 覆写的 {@code GlyphVisitor.accept(renderable, x, y, width)} 在本世代<b>不存在</b>：
 *       {@code Font$GlyphVisitor} 是 {@code acceptGlyph(TextRenderable$Styled)} +
 *       {@code acceptEffect(TextRenderable)} 两个 default 方法。二者都是 renderable，进同一分组。</li>
 *   <li>本世代 {@code TextRenderable} 自带 {@code left/top/right/bottom}，位置由 renderable 自己带进
 *       {@code render(Matrix4f, VertexConsumer, int light, boolean)}（vanilla 字体批次就是这么做的）
 *       ⇒ 我们<b>不需要</b>自己传 x/y/width，只需要把 {@code prepareText} 收到的偏移交给 vanilla 的
 *       {@code prepareText(seq, x, y, color, shadow, seeThrough, background)}。</li>
 *   <li>纹理绑定：本世代 {@code RenderSetupBuilder} 只有 {@code withTexture(String, Identifier)} 两个
 *       重载（不直接吃 {@code GpuTextureView}）⇒ 26.1.2 的"壳纹理"在本世代同样是<b>必需</b>件，
 *       不是 1.20.1 的历史包袱。见 {@link PageHandle}。</li>
 *   <li>门禁收敛为 {@link ScopeDepthCopyState#isMaskCycleValid()}：本帧没走完「世界备份 + 孔径拷贝」
 *       就回退 vanilla 提交，绝不采样陈旧掩码。</li>
 * </ul>
 *
 * <h2>多页字体图集的处理</h2>
 * 每个字形的 UV 挂在它自己那页图集上（{@code TextRenderable#textureView()}）。vanilla 靠 renderType 里
 * 已绑好的页纹理分批；我们按 view 分组，每组对应一个「壳 Identifier」（{@link PageHandle}：
 * {@code AbstractTexture} 空壳 + 每帧刷新指向），组内字形共用一次 {@code submitCustomGeometry}。
 * 默认位图字体常驻页数 1-2，分组开销可忽略。资源重载后旧 view 的表项残留只是闲置壳，无害（序号永不复用）。
 *
 * <h2>失败时的退路</h2>
 * 掩码不可用时 {@link #submit} 返回 false，调用方回退 vanilla {@code submitText} —— 与 26.2 相同的
 * 失败语义：<b>不丢字、不画错</b>，最差回到「边缘可能溢出」的旧行为。
 */
public final class ScopeTextSubmitter {

    /**
     * 字体图集页的壳纹理（26.1.2 同名部件的等价实现）。
     *
     * <p>sampler 抄 vanilla {@code FontTexture.<init>}：{@code getRepeat(FilterMode.NEAREST)} ——
     * 字体图集就是这么绑的，换 clamp 会在字形边缘出现与 vanilla 不一致的采样。</p>
     */
    private static final class PageHandle extends AbstractTexture {
        void point(GpuTextureView view) {
            // 只填 textureView 与 sampler：本世代的绑定链路是
            // RenderSetup(Identifier) -> TextureManager#getTexture(id).getTextureView()（见
            // cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer#resolveTextureView），
            // AbstractTexture#texture 那个字段不参与这里的绑定，因此不去动它（也不依赖
            // GpuTextureView 是否暴露 texture()）。
            this.textureView = view;
            this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
        }

        @Override
        public void close() {
            // 页纹理归 FontTexture 所有，壳不持有它，勿动（否则资源重载会释放别人的纹理）。
        }
    }

    /** view → 壳 Identifier。字体图集页会随资源重载更换 view，用 identity 语义即可。 */
    private static final Map<GpuTextureView, Identifier> PAGE_IDS = new HashMap<>();
    private static final Map<Identifier, PageHandle> PAGE_HANDLES = new HashMap<>();
    private static int nextPageOrdinal = 0;

    /** 只打一次：光影包开关会在会话中途切换掩码可用性，一条布尔日志不足以说明走了哪条路。 */
    private static boolean loggedMaskedActive;

    private static Identifier pageId(GpuTextureView view) {
        Identifier id = PAGE_IDS.get(view);
        if (id == null) {
            // 注册名只求稳定唯一；序号一旦发出永不复用。
            id = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope_text_page_" + nextPageOrdinal++);
            PAGE_IDS.put(view, id);
        }
        PageHandle handle = PAGE_HANDLES.get(id);
        if (handle == null) {
            handle = new PageHandle();
            PAGE_HANDLES.put(id, handle);
            Minecraft.getInstance().getTextureManager().register(id, handle);
        }
        // 每帧刷新指向（幂等）：页纹理归 FontTexture 所有，壳只借指向。
        handle.point(view);
        return id;
    }

    /**
     * 用镜内裁剪管线提交一段文字。调用方语义与 {@code SubmitNodeCollector#submitText} 对齐
     * （{@code Font.DisplayMode.NORMAL} 的子集：瞄具文字不需要 SEE_THROUGH / POLYGON_OFFSET 那两族）。
     *
     * @return true = 已提交（含"空文本"的静默成功）；false = 本帧不可用（掩码没就绪），
     *         调用方应回退 vanilla {@code submitText}
     */
    public static boolean submit(SubmitNodeCollector collector,
                                 PoseStack poseStack,
                                 float x, float y,
                                 FormattedCharSequence text,
                                 boolean shadow,
                                 int packedLight,
                                 int color) {
        // 门禁：本帧的「世界备份 + 孔径拷贝」是否就绪。未开镜 / 序列中断时为 false，与 26.2 的
        // syncToMaskTarget() 失败语义一致 —— 回退，绝不采样陈旧掩码。
        if (!ScopeDepthCopyState.isMaskCycleValid()) {
            return false;
        }
        Font font = Minecraft.getInstance().font;
        Font.PreparedText prepared = font.prepareText(text, x, y, color, shadow, false, 0);

        // 先按图集页分组，再逐组提交 —— 一组一个 RenderType，一次 custom geometry。
        // 本世代的 visitor 把字形（Styled）与下划线/删除线（effect）分开回调，两者都进同一分组；
        // 无纹理项（空字形等）静默跳过。
        Map<GpuTextureView, List<TextRenderable>> byPage = new HashMap<>();
        prepared.visit(new Font.GlyphVisitor() {
            @Override
            public void acceptGlyph(TextRenderable.Styled renderable) {
                collect(renderable);
            }

            @Override
            public void acceptEffect(TextRenderable renderable) {
                collect(renderable);
            }

            private void collect(TextRenderable renderable) {
                GpuTextureView view = renderable.textureView();
                if (view == null) {
                    return;
                }
                byPage.computeIfAbsent(view, v -> new ArrayList<>()).add(renderable);
            }
        });
        if (byPage.isEmpty()) {
            return true;
        }

        // 快照矩阵：submitCustomGeometry 的回调在阶段边界才执行，到时调用方的 poseStack 早就翻篇了
        // （与 TextShowRender 的既有约定一致）。
        Matrix4f pose = new Matrix4f(poseStack.last().pose());

        for (Map.Entry<GpuTextureView, List<TextRenderable>> entry : byPage.entrySet()) {
            Identifier page = pageId(entry.getKey());
            List<TextRenderable> renderables = entry.getValue();
            PoseStack identity = new PoseStack();
            collector.submitCustomGeometry(identity, ScopeRenderTypes.maskedText(page),
                    (entryPose, consumer) ->
                            renderables.forEach(r -> r.render(pose, consumer, packedLight, false)));
        }
        if (!loggedMaskedActive) {
            loggedMaskedActive = true;
            GunMod.LOGGER.info("[TACZ Scope] In-scope text is now clipped to the ocular aperture mask "
                    + "({} font page group(s)).", byPage.size());
        }
        return true;
    }

    private ScopeTextSubmitter() {
    }
}
