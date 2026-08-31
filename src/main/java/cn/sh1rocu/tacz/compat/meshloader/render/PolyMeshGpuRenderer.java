package cn.sh1rocu.tacz.compat.meshloader.render;

import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMesh;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import com.tacz.guns.compat.iris.IrisCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * poly_mesh 的 GPU 静态烘焙渲染器 —— <b>仅第一人称手部 pass</b>。
 *
 * <h2>为什么前几次内置失败、这次怎么改</h2>
 *
 * <p>26.2 的 {@code entity.vsh} 是 {@code ProjMat * ModelViewMat * Position}。
 * 立方体路径把 submit 时的 pose 烘焙进顶点，再以 identity PoseStack 交给 collector，
 * 绘制时 ModelViewMat = I。GPU 路径把顶点留在骨骼本地、把同一份 pose 写成
 * DynamicTransforms.ModelViewMat，数学上等价。</p>
 *
 * <p>关 PR（#33/#69/#70/#71）的两条硬伤不在这套代数上：</p>
 * <ol>
 *   <li><b>全局 WORLD_DRAWS 表</b>：GUI / 掉落物 / 展示框 / 第三人称的 submit
 *       也被登记，然后在<b>世界</b>那次 {@code renderAllFeatures} 用世界投影画出去。
 *       这就是「帖图不对」——枪出现在错误的 pass / 投影里。</li>
 *   <li><b>弹匣</b>：没接 26.2 的 {@code IMirrorGeometry}，纯 mesh 弹匣在主路径里
 *       被漏画。</li>
 * </ol>
 *
 * <p>这次 GPU 表<b>只收第一人称手部 pass 当时登记的骨骼</b>，并且只在
 * {@code inHandPass=true} 的 {@code renderAllFeatures} 里、{@code executeSolid}
 * <b>之后</b>画（立方体已经进深度，投影是手部 FOV）。世界/GUI 全部走 collector。</p>
 *
 * <h2>管线配方（对照 26.2 jar 逐符号核对）</h2>
 * <p>底子用 {@code MATRICES_FOG_SNIPPET}（Globals + DynamicTransforms + Projection
 * + Fog —— 与 {@code ScopeMaskRenderer.MASK_PIPELINE} 同一理由：core shader 的
 * apply_fog 引用 Fog uniform 块，手拼 layout 少一个就编译不过）。shader 用 vanilla
 * {@code core/entity}：defines 取 {@code ALPHA_CUTOUT 0.1 + NO_OVERLAY +
 * NO_CARDINAL_LIGHTING}，即「顶点色直通 + lightmap 采样」——法线方向光在枪模上
 * 本来就被 NO_CARDINAL 掉（与 collector 的 entityCutout 视觉差异只有 overlay，
 * 枪不受伤没有 overlay）。lightmap 拿不到时退化 EMISSIVE 管线（不采 Sampler2）。</p>
 */
@Environment(EnvType.CLIENT)
public final class PolyMeshGpuRenderer {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("TacZMeshLoader");

    public static final int FULL_BRIGHT = 0xF000F0;
    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
    private static final int LIGHT_GRID = 4;

