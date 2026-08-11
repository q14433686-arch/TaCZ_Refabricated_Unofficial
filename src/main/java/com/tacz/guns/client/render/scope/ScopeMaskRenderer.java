package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.PrimitiveTopology;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Optional;

/**
 * 【Step 2 正式版】把当帧所有目镜几何画进离屏掩码。
 *
 * <h2>这一步要证明什么</h2>
 * 预览块里应出现一个<b>随枪移动的白色形状</b>。
 * 它一次性验证整个方案最核心的假设：
 * <b>裁剪区域 = 目镜几何的屏幕投影</b>。
 *
 * <p>这正是上游 stencil 的真实语义（{@code SCOPE_UPSTREAM_TRUTH} §1）：
 * <pre>
 * renderOcularStencil: colorMask(false×4) + stencilOp(KEEP,KEEP,REPLACE)
 *     → 模板非 0 区 = 目镜的【屏幕投影形状】
 * scope_body: stencilFunc(EQUAL, 0)   // 只在目镜没盖到处画镜身
 * </pre>
 * 形状对 = 后续「镜身采样掩码并 discard」必然成立；
 * 形状不对（不跟枪动 / 位置错） = 还有更深的误解，此时止损远比继续写划算。
 *
 * <h2>为什么不用 collector / RenderType</h2>
 * r51 就是那么干的 —— 给目镜配一个 {@code outputTarget} 不同的 RenderType，
 * 照常走 collector。结果引擎按 RenderType 分批执行时，把
 * 「主 target → 掩码 target → 主 target」的切换<b>零散穿插</b>进 solid 阶段内部，
 * 触发 {@code VK_ERROR_DEVICE_LOST}。
 *
 * <p>所以这里<b>完全绕开 collector</b>，自建顶点缓冲，在阶段边界一次性画完。
 * 该时机的安全性已由上一轮的空 pass 探针实测证实（预览块变绿）。
 *
 * <h2>绘制配方（逐项对照 {@code PreparedRenderType#drawFromBuffer} 反汇编）</h2>
 * <pre>
 * createRenderPass(名字, 颜色附件, 清空色)
 * setPipeline(pipeline)
 * RenderSystem.bindDefaultUniforms(pass)               // ← 少这句会缺 Projection/Fog
 * setUniform("DynamicTransforms", 写入 ModelView)      // ← 少这句 shader 拿不到 ModelViewMat
 * setVertexBuffer(0, vertexBuffer.slice())
 * setIndexBuffer(共享四边形索引, 类型)
 * drawIndexed(0, 0, indexCount, 1)
 * </pre>
 */
@Environment(EnvType.CLIENT)
public final class ScopeMaskRenderer {

