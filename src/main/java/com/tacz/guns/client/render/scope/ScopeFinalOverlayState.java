package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 光影 + PIP 下把物理目镜框（ocular_ring）的最终着色<b>延后到 PIP 合成之后</b>重画。
 *
 * <h2>为什么需要（26.2 光影遮光环半透明 bug 的真因）</h2>
 * 「合成 vs 手持」的先后关系在两条路径上是<b>反的</b>：
 * <pre>
 * 无光影：compositeAtPhaseBoundary() 在 renderAllFeatures 里 executeSolid() 之前
 *         = 合成画在手持之【前】 ⇒ 手持（含案例⑨ 无裁剪重画的 ocular_ring）
 *           盖在合成之上，一切正常；
 * 有光影：Iris 把整个手部 pass 搬进 LevelRenderer#render 内部，最终画面要等
 *         整条 Iris 管线收工才存在，compositeAfterLevelUnderShaders() 只能排在
 *         LevelRenderer#render 的返回处 = 合成画在手持之【后】
 *         ⇒ 把刚画好的物理目镜框整片盖掉。
 * </pre>
 * 被盖掉的正是案例⑨救回来的那部分：ocular_ring 的内圈与目镜投影（掩码）重叠。
 * 对着光才看得见 —— 露出来的是放大的世界拷贝，世界暗时与黑环无异。
 *
 * <h2>机制来源</h2>
 * 本仓 1.21.11 分支 commit 2710c7c「render reticle after Iris final composite」的
 * ScopeFinalOverlayState / queueOcularRing 同源形态，按 26.2 API 重写（不照抄）：
 * <ol>
 *   <li>26.2 不自建 FeatureRenderDispatcher（构造函数改吃 RenderBuffers）——
 *       用官方的 {@code gameRenderer.featureRenderDispatcher().renderAllFeatures(storage)}；</li>
 *   <li>26.2 的 {@code SubmitNodeStorage} 没有 endFrame()/clear() —— 每次用全新实例，
 *       renderAllFeatures 的 prepareFrame 会消费它；</li>
 *   <li>刷新点不是 finalizeLevelRendering TAIL（那跑在 LevelRenderer#render 内部、
 *       早于 PIP 合成，挂在那里等于没延后）—— 挂在
 *       {@code compositeAfterLevelUnderShaders()} 之后（GameRendererMixin 同一注入点内）。</li>
 * </ol>
 *
 * <h2>矩阵语义（坑 B）</h2>
 * 几何快照顶点是已套 poseStack 的坐标、节点 pose 是单位矩阵 —— 落点完全取决于
 * 绘制那一刻 RenderSystem 里挂的投影与模型视图。手持的这两个矩阵必须在
 * <b>阶段边界</b>（与画掩码同一个位置，executeSolid 之前）取；submit 阶段
 * RenderSystem 里挂的还是世界那套，取到它目镜框会整个飘出画面。
 * 投影只存切片对象（{@link GpuBufferSlice}），不 map() 读回 —— 那 64 字节光影下不可读。
 *
 * <h2>两个「别做」</h2>
 * <ul>
 *   <li>ring-final 管线<b>不</b>注册给 Iris 的 HAND 程序：注册了就被塞回光影管线，
 *       既拿不到无雾语义又会被后置 pass 再盖一次；</li>
 *   <li><b>不</b>无条件延后：只在 {@code ScopePipRenderer.wantsIrisComposite()} 为真时
 *       排队（BedrockAttachmentModel 的调用点把关）；其余路径（无光影 / 未开 PIP /
 *       关着 AllowShaderPacks / 第三人称）逐位保持原样。队列在帧首清空
 *       （{@link #beginFrame()}），否则某帧没走到刷新点时残留快照会画到下一帧。</li>
 * </ul>
 */
public final class ScopeFinalOverlayState {

    /** 与 1.21.11 母本一致：目镜框排在准星（20_000，若将来补）之后，后画者盖前画者。 */
    private static final int FINAL_OCULAR_RING_ORDER = 20_001;

    private static final List<RingDraw> PENDING_RINGS = new ArrayList<>();
    @Nullable
    private static HandTransform handTransform;
    private static boolean loggedRendered;
    private static boolean loggedFailure;

    private ScopeFinalOverlayState() {
    }

    /** 帧首清空（挂 GameRenderer#extract HEAD）：残留快照绝不许跨帧。 */
    public static void beginFrame() {
        PENDING_RINGS.clear();
        handTransform = null;
    }

    /**
     * 登记一份冻结的目镜框快照。只应在 {@code wantsIrisComposite()} 为真的
     * 第一人称手持 submit 里调用（调用点把关，这里不重复判定）。
     */
    public static void queueOcularRing(BedrockRenderSnapshot snapshot, RenderType renderType) {
        if (!snapshot.isEmpty()) {
            PENDING_RINGS.add(new RingDraw(snapshot, renderType));
        }
    }

    /**
     * 在手部 pass 的阶段边界（executeSolid 之前，与掩码同点）抓手持投影/模型视图。
     * 一帧只抓一次 —— Iris 的 HandRenderer 一帧调两次 renderAllFeatures
     * （solid 与 translucent），两次都是手持矩阵，第一次的就够。
     */
    public static void capturePhaseBoundaryTransform() {
        if (handTransform != null || PENDING_RINGS.isEmpty()) {
            return;
        }
        if (!ScopeMaskRenderer.isInHandPass()) {
            return;
        }
        handTransform = new HandTransform(
                RenderSystem.getModelViewMatrixCopy(),
                RenderSystem.getProjectionMatrixBuffer(),
                RenderSystem.getProjectionType());
    }

    /**
     * PIP 合成之后重画目镜框。调用点：{@code GameRendererMixin} 里
     * {@code ScopePipRenderer.compositeAfterLevelUnderShaders()} 的下一句。
     *
     * <p>此刻整条 Iris 管线已收工、镜内放大画面已合成进主 target ——
     * 在它之上用原版管线（无雾片元）把物理目镜框画回来，恢复
     * 「目镜框永远在合成之上」的无光影语序。</p>
     */
    public static void renderAfterLevelComposite() {
        if (PENDING_RINGS.isEmpty()) {
            return;
        }
        List<RingDraw> rings = List.copyOf(PENDING_RINGS);
        HandTransform transform = handTransform;
        PENDING_RINGS.clear();
        handTransform = null;
        if (transform == null) {
            // 排了队但没经过手部阶段边界（异常路径）：没有可信矩阵，宁可不画。
            return;
        }
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
        if (main == null) {
            return;
        }

        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        GpuTextureView previousColorOverride = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepthOverride = RenderSystem.outputDepthTextureOverride;

        modelView.pushMatrix();
        modelView.set(transform.modelView());
        RenderSystem.setProjectionMatrix(transform.projection(), transform.projectionType());
        RenderSystem.outputColorTextureOverride = main.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = main.getDepthTextureView();
        try {
            // 26.2：storage 每次全新（prepareFrame 消费它，且 endFrame/clear 已被移除）；
            // dispatcher 用官方共享实例 —— 此刻 LevelRenderer#render 已返回，
            // 它的 PreparedFrame 已 close，renderAllFeatures 可安全重入。
            SubmitNodeStorage storage = new SubmitNodeStorage();
            OrderedSubmitNodeCollector ringCollector = storage.order(FINAL_OCULAR_RING_ORDER);
            for (RingDraw draw : rings) {
                ringCollector.submitCustomGeometry(new PoseStack(), draw.renderType(),
                        (entryPose, consumer) -> draw.snapshot().write(consumer));
            }
            minecraft.gameRenderer.featureRenderDispatcher().renderAllFeatures(storage);
            if (!loggedRendered) {
                loggedRendered = true;
                GunMod.LOGGER.info("[TACZ Scope] Redrew ocular ring after shader-pack PIP composite.");
            }
        } catch (RuntimeException e) {
            // 可选的 Iris 集成不许把光影包边缘情况变成客户端崩溃。
            if (!loggedFailure) {
                loggedFailure = true;
                GunMod.LOGGER.warn("[TACZ Scope] Post-composite ocular ring redraw failed; skipping.", e);
            }
        } finally {
            RenderSystem.outputColorTextureOverride = previousColorOverride;
            RenderSystem.outputDepthTextureOverride = previousDepthOverride;
            modelView.popMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
        }
    }

    /**
     * 阶段边界抓到的手持变换。modelView 由 {@code getModelViewMatrixCopy()} 返回的
     * 就已是拷贝；projection 只持有切片引用（坑 B：不 map() 读回）。
     */
    private record HandTransform(Matrix4f modelView,
                                 GpuBufferSlice projection,
                                 ProjectionType projectionType) {
    }

    private record RingDraw(BedrockRenderSnapshot snapshot, RenderType renderType) {
    }
}
