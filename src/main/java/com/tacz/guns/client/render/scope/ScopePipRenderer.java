package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.compat.iris.IrisScopePipelineCompat;
import com.tacz.guns.compat.physicsmod.PhysicsModCompat;
import com.tacz.guns.compat.sodium.SodiumCompat;
import com.tacz.guns.compat.voxy.VoxyCompat;
import com.tacz.guns.compat.voxy.VoxyScopePipelineCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.tacz.guns.util.math.MathUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 瞄准镜「镜内画中画（PIP）」渲染。
 *
 * <h2>它改变了什么</h2>
 * 移植自上游的原方案里，「镜内放大」其实是<b>整屏</b> FOV 变焦
 * （{@code CameraSetupEvent#applyScopeMagnification} 压小世界 FOV），
 * 镜片只是被掏空、让已经放大的主画面原样透出来。观感上整个屏幕都在变焦，
 * 镜筒外的世界也跟着放大 —— 这不是真实瞄具的样子。
 *
 * <p>本类让镜外保持 1×，只有镜片里是放大的。
 *
 * <h2>两种模式</h2>
 * 由 {@code ScopePipRerender} 切换，合成阶段共用同一条通路：
 * <table border="1">
 *   <caption>镜内画面从哪来</caption>
 *   <tr><th></th><th>重投影（默认）</th><th>二次渲染</th></tr>
 *   <tr><td>镜内画面</td><td>主画面拷贝 + 屏幕空间等比放大</td><td>窄 FOV 真画一遍</td></tr>
 *   <tr><td>镜内分辨率</td><td>屏幕分辨率 ÷ 倍率（8× 下很糊）</td><td>原生</td></tr>
 *   <tr><td>每帧代价</td><td>一次全屏拷贝</td><td>一整遍世界渲染</td></tr>
 *   <tr><td>兼容性</td><td>只读最终颜色缓冲，天然兼容</td><td>要同步 Sodium 的投影快照，见 {@code SodiumCompat}</td></tr>
 * </table>
 *
 * <h2>重投影模式的原理</h2>
 * 关键的几何事实（见 {@link ScopePipTarget} 的类注释）：透视投影下，
 * 「把 FOV 压窄 Z 倍」在屏幕空间<b>恒等于</b>「绕光轴把画面放大 Z 倍」。
 * 于是镜内那张图不必重新渲染，只要对已经画好的世界按
 * <pre>
 * wideUV = center + (narrowUV − center) / Z
 * </pre>
 * 重采样即可 —— 结果与「用窄 FOV 重渲一遍」<b>逐像素等价</b>（分辨率除外）。
 *
 * <p>每帧只多做两件事：一次全屏纹理拷贝、一次全屏三角形绘制。
 *
 * <h2>二次渲染那条路的历史（读之前先读这段）</h2>
 * 它是本特性的第一版，实测在 Sodium 环境下画面崩坏，一度被整体否决。
 * 事后逐条反编译 Sodium 0.9.1 才查清真正的机理 —— <b>与最初的判断不同</b>：
 * <ul>
 *   <li>Sodium <b>确实</b>走 {@code GameRenderer#mainRenderTarget()}
 *       （{@code TerrainRenderPass} 字节码实读），所以<b>重定向对它是有效的</b>。
 *       最初「Sodium 完全绕开该方法」的判断是错的；</li>
 *   <li>真正的病灶是<b>投影矩阵</b>：Sodium 用
 *       {@code GameRendererStorage.sodium$getProjectionMatrix()} 里自己快照的那一份
 *       （由它 {@code WrapOperation} 包住 {@code ProjectionMatrixBuffer#getBuffer}
 *       在 {@code renderLevel} 里抓取），<b>不看</b>
 *       {@code RenderSystem.setProjectionMatrix}。第一版只改了后者，
 *       于是地形用宽 FOV、原版路径的实体用窄 FOV，两套比例糊在一起；</li>
 *   <li>另外，一帧内两次驱动 {@code LevelRenderer#render} 会打乱某处的逐帧状态
 *       → <b>镜外</b>的实体与部分物件整个消失。<b>这一条至今未查清</b>。
 *       已排除的嫌疑：ImmediatelyFast 的
 *       {@code avoid_redundant_framebuffer_switching}（它只是跳过绑定 FBO 0，
 *       不缓存「当前绑定的是谁」）。</li>
 * </ul>
 * 投影那条已由 {@code SodiumCompat} 修好，所以二次渲染重新开放为实验开关；
 * 第三条未解，这是它默认关闭的原因。
 *
 * <p>重投影方案没有这些问题：只读<b>最终颜色缓冲</b>，
 * 那些像素是谁画的、用什么投影画的都无所谓。
 *
 * <h2>代价：镜内分辨率上限 = 屏幕分辨率 ÷ 倍率</h2>
 * 合成是对<b>整屏</b>做重投影，镜片只是这张重投影图上的一个窗口：
 * 屏幕上直径 D 的镜片，其像素映射回原画面只覆盖 {@code D / Z} 个像素，
 * 却要铺满 D 个像素 ⇒ <b>放大倍数恒等于 Z</b>，与镜片大小无关。
 * 6 倍镜就是 6× 放大，必然明显变软。
 *
 * <p>着色器侧用 Catmull-Rom 双三次重建 + 按倍率加权的锐化尽量挽回主观锐度，
 * 但<b>真实细节找不回来</b> —— 信息量本来就不在主画面里。
 * 要真正的高倍清晰度只能回到二次渲染，而那条路卡在上面第三条。
 *
 * <h2>合成时机：掩码画完、镜身画之前</h2>
 * {@code renderItemInHand → renderAllFeatures} 的阶段边界，
 * {@link ScopeMaskRenderer#renderAtPhaseBoundary()} 之后立刻合成：
 * <pre>
 * 掩码画好          → 知道镜内是哪些像素
 * 【合成 PIP】      → 那些像素被贴上放大后的世界
 * executeSolid 起  → 镜身在镜内 discard（掩码），于是 PIP 画面留住；
 *                    准星反向裁剪（只画镜内），于是浮在 PIP 画面之上
 * </pre>
 * 若挪到手持渲染之后，准星就会被 PIP 盖掉；若挪到掩码之前，掩码还没就绪。
 *
 * <h2>失败即退回，永不加剧</h2>
 * 任何一环出问题都会把 {@link #failed} 置位并永久停用本特性。此后
 * {@code applyScopeMagnification} 的整屏变焦会在<b>下一帧</b>自动恢复 ——
 * 因为它每帧都重新问一次 {@link #suppressesWorldFovZoom()}，不缓存。
 * 最坏情况是回到现有已验证行为，不会出现「既没有 PIP 也没有放大」的死角。
 */
@Environment(EnvType.CLIENT)
public final class ScopePipRenderer {

    /** 合成用的掩码采样器名。与 {@code ScopeBodyRenderTypes} 那份同名不同 layout，互不影响。 */
    private static final String MASK_SAMPLER = "ScopeMaskSampler";

    /**
     * 合成管线：一个全屏三角形，按倍率重采样场景拷贝，按掩码 discard。
     *
     * <h3>配方来源</h3>
     * 逐项对照 vanilla {@code ENTITY_OUTLINE_BLIT}（{@code RenderPipelines.<clinit>}
     * 偏移 5367-5437 实读）与它的用法 {@code RenderTarget#blitAndBlendToTexture}：
     * <pre>
     * builder(GLOBALS_SNIPPET)
     *     .withVertexShader("core/screenquad")   // 无顶点缓冲，靠 gl_VertexID 造三角形
     *     .withFragmentShader("core/blit_screen")
     *     .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
     *     .withPrimitiveTopology(TRIANGLES)
     * </pre>
     * 这里 {@code GLOBALS_SNIPPET} 是 private，但 {@code POST_PROCESSING_SNIPPET}
     * 就是 {@code builder(GLOBALS_SNIPPET).withPrimitiveTopology(TRIANGLES)}
     * （偏移 1120-1142 实读）且是 public —— 正好等价，直接用它当底子。
     *
     * <h3>与 vanilla 的差异</h3>
     * <ul>
     *   <li>片元着色器换成我们的 {@code tacz:core/scope_pip}（重采样 + 掩码 discard）；</li>
     *   <li>多绑一个掩码采样器 layout；</li>
     *   <li>多绑 {@code DYNAMIC_TRANSFORMS}，借 {@code ColorModulator.r} 把倍率送进去。
     *       {@code bindDefaultUniforms} 不管这个块，得自己 {@code setUniform}
     *       —— 与 {@code ScopeMaskRenderer} 同一套路。</li>
     * </ul>
     *
     * <h3>写掩码取 {@code WRITE_COLOR} 而不是 {@code WRITE_ALL}</h3>
     * 镜内画面只该改颜色。主 target 的 alpha 通道后面还要参与 GUI/后处理的混合，
     * 顺手覆写会引入难查的偏差 —— vanilla 的 {@code ENTITY_OUTLINE_BLIT} 同样传 7。
     *
     * <h3>不声明 DepthStencilState</h3>
     * 合成是纯屏幕空间的覆盖：镜内像素无条件换成镜内画面，不参与深度测试，
     * 也不该写深度（写了会让紧接着画的准星被判成遮挡）。
     * {@code RenderPipeline#wantsDepthTexture()} 的判据是「字段是否为 null」，
     * 所以这里必须<b>不设</b>，而不是设一个 ALWAYS_PASS ——
     * 这条坑 {@code ScopeMaskRenderer} 已经踩过一次，别再踩。
     *
     * <h3>为什么是懒加载而不是 static final</h3>
     * 本类的静态初始化会被 {@code CameraSetupEvent} 的 FOV 事件触发 ——
     * 那是<b>每帧、且不看 PIP 开没开</b>的路径。若管线在 {@code <clinit>} 里构建，
     * 一旦构建抛异常（版本漂移、层名对不上）就是 {@code ExceptionInInitializerError}，
     * 连关着 PIP 的玩家都会被带崩。放进真正要用它的那一步，
     * 就落在 {@link #compositeAtPhaseBoundary()} 的 try/catch 里，最坏只是自我停用。
     */
    @Nullable
    private static RenderPipeline compositePipeline;

    private static RenderPipeline compositePipeline() {
        if (compositePipeline == null) {
            BindGroupLayout maskLayout = BindGroupLayout.builder().withSampler(MASK_SAMPLER).build();
            compositePipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_pip_composite"))
                    .withVertexShader("core/screenquad")
                    .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_pip"))
                    .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                    .withBindGroupLayout(maskLayout)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withColorTargetState(new ColorTargetState(
                            Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
                    .build();
        }
        return compositePipeline;
    }

    /** 场景纹理里是否有一张可用的本帧镜内画面。 */
    private static boolean sceneCaptured = false;

    /**
     * 「{@code mainRenderTarget()} 正在被重定向」窗口，同时就是要顶上去的那个 target。
     *
     * <p>只在二次渲染模式下、{@code LevelRenderer#render} 那一次调用期间非空。
     * 存引用而不是 boolean + 现查：一次世界渲染里 {@code mainRenderTarget()} 会被问很多次，
     * 中途若变成 null，剩下的绘制就会落回主画面，表现为「半张世界画在屏幕上」。
     */
    @Nullable
    private static RenderTarget redirectTarget = null;

    /** 二次渲染的投影暂存。首次使用时创建（构造需要 GPU 设备就绪）。 */
    @Nullable
    private static ProjectionMatrixBuffer projectionBuffer;
    private static final Projection PROJECTION = new Projection();
    /** 传给 Sodium 的窄投影矩阵，复用避免每帧分配。 */
    private static final Matrix4f NARROW_MATRIX = new Matrix4f();

    /**
     * {@code GameRendererMixin} 的 {@code mainRenderTarget()} 注入点读这里。
     *
     * @return 需要顶替主 target 时返回离屏 target；其余一切时候返回 {@code null}（不干预）
     */
    @Nullable
    public static RenderTarget redirectTarget() {
        return redirectTarget;
    }

    /**
     * 是否走「二次渲染」：用窄 FOV 把世界真画一遍，而不是把主画面重投影。
     *
     * <h3>光影下也允许了（原先是硬性拦下）</h3>
     * 早前的注释说「一帧两次驱动 Iris 会搞乱它的时域/乒乓缓冲」。那个担心针对的是
     * <b>嵌套</b>调用。实际做法是<b>顺序</b>的两遍完整管线：
     * 镜内那遍先跑完（{@code beginLevelRendering → … → finalizeLevelRendering} 走完整轮），
     * 我们把成品拷走，vanilla 那遍再从头跑一次。每一遍对 Iris 而言都是自洽的一帧。
     *
     * <p>而且 Iris 的 {@code RenderTargets} 是<b>一整套、private final</b>（字节码实读），
     * 两遍复用同一套缓冲 —— 所以这条路<b>不额外占显存</b>，第二遍只是把第一遍的内容覆盖掉。
     *
     * <p>代价是<b>真实的</b>：整条光影管线（含阴影贴图与 composite 链）跑两遍，
     * 帧率大约减半；时域效果（TAA、动态模糊）的历史每帧被推进两次，可能出现重影或噪点。
     * 所以它是玩家自选的开关，不是默认。
     */
    private static boolean rerenderMode() {
        return RenderConfig.SCOPE_PIP_RERENDER != null && RenderConfig.SCOPE_PIP_RERENDER.get();
    }

    /**
     * 是否正处在「镜内那一遍」的世界渲染调用之中。
     *
     * <p>{@code IrisScopeDimensionMixin} 靠它决定要不要把「当前维度」换成瞄具专用的那个 ——
     * 换掉之后 Iris 会给这一遍配一套<b>独立管线</b>，时域状态与主画面彻底分开。
     * 严格只在 {@code levelRenderer.render} 那一句的前后置位/清零，窗口越窄越安全。
     */
    private static volatile boolean scopePassActive = false;

    /**
     * 本遍是否还额外用了「独立 Iris 管线」。
     *
     * <h3>为什么要跟 {@link #scopePassActive} 分开</h3>
     * Voxy 的两条兼容策略是<b>互斥</b>的，取哪条正好看这个标志：
     * <ul>
     *   <li>隔离<b>开</b> → 场上有两套 Iris 管线，而 {@code VoxyRenderSystem.pipeline}
     *       是 {@code private final}、构造时就绑死在<b>其中一套</b>上
     *       （{@code RenderPipelineFactory} 取 {@code getPipelineNullable()} 当场绑定）。
     *       另一套下的 LOD 必然用错着色器/绘制目标 ⇒ 只能让 Voxy 在镜内那一遍<b>整体缺席</b>。</li>
     *   <li>隔离<b>关</b> → 只有一套管线，绑定不含糊，于是可以让 Voxy 照常在镜内渲染，
     *       只把它的<b>视口</b>分开（{@code VoxyScopeViewportMixin}）。</li>
     * </ul>
     */
    private static volatile boolean scopePassIsolated = false;

    /** 这一遍把 Voxy 换到瞄具那套了吗（换了就必须换回来）。 */
    private static boolean voxySwapped = false;
    /** 这一遍用的那个 VoxyRenderSystem —— 换回去时必须用同一个实例。 */
    private static Object voxySystemThisPass;

    /** 供 Iris 兼容层查询：当前是不是镜内那一遍。 */
    public static boolean isScopePassActive() {
        return scopePassActive;
    }

    /**
     * 是不是正处在镜内那一次 {@code levelRenderer.render} 里面 ——
     * <b>不分光影开没开</b>。
     *
     * <h3>为什么不能复用 {@link #isScopePassActive()}</h3>
     * 那个标志是 {@code scopePassActive = iris}，<b>只在开光影时</b>为真，
     * 因为它服务的是「把当前维度换成瞄具维度」这件光影专属的事。
     * 而「这一帧的提交节点被镜内那一遍吃掉了」这个问题两条路径都有，
     * 所以需要一个与光影无关的标志。
     *
     * @see com.tacz.guns.mixin.client.SimpleFeatureRenderPhaseMixin
     */
    public static boolean isInsideScopeLevelRender() {
        return insideScopeLevelRender;
    }

    /**
     * 严格套在镜内那一次 {@code levelRenderer.render} 外面。
     *
     * <p>与 {@link #scopePassActive} 的区别见 {@link #isInsideScopeLevelRender()}。
     */
    private static volatile boolean insideScopeLevelRender = false;

    /** 供 Voxy 兼容层查询：镜内这一遍是否用了独立的 Iris 管线。 */
    public static boolean isScopePassIsolated() {
        return scopePassIsolated;
    }

    /**
     * 镜内这一遍要不要让 Voxy「别画」。
     *
     * <p>只有在<b>隔离了 Iris 管线、却没能把 Voxy 切到对应的第二套渲染栈</b>时才为真 ——
     * 那种情况下 Voxy 会用主管线的绘制目标往瞄具管线里画，结果是错乱的远景。
     * 宁可镜内没有 LOD，也不能画错。
     *
     * <p>切换成功时返回 {@code false}：那时 Voxy 用的是绑定瞄具管线的那一套，
     * <b>画出来是对的</b>，正是我们要的镜内 LOD 远景。
     */
    public static boolean shouldSuppressVoxyDraw() {
        return scopePassIsolated && !voxySwapped;
    }

    /**
     * 在帧首把瞄具那套 Iris 管线预先建好，免得它落在第一次开镜的帧中途去编译。
     *
     * <p>判据与镜内那一遍一致（要开着二次渲染、开着隔离、且确实在用光影），
     * 但<b>不看开镜进度</b> —— 预热的全部意义就是赶在开镜之前做完。
     * 已经建过就是一次引用比较，代价可忽略。</p>
     *
     * <p>【空闲释放实验】{@code ScopePipReleaseIdlePipeline} 开启时，连续空闲超过
     * {@code ScopePipIdleReleaseDelayFrames} 帧就销毁瞄具管线释放 GPU 资源，
     * 空闲期间不再预热（否则释放完当帧就被重建，等于白释放）；
     * 玩家重新开镜的那一帧 extract 里重建 —— 见
     * {@link IrisScopePipelineCompat#releaseScopePipelineIfPresent}。</p>
     */
    public static void prewarmShaderPipelineIfNeeded() {
        if (failed || !rerenderMode() || !isolatePipeline()) {
            return;
        }
        if (RenderConfig.SCOPE_PIP_ENABLE == null || !RenderConfig.SCOPE_PIP_ENABLE.get()) {
            return;
        }
        if (!allowShaderPacks()) {
            return;
        }
        if (releaseIdlePipeline()) {
            if (currentAimingProgress() <= minAimingProgress()) {
                if (++idleReleaseFrames >= idleReleaseDelayFrames()) {
                    IrisScopePipelineCompat.releaseScopePipelineIfPresent();
                }
                // 空闲期间不预热：预热会立刻重建刚释放的管线。
                return;
            }
            idleReleaseFrames = 0;
        }
        IrisScopePipelineCompat.prewarmIfNeeded();
    }

    /**
     * 【光影下开镜帧率持续衰减 · 实验开关】空闲时释放瞄具管线。见
     * {@link IrisScopePipelineCompat#releaseScopePipelineIfPresent} 的完整背景。
     */
    private static boolean releaseIdlePipeline() {
        return RenderConfig.SCOPE_PIP_RELEASE_IDLE_PIPELINE != null
                && RenderConfig.SCOPE_PIP_RELEASE_IDLE_PIPELINE.get();
    }

    private static int idleReleaseFrames = 0;

    private static int idleReleaseDelayFrames() {
        return RenderConfig.SCOPE_PIP_IDLE_RELEASE_DELAY_FRAMES == null
                ? 120 : RenderConfig.SCOPE_PIP_IDLE_RELEASE_DELAY_FRAMES.get();
    }

    /** 本会话累计跑过多少次镜内那一遍（诊断探针用）。 */
    private static int scopePassCount = 0;

    public static int scopePassCount() {
        return scopePassCount;
    }

    /**
     * 是否给镜内那一遍配独立的 Iris 管线。
     *
     * <h3>与 Voxy 的关系（这条查了好几轮，别再兜圈子）</h3>
     * 隔离本身<b>不是</b>病根。真正的病根是 Voxy 的<b>每视口持续状态</b>
     * （{@code Viewport.frameId}、层级遮挡遍历、异步节点管理）被同一帧的两个不同投影
     * 轮流推进 —— 一旦写坏就不会自己复原，表现为镜外远景永久拉丝／错块。
     *
     * <p>解法不是「不隔离」，也不是「让 Voxy 在镜内缺席」，而是照 Voxy 自己的先例
     * 给镜内那一遍<b>单独要一个视口</b>（{@code VoxyScopeViewportMixin}）——
     * Iris 的阴影通道用的就是这个机制，它与镜内那一遍是同一种「同帧不同投影」的关系。
     * 这样 Voxy 依旧在镜内渲染（<b>镜内能看到 LOD 远景</b>），两边状态各走各的。
     */
    private static boolean isolatePipeline() {
        return RenderConfig.SCOPE_PIP_ISOLATE_PIPELINE == null
                || RenderConfig.SCOPE_PIP_ISOLATE_PIPELINE.get();
    }

    /** 一旦出过错就永久停用。 */
    private static boolean failed = false;

    /**
     * 镜内那一遍抛异常了，{@code FeatureRenderDispatcher} 那个「本帧的 PreparedFrame」
     * 可能还开着没关。
     *
     * <h3>为什么必须专门管这件事</h3>
     * {@code LevelRenderer#render} 的形状是（26.2 字节码实读）：
     * <pre>
     * PreparedFrame f = featureRenderDispatcher.prepareFrame(storage);   // line 183，开
     * frameGraph.execute(...);                                           // line 240，画
     * f.close();                                                         // 关
     * </pre>
     * 中间那句抛出来的话，{@code close()} 就不会被执行 —— 而
     * {@code FeatureRenderDispatcher} 全程<b>只有一个</b> PreparedFrame 实例
     * （{@code private final} 字段），它的「在用」标志就是 {@code context != null}。
     *
     * <p>于是镜内那一遍一旦失败，紧接着<b>主画面</b>那一遍调 {@code prepareFrame} 时
     * 就会撞上 {@code begin()} 开头那句：
     * <pre>if (this.context != null) throw new IllegalStateException("PreparedFrame already in use");</pre>
     * 我们这边明明已经打印了「PIP disabled, falling back to whole-screen FOV zoom」
     * 并优雅降级，游戏却还是在下一句崩掉，崩溃报告里只剩这个<b>毫不相干</b>的二次错误。
     * 玩家看到的「改完区块视距一开镜就崩」，最后杀死进程的就是它。
     *
     * <p>所以降级要真的算数，就必须把那个漏掉的 frame 关上。由
     * {@code FeatureRenderDispatcherMixin} 在下一次 {@code prepareFrame} 的 HEAD 处消费。
     */
    private static volatile boolean preparedFrameMayBeLeaked = false;

    /**
     * 取走并清掉「上一次镜内那一遍失败了」这个一次性标志。
     *
     * @return 是否刚发生过一次失败的镜内渲染（即：可能有 PreparedFrame 没关）
     */
    public static boolean consumePreparedFrameLeak() {
        if (!preparedFrameMayBeLeaked) {
            return false;
        }
        preparedFrameMayBeLeaked = false;
        return true;
    }

    private static boolean loggedFirstCapture = false;

    private ScopePipRenderer() {
    }

    // ------------------------------------------------------------------
    // 判定
    // ------------------------------------------------------------------

    /**
     * 是否该让 {@code CameraSetupEvent#applyScopeMagnification} 的整屏变焦让位给 PIP。
     *
     * <p>让位是有前提的：<b>只有真正会产出目镜掩码的通道才能走 PIP</b>。
     * 掩码没产出 = 合成阶段贴不进任何像素，此时若还把整屏变焦也关掉，
     * 那把枪就彻底没有放大了 —— 这正是红点/全息与组合镜低倍档的处境
     * （{@code BedrockAttachmentModel#activeGroupIsScope} 判定为 sight 通道时
     * 不登记目镜几何，因为上游 {@code renderSight} 本来就不裁镜身）。</p>
     *
     * <p>判据直接取模型自己的结论（{@link ScopeMaskRenderer#hasMaskThisFrame()}），
     * 而不是在这里重算一遍 sight/scope/组合镜的分档 ——
     * 那套状态机牵扯 {@code views[]} 索引与目镜节点命名，复制一份必然走样。</p>
     *
     * <h3>关于一帧延迟</h3>
     * FOV 事件发生在 {@code extract} 阶段，掩码画在同一帧稍后的手持渲染里，
     * 所以这里读到的是<b>上一帧</b>的结论。这不构成问题：
     * 唯一会读到「旧值 false」的时刻是刚开始抬镜的那一帧，
     * 而那一帧 {@code aimingProgress ≈ 0.02}，变焦公式
     * {@code 1 + (zoom - 1) * progress} 算出来几乎就是原始 FOV，
     * 再经 {@code WORLD_FOV_DYNAMICS} 二阶平滑，肉眼不可见。收镜时同理对称。
     */
    public static boolean suppressesWorldFovZoom() {
        return isEnabledForHeldGun();
    }

    /**
     * 【诊断】上一次报告过的闸门状态，以及报告时刻。
     *
     * <p>本特性有七道闸门，任何一道不满足都表现为「PIP 没生效、还是整屏变焦」——
     * 而这七种情况在画面上<b>完全一样</b>，纯靠肉眼无法区分是配置没生效、
     * 是瞄具通道不对、还是光影拦住了。每次状态<b>变化</b>时打一行日志，
     * 把「为什么没生效」变成一个可读的事实，省掉来回猜。</p>
     */
    private static String lastReportedGate = "";
    private static int gateChangeCount = 0;
    /** 已经播报过的闸门理由；每种只说一次，避免开镜/收镜来回刷屏。 */
    private static final java.util.Set<String> REPORTED_GATES = new java.util.HashSet<>();
    private static final int GATE_LOG_LIMIT = 40;

    private static void reportGate(@Nullable String reason) {
        // 功能整个关着的时候不吭声，否则对不用这个特性的玩家就是刷屏。
        if (RenderConfig.SCOPE_PIP_ENABLE == null || !RenderConfig.SCOPE_PIP_ENABLE.get()) {
            return;
        }
        String now = reason == null ? "ACTIVE" : reason;
        if (now.equals(lastReportedGate)) {
            return;
        }
        lastReportedGate = now;
        // 【每种理由只说一次】特性跑通之后，ACTIVE ↔ 未激活 的来回翻转就是
        // 玩家正常的抬镜/收镜，一次开镜一行，一局下来能刷上百行，纯粹是噪音。
        // 而「为什么没生效」这个信息本身仍然有价值 —— 所以按<b>理由</b>去重：
        // 每种理由（含 ACTIVE）只播报第一次，之后同样的理由再出现多少次都不吭声。
        if (!REPORTED_GATES.add(now)) {
            return;
        }
        gateChangeCount++;
        // 【不做时间限流】第一版按「两秒最多一条」限流，结果把一次【逐帧抖动】
        // 打印成了整齐的两秒间隔，看上去就像玩家在正常地抬镜/收镜 ——
        // 真正的病灶（Iris 双手部 pass 把标志抹掉，导致判据每帧翻转）
        // 反而被日志的形状藏住了。诊断日志把病症整形成正常样，比没有日志更糟。
        //
        // 改成「按变化打印 + 总量封顶」：抖动会以密集的连续行原形毕露，
        // 又不会无限刷屏。
        if (gateChangeCount <= GATE_LOG_LIMIT) {
            GunMod.LOGGER.info("[TACZ Scope] Scope PIP gate -> {}", now);
            if (gateChangeCount == GATE_LOG_LIMIT) {
                GunMod.LOGGER.info("[TACZ Scope] Scope PIP gate changed {} times; muting further gate logs. "
                        + "If those lines were alternating rapidly, the gate is flapping, not settling.",
                        GATE_LOG_LIMIT);
            }
        }
    }

    private static boolean isEnabledForHeldGun() {
        String reason = inactiveReason();
        reportGate(reason);
        return reason == null;
    }

    /**
     * @return {@code null} 表示七道闸门全过；否则返回卡在哪一道的可读说明
     */
    @Nullable
    private static String inactiveReason() {
        if (failed) {
            return "disabled after a runtime failure (see the earlier error above)";
        }
        if (RenderConfig.SCOPE_PIP_ENABLE == null || !RenderConfig.SCOPE_PIP_ENABLE.get()) {
            return "ScopePipEnable is off";
        }
        // 合成完全依赖目镜掩码提供「镜内是哪些像素」。掩码关掉时 PIP 无从落地，
        // 此时必须让整屏变焦继续生效，否则倍镜等于失效。
        if (RenderConfig.SCOPE_MASK_ENABLE == null || !RenderConfig.SCOPE_MASK_ENABLE.get()) {
            return "ScopeMaskEnable is off (PIP needs the ocular mask to know where the lens is)";
        }
        // 掩码自己判定不安全的环境（目前是 Sulkan），PIP 无条件跟着关 ——
        // 没有掩码就没有孔径，谈不上贴图。
        if (IrisCompat.shouldDisableScopeMaskUnderShaderPack()) {
            return "the ocular mask reports this renderer as unsafe (Sulkan)";
        }
        // 光影包：默认关，但可由玩家打开。
        //
        // 这里刻意【不】沿用 shouldDisableScopeMaskUnderShaderPack 的结论 ——
        // 那个方法对 Iris 返回 false（本仓库专门做了 assignPipeline → Iris HAND program
        // 的兼容层，目镜掩码在光影下是支持的）。PIP 比掩码多两件未验证的事：
        //   1. 抓取时机在 LevelRenderer#render 之后，而延迟管线的 composite 可能还没跑完，
        //      拷到的也许是未着色的中间结果；
        //   2. 合成写的是裸颜色，而光影通常在 tonemap 之前工作于线性/HDR 空间，
        //      镜内可能偏灰或过曝。
        // 两者都只是【观感】风险 —— 重投影不重新驱动世界渲染，
        // 不可能像第一版那样打乱别的 mod 的逐帧状态，所以「打开试一眼」是安全的。
        // 默认关是保守，不是「已知不兼容」。
        if (IrisCompat.isUsingRenderPack() && !allowShaderPacks()) {
            return "a shader pack is active and ScopePipAllowShaderPacks is off";
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return "no level/player";
        }
        if (!mc.options.getCameraType().isFirstPerson()) {
            return "not in first person";
        }
        if (scopeMagnification() <= 1.0f) {
            return "held gun has no scope attachment with zoom > 1 (iron sights and 1x optics keep the old FOV zoom)";
        }
        // 最后一道，也是最容易被误判成「PIP 坏了」的一道：目镜掩码到底有没有产出。
        // 没有 = 当前是 sight 通道（红点/全息/组合镜低倍档），或者掩码链路在当前
        // 渲染环境下没跑起来。两种都必须让整屏变焦继续接管。
        //
        // 【两个快照都认】本方法被三个时机调用，各自该看哪一份并不相同：
        //   FOV 让位（extract 阶段）、镜内抓取（renderLevel 里）→ 本帧掩码还没画，看上一帧；
        //   合成（手部 pass，掩码刚画完）                        → 看本帧。
        // 取「上一帧或本帧画过」这个并集，三个时机就都成立，
        // 也不必给调用方分叉出两套判据。
        if (!ScopeMaskRenderer.hadMaskLastFrame() && !ScopeMaskRenderer.hasMaskThisFrame()) {
            return "no ocular mask produced (sight/red-dot channel, or the mask pass is not running "
                    + "in this renderer setup)";
        }
        return null;
    }

    /**
     * 当前瞄具配件的倍率；没装倍镜（含机瞄）时返回 1。
     *
     * <h3>为什么按配件取而不是 {@code IGun#getAimingZoom}</h3>
     * 后者把机瞄的 {@code ironZoom}（默认枪包里普遍是 1.2~1.5）也算进来。
     * 机瞄<b>没有</b> {@code ocular} 骨骼，掩码永远是空的 —— PIP 贴不进任何像素，
     * 却顺带丢掉了整屏变焦。所以这里只认真正的瞄具配件，
     * 口径与用户的诉求（scope attachment）一致。
     *
     * <p>取档逻辑与 {@code MouseHandlerMixin#reduceSensitivity} 逐行同源：
     * {@code zoom[zoomNumber % zoom.length]}，组合镜切档自动跟随。
     */
    /**
     * 本帧的瞄具倍率记忆值（NaN = 本帧还没算过）。
     *
     * <p>这个值一帧内<b>不会变</b>（持有的物品与配件在一帧渲染中是快照），
     * 而它每帧要被问将近十次：闸门判定、FOV 让位、倍率拆分、合成、窄投影……
     * 而每次都要读两次 NBT（{@code getAttachmentId} / {@code getAttachmentTag}）
     * 并走一次 {@code TimelessAPI} 的 Optional 查表 —— 都是有分配的。算一次就够。
     *
     * <p>由 {@link #beginFrame()} 在帧首清空。
     */
    private static float magnificationThisFrame = Float.NaN;

    /** 每帧清一次帧内记忆值。挂在 {@code GameRenderer#extract} 的 HEAD。 */
    public static void beginFrame() {
        magnificationThisFrame = Float.NaN;
    }

    private static float scopeMagnification() {
        float cached = magnificationThisFrame;
        if (!Float.isNaN(cached)) {
            return cached;
        }
        float value = computeScopeMagnification();
        magnificationThisFrame = value;
        return value;
    }

    private static float computeScopeMagnification() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 1.0f;
        }
        ItemStack stack = KeepingItemRenderer.getRenderer().getCurrentItem();
        if (!(stack.getItem() instanceof IGun iGun)) {
            return 1.0f;
        }
        Identifier scopeId = iGun.getAttachmentId(stack, AttachmentType.SCOPE);
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            scopeId = iGun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
        }
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            return 1.0f;
        }
        return TimelessAPI.getClientAttachmentIndex(scopeId).map(index -> {
            float[] zoom = index.getZoom();
            if (zoom == null || zoom.length == 0) {
                return 1.0f;
            }
            CompoundTag scopeTag = iGun.getAttachmentTag(stack, AttachmentType.SCOPE);
            int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
            return zoom[Math.floorMod(zoomNumber, zoom.length)];
        }).orElse(1.0f);
    }

    /**
     * 光影路径专用：本帧是否该在 Iris 的帧缓冲里合成镜内画面。
     *
     * <p>与无光影路径共用同一套闸门（配件倍率、第一人称、开镜进度、掩码通道），
     * 只是<b>反过来</b>要求光影处于启用状态 —— 那条路只在 Iris 管线里才成立。
     *
     * <p>掩码判据取 {@link ScopeMaskRenderer#hadMaskLastFrame()}：合成点在手部 pass 之前，
     * 本帧的掩码还没画出来。这就是那一帧延迟的来源，见 {@code IrisScopePip} 的说明。
     */
    public static boolean wantsIrisComposite() {
        if (failed) {
            return false;
        }
        if (RenderConfig.SCOPE_PIP_ENABLE == null || !RenderConfig.SCOPE_PIP_ENABLE.get()) {
            return false;
        }
        if (RenderConfig.SCOPE_MASK_ENABLE == null || !RenderConfig.SCOPE_MASK_ENABLE.get()) {
            return false;
        }
        if (!allowShaderPacks() || !IrisCompat.isUsingRenderPack()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
            return false;
        }
        if (scopeMagnification() <= 1.0f) {
            return false;
        }
        if (currentAimingProgress() <= minAimingProgress()) {
            return false;
        }
        return ScopeMaskRenderer.hadMaskLastFrame();
    }

    /** 供光影合成路径读取当前倍率。 */
    public static float currentMagnification() {
        return Math.max(1.0f, scopeMagnification());
    }

    // ------------------------------------------------------------------
    // 倍率拆分：世界放大 W × 镜内放大 P = 瞄具倍率 Z
    // ------------------------------------------------------------------

    /**
     * 镜外世界要跟着放大多少倍（满开镜时）。
     *
     * <h3>它解决的是镜内分辨率，而且是唯一能真正解决的办法</h3>
     * 镜内画面是主画面中心那一小块放大来的：放大 Z 倍，就只有 {@code 1/Z} 的屏幕像素可用 ——
     * 这是<b>信息量</b>上限，锐化、双三次重建都只能改善主观锐度，变不出真实细节。
     * 唯一的出路是<b>让镜内少放大一点</b>：世界先放大 W，镜内只需再放大 {@code Z/W}，
     * 于是镜内拿到的真实像素<b>多 W 倍</b>。
     *
     * <p>代价正是 PIP 存在的意义本身（镜外保持 1×），所以默认 {@code 1.0} = 一点都不让，
     * 由玩家自己在「镜外纯净」与「镜内清晰」之间挑。
     *
     * <p>取值被瞄具倍率夹住：{@code W = Z} 时镜内不再放大，本特性退化成改动前的整屏变焦
     * —— 那一档反而是最清晰的（镜内像素全部原生），这条连续性正是这个旋钮的下界与上界。
     *
     * <h3>二次渲染模式恒返回 1</h3>
     * 那条路的镜内像素是用窄 FOV <b>真画出来的</b>，本来就没有分辨率上限，
     * 让世界放大只会白白牺牲镜外画质、换不到任何东西。
     */
    private static float worldZoomTarget() {
        if (rerenderMode()) {
            return 1.0f;
        }
        if (RenderConfig.SCOPE_PIP_WORLD_ZOOM_SHARE == null) {
            return 1.0f;
        }
        float share = Mth.clamp(RenderConfig.SCOPE_PIP_WORLD_ZOOM_SHARE.get().floatValue(), 0.0f, 1.0f);
        if (share <= 0.0f) {
            return 1.0f;
        }
        float zoom = Math.max(1.0f, scopeMagnification());
        // 【为什么是 Z^share 而不是「允许的最大世界倍率」】
        //
        // 第一版给的是绝对上限 ScopePipMaxWorldZoom，它有个致命缺陷：W 被 Z 夹住，
        // 于是任何 >= Z 的取值都得到 W = Z ⇒ 镜内倍率 Z/W = 1 ⇒ **镜内彻底不放大了**，
        // PIP 名存实亡。而那个临界点是<b>瞄具倍率本身</b>，换把枪就换个位置 ——
        // 玩家实测「超过 3.0 就没有放大了」正是撞上了这条（那把镜正好 3×）。
        // 于是整个 3.0~8.0 区间是一段行为完全相同、且功能已被关掉的死区。
        //
        // 改成按比例分配就没有这个问题：倍率是<b>相乘</b>的，
        //     世界 W = Z^share，镜内 P = Z/W = Z^(1-share)
        // share 在 [0,1] 上每一点都有意义、且与瞄具倍率无关：
        //     share=0   → W=1、P=Z      纯 PIP（镜外 1×，最软）
        //     share=0.5 → W=P=√Z        对半分，镜内真实像素 √Z 倍
        //     share=1   → W=Z、P=1      整屏变焦（最锐，等于关掉 PIP）
        // 两端都是已知行为，中间连续，没有死区，换任何倍率的镜表现都一致。
        return (float) Math.pow(zoom, share);
    }

    /**
     * 当前这一帧世界实际放大了多少 —— 与 {@code CameraSetupEvent} 里那条
     * {@code 1 + (zoom-1)·progress} 完全同式，两边必须逐帧一致，否则镜内外会打架。
     */
    public static float worldZoomAtProgress(float aimingProgress) {
        return 1.0f + (worldZoomTarget() - 1.0f) * Mth.clamp(aimingProgress, 0.0f, 1.0f);
    }

    /** 供 {@code CameraSetupEvent} 使用：PIP 生效时世界该放大多少。 */
    public static float currentWorldZoom() {
        return worldZoomAtProgress(currentAimingProgress());
    }

    private static float currentAimingProgress() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return 0.0f;
        }
        return Mth.clamp(IClientPlayerGunOperator.fromLocalPlayer(player)
                .getClientAimingProgress(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)), 0.0f, 1.0f);
    }

    // ------------------------------------------------------------------
    // 抓取本帧的世界画面
    // ------------------------------------------------------------------

    /**
     * 把刚画完的世界拷进离屏纹理，留给稍后的合成阶段重采样。
     *
     * <h3>为什么必须卡在这一刻</h3>
     * 由 {@code GameRendererMixin} 注入在 {@code renderLevel} 里
     * {@code LevelRenderer#render} 那次调用<b>之后</b>：
     * <ul>
     *   <li>再早 → 世界还没画完；</li>
     *   <li>再晚（越过 {@code renderItemInHand}）→ 拷贝里会混进枪和手，
     *       镜片里就会出现一把缩小的枪。</li>
     * </ul>
     * 这个位置正好是「世界已完成、视模尚未开始」的唯一窗口
     * （renderLevel 偏移 405 之后、502 的 renderItemInHand 之前）。
     *
     * <p>已知的小缺口：发光实体描边由 {@code levelRenderer.doEntityOutline()}
     * 在 {@code renderLevel} 返回<b>之后</b>才贴到主画面（GameRenderer 偏移 275），
     * 所以镜内看不到那圈描边。属可接受的观感差异，不影响正确性。
     */
    public static void captureScene(Minecraft mc) {
        if (failed || rerenderMode()) {
            // 二次渲染模式下镜内画面由 renderScopeView 产出，不走拷贝。
            return;
        }
        // 光影下这个注入点的含义不同：Iris 把手部渲染搬进了 LevelRenderer#render 内部，
        // 于是「LevelRenderer#render 之后」= 整条 Iris 管线（延迟光照、composite、
        // 色调映射、手部）<b>全部跑完</b>之后 —— 抓到的正是最终画面。
        // 合成随后由 compositeAfterLevelUnderShaders() 在同一处完成。
        if (!isEnabledForHeldGun() || currentAimingProgress() <= minAimingProgress()) {
            sceneCaptured = false;
            return;
        }
        try {
            RenderTarget main = mc.gameRenderer.mainRenderTarget();
            if (main == null) {
                sceneCaptured = false;
                return;
            }
            GpuTexture source = main.getColorTexture();
            if (source == null || source.isClosed()) {
                sceneCaptured = false;
                return;
            }
            int w = source.getWidth(0);
            int h = source.getHeight(0);
            // 格式取自源纹理本身：copyTextureToTexture 要求两端一致，
            // 而主 target 的格式并不保证永远是 RGBA8_UNORM（取决于 MainTarget 的构造）。
            TextureTarget copy = ScopePipTarget.getOrCreate(w, h, source.getFormat(), false);
            if (copy == null) {
                failed = true;
                sceneCaptured = false;
                return;
            }
            // 逐像素拷贝。两张纹理都是 RenderTarget#createBuffers 建的，
            // usage 位是 15 = COPY_DST|COPY_SRC|TEXTURE_BINDING|RENDER_ATTACHMENT
            //（字节码 bipush 15 实读），拷贝所需的两个位都在。
            //
            // 参数顺序照 CommandEncoder#copyTextureToTexture 的边界检查反推：
            //   (src, dst, mipLevel, dstX, dstY, srcX, srcY, width, height)
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(source, copy.getColorTexture(), 0, 0, 0, 0, 0, w, h);
            sceneCaptured = true;
            if (!loggedFirstCapture) {
                loggedFirstCapture = true;
                GunMod.LOGGER.info("[TACZ Scope] Scope PIP active: reprojecting a {}x{} scene copy at {}x magnification.",
                        w, h, scopeMagnification());
            }
        } catch (Exception e) {
            failed = true;
            sceneCaptured = false;
            GunMod.LOGGER.error("[TACZ Scope] Scope PIP scene capture failed; PIP disabled, "
                    + "falling back to whole-screen FOV zoom.", e);
        }
    }

    // ------------------------------------------------------------------
    // 二次渲染（ScopePipRerender = true）
    // ------------------------------------------------------------------

    /**
     * 【二次渲染模式】用窄 FOV 把世界再画一遍到离屏 target，得到<b>原生分辨率</b>的镜内画面。
     *
     * <p>由 {@code GameRendererMixin} 注入在 {@code renderLevel} 里
     * {@code LevelRenderer#render} 那次调用<b>之前</b> —— 让 vanilla 那一遍收尾，
     * 覆盖掉我们可能污染到的共享状态（{@code entityOutlineTarget}、区块编译调度等）。
     *
     * <h2>与重投影模式的取舍</h2>
     * 重投影的镜内分辨率上限是「屏幕分辨率 ÷ 倍率」，8 倍镜下惨不忍睹。
     * 这条路的镜内像素是<b>真画出来的</b>，没有那个上限；代价是每帧多跑一遍世界渲染。
     *
     * <h2>两处必须同时改的投影</h2>
     * <ol>
     *   <li>{@code RenderSystem.setProjectionMatrix(...)} —— 原版路径（实体、粒子、天空）看这个；</li>
     *   <li>{@link SodiumCompat#overrideProjection} —— <b>Sodium 的地形只看它自己的快照</b>。
     *       第一版漏了这条，结果地形用宽 FOV、实体用窄 FOV，镜内两套比例糊在一起。</li>
     * </ol>
     */
    public static void renderScopeView(Minecraft mc,
                                       GraphicsResourceAllocator allocator,
                                       FogRenderer fogRenderer,
                                       GameRenderState gameRenderState,
                                       DeltaTracker deltaTracker) {
        if (failed || !rerenderMode()) {
            return;
        }
        if (redirectTarget != null) {
            // 理论不可达（我们调的是 levelRenderer.render，不是 renderLevel），
            // 但重入一次就会把离屏 target 画花且极难定位。
            return;
        }
        if (!isEnabledForHeldGun() || currentAimingProgress() <= minAimingProgress()) {
            sceneCaptured = false;
            return;
        }
        CameraRenderState camera = gameRenderState.levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized || camera.isPanoramicMode) {
            sceneCaptured = false;
            return;
        }
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            sceneCaptured = false;
            return;
        }
        GpuTexture mainColor = main.getColorTexture();
        if (mainColor == null || mainColor.isClosed()) {
            sceneCaptured = false;
            return;
        }
        // 光影下不重定向渲染目标，所以离屏纹理只当「拷贝目的地」用，不需要深度附件；
        // 无光影下我们要把整遍世界画进它，没有深度就没有遮挡关系。
        boolean iris = IrisCompat.isUsingRenderPack();
        TextureTarget pip = ScopePipTarget.getOrCreate(main.width, main.height, mainColor.getFormat(), !iris);
        if (pip == null) {
            failed = true;
            sceneCaptured = false;
            return;
        }

        // 存档。刻意不用 RenderSystem.backupProjectionMatrix()：那对 save/restore 共用
        // 一个静态槽位，若二次渲染内部（后处理链等）也用了它，我们的还原就会拿到别人的值。
        GpuBufferSlice savedProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType savedProjectionType = RenderSystem.getProjectionType();
        // 【第三份投影】相机状态里的那一份。必须在 buildNarrowProjection <b>之后</b>再改 ——
        // 它要从这里反解基准 FOV（读 m11）。
        Matrix4f savedCameraProjection = new Matrix4f(camera.projectionMatrix);
        boolean cameraProjectionPatched = false;
        boolean physicsPatched = false;
        boolean sodiumPatched = false;
        ScopePipResourceProbe.onScopePassBegin();
        try {
            if (!buildNarrowProjection(camera, pip)) {
                sceneCaptured = false;
                return;
            }
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(PROJECTION), ProjectionType.PERSPECTIVE);
            sodiumPatched = SodiumCompat.overrideProjection(NARROW_MATRIX);
            // 【第三处必须同步的投影 —— 漏了它 Voxy 的 LOD 地形不会跟着放大】
            //
            // 一共有三份互不相干的投影来源，缺一处就有一类东西留在宽 FOV：
            //   ① RenderSystem.setProjectionMatrix        原版路径：实体、粒子、天空
            //   ② sodium$getProjectionMatrix（快照）      Sodium 地形，**以及 Iris 的 gbuffer 投影**
            //   ③ CameraRenderState.projectionMatrix     ← 这里
            //
            // ③ 是 Voxy 在光影下的取值处（`MixinLevelRenderer.voxy$injectIrisCompat`
            // 直接 getfield 这个字段，字节码实读）。只改 ①② 的话，近处地形被放大、
            // 而远处 Voxy LOD 仍是宽 FOV —— 实测正是「近景跟着放大、远景纹丝不动」。
            //
            // 就地 set 而不是换引用：这个 Matrix4f 对象被别处持有着，
            // 换引用改不到那些持有者，就地改再改回来才是对的。
            camera.projectionMatrix.set(NARROW_MATRIX);
            cameraProjectionPatched = true;
            // 【第四处】Physics Mod 自己存了一份投影，草/灯笼/门/旗帜这些可动方块用它。
            // 不同步的话它们在镜内不放大，还叠在放大后的画面之上。见 PhysicsModCompat。
            physicsPatched = PhysicsModCompat.overrideProjection(NARROW_MATRIX);

            boolean renderSky = !mc.gui.hud.getBossOverlay().shouldCreateWorldFog();
            // 【光影下不重定向】Iris 不往 mainRenderTarget() 画世界 —— 它画进自己那套
            // colortex，最后由 FinalPassRenderer.renderFinalPass() 把成品合成到主帧缓冲
            // （IrisRenderingPipeline#finalizeLevelRendering 字节码实读）。
            // 所以镜内那遍要的是「让它照常跑完，然后把主帧缓冲拷走」，
            // 而不是把它的输出目标换掉 —— 后者只会让重定向落空、还可能把 Iris 弄乱。
            ScopePipTrace.mark(iris
                    ? "SCOPE-PASS BEGIN (iris: full pipeline, captured from the main target)"
                    : "SCOPE-PASS BEGIN (redirect active)");
            redirectTarget = iris ? null : pip;
            // 【时域隔离】只在这一段把「当前维度」换成瞄具专用的那个，
            // 于是 Iris 给镜内这一遍配一套<b>独立的管线</b>（独立的 colortex、
            // 独立的 previous 系列 uniform）。见 IrisScopeDimensionMixin。
            scopePassActive = iris;
            scopePassIsolated = iris && isolatePipeline();
            // 隔离模式下，把 Voxy 整体切到「绑定瞄具管线」的那第二套渲染栈上，
            // 于是镜内也能画出 LOD 远景。切不过去（没装 Voxy／建栈失败）就照旧，
            // 由 VoxyRenderSystemMixin 让它在这一遍不画，至少不会画错。
            // 【这里只换，绝不建】建栈必须发生在预热那个窗口里 ——
            // 那时我们主动把瞄具管线设成了「当前管线」，Voxy 才会绑对。
            // 在这里建过一次，代价是整局崩：重复建会抛 "Pipeline data already bound"，
            // 而 Voxy 捕获后会调 disableIrisShaders() 把 Iris 整个拆掉，
            // 主画面下一次 Voxy 绘制就 NPE。教训写在 VoxyScopePipelineCompat 里。
            voxySystemThisPass = scopePassIsolated ? VoxyCompat.renderSystem() : null;
            voxySwapped = voxySystemThisPass != null
                    && VoxyScopePipelineCompat.swapIn(voxySystemThisPass);
            // 【本帧的提交节点要留给主画面】开着这个标志时，各 FeatureRenderPhase
            // 在 sortInto 末尾<b>不清空自己</b>，于是紧随其后的主画面那一遍还能
            // 再取一次同样的实体/方块实体/名牌。详见 SimpleFeatureRenderPhaseMixin。
            insideScopeLevelRender = true;
            try {
                mc.levelRenderer.render(
                        allocator,
                        deltaTracker,
                        // 方块高亮线框：镜内不画，屏幕空间的描边在镜内没有意义。
                        false,
                        camera,
                        camera.viewRotationMatrix,
                        fogRenderer.getBuffer(FogRenderer.FogMode.WORLD),
                        camera.fogData.color,
                        renderSky);
            } finally {
                // 必须最先清：从这里往后（主画面那一遍）各 phase 要恢复
                // 「取完就清空」的原样，否则节点会一直堆到下一帧去。
                insideScopeLevelRender = false;
                // 必须先关掉：之后任何再问「当前维度」的代码都必须拿到<b>真实</b>维度，
                // 否则 Iris 在切世界时会把 lastDimension 与当前值比出「维度变了」，
                // 进而 destroyPipeline() —— 那是整套缓冲重建，正是要避免的抖动。
                // 先把 Voxy 换回主管线，再清标志 —— 顺序不能反：
                // swapOut 里读的是「这一遍用的那个 system」，而清标志会让其它兼容层
                // 立刻恢复常态，两者之间不该有交叉窗口。
                if (voxySwapped) {
                    VoxyScopePipelineCompat.swapOut(voxySystemThisPass);
                    voxySwapped = false;
                }
                voxySystemThisPass = null;
                scopePassActive = false;
                scopePassIsolated = false;
                redirectTarget = null;
                ScopePipTrace.mark("SCOPE-PASS END");
            }
            if (iris) {
                // 立刻拷走：紧接着 vanilla 那一遍会把主帧缓冲整个重画。
                // 这一拷贝就是镜内画面 —— 已经过完整光影着色，且是<b>原生分辨率</b>
                // 用窄 FOV 真画出来的，不是放大出来的。
                GpuTexture shaded = main.getColorTexture();
                if (shaded == null || shaded.isClosed()) {
                    sceneCaptured = false;
                    return;
                }
                RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                        shaded, pip.getColorTexture(), 0, 0, 0, 0, 0,
                        Math.min(main.width, pip.width), Math.min(main.height, pip.height));
            }
            sceneCaptured = true;
            scopePassCount++;
            if (!loggedFirstCapture) {
                loggedFirstCapture = true;
                GunMod.LOGGER.info("[TACZ Scope] Scope PIP second-render pass active: {}x{} at {}x "
                                + "(sodium terrain projection synced: {}).",
                        pip.width, pip.height, scopeMagnification(), sodiumPatched);
            }
        } catch (Exception e) {
            failed = true;
            sceneCaptured = false;
            // 【降级要真的算数】上面那句 levelRenderer.render 若是在
            // frame graph 执行途中抛的，本帧的 PreparedFrame 就还开着 ——
            // 不关掉的话，紧随其后的主画面那一遍会以
            // "PreparedFrame already in use" 当场崩游戏，把这里的优雅降级变成一句空话。
            // 见 preparedFrameMayBeLeaked 的注释。
            preparedFrameMayBeLeaked = true;
            GunMod.LOGGER.error("[TACZ Scope] Scope PIP second-render pass failed; PIP disabled, "
                    + "falling back to whole-screen FOV zoom.", e);
        } finally {
            ScopePipResourceProbe.onScopePassEnd();
            if (cameraProjectionPatched) {
                // 必须还原：这个字段是 vanilla 那一遍以及后续所有消费者共用的。
                // 留着窄投影会让主画面的 Voxy LOD 也跟着放大 —— 正好是反过来的病。
                camera.projectionMatrix.set(savedCameraProjection);
            }
            if (physicsPatched) {
                PhysicsModCompat.restoreProjection();
            }
            if (sodiumPatched) {
                SodiumCompat.restoreProjection();
            }
            // 【关键】把 Sodium「本帧区块 uniform 已上传」的闸重新打开。
            //
            // 不做这一步，紧随其后的 vanilla 那一遍调 update() 会被早退挡掉，
            // 主画面的地形就继续用我们刚上传的【窄投影】绘制 —— 表现为近处的水、
            // 冰柱被拉伸放大，而远处走 Voxy LOD 的地形正常。这正是长期被误判为
            // 「镜内画面溢出到镜外」的那个症状的真正成因。详见 SodiumCompat 的说明。
            //
            // 放在 finally 里：哪怕镜内那一遍中途抛了异常，也绝不能把主画面留在错误的投影上。
            SodiumCompat.resetChunkUniformUpload();
            RenderSystem.setProjectionMatrix(savedProjection, savedProjectionType);
        }
    }

    /**
     * 按瞄具倍率算出镜内那一遍的透视投影，写进 {@link #PROJECTION} 与 {@link #NARROW_MATRIX}。
     *
     * <h3>基准 FOV 从投影矩阵反解，而不是读 {@code options.fov()}</h3>
     * 当帧的世界 FOV 还叠着疾跑/药水/{@code fovEffectScale} 与我们自己的平滑。
     * 而透视矩阵的 {@code m11 = 1 / tan(fovY / 2)} 是恒等式，与纵横比、近远平面都无关，
     * 反解出来的就是 vanilla 本帧真正用的那个 FOV。
     */
    private static boolean buildNarrowProjection(CameraRenderState camera, TextureTarget pip) {
        float m11 = camera.projectionMatrix.m11();
        if (!Float.isFinite(m11) || m11 <= 1.0e-4f) {
            return false;
        }
        double baseFov = Math.toDegrees(2.0 * Math.atan(1.0 / m11));
        double pipFov = MathUtil.magnificationToFov(scopeMagnification(), baseFov);
        if (!Double.isFinite(pipFov) || pipFov <= 0.0) {
            return false;
        }
        // 近/远平面与 vanilla 相机逐值一致（Camera#update 偏移 160-175）。
        PROJECTION.setupPerspective(0.05f, camera.depthFar, (float) pipFov, pip.width, pip.height);
        PROJECTION.getMatrix(NARROW_MATRIX);
        if (projectionBuffer == null) {
            projectionBuffer = new ProjectionMatrixBuffer("tacz scope pip");
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 合成
    // ------------------------------------------------------------------

    /**
     * 在阶段边界把放大后的世界贴进目镜孔径。
     *
     * <p>调用点在 {@code FeatureRenderDispatcherMixin}，紧跟
     * {@link ScopeMaskRenderer#renderAtPhaseBoundary()} 之后 —— 见类注释里的顺序论证。
     */
    public static void compositeAtPhaseBoundary() {
        if (failed || !sceneCaptured) {
            return;
        }
        if (irisOwnsLens()) {
            // 见 captureScene：光影下这条路整条让开，镜片只能有一个主人。
            return;
        }
        // 【诊断】只跑镜内那一遍、不合成。见 ScopePipDebugNoComposite 的说明。
        if (RenderConfig.SCOPE_PIP_DEBUG_NO_COMPOSITE != null
                && RenderConfig.SCOPE_PIP_DEBUG_NO_COMPOSITE.get()) {
            return;
        }
        if (!ScopeMaskRenderer.isInHandPass()) {
            return;
        }
        // 每帧只合成一次。Iris 的 HandRenderer 一帧跑两次手部 pass，
        // 第二次再合成会把 solid 阶段已经画进孔径的东西（蚀刻准星等）整片盖掉。
        if (!ScopeMaskRenderer.claimCompositeSlot()) {
            return;
        }
        // 注意：isEnabledForHeldGun 里那道掩码判据，在这个调用点读到的是【本帧】的值
        //（掩码刚在上一句 renderAtPhaseBoundary 里画完），而在
        // suppressesWorldFovZoom / captureScene 那两个调用点读到的是上一帧的 ——
        // 同一个字段、不同时刻，这正是设计里说的那一帧延迟。
        if (!isEnabledForHeldGun()) {
            return;
        }
        // 二次渲染模式下离屏纹理【已经】是窄 FOV 画出来的，屏幕坐标与主画面一一对应，
        // 再做一次重投影就等于放大两遍。倍率传 1 = 直接逐像素取用。
        //
        // 重投影模式要除掉世界已经放大的那一份：世界放大 W 之后，主画面里的物体本来就大了
        // W 倍，镜内只需再补 Z/W 就够。不除的话总倍率会变成 W×Z（镜内外对不上）。
        runComposite(rerenderMode()
                ? 1.0f
                : Math.max(1.0f, scopeMagnification()) / worldZoomAtProgress(currentAimingProgress()));
    }

    /**
     * 【光影路径 · 屏幕空间】Iris 整条管线跑完之后，直接在<b>最终画面</b>上做镜内放大。
     *
     * <h2>为什么这条路比「在 pack 的着色器里采样 colortex」可靠得多</h2>
     * 那条路要求我们猜中「已着色的场景此刻躺在哪张 colortex 里」，而这个答案
     * <b>逐 pack 不同</b>（Eclipse 是 colortex2，别家可能是 0/3/…），
     * 还要考虑乒乓翻转、缓冲被后续 pass 覆盖等等 —— 每一条都是一次盲猜。
     * 这里读的是 Iris <b>已经完工</b>的那张图：光照、体积雾、色调映射全部就位，
     * 与镜外像素<b>逐字节同源</b>，因此镜内外的色调天然一致，也不存在任何 pack 依赖。
     *
     * <h2>镜内为什么不会出现一把缩小的枪</h2>
     * 这是这条路能成立的关键。镜身在孔径内是 {@code discard} 的（模式 1），
     * 所以最终画面里<b>孔径那块就是 1× 的世界</b>，没有枪。
     * 而重投影采样点是 {@code center + (uv-center)/Z}：{@code uv} 取遍孔径时，
     * 采样点只覆盖以中心为原点、半径缩小到 {@code 1/Z} 的一小块 ——
     * 那块<b>整个落在孔径内部</b>，于是采到的全都是干净的世界像素。
     * （无光影路径必须在 {@code renderItemInHand} 之前抓图，正是因为它没有这个性质。）
     *
     * <h2>代价</h2>
     * 镜内实际只由孔径内 {@code 1/Z} 那一小块像素放大而来，高倍镜下会明显变软 ——
     * 与重投影模式同一个上限（见 IMPLEMENTATION 文档 §3）。
     * 换来的是「一定出图、颜色一定对、不依赖任何 pack 约定」。
     */
    public static void compositeAfterLevelUnderShaders() {
        if (failed || !sceneCaptured || !irisOwnsLens()) {
            return;
        }
        if (RenderConfig.SCOPE_PIP_DEBUG_NO_COMPOSITE != null
                && RenderConfig.SCOPE_PIP_DEBUG_NO_COMPOSITE.get()) {
            return;
        }
        // 每帧一次。这里不看 isInHandPass —— 调用点在手部 pass 之外（整条管线之后）。
        if (!ScopeMaskRenderer.claimCompositeSlot()) {
            return;
        }
        if (!isEnabledForHeldGun()) {
            return;
        }
        // 让诊断 trace 知道「这一帧真的合成了」—— 光影下没有 SCOPE-PASS，
        // 只有这里能给它一个可扣预算的信号。见 ScopePipTrace.ARMED_FRAME_LIMIT 的说明。
        ScopePipTrace.mark("PIP COMPOSITE (screen space, after the Iris pipeline finished)");
        // 【倍率跟着开镜进度走】这条路的素材是最终画面，画面里是有枪的 ——
        // 干净的世界像素只存在于孔径那一块。抬镜过程中瞄具还没移到屏幕中心，
        // 而重投影的采样点恒定绕屏幕中心收缩，此时那块可能还压在枪身上，
        // 直接给满倍率会让镜内闪过一段放大的枪。
        //
        // 用 1 + (Z-1)·progress：progress→0 时倍率→1，采样点 == 当前像素本身，
        // 于是「合成结果 == 底下已经画好的 1× 画面」，肉眼零变化；
        // 瞄具归位（progress→1）时才到满倍率。顺带与整屏变焦那条老路的公式一致
        // （CameraSetupEvent 的 1 + (zoom-1)·progress），过渡手感也对得上。
        if (rerenderMode()) {
            // 二次渲染：离屏纹理里已经是用窄 FOV【真画出来】的原生分辨率画面，
            // 屏幕坐标与主画面一一对应。倍率传 1 = 逐像素取用，再放大就是放大两遍。
            runComposite(1.0f);
            return;
        }
        float progress = currentAimingProgress();
        float zoom = Math.max(1.0f, scopeMagnification());
        // 总倍率按进度插值，再除掉世界这一帧已经放大的那一份，剩下的才归镜内。
        // 两者相乘恒等于 1+(Z-1)·progress，所以镜内外任何时刻都是同一个总倍率。
        runComposite((1.0f + (zoom - 1.0f) * progress) / worldZoomAtProgress(progress));
    }

    /** 合成本体：读离屏拷贝、按倍率重投影、只写进目镜孔径。两个调用点共用。 */
    private static void runComposite(float magnification) {
        TextureTarget scene = ScopePipTarget.current();
        RenderTarget mask = ScopeMaskTarget.current();
        if (scene == null || mask == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null) {
            return;
        }
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            // 不挂深度附件：合成不测试也不写深度（见 compositePipeline 的注释）。
            // Optional.empty() = 不清空，保留主 target 已有的画面。
            //
            // 注意这里【读】的是 scene 那张拷贝、【写】的是主 target ——
            // 两者是不同的纹理。若省掉拷贝直接采样主 target，就成了同一个 pass 里
            // 又读又写同一张纹理，那是未定义行为。拷贝那一步存在的全部意义就在这里。
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "tacz_scope_pip_composite",
                    main.getColorTextureView(),
                    Optional.empty())) {
                pass.setPipeline(compositePipeline());
                // 【硬件剪裁 —— 越界的最后一道闸】
                //
                // 着色器里的「掩码为假就 discard」是【软】约束：掩码纹理一旦有任何问题
                //（没绑上、内容不对、采样错位），discard 不触发，这张放大的世界就会被
                // 整屏糊上去 —— 用户实测的「放大画面溢出到镜外」正是这个形态。
                //
                // 而目镜在屏幕上的包围盒是掩码阶段本来就算得出来的。用它开 scissor，
                // 合成在物理上就不可能画到镜片之外：掩码继续负责镜片内部的精确形状，
                // scissor 负责「绝不越界」。两道约束一软一硬，一道失效还有另一道。
                applyLensScissor(pass, main);
                // Globals（ScreenSize）由它提供，收缩带的纵横比修正要用。
                RenderSystem.bindDefaultUniforms(pass);
                // 倍率与锐化强度经 ColorModulator 的 r/g 送进着色器。
                // bindDefaultUniforms 不管 DynamicTransforms 这个块，
                // 得自己写 —— 与 ScopeMaskRenderer 同一套路。
                pass.setUniform("DynamicTransforms",
                        RenderSystem.getDynamicUniforms().writeTransform(
                                new Matrix4f(),
                                new Vector4f(magnification, sharpness(), paintLensFlag(), 1.0f)));
                // 场景拷贝：LINEAR。着色器里的 Catmull-Rom 重建正是用一组
                // 硬件双线性抽头拼出来的，所以这里必须是 LINEAR 而非 NEAREST。
                pass.bindTexture("InSampler", scene.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                // 掩码：NEAREST。二值数据，线性过滤会在边缘产生 0.5 附近的中间值，
                // 让 `> 0.5` 判定抖动成一圈毛边 —— 与 ScopeMaskTextureHandle 同一理由。
                pass.bindTexture(MASK_SAMPLER, mask.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                // 无顶点缓冲的全屏三角形：core/screenquad.vsh 用 gl_VertexID 造顶点。
                // 参数顺序照 RenderTarget#blitAndBlendToTexture 抄：draw(3, 1, 0, 0)。
                pass.draw(3, 1, 0, 0);
            }
        } catch (Exception e) {
            failed = true;
            GunMod.LOGGER.error("[TACZ Scope] Scope PIP composite failed; PIP disabled, "
                    + "falling back to whole-screen FOV zoom.", e);
        }
    }

    /**
     * 把合成限制在目镜的屏幕包围盒内。
     *
     * <p>包围盒来自 {@link ScopeMaskRenderer#maskBoundsNdc()}，与掩码同一坐标系、同一帧算出。
     * 拿不到包围盒（读投影 UBO 失败等）就不开剪裁，退回纯掩码约束 —— 即旧行为，不会更糟。
     *
     * <p>四周各留 2 像素余量：掩码的边缘羽化与 NDC→像素的取整都可能差一两个像素，
     * 剪裁若卡得比掩码还紧，镜片边缘会被削掉一圈。
     */
    private static void applyLensScissor(RenderPass pass, RenderTarget target) {
        if (!ScopeMaskRenderer.hasMaskBounds()) {
            return;
        }
        float[] b = ScopeMaskRenderer.maskBoundsNdc();
        int w = target.width;
        int h = target.height;
        // NDC[-1,1] → 像素。scissor 的原点在左下，与 NDC 的 y 方向一致，不需要翻转。
        int x0 = (int) Math.floor((b[0] * 0.5f + 0.5f) * w) - 2;
        int y0 = (int) Math.floor((b[1] * 0.5f + 0.5f) * h) - 2;
        int x1 = (int) Math.ceil((b[2] * 0.5f + 0.5f) * w) + 2;
        int y1 = (int) Math.ceil((b[3] * 0.5f + 0.5f) * h) + 2;
        x0 = Mth.clamp(x0, 0, w);
        y0 = Mth.clamp(y0, 0, h);
        x1 = Mth.clamp(x1, 0, w);
        y1 = Mth.clamp(y1, 0, h);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        pass.enableScissor(x0, y0, x1 - x0, y1 - y0);
    }

    // ------------------------------------------------------------------
    // 配置读取（配置可能尚未加载，一律带 null 兜底）
    // ------------------------------------------------------------------

    private static float minAimingProgress() {
        return RenderConfig.SCOPE_PIP_MIN_AIMING_PROGRESS == null
                ? 0.05f : RenderConfig.SCOPE_PIP_MIN_AIMING_PROGRESS.get().floatValue();
    }

    private static float sharpness() {
        return RenderConfig.SCOPE_PIP_SHARPNESS == null
                ? 0.5f : RenderConfig.SCOPE_PIP_SHARPNESS.get().floatValue();
    }

    /**
     * 合成着色器的诊断标志：0 = 把覆盖区涂成品红，1 = 正常出图。
     * 走 {@code ColorModulator.b}，那个通道本来就是常量 1.0 的空闲载体。
     */
    private static float paintLensFlag() {
        boolean paint = RenderConfig.SCOPE_PIP_DEBUG_PAINT_LENS != null
                && RenderConfig.SCOPE_PIP_DEBUG_PAINT_LENS.get();
        return paint ? 0.0f : 1.0f;
    }

    /**
     * 光影开着时，镜片归 Iris 那条 shader 注入路（模式 3）管，本类的
     * 「拷贝主画面 + 全屏重投影」通道必须整条让开。
     *
     * <h3>为什么必须互斥</h3>
     * 两条路都想往孔径里落笔。同时跑的话，重投影那趟会用 vanilla 管线把一张
     * <b>未经光影着色</b>的拷贝糊进镜片，正好覆盖掉 shader 注入刚写对的颜色 ——
     * 表现为「镜内偏灰/发暗、或与镜外色调对不上」。而且它本来就跑不通：
     * 光影管线下这趟绘制的写入会被下游丢弃（历史上试过，见 docs §2.4）。
     *
     * <p>顺带省掉一次每帧的全屏 {@code copyTextureToTexture}。</p>
     */
    private static boolean irisOwnsLens() {
        return IrisCompat.isUsingRenderPack();
    }

    private static boolean allowShaderPacks() {
        return RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS != null
                && RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.get();
    }
}