    /**
     * 掩码管线。
     *
     * <h3>为什么用 {@code POSITION} 而不是 {@code ENTITY} 格式</h3>
     * 掩码只关心「这个像素有没有被目镜盖到」，<b>不需要</b>贴图、光照、法线。
     * 用最简格式有三个好处：
     * <ul>
     *   <li>顶点数据最小（每顶点 12 字节）；</li>
     *   <li>{@code core/position} 这套 shader <b>不声明任何 sampler</b> ——
     *       彻底避开 r52 那次 {@code Missing sampler Sampler0} 的坑；</li>
     *   <li>不需要自己写 shader，用 vanilla 现成的，也就没有
     *       r46 那种「shader 声明的 uniform 与管线不匹配」的风险。</li>
     * </ul>
     *
     * <h3>关于颜色</h3>
     * {@code position.fsh} 输出 {@code apply_fog(ColorModulator, ...)}。
     * {@code ColorModulator} 由 DynamicTransforms 提供，我们写入纯白，
     * 于是目镜覆盖处 = 白，其余 = 清空色（黑）。正好是一张二值掩码。
     *
     * <p>雾在近距离（手持物就在眼前）几乎不衰减，不影响判读；
     * 何况本步骤只看<b>形状</b>，不看颜色精度。
     *
     * <h3>深度与剔除</h3>
     * 深度状态传 {@code Optional.empty()}：掩码 target 是 {@code useDepth=false}
     * 建的，没有深度附件，管线必须声明自己不需要深度。
     *
     * <p>{@code withCull(false)}：目镜是<b>单层薄片</b>（实测 30/33 个瞄具的
     * 目镜 z 厚度 &lt; 0.15），背面剔除可能把它整个剔掉，取决于建模朝向。
     * 掩码只要「投影形状」，正反面都算数。
     */
    private static final RenderPipeline MASK_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            // 用 MATRICES_FOG_SNIPPET 而不是自己拼 GLOBALS + MATRICES_PROJECTION。
            //
            // 上一版实测报错：
            //     Couldn't compile pipeline tacz:pipeline/scope_mask:
            //         Unable to find shader defined uniform (Fog)
            // 原因是 core/position.fsh 里 apply_fog(...) 引用了 Fog uniform 块，
            // 而 BindGroupLayouts.MATRICES_PROJECTION 只声明了
            // DynamicTransforms + Projection 两个 uniform（字节码确认），
            // Fog / Globals 是【各自独立】的 layout。少一个就编译不过。
            //
            // vanilla 早就把这个组合封装好了（RenderPipelines <clinit> 偏移 30-57）：
            //     MATRICES_FOG_SNIPPET = builder(GLOBALS_SNIPPET)
            //                              .withBindGroupLayout(MATRICES_PROJECTION)
            //                              .withBindGroupLayout(FOG)
            // 即 Globals + DynamicTransforms + Projection + Fog，
            // 正是 core/position 这套 shader 需要的全套。直接复用，不再手拼。
            .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_mask"))
            .withVertexShader("core/position")
            .withFragmentShader("core/position")
            // 不混合、不写深度：掩码就是「盖到=白，没盖到=清空色」的二值图，
            // 直接覆写即可。Optional.empty() 表示【不启用 blend】
            // （对照 ColorTargetState 的两个构造：单参版是「带 blend」，
            //  三参版第一个是 Optional<BlendFunction>，empty = 关闭混合）。
            .withColorTargetState(new ColorTargetState(
                    Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
            // 掩码 target 是 useDepth=false 建的 —— 它【没有深度附件】。
            //
            // 这里必须传 Optional.empty() 而不是一个 DepthStencilState 实例。
            // 上一版传的是 new DepthStencilState(ALWAYS_PASS, false)，本意是「不测试不写入」，
            // 但 RenderPipeline#wantsDepthTexture() 的判据是
            //     return this.depthStencilState != null;
            // 也就是说【只要设了这个字段，管线就声明自己需要深度附件】，
            // 与它内部是不是 ALWAYS_PASS 无关。而我们的 render pass 压根没给深度附件，
            // 声明与实际不符，绘制被丢弃 —— 表现为「日志说画了 36 indices，画面却全黑」。
            //
            // vanilla 对「无深度附件」一律用 Optional.empty()（<clinit> 里 4 处，
            // 全是 TEXT_SEE_THROUGH / GUI_TEXT 这类不需要深度的管线）。照抄该惯例。
            .withDepthStencilState(Optional.empty())
            // 目镜是【单层薄片】（实测 30/33 个瞄具的目镜 z 厚度 < 0.15），
            // 背面剔除可能因建模朝向把它整个剔掉。掩码只要投影形状，正反面都算。
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    /** 顶点暂存区。复用同一个，避免每帧分配。 */
    private static final ByteBufferBuilder SCRATCH = new ByteBufferBuilder(4096);

    private static boolean failed = false;
    private static boolean loggedSuccess = false;
    /** 「开着调试却没有任何目镜几何」只警告一次，避免刷屏。 */
    private static boolean loggedEmpty = false;

    /**
     * 当前是否正在渲染手持物（第一人称枪械）。
     *
     * <p>{@code renderAllFeatures} 每帧被调用多次（世界一次、手持一次），
     * 而瞄具只出现在手持那次。掩码必须<b>只在手持那次</b>绘制：
     * 若在世界那次也跑，会先把 target 清空一遍，把手持那次的结果冲掉。
     * 由 {@code GameRendererMixin} 的 {@code renderItemInHand} HEAD/RETURN 维护。</p>
     */
    private static boolean inHandPass = false;

    private ScopeMaskRenderer() {
    }

    public static void setInHandPass(boolean value) {
        inHandPass = value;
    }

    public static boolean isInHandPass() {
        return inHandPass || IrisCompat.isHandRendererActive();
    }

    /**
     * 在阶段边界把当帧登记的目镜几何画进掩码 target。
     *
     * <p>无论成败，末尾都会清空当帧清单 —— 见 {@code finally}。
     */
    public static void renderAtPhaseBoundary() {
        boolean activeHandPass = isInHandPass();
        // 【诊断】上一版实测「预览全黑 + 日志一行都没有」，原因是几何一个都没登记，
        // isEmpty() 直接 return，于是连个说法都没有。静默失败最难查，
        // 所以这里补一条：开着调试却收不到任何目镜几何时，明确说出来（只说一次）。
        if (activeHandPass && RenderConfig.SCOPE_MASK_ENABLE.get()
                && ScopeMaskGeometry.isEmpty() && !loggedEmpty) {
            loggedEmpty = true;
            GunMod.LOGGER.warn("[TACZ Scope] Mask enabled but no ocular geometry was registered this frame. "
                    + "Either no scope is equipped/aimed, or ocular collection is broken.");
        }
        if (!activeHandPass) {
            // 世界渲染那次直接跳过，且【不清空】清单 ——
            // 目镜是在手持渲染的 submit 阶段登记的，而手持渲染发生在世界之后，
            // 所以此刻清单本就是空的；真要清反而会误伤（万一顺序变了）。
            //
            // 那会不会漏清？不会：登记只发生在第一人称手持路径，
            // 而该路径必然紧跟着一次 inHandPass=true 的 renderAllFeatures，
            // 那次的 finally 会兜底清空。
            return;
        }
        if (!RenderConfig.SCOPE_MASK_ENABLE.get()) {
            // 功能没开也要清，否则清单会无限增长。
            ScopeMaskGeometry.clear();
            return;
        }
        try {
            if (failed || ScopeMaskGeometry.isEmpty()) {
                return;
            }
            TextureTarget target = ScopeMaskTarget.getOrCreate();
            if (target == null) {
                return;
            }
            drawMask(target);
        } catch (Exception e) {
            failed = true;
            GunMod.LOGGER.error("[TACZ Scope] Failed to render ocular mask; mask disabled.", e);
        } finally {
            // 关键：无条件清空。哪怕上面 return 了，也不能把几何留到下一帧 ——
            // 否则收起瞄具后掩码会「粘住」不消失。
            ScopeMaskGeometry.clear();
        }
    }

    private static void drawMask(TextureTarget target) {
        MeshData mesh = buildMesh();
        if (mesh == null) {
            // 没有可画的几何：仍然开一次 pass 把掩码清空。
            // 否则上一帧的白色形状会残留在纹理里（target 不会自己变黑）。
            clearOnly(target);
            return;
        }
        try (mesh) {
            MeshData.DrawState draw = mesh.drawState();
            GpuBuffer vertexBuffer = null;
            try {
                vertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "tacz_scope_mask_vertices",
                        GpuBuffer.USAGE_VERTEX,
                        mesh.vertexBuffer());

                // 共享的四边形索引缓冲：把 QUADS 展开成三角形。
                // 用 vanilla 现成的，不必自己生成索引。
                RenderSystem.AutoStorageIndexBuffer indices =
                        RenderSystem.getSequentialBuffer(draw.primitiveTopology());
                GpuBuffer indexBuffer = indices.getBuffer(draw.indexCount());

                CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                try (RenderPass pass = encoder.createRenderPass(
                        () -> "tacz_scope_mask",
                        target.getColorTextureView(),
                        // 每帧从全黑重来。掩码是「当帧目镜盖到哪」，没有历史含义。
                        Optional.of(new Vector4f(0.0f, 0.0f, 0.0f, 1.0f)))) {
                    pass.setPipeline(MASK_PIPELINE);
                    // 这两句缺一不可，是照 PreparedRenderType#drawFromBuffer 抄的：
                    //   bindDefaultUniforms 提供 Projection / Fog 等全局 uniform；
                    //   DynamicTransforms 提供 ModelViewMat 与 ColorModulator。
                    // 少任何一句，shader 都会因为 uniform 缺失而画不出正确结果
                    // （症状类似 r46 的 "Unable to find shader defined uniform"）。
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms",
                            RenderSystem.getDynamicUniforms().writeTransform(
                                    // ScopeMaskGeometry entries are captured with the submit-time ModelView already
                                    // baked into their pose. Do not multiply the phase-boundary ModelView again here:
                                    // under Iris that matrix can represent a stale/world-facing hand state, which pins
                                    // the mask to north. Using identity makes the mask pass consume the exact clip-space
                                    // basis captured when the scope geometry was submitted.
                                    new Matrix4f(),
                                    // R = 1：被目镜盖到的像素，红通道恒为 1（掩码本体）。
                                    // G = 开镜进度：镜身/准星 shader 用它做屏幕空间的渐进收缩。
                                    //
                                    // 为什么把进度塞进颜色通道而不是新加一个 uniform：
                                    // 掩码管线本就要写 ColorModulator，绿通道是现成的空闲载体；
                                    // 新增 uniform 意味着再改一次 bind group layout，
                                    // 而那正是 r46/r52 两次崩溃的来源。能不动就不动。
                                    new Vector4f(1.0f, currentAimingProgress(), 1.0f, 1.0f)));
                    pass.setVertexBuffer(0, vertexBuffer.slice());
                    pass.setIndexBuffer(indexBuffer, indices.type());
                    // 【参数顺序照字节码抄】RenderPass#drawIndexed 是 5 个 int。
                    // vanilla PreparedRenderType#drawFromBuffer 偏移 227-237 的实参依次是：
                    //     aload  indexCount(局部槽6)
                    //     iconst_1
                    //     iload  firstIndex(槽5)
                    //     iload  baseVertex(槽4)
                    //     iconst_0
                    // 即 drawIndexed(indexCount, 1, firstIndex, baseVertex, 0)。
                    // 我们的顶点/索引都是从头开始的单批，所以 firstIndex 与 baseVertex 都是 0。
                    pass.drawIndexed(draw.indexCount(), 1, 0, 0, 0);
                }
                if (!loggedSuccess) {
                    loggedSuccess = true;
                    GunMod.LOGGER.info("[TACZ Scope] Ocular mask drawn: {} indices from {} batches.",
                            draw.indexCount(), ScopeMaskGeometry.entries().size());
                }
            } finally {
                if (vertexBuffer != null) {
                    // 每帧新建、每帧释放。这里不做缓冲池 —— 目镜几何量极小
                    // （单个瞄具几个 cube），过早优化只会增加生命周期出错的机会。
                    vertexBuffer.close();
                }
            }
        }
    }

