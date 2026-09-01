package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
 * 把一段文字用【镜内裁剪】管线提交 —— 绕开 vanilla 字体管线的徒手路径（镜内文字一案）。
 *
 * <p>这是 26.2 {@code 9d036594}（in-scope text clipping）+ {@code c4eb4e2}（mode mapping
 * 修正）的 <b>26.1.2 深度孔径移植</b>：语义逐条对齐，API 全部换成 26.1.2 侧已核实的对应物。</p>
 *
 * <h2>为什么必须绕开 vanilla 管线</h2>
 * {@code submitText} 的下游（26.2 字节码实读，26.1.2 的
 * {@code TextFeatureRenderer} 结构相同）：{@code Font#prepareText} 得到
 * {@code PreparedText}，{@code visit(GlyphVisitor)} 逐字形
 * {@code renderable.render(pose, consumer, light, false)} —— RenderType 由
 * {@code GlyphRenderTypes} 三件套写死，调用方没有注入点，文字于是会溢出目镜圆孔。
 * 但 {@code prepareText → visit} 是公开 API，{@code TextRenderable#render} 接受任意
 * consumer —— 本类把 vanilla 那两步搬过来，唯独把 RenderType 换成
 * {@link ScopeRenderTypes#maskedText}（掩码裁剪版）。
 *
 * <h2>相对 26.2 的 API 映射（全部逐符号核实）</h2>
 * <ul>
 *   <li>26.2 的 {@code GlyphVisitor.acceptRenderable(TextRenderable)} 在 26.1.2 拆成
 *       {@code acceptGlyph(TextRenderable$Styled)} + {@code acceptEffect(TextRenderable)}；
 *       {@code Styled implements TextRenderable, ActiveArea}，两者都是 renderable，同一分组。</li>
 *   <li>26.2 的掩码是<b>纹理掩码</b>（ScopeMaskTextureHandle 壳）+ {@code SCOPE_MASK}
 *       着色器分支；26.1.2 的等价物是 {@link ScopeDepthCopyState} 的<b>双深度拷贝</b> +
 *       {@code tacz_ScopeMaskMode} 分支 —— RenderType 由
 *       {@code ScopeRenderTypes.maskedText} 经 {@code DepthCopyRenderType(MASK)} 在绘制边界
 *       现场绑定，与蚀刻准星完全同构。</li>
 *   <li>26.2 的门禁（总开关/光影禁用/掩码 target 同步）在 26.1.2 收敛为
 *       {@link ScopeDepthCopyState#isMaskCycleValid()}：本帧没走完
 *       「世界备份 + 孔径拷贝」就回退 vanilla 提交，绝不采样陈旧掩码。</li>
 *   <li>26.2 的 {@code WORLD_TEXT_SNIPPET} 配方在 26.1.2 由
 *       {@code clonePipeline(RenderPipelines.TEXT)} 承担（见 ScopeRenderTypes）。</li>
 * </ul>
 *
 * <h2>多页字体图集的处理</h2>
 * 每个字形的 UV 挂在它自己那页图集上（{@code TextRenderable#textureView()}）。
 * vanilla 靠 renderType 里已绑好的页纹理分批；我们按 view 分组，每组对应一个
 * 「壳 Identifier」（{@link PageHandle}：AbstractTexture 空壳 + 每帧刷新指向），
 * 组内字形共用一次 {@code submitCustomGeometry}。默认位图字体常驻页数 1-2，
 * 分组开销可忽略。资源重载后旧 view 的表项残留也只是闲置壳，无害（序号永不复用）。
 *
 * <h2>失败时的退路</h2>
 * 掩码不可用时调用方回退 vanilla {@code submitText} —— 文字特性坏掉最多回到
 * 「边缘可能溢出」的旧行为，不会画错、不会丢字。
 */
@Environment(EnvType.CLIENT)
public final class ScopeTextSubmitter {

    /**
     * 字体图集页的壳纹理（26.2 同名部件的逐行移植）。
     *
     * <p>sampler 抄 vanilla {@code FontTexture.<init>}（26.2 字节码实读）：
     * {@code getRepeat(FilterMode.NEAREST)} —— 字体图集就是这么绑的，
     * 换 clamp 会在字形边缘出现与 vanilla 不一致的采样。</p>
     */
    private static final class PageHandle extends AbstractTexture {
        void point(GpuTextureView view) {
            this.texture = view.texture();
            this.textureView = view;
            this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
        }

        @Override
        public void close() {
            // 页纹理归 FontTexture 所有，勿动。
        }
    }

    /** view → 壳。字体图集页会随资源重载更换 view，用 identity 语义即可。 */
    private static final Map<GpuTextureView, Identifier> PAGE_IDS = new HashMap<>();
    private static final Map<Identifier, PageHandle> PAGE_HANDLES = new HashMap<>();
    private static int nextPageOrdinal = 0;

    /** 「镜内文字已走掩码裁剪」这条判据日志只打一次（1.21.11 线 SYNC_CHECKLIST §2 第 1 条）。 */
    private static volatile boolean LOGGED_CLIPPED = false;

    /**
     * 字体页缓存遇到资源重载时的隐患（<b>已知未修，本轮只登记</b> —— 1.21.11 线
     * SYNC_CHECKLIST §2 第 2 条点名）。
     *
     * <p>两个缓存都是<b>按 id / 按 view</b> 的常驻映射：资源重载后 {@code FontTexture} 会换掉页
     * view，而 {@code PAGE_HANDLES} 里的壳仍指向旧对象。清空入口需要接在一个客户端资源重载
     * 监听上（本分支现成的入口是 {@code TaczMeshyIntegration} 那种
     * {@code ResourceManagerHelper.registerReloadListener}），本轮<b>没有接线</b>：接线点要挑、
     * 又只能靠实机验证，不做一个没验过的运行期改动去换一个还没人踩到的隐患。
     * 现状的失败半径：旧壳只是借指向，重载后第一次取用的可能是已释放的 view。</p>
     */

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
     * 用镜内裁剪管线提交一段文字。调用方语义与
     * {@code SubmitNodeCollector#submitText} 对齐（NORMAL 模式子集）。
     *
     * <p>vanilla 对 NORMAL 走 {@code prepareText(seq, x, y, color, shadow, false, bg)} ——
     * 这里传 background=0（瞄具文字从不配底色，{@code TextShowRender} 现状也是传 0）。</p>
     *
     * @return true = 已提交（可能是空文本的静默成功）；
     *         false = 本帧不可用（掩码没就绪），调用方应回退 vanilla submitText
     */
    public static boolean submit(SubmitNodeCollector collector,
                                 PoseStack poseStack,
                                 float x, float y,
                                 FormattedCharSequence text,
                                 boolean shadow,
                                 int packedLight,
                                 int color) {
        // 门禁：本帧的「世界备份 + 孔径拷贝」是否就绪。未开镜 / 序列中断时为 false，
        // 与 26.2 的 syncToMaskTarget() 失败语义一致 —— 回退，绝不采样陈旧掩码。
        if (!ScopeDepthCopyState.isMaskCycleValid()) {
            return false;
        }
        Font font = Minecraft.getInstance().font;
        Font.PreparedText prepared = font.prepareText(text, x, y, color, shadow, false, 0);

        // 先按图集页分组，再逐组提交 —— 一组一个 RenderType，一次 custom geometry。
        // 26.1.2 的 visitor 把字形（Styled）与下划线/删除线（effect）分开回调，
        // 两者都是 TextRenderable，进同一分组；无纹理项（EmptyGlyph 等）静默跳过。
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

        // 快照矩阵：submitCustomGeometry 的回调在阶段边界才执行，
        // 到时调用方的 poseStack 早就翻篇了（与 TextShowRender 的既有约定一致）。
        Matrix4f pose = new Matrix4f(poseStack.last().pose());

        for (Map.Entry<GpuTextureView, List<TextRenderable>> entry : byPage.entrySet()) {
            Identifier page = pageId(entry.getKey());
            List<TextRenderable> renderables = entry.getValue();
            PoseStack identity = new PoseStack();
            collector.submitCustomGeometry(identity, ScopeRenderTypes.maskedText(page),
                    (entryPose, consumer) ->
                            renderables.forEach(r -> r.render(pose, consumer, packedLight, false)));
        }
        // 【log-once 判据 —— 1.21.11 线 SYNC_CHECKLIST §2 第 1 条】没有它，
        // 「走了掩码」与「isMaskCycleValid() 为假 ⇒ 回退 vanilla」在屏幕上长得一模一样，
        // 验收矩阵无法区分这两种状态。提交成功时说一句，只说一次。
        if (!LOGGED_CLIPPED) {
            LOGGED_CLIPPED = true;
            GunMod.LOGGER.info("[TACZ Scope] In-scope text is now clipped to the ocular aperture mask "
                    + "({} font page group(s)).", byPage.size());
        }
        return true;
    }

    private ScopeTextSubmitter() {
    }
}