    private static final RenderPipeline LIT_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/mesh_entity"))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    private static final RenderPipeline EMISSIVE_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/mesh_entity_emissive"))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    /** 一根骨骼烘出的常驻 VBO。顶点是骨骼本地坐标，light 已按量化档烘进 UV2。 */
    public static final class BakedBone {
        public final GpuBuffer vertexBuffer;
        public final int indexCount;

        BakedBone(GpuBuffer vertexBuffer, int indexCount) {
            this.vertexBuffer = vertexBuffer;
            this.indexCount = indexCount;
        }

        public void close() {
            vertexBuffer.close();
        }
    }

    public record DrawEntry(Matrix4f model, Identifier texture, BakedBone bone) {
    }

    /** 仅第一人称手部。世界/GUI 禁止写入。 */
    private static final List<DrawEntry> HAND_DRAWS = new ArrayList<>();

    /**
     * 世界语境（第三人称/掉落物/展示框/展示台）的登记表。
     *
     * <p>与关 PR 的全局 WORLD_DRAWS 表不同名同义但<b>约束完全不同</b>——泄漏靠
     * 提交侧闸门（{@link #shouldSubmitGpuWorld}）从源头掐死：GUI 语境、GUI 预览
     * 窗口（{@code RenderDistance.isGuiRender()}）、镜内那一遍、阴影 pass 的提交
     * 一概进不来。消费侧只认「主世界那一次 renderAllFeatures」（非手部、非镜内、
     * 非阴影），并且 {@link #beginFrame} 每帧清空 —— 任何一帧内没被消费的残留
     * 都活不过下一帧。</p>
     */
    private static final List<DrawEntry> WORLD_DRAWS = new ArrayList<>();

    /**
     * 延迟释放池：世界烘焙缓存（LRU）被逐出/失效的 VBO 先进这里，
     * 下一帧 {@link #beginFrame} 才真正 close。
     *
     * <p>为什么不能当场 close：同一帧内两个掉落物共享同一个模型实例，
     * 甲的 submit 已把某档光照的 VBO 登记进 {@link #WORLD_DRAWS}，
     * 乙的 submit 触发 LRU 逐出同一档 —— 当场 close 的话，
     * 帧末绘制会引用已销毁的 buffer。绘制永远发生在本帧提交之后、
     * 下一帧 beginFrame 之前，所以「下一帧再关」是最小充分延迟。</p>
     */
    private static final List<BakedBone> DEFERRED_RELEASE = new ArrayList<>();

    private static boolean loggedFirstDraw = false;
    private static boolean loggedFirstWorldDraw = false;
    private static boolean loggedFirstIrisDraw = false;
    private static boolean gpuDisabledThisSession = false;
    private static boolean lightmapUnavailable;
    /**
     * 本帧已画过。Iris 的 HandRenderer 一帧跑两次 renderAllFeatures
     * （solid 与 translucent，两次都会重新 submit），不挡的话同一批骨骼
     * 会被画两遍 —— 不透明几何画两遍视觉无差但白付一倍顶点成本。
     */
    private static boolean drawnThisFrame = false;
    /** 世界表同款「一帧一画」闸门（Iris 一帧两次 renderAllFeatures 的重复消费防线）。 */
    private static boolean worldDrawnThisFrame = false;
    /** 本帧已执行的世界烘焙次数（额度见 {@link #tryReserveBake}）。 */
    private static int bakesThisFrame = 0;
    /**
     * 烘焙世代号：光影包开关每翻转一次 +1（{@link #beginFrame} 逐帧检测）。
     *
     * <p>烘焙产物依赖当时的光影状态——Iris 激活时会扩展实体顶点格式
     * （附加属性、stride 变化），经 {@code BufferBuilder} 写出的常驻 VBO
     * 布局随之不同；切换光影后用新管线按新 stride 解读旧 buffer，
     * 属性错位表现为<b>模型拉伸</b>（用户实测：站着不动开关光影必现，
     * 光照跨档触发重烘后“自愈”）。持有烘焙缓存的模型在 submit 时比对
     * 世代号，不匹配立即重烘——不受光照档节流约束。</p>
     */
    private static int bakeGeneration = 0;
    private static boolean lastShaderPackState = false;

    private PolyMeshGpuRenderer() {
    }

    /**
     * 当前这次 submit 是否该走 GPU。必须同时满足：
     * <ul>
     *   <li>配置打开且本会话未因异常关闭；</li>
     *   <li>光影包未启用（或实验开关强开）；</li>
     *   <li><b>现在就在手部 pass 里</b>——用 {@code ScopeMaskRenderer.isInHandPass()}，
     *       而不是 {@code transformType.firstPerson()}。后者对「用第一人称上下文
     *       画 GUI」这类路径也会为 true，正是关 PR WORLD_DRAWS 泄漏的入口。</li>
     * </ul>
     */
    public static boolean shouldSubmitGpu() {
        if (!isGpuPathUsable()) {
            return false;
        }
        return ScopeMaskRenderer.isInHandPass();
    }

    /**
     * 当前这次<b>世界语境</b> submit 是否该走 GPU（登记进 {@link #WORLD_DRAWS}）。
     *
     * <p>提交侧闸门 —— 关 PR 世界表泄漏的每个入口都在这里逐个封死：</p>
     * <ul>
     *   <li>配置 {@code MeshGpuWorld} 打开、GPU 路径本会话可用；</li>
     *   <li><b>不在</b>手部 pass（手部有自己的表；这里进来的只能是世界提取阶段）；</li>
     *   <li><b>不在</b> Screen 提取窗口 —— {@link ScreenRenderTracker} 精确框住
     *       {@code Screen} 的 extract 阶段，背包人偶/枪匠桌预览这类 GUI 内嵌 3D
     *       的 submit 全落在这个窗口里；它们的 pose 是 GUI 投影，落进世界表就是
     *       关 PR 的「枪画进世界」事故。刻意<b>不用</b>
     *       {@code RenderDistance.isGuiRender()}：那是个 100ms 时间戳窗口，
     *       开着菜单时世界提取阶段也会命中，等于「一开背包全场景 mesh 枪
     *       跌回 collector」—— 上游 TML 注释里记载过的同款实机事故；</li>
     *   <li><b>不在</b>镜内那一遍（PIP 二次渲染）—— 防御性闸门：提交都发生在
     *       extract 阶段（镜内那遍开始之前），正常流程根本走不进这个分支；
     *       万一有 mod 在镜内窗口里补提交，pose 语义未知，宁可拒收。
     *       镜内那遍怎么消费世界表见 {@link #renderWorldAfterSolid}
     *       （画但不清表，与 collector 的两遍一致裁定同构）；</li>
     *   <li><b>不在</b>阴影 pass —— Iris 阴影遍的投影/MV 是太阳视角，
     *       登记进主视角的表必然画错；{@code MeshPolyInShadow=false} 时提交
     *       在 {@code PolyRenderPolicy} 就被拦了，这里是 true 时的第二道保险。</li>
     * </ul>
     */
    public static boolean shouldSubmitGpuWorld() {
        if (!isGpuPathUsable() || !MeshyConfig.GPU_WORLD.get()) {
            return false;
        }
        if (ScopeMaskRenderer.isInHandPass()) {
            return false;
        }
        if (ScreenRenderTracker.isExtractingScreen()) {
            return false;
        }
        if (com.tacz.guns.client.render.scope.ScopePipRenderer.isInsideScopeLevelRender()) {
            return false;
        }
        return !IrisCompat.isRenderShadow();
    }

    public static boolean isGpuPathUsable() {
        if (gpuDisabledThisSession || !MeshyConfig.GPU_BAKING.get()) {
            return false;
        }
        // 光影下 GPU 路径默认【走 vanilla RenderType 管道】而非自定义 pass：
        // RenderType.prepare() + PreparedRenderType.drawFromBuffer() 用的是
        // entityCutout 的 RenderPipeline —— 该管线已由
        // IrisCompat.assignCommonEntityPipelinesToHandIfNeeded() 归入 Iris HAND
        // program（抛壳/火光同一条兼容链路），Iris 按管线拦截，枪体因此拿到
        // 光影光照。顶点常驻 VBO 不变，每帧仍只写 O(骨骼) 个 DynamicTransforms。
        // MeshGpuUnderShaders=true 时改走自定义 pass（绕开光影管线，无光影光照，
        // 但绘制语义与无光影路径逐位一致 —— 留作排查光影兼容问题的对照组）。
        return true;
    }

    /** 本帧是否该走「vanilla RenderType 管道」变体（光影激活且未强制自定义 pass）。 */
    private static boolean useRenderTypeRoute() {
        return IrisCompat.isUsingRenderPack() && !MeshyConfig.GPU_UNDER_SHADERS.get();
    }

    /**
     * 光照 4 级量化：光照烘在顶点里，逐帧光照变化本会逼着逐帧重烘，
     * 量化 + {@code ensureBaked} 的 1 秒节流把重烘频率压到「跨光照档才发生」。
     */
    public static int quantizeLight(int packedLight) {
        int block = Math.min(15, Math.max(0, (packedLight >> 4) & 0xF));
        int sky = Math.min(15, Math.max(0, (packedLight >>> 20) & 0xF));
        int qb = (block / LIGHT_GRID) * LIGHT_GRID;
        int qs = (sky / LIGHT_GRID) * LIGHT_GRID;
        return LightCoordsUtil.pack(qb, qs);
    }

    public static BakedBone bakeBone(List<PolyMesh> meshes, int lightKey) {
        int vertexCount = 0;
        for (PolyMesh mesh : meshes) {
            vertexCount += mesh.getVertexCount();
        }
        if (vertexCount == 0) {
            return null;
        }
        // ENTITY 格式 stride 36 字节，预留些余量避免 grow。
        long capacity = vertexCount * 48L + 1024L;
        ByteBufferBuilder scratch = new ByteBufferBuilder((int) Math.min(capacity, Integer.MAX_VALUE));
        BufferBuilder builder = new BufferBuilder(scratch, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
        for (PolyMesh mesh : meshes) {
            mesh.writeRaw(builder, lightKey);
        }
        MeshData meshData = builder.build();
        if (meshData == null) {
            scratch.close();
            return null;
        }
        try (meshData) {
            GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "tacz_mesh_bone", GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());
            return new BakedBone(vertexBuffer, meshData.drawState().indexCount());
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to bake bone geometry", e);
            return null;
        } finally {
            scratch.close();
        }
    }

    public static void submitBone(Matrix4f bonePose, Identifier texture, BakedBone bone) {
        if (bone == null) {
            return;
        }
        HAND_DRAWS.add(new DrawEntry(new Matrix4f(bonePose), texture, bone));
    }

    /** 世界语境版本：调用方必须已通过 {@link #shouldSubmitGpuWorld} 闸门。 */
    public static void submitBoneWorld(Matrix4f bonePose, Identifier texture, BakedBone bone) {
        if (bone == null) {
            return;
        }
        WORLD_DRAWS.add(new DrawEntry(new Matrix4f(bonePose), texture, bone));
    }

    /**
     * 把一个被 LRU 逐出/失效的烘焙骨骼交给延迟释放池（下一帧才 close）。
     * 见 {@link #DEFERRED_RELEASE} 的注释 —— 本帧可能已有 DrawEntry 引用它。
     */
    public static void releaseDeferred(BakedBone bone) {
        if (bone != null) {
            DEFERRED_RELEASE.add(bone);
        }
    }

    /** 挂在 {@code GameRenderer#extract} HEAD（与 ScopeMaskRenderer.beginFrame 同点）。 */
    public static void beginFrame() {
        boolean shaders = IrisCompat.isUsingRenderPack();
        if (shaders != lastShaderPackState) {
            lastShaderPackState = shaders;
            bakeGeneration++;
            LOGGER.info("[TacZMeshLoader] Shader pack state changed (active={}); mesh bake generation -> {}",
                    shaders, bakeGeneration);
        }
        HAND_DRAWS.clear();
        WORLD_DRAWS.clear();
        drawnThisFrame = false;
        worldDrawnThisFrame = false;
        bakesThisFrame = 0;
        // LevelRendererWorldPassMixin 的 RETURN 注入在异常路径不触发，
        // 括号标志可能泄漏 —— 每帧兜底归零。
        insideLevelRender = false;
        if (!DEFERRED_RELEASE.isEmpty()) {
            // 上一帧被逐出的 VBO：绘制已经结束（上一帧帧末），现在关是安全的。
            for (BakedBone bone : DEFERRED_RELEASE) {
                bone.close();
            }
            DEFERRED_RELEASE.clear();
        }
    }

    /**
     * 申请一次「本帧烘焙额度」。
     *
     * <p>病理场景：世界里同帧出现的量化光照档数超过 LRU 容量（比如一排掉落枪
     * 横跨明暗边界），没有额度闸门的话，每帧都会「逐出-重烘」打摆 ——
     * 烘焙风暴比 collector 还慢。额度用完后本帧余下的枪回退 collector，
     * 下一帧额度重置，缓存逐帧收敛到稳态。</p>
     */
    public static boolean tryReserveBake() {
        int cap = Math.max(4, MeshyConfig.GPU_LIGHT_CACHE_SIZE.get());
        if (bakesThisFrame >= cap) {
            return false;
        }
        bakesThisFrame++;
        return true;
    }

    /** 当前烘焙世代号。烘焙缓存持有者在 submit 时比对，不匹配须立即重烘。 */
    public static int getBakeGeneration() {
        return bakeGeneration;
    }

    /**
     * 在手部 {@code renderAllFeatures} 的 {@code executeSolid} <b>之后</b>绘制。
     * 世界那次直接清空残留（理论上不应有）。
     */
    public static void renderAfterSolid() {
        // 【PIP 二次渲染 × 光影 —— 必须最先挡】Iris 把手部渲染搬进
        // LevelRenderer.render 内部，于是镜内那一遍也有自己的手部 pass，
        // 本方法会先于主画面那一遍被调到。不挡的话：
        //   1. 镜内那一遍把 HAND_DRAWS 消费掉并置 drawnThisFrame=true；
        //   2. 主画面那一遍重新 submit 的条目被 drawnThisFrame 闸门拦下；
        //   ⇒ 主画面上 mesh 枪件整个消失（只在开镜 + ScopePipRerender + 光影时触发）。
        // 这里清空本遍的提交、不画也不占用 drawnThisFrame —— 主画面那一遍
        // 会重新 submit 一份并正常绘制。镜内内容不受损：合成只取镜片孔径内
        // 的像素，孔径里本来就该是干净的世界画面，不该有枪件。
        //
        if (com.tacz.guns.client.render.scope.ScopePipRenderer.isInsideScopeLevelRender()) {
            HAND_DRAWS.clear();
            return;
        }
        if (!ScopeMaskRenderer.isInHandPass()) {
            // 非手部的 renderAllFeatures：GUI 图集（GuiItemAtlas /
            // PictureInPictureRenderer）或 renderLevel 偏移 560 的收尾调用。
            // 世界表【不在这里】消费 —— MV-PROBE v2 字节码取证：26.2 的世界
            // 实体 pass 根本不经过 renderAllFeatures（LevelRenderer.render 的
            // 帧图 lambda 直调 PreparedFrame.executeSolid，lambda$addMainPass$0
            // 偏移 177），而 renderLevel 560 处 MV 栈已 popMatrix 回单位阵
            // （LevelRenderer.render 内 30-45 push+mul(viewRotation)、591 pop、
            // 560 在 render 返回之后）—— 首版世界烘焙就是在这里被消费的，
            // 单位阵 MV = 丢相机旋转层 = 「枪固定在视角空间」（实测症状，
            // 与第一人称 0ea0fb6 丢 MV_draw 是同一个病）。
            // 世界的正确消费点在 renderWorldAfterSolid
            // （PreparedFrameSolidMixin，executeSolid RETURN，MV 栈顶=viewRotation）。
            HAND_DRAWS.clear();
            return;
        }
        if (HAND_DRAWS.isEmpty()) {
            return;
        }
        if (drawnThisFrame) {
            // Iris 第二次手部 pass（renderTranslucent）的重复 submit：跳过。
            HAND_DRAWS.clear();
            return;
        }
        try {
            if (useRenderTypeRoute()) {
                drawListViaRenderType(HAND_DRAWS);
            } else {
                drawList(HAND_DRAWS);
            }
            drawnThisFrame = true;
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] GPU mesh pass failed; falling back to collector path for this session.", e);
            gpuDisabledThisSession = true;
            MeshyConfig.GPU_BAKING.set(false);
        } finally {
            HAND_DRAWS.clear();
        }
    }

    /**
     * 「此刻在不在 LevelRenderer.render 里」—— 世界消费点的调用者判据，
     * 由 {@code LevelRendererWorldPassMixin} 在 render 的 HEAD/RETURN 置位。
     * RETURN 在异常路径不触发（镜内那遍的失败被上层捕获），
     * {@link #beginFrame} 每帧兜底归零。
     */
    private static boolean insideLevelRender = false;

    public static void setInsideLevelRender(boolean value) {
        insideLevelRender = value;
    }

    /**
     * 世界 poly_mesh GPU 表的消费点。挂在 {@code PreparedFrame.executeSolid}
     * 的 RETURN（{@code PreparedFrameSolidMixin}）。
     *
     * <h2>为什么是这里（MV-PROBE v2 字节码取证，minecraft-merged-26.2）</h2>
     * <ul>
     *   <li>26.2 世界实体 pass = {@code LevelRenderer.render} 帧图的
     *       {@code lambda$addMainPass$0} <b>直调</b> executeSolid（偏移 177），
     *       不经过 renderAllFeatures —— 首版挂错了地方；</li>
     *   <li>{@code LevelRenderer.render} 开头（30-45）
     *       {@code getModelViewStack().pushMatrix(); mul(viewRotation)}，
     *       帧图执行（572）在 popMatrix（591）之前 ——
     *       <b>本方法执行时 MV 栈顶恰好是 viewRotation</b>，
     *       两个绘制核心（drawList / drawViaRenderTypeCore）从栈顶取 MV_draw
     *       的既有逻辑在这里天然正确，与手部 pass 完全同构。</li>
     * </ul>
     *
     * <h2>executeSolid 的四类调用者怎么分流</h2>
     * <ul>
     *   <li><b>手部 renderAllFeatures</b>（vanilla renderItemInHand 185 /
     *       Iris HandRenderer）：{@code isInHandPass} 拒收 —— 手部有自己的表；</li>
     *   <li><b>GUI renderAllFeatures</b>（GuiItemAtlas /
     *       PictureInPictureRenderer / renderLevel 560 的收尾调用）：
     *       {@code insideLevelRender=false} 拒收；</li>
     *   <li><b>镜内那遍</b>（我们自己驱动的 levelRenderer.render，也在括号内）：
     *       照画但<b>不清表、不占帧标志</b> —— 维护者 08-30 裁定两遍内容必须
     *       一致（collector 也是两遍照画）；WORLD_DRAWS 在 extract 阶段登记、
     *       每帧只一份，镜内清了主画面就没了。无光影时 mainRenderTarget()
     *       已重定向到 pip target、MV 栈顶是镜内那遍自己 push 的 viewRotation
     *       （同一个 render 方法、同一段字节码）—— 语义自动正确；</li>
     *   <li><b>主世界帧图</b>：消费 + 置帧标志 + 清表。</li>
     * </ul>
     *
     * <p>阴影 pass（Iris 有自己的渲染循环，理论到不了这里）：只跳过、
     * <b>不清表</b> —— 它若真跑到，也发生在主 gbuffers 遍之前，
     * 清表等于把主画面的条目扔了。</p>
     *
     * <p>时机安全性：executeSolid 内部逐 phase 开/关各自的 render pass，
     * RETURN 处不在任何 pass 内，createRenderPass 断言安全；
     * 立方体/地形深度已就绪，GPU poly 同一张 depth view 深度测试即正确遮挡。</p>
     */
    public static void renderWorldAfterSolid() {
        if (!insideLevelRender) {
            return;
        }
        if (ScopeMaskRenderer.isInHandPass()) {
            // Iris 把手部 renderAllFeatures 搬进 LevelRenderer.render 内部，
            // 括号内也可能出现手部的 executeSolid —— 那是手部表的事，这里不碰。
            return;
        }
        if (IrisCompat.isRenderShadow()) {
            return;
        }
        if (WORLD_DRAWS.isEmpty()) {
            return;
        }
        boolean inScopePass = com.tacz.guns.client.render.scope.ScopePipRenderer.isInsideScopeLevelRender();
        if (!inScopePass && worldDrawnThisFrame) {
            // 主世界重复消费（防御性；正常一帧只有一次主世界帧图）。
            WORLD_DRAWS.clear();
            return;
        }
        try {
            if (useRenderTypeRoute()) {
                drawWorldListViaRenderType(WORLD_DRAWS);
            } else {
                drawList(WORLD_DRAWS);
            }
            if (!inScopePass) {
                worldDrawnThisFrame = true;
                WORLD_DRAWS.clear();
            }
            // 镜内那遍：表保留原样，主画面那遍再消费。
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] GPU world mesh pass failed; falling back to collector path for this session.", e);
            gpuDisabledThisSession = true;
            MeshyConfig.GPU_BAKING.set(false);
            WORLD_DRAWS.clear();
        }
    }

    /**
     * 【光影路径】vanilla RenderType 管道变体：常驻 VBO + 官方 prepare()/drawFromBuffer。
     *
     * <h2>为什么这条路在 Iris 下能拿到光影光照</h2>
     * Iris 按 {@code RenderPipeline} 对象拦截绘制（本仓证据链：抛壳/火光用 vanilla
     * ENTITY_CUTOUT 提交，经 {@code assignCommonEntityPipelinesToHandIfNeeded()}
     * 归入 HAND program 后光影下渲染正确）。这里用 {@code RenderTypes.entityCutout}
     * —— 管线正是那条链路已注册的 ENTITY_CUTOUT，Iris 对它的接管方式与 vanilla
     * 立方体/collector poly 完全一致。
     *
     * <h2>每骨骼矩阵怎么进去（字节码依据：RenderType.prepare() 偏移 55-58）</h2>
     * prepare() 内部 {@code getModelViewMatrixCopy() -> writeDynamicTransforms(mv)}
     * —— 从 ModelView 栈顶取矩阵。所以把 {@code MV_hand × pose_bone} 压栈 →
     * prepare() → 弹栈，每骨骼得到一份独立的 DynamicTransforms 切片，
     * 顶点仍留在骨骼本地系的常驻 VBO 里，零 CPU 变换。
     *
     * <p>{@code drawFromBuffer(vb, ib, type, 0, 0, indexCount)} 的参数序按其字节码
     * {@code drawIndexed(arg6, 1, arg5, arg4, 0)} 与已实测正确的
     * {@code drawIndexed(indexCount, 1, 0, 0, 0)} 对齐：arg6=indexCount，其余置 0。</p>
     *
     * <h2>视觉对齐</h2>
     * entityCutout = PER_FACE_LIGHTING + overlay + lightmap，与 collector 路径
     * 同一 RenderType —— 无光影时两条路视觉逐位一致；顶点里 UV1=NO_OVERLAY、
     * UV2=量化光照，语义同 collector 写入。
     */
    private static void drawListViaRenderType(List<DrawEntry> draws) {
        IrisCompat.assignCommonEntityPipelinesToHandIfNeeded();
        long totalIndices = drawViaRenderTypeCore(draws);
        if (!loggedFirstIrisDraw) {
            loggedFirstIrisDraw = true;
            LOGGER.info("[TacZMeshLoader] GPU mesh pass (RenderType route, shader-pack compatible) drew {} bones "
                    + "({} indices) on hand pass.", draws.size(), totalIndices);
        }
    }

    /**
     * 【光影 · 世界 pass】RenderType 管道变体。
     *
     * <p>与手部变体唯一的语义差异：<b>不调</b>
     * {@code assignCommonEntityPipelinesToHandIfNeeded()} —— 那是手部 pass 的
     * 专项修复（把抛壳用的 vanilla 管线归入 Iris HAND program）。世界 pass 里
     * ENTITY_CUTOUT 就是 vanilla 世界实体在用的管线，Iris 对它的默认接管
     * （gbuffers_entities 链路）正是我们想要的；这里主动去动管线归属反而可能
     * 干扰别的实体。绘制机制（prepare() 压栈取 MV × drawFromBuffer）与手部
     * 完全同构，两层变换定理不区分 pass。</p>
     */
    private static void drawWorldListViaRenderType(List<DrawEntry> draws) {
        long totalIndices = drawViaRenderTypeCore(draws);
        if (!loggedFirstWorldDraw) {
            loggedFirstWorldDraw = true;
            LOGGER.info("[TacZMeshLoader] GPU world mesh pass (RenderType route) drew {} bones "
                    + "({} indices) on world pass.", draws.size(), totalIndices);
        }
    }

    private static long drawViaRenderTypeCore(List<DrawEntry> draws) {
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();

        Map<Identifier, List<DrawEntry>> byTexture = new HashMap<>();
        for (DrawEntry entry : draws) {
            byTexture.computeIfAbsent(entry.texture(), k -> new ArrayList<>()).add(entry);
        }

        long totalIndices = 0;
        for (Map.Entry<Identifier, List<DrawEntry>> group : byTexture.entrySet()) {
            RenderType renderType = RenderTypes.entityCutout(group.getKey());
            for (DrawEntry entry : group.getValue()) {
                // MV = MV_draw(栈顶) × pose_bone。压栈让 prepare() 自己取，
                // 弹栈还原 —— 不污染后续 executeTranslucent 的矩阵状态。
                mvStack.pushMatrix();
                mvStack.mul(entry.model());
                PreparedRenderType prepared;
                try {
                    prepared = renderType.prepare();
                } finally {
                    mvStack.popMatrix();
                }
                RenderSystem.AutoStorageIndexBuffer indices =
                        RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                GpuBuffer indexBuffer = indices.getBuffer(entry.bone().indexCount);
                prepared.drawFromBuffer(entry.bone().vertexBuffer, indexBuffer, indices.type(),
                        0, 0, entry.bone().indexCount);
                totalIndices += entry.bone().indexCount;
            }
        }
        return totalIndices;
    }

    private static void drawList(List<DrawEntry> draws) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        if (mainTarget == null) {
            return;
        }
        GpuTextureView colorView = mainTarget.getColorTextureView();
        GpuTextureView depthView = mainTarget.getDepthTextureView();
        if (colorView == null || depthView == null) {
            return;
        }
        GpuTextureView lightmapView = resolveLightmap(mc);
        GpuSampler linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        // 【朝向恒北 bug 的修复 · 字节码依据】RenderType.prepare() 偏移 55-58：
        //     getModelViewMatrixCopy() -> writeDynamicTransforms(mv)
        // vanilla 画 collector 提交是【两层】变换：顶点里烘 submit 时的 pose，
        // 绘制时 ModelViewMat = RenderSystem 当刻的 MV（手部 pass = 相机旋转/俯仰
        // /视模那套）。GPU 路径顶点在骨骼本地系、pose 写进 DynamicTransforms，
        // 若只写 entry.model() 就丢了 MV_draw 这一层 —— 枪位置对（pose 带平移）
        // 但朝向恒北、俯仰为平（相机旋转全在丢的那层里），实测症状完全吻合。
        // 本方法跑在手部 renderAllFeatures 的 executeSolid 之后、同一调用内，
        // MV 与 prepareFrame 时一致，取一次全体通用。
        Matrix4f handModelView = RenderSystem.getModelViewMatrixCopy();

        Map<Identifier, List<DrawEntry>> byTexture = new HashMap<>();
        for (DrawEntry entry : draws) {
            byTexture.computeIfAbsent(entry.texture(), k -> new ArrayList<>()).add(entry);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        // 阶段边界不在任何 render pass 内（FeatureRenderDispatcherMixin 的字节码分析），
        // createRenderPass 的 isInRenderPass 断言安全。颜色 Optional.empty() = 不清屏，
        // 深度 OptionalDouble.empty() = 不清深度 —— executeSolid 画好的立方体深度要留着。
        try (RenderPass pass = encoder.createRenderPass(
                () -> "tacz_mesh_gpu",
                colorView,
                Optional.empty(),
                depthView,
                OptionalDouble.empty())) {
            boolean lit = lightmapView != null;
            pass.setPipeline(lit ? LIT_PIPELINE : EMISSIVE_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            if (lit) {
                pass.bindTexture("Sampler2", lightmapView,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            }

            for (Map.Entry<Identifier, List<DrawEntry>> group : byTexture.entrySet()) {
                GpuTextureView textureView = resolveTextureView(group.getKey());
                if (textureView == null) {
                    textureView = resolveTextureView(MissingTextureAtlasSprite.getLocation());
                }
                if (textureView == null) {
                    continue;
                }
                pass.bindTexture("Sampler0", textureView, linearSampler);

                for (DrawEntry entry : group.getValue()) {
                    // ModelViewMat = MV_draw * pose_submit（乘序同 vanilla：顶点先套
                    // pose 再进相机系）。scratch 每骨骼重算，不污染 entry.model()。
                    Matrix4f mv = new Matrix4f(handModelView).mul(entry.model());
                    pass.setUniform("DynamicTransforms",
                            RenderSystem.getDynamicUniforms().writeTransform(mv, WHITE));
                    pass.setVertexBuffer(0, entry.bone().vertexBuffer.slice());
                    RenderSystem.AutoStorageIndexBuffer indices =
                            RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                    pass.setIndexBuffer(indices.getBuffer(entry.bone().indexCount), indices.type());
                    pass.drawIndexed(entry.bone().indexCount, 1, 0, 0, 0);
                }
            }
            if (!loggedFirstDraw) {
                loggedFirstDraw = true;
                long indices = 0;
                for (DrawEntry entry : draws) {
                    indices += entry.bone().indexCount;
                }
                LOGGER.info("[TacZMeshLoader] GPU mesh pass drew {} bones ({} indices) on hand pass, lit={}",
                        draws.size(), indices, lit);
            }
        }
    }

    private static GpuTextureView resolveTextureView(Identifier texture) {
        try {
            return Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView();
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to resolve texture view for {}", texture, e);
            return null;
        }
    }

    private static GpuTextureView resolveLightmap(Minecraft mc) {
        if (lightmapUnavailable) {
            return null;
        }
        try {
            GpuTextureView view = mc.gameRenderer.levelLightmap();
            if (view == null) {
                lightmapUnavailable = true;
                LOGGER.warn("[TacZMeshLoader] Level lightmap view unavailable; GPU path falls back to EMISSIVE.");
            }
            return view;
        } catch (Throwable t) {
            lightmapUnavailable = true;
            LOGGER.warn("[TacZMeshLoader] Failed to read level lightmap; GPU path falls back to EMISSIVE.", t);
            return null;
        }
    }
}