    /**
     * 当前开镜进度（0 = 完全没开镜，1 = 完全开镜）。
     *
     * <p>写进掩码的绿通道，供镜身/准星 shader 做屏幕空间渐进。
     * 与 {@code BedrockAttachmentModel#currentAimingProgress} 同源，
     * 都取自 {@code IClientPlayerGunOperator}。
     */
    private static float currentAimingProgress() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0.0f;
        }
        return Mth.clamp(IClientPlayerGunOperator.fromLocalPlayer(player)
                .getClientAimingProgress(Minecraft.getInstance().getDeltaTracker()
                        .getGameTimeDeltaPartialTick(false)), 0.0f, 1.0f);
    }

    /** 没有几何时也要把 target 刷黑，否则会残留上一帧的形状。 */
    private static void clearOnly(TextureTarget target) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(
                () -> "tacz_scope_mask_clear",
                target.getColorTextureView(),
                Optional.of(new Vector4f(0.0f, 0.0f, 0.0f, 1.0f)))) {
            pass.pushDebugGroup(() -> "tacz_scope_mask_empty");
            pass.popDebugGroup();
        }
    }

    /**
     * 把当帧登记的所有目镜 cube 写成一份顶点数据。
     *
     * @return 顶点网格；没有任何可画几何时返回 {@code null}
     */
    private static MeshData buildMesh() {
        BufferBuilder builder = new BufferBuilder(SCRATCH, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
        boolean hullFill = RenderConfig.SCOPE_MASK_HULL_FILL.get();
        for (ScopeMaskGeometry.Entry entry : ScopeMaskGeometry.entries()) {
            if (hullFill && writeHullFill(builder, entry.pose(), entry.cubes())) {
                continue;
            }
            for (BedrockCube cube : entry.cubes()) {
                // 通过接口取面，【不做 instanceof 判断】。
                //
                // 第一版写的是 `if (cube instanceof BedrockCubeBox box)`，实测掩码全黑：
                // 默认枪包 161 个目镜立方体【无一例外】都是 BedrockCubePerFace
                // （它们都带 face_uv），被那个 instanceof 百分之百滤掉了。
                // 两个实现的 polygons 结构完全同构，能力应当由接口表达。
                writeCube(builder, entry.pose(), cube);
            }
        }
        // 一个顶点都没写时 build() 返回 null，调用方据此走「只清空」分支。
        return builder.build();
    }

    /**
     * 【案例③ 第二轮 · 凸包填充模式】把目镜几何投影的 <b>2D 凸包</b>整体涂进掩码。
     *
     * <h2>它解决哪个洞</h2>
     * 「几何描摹」掩码忠实复制目镜网格形状。对全玻璃板目镜（红点/全息）它≈孔径，
     * 表现正确；但对<b>板条拼玻璃</b>的目镜（AUG 3 条十字、elcan 8 片竖板、
     * lpvo 细十字），掩码只剩板条本身 —— 孔径内其余区域的镜身内壁网格全部漏裁，
     * 镜片里残留灰块（AUG 实测最明显）。板条的张开跨度恰好勾勒孔径的内接多边形，
     * 故取凸包即得「孔径近似」，覆盖面严格不小于板条描摹（漏裁类残块必消）。
     *
     * <h2>做法（矩阵链与 {@link #writeCube} 严格同源）</h2>
     * <ol>
     *   <li>条目顶点先按 {@code pos/16 → mul(pose)} 得到绘制空间坐标（与描摹路径一致）；</li>
     *   <li>用<b>本 pass 将实际使用的同一投影矩阵</b>投影到 NDC（xy 做透视除法）；</li>
     *   <li>对 NDC 平面点集求 Andrew 单调链凸包；</li>
     *   <li>凸包顶点用投影的逆变回绘制空间，按<b>退化四边形扇</b>
     *       (v0, hi, hi+1, hi+1) 写出 —— 复用 QUADS 展开索引即可成三角扇，
     *       不动管线与索引结构。着色阶段 DynamicTransforms=单位阵，
     *       顶点再过一次同一投影 ⇒ 精确落回凸包 NDC。</li>
     * </ol>
     *
     * @return 有效点不足（凸包退化，如全重合）时返回 false，调用方回退逐立方体描摹
     */
    private static boolean writeHullFill(BufferBuilder builder, Matrix4f pose, java.util.List<BedrockCube> cubes) {
        java.util.List<float[]> pts = new java.util.ArrayList<>();
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Vector4f tmp = new Vector4f();
        for (BedrockCube cube : cubes) {
            for (var polygon : cube.getPolygons()) {
                if (polygon == null) {
                    continue;
                }
                for (var vertex : polygon.vertices) {
                    tmp.set(vertex.pos.x() / 16.0F, vertex.pos.y() / 16.0F, vertex.pos.z() / 16.0F, 1.0F);
                    tmp.mul(pose);
                    tmp.mul(proj);
                    // 只收「相机前方」的点：w<=0 的顶点经过透视除法会被翻到
                    // NDC 对面（x/w、y/w 符号反转）。瞄具在极端侧头/切视角瞬间可能
                    // 有顶点落到相机平面后方，若照收，凸包会被这类镜像点拉到屏幕
                    // 另一端，当帧大片画面被错判成“镜内”而整块消失。
                    // 丢掉后凸包不足 3 点会回退逐立方体描摹（=旧行为），安全。
                    if (tmp.w <= 1.0e-6f) {
                        continue;
                    }
                    pts.add(new float[]{tmp.x() / tmp.w, tmp.y() / tmp.w});
                }
            }
        }
        if (pts.size() < 3) {
            return false;
        }
        // Andrew 单调链凸包（输入先按 x、再按 y 排序去重）
        pts.sort((a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : Float.compare(a[1], b[1]));
        java.util.List<float[]> unique = new java.util.ArrayList<>();
        for (float[] p : pts) {
            if (unique.isEmpty()) {
                unique.add(p);
                continue;
            }
            float[] q = unique.get(unique.size() - 1);
            if (Math.abs(q[0] - p[0]) > 1.0e-6f || Math.abs(q[1] - p[1]) > 1.0e-6f) {
                unique.add(p);
            }
        }
        if (unique.size() < 3) {
            return false;
        }
        java.util.List<float[]> hull = new java.util.ArrayList<>();
        // 下壳
        for (float[] p : unique) {
            while (hull.size() >= 2 && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), p) <= 0) {
                hull.remove(hull.size() - 1);
            }
            hull.add(p);
        }
        // 上壳
        int lower = hull.size() + 1;
        for (int i = unique.size() - 2; i >= 0; i--) {
            float[] p = unique.get(i);
            while (hull.size() >= lower && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), p) <= 0) {
                hull.remove(hull.size() - 1);
            }
            hull.add(p);
        }
        if (hull.size() > 1) {
            hull.remove(hull.size() - 1); // 收尾重复点
        }
        if (hull.size() < 3) {
            return false;
        }
        // 凸包顶点（NDC）逆投影回绘制空间，按退化四边形扇写出
        Matrix4f invProj = proj.invert(new Matrix4f());
        float[] p0 = hull.get(0);
        for (int i = 1; i + 1 < hull.size(); i++) {
            emitNdcAsQuad(builder, invProj, p0, hull.get(i), hull.get(i + 1));
        }
        return true;
    }

    /** 单调链叉积：(b−a)×(c−a) 的 z 分量。 */
    private static float cross(float[] a, float[] b, float[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    /** 把三个 NDC 点写成一个退化四边形（第 4 顶点重复），顺带回绘制空间。 */
    private static void emitNdcAsQuad(BufferBuilder builder, Matrix4f invProj,
                                      float[] a, float[] b, float[] c) {
        emitNdcVertex(builder, invProj, a);
        emitNdcVertex(builder, invProj, b);
        emitNdcVertex(builder, invProj, c);
        emitNdcVertex(builder, invProj, c);
    }

    private static void emitNdcVertex(BufferBuilder builder, Matrix4f invProj, float[] ndc) {
        Vector4f v = new Vector4f(ndc[0], ndc[1], 0.0f, 1.0f);
        v.mul(invProj);
        if (Math.abs(v.w) > 1.0e-6f) {
            v.div(v.w);
        }
        builder.addVertex(v.x(), v.y(), v.z());
    }

    /**
     * 写一个立方体的 6 个面。
     *
     * <p>顶点变换与 {@link BedrockCubeBox#compile} <b>逐行一致</b>：
     * {@code pos / 16 → mul(matrix)}。两条路径必须用同一套算法，
     * 否则掩码会与画面错位 —— 那种偏差极难排查。
     */
    private static void writeCube(BufferBuilder builder, Matrix4f pose, BedrockCube cube) {
        for (var polygon : cube.getPolygons()) {
            if (polygon == null) {
                continue;
            }
            for (var vertex : polygon.vertices) {
                float x = vertex.pos.x() / 16.0F;
                float y = vertex.pos.y() / 16.0F;
                float z = vertex.pos.z() / 16.0F;
                Vector4f v = new Vector4f(x, y, z, 1.0F);
                v.mul(pose);
                builder.addVertex(v.x(), v.y(), v.z());
            }
        }
    }
}
