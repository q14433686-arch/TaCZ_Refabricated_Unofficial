package com.tacz.guns.compat.voxy;

import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 让 Voxy 同时服务<b>两套 Iris 管线</b>：主画面一套，镜内那一遍一套。
 *
 * <h2>为什么需要这个</h2>
 * 开着管线隔离时场上有两套 Iris 管线，而 Voxy 的渲染栈是<b>逐管线绑定</b>的：
 * <pre>
 * VoxyRenderSystem.java:140-149（0.2.19 源码）
 *   this.pipeline       = RenderPipelineFactory.createPipeline(...)   // 绑定当前 Iris 管线
 *   this.traversal.lateStageCompile(this.pipeline)                    // 遍历器记住它，用于 bindUniforms
 *   var sectionRenderer = backendFactory.create(this.pipeline, ...)   // 逐管线
 *   this.viewportSelector = new ViewportSelector&lt;&gt;(sectionRenderer::createViewport)  // 视口由它产出
 * </pre>
 * 只有一份，所以另一套管线下必然用错绘制目标 —— 表现为某一侧远景永久错乱。
 *
 * <h2>为什么这么做是安全的（关键判断）</h2>
 * <ul>
 *   <li>Voxy <b>本来就是</b>「一套 Iris 管线数据 ↔ 一个 Voxy 管线」的一对一设计：
 *       {@code IrisVoxyRenderPipeline} 构造时若 {@code data.thePipeline != null} 会直接抛
 *       {@code "Pipeline data already bound"}。每套 Iris 管线各有自己的
 *       {@code IrisVoxyRenderPipelineData}，所以再建一个<b>不违背它的约束</b>。</li>
 *   <li><b>不会复制大块显存</b>：LOD 几何在 {@code geometryData}/{@code WorldEngine} 里，
 *       是共享的。第二套只多出两个深度 framebuffer、一个 uniform buffer、几个全屏 blit
 *       着色器，以及 section renderer 的三个 1KB 级缓冲。</li>
 * </ul>
 *
 * <h2>逐帧要换的四样东西</h2>
 * <ol>
 *   <li>{@code VoxyRenderSystem.pipeline}      —— 绘制用哪套；</li>
 *   <li>{@code VoxyRenderSystem.viewportSelector} —— 视口必须由<b>对应的</b>
 *       section renderer 产出（见上面 149 行），拿错了就是拿了别套的视口；</li>
 *   <li>{@code HierarchicalOcclusionTraverser.pipeline} —— 遍历器每帧
 *       {@code this.pipeline.bindUniforms()}（第 251 行），不换就绑到别套的 uniform 上；</li>
 *   <li>换完必须<b>原样换回去</b>，否则主画面接着用瞄具那套。</li>
 * </ol>
 * 三个字段都是非静态 final，{@code setAccessible(true)} 后可写（这是 JDK 明确允许的：
 * 非静态 final 字段在 setAccessible 成功后可写）。
 *
 * <h2>生命周期：宁可不建，也不能泄漏</h2>
 * 第二套的所有权在我们手里，所以必须自己回收。判据是<b>身份比较</b>：
 * 只要 {@code VoxyRenderSystem} 实例或主 Voxy 管线换了人（切世界、重载光影包、
 * Voxy 自己重建），就先 {@code free()} 旧的再重建。
 * 这个仓库已经被显存问题咬过一次，这里宁可保守。
 *
 * <p>全程反射；任何一步失败都<b>整体放弃</b>并退回「镜内不画 LOD」，不会半途留下坏状态。
 */
@Environment(EnvType.CLIENT)
public final class VoxyScopePipelineCompat {

    private static final String PKG = "me.cortex.voxy.client.core.";

    private static boolean resolved;
    private static boolean unavailable;

    // VoxyRenderSystem 的私有字段
    private static Field fPipeline;
    private static Field fViewportSelector;
    private static Field fProperties;
    private static Field fNodeManager;
    private static Field fNodeCleaner;
    private static Field fTraversal;
    private static Field fModelService;
    private static Field fGeometryData;
    // 遍历器里那份「绑谁的 uniform」
    private static Field fTraversalPipeline;

    private static Method mCreatePipeline;
    private static Method mSetupExtraModelBakeryData;
    private static Method mSetSectionRenderer;
    private static Method mFreePipeline;
    private static Method mGetStore;
    private static Method mFactoryCreate;
    private static Method mCreateViewport;
    private static Method mFrexStillHasWork;
    private static Method mSelectorFree;
    private static Object sectionRendererFactory;
    private static Constructor<?> cViewportSelector;

    /** 已经为哪一个 VoxyRenderSystem / 哪一套主 Voxy 管线建过第二套。 */
    private static Object builtForSystem;
    private static Object builtAlongsideMainPipeline;

    private static Object scopePipeline;
    private static Object scopeViewportSelector;

    /** 换上去期间保存的原值。 */
    private static Object savedPipeline;
    private static Object savedSelector;
    private static Object savedTraversalPipeline;
    private static boolean swapped;

    private static boolean loggedBuilt;
    private static boolean loggedGaveUp;

    private VoxyScopePipelineCompat() {
    }

    public static boolean isAvailable() {
        return resolveOnce() && !unavailable;
    }

    /** 第二套渲染栈是否已经为这个 {@code VoxyRenderSystem} 建好且仍然有效。 */
    public static boolean isBuiltFor(Object voxySystem) {
        if (scopePipeline == null || voxySystem == null || builtForSystem != voxySystem) {
            return false;
        }
        try {
            return fPipeline.get(voxySystem) == builtAlongsideMainPipeline;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean resolveOnce() {
        if (resolved) {
            return !unavailable;
        }
        resolved = true;
        try {
            if (!FabricLoader.getInstance().isModLoaded("voxy")) {
                unavailable = true;
                return false;
            }
            Class<?> system = Class.forName(PKG + "VoxyRenderSystem");
            Class<?> abstractPipeline = Class.forName(PKG + "AbstractRenderPipeline");
            Class<?> factoryCls = Class.forName(PKG + "RenderPipelineFactory");
            Class<?> properties = Class.forName(PKG + "RenderProperties");
            Class<?> nodeManager = Class.forName(PKG + "rendering.hierachical.AsyncNodeManager");
            Class<?> nodeCleaner = Class.forName(PKG + "rendering.hierachical.NodeCleaner");
            Class<?> traversal = Class.forName(PKG + "rendering.hierachical.HierarchicalOcclusionTraverser");
            Class<?> modelBakery = Class.forName(PKG + "model.ModelBakerySubsystem");
            Class<?> modelStore = Class.forName(PKG + "model.ModelStore");
            Class<?> geometry = Class.forName(PKG + "rendering.section.geometry.IGeometryData");
            Class<?> sectionRenderer = Class.forName(PKG + "rendering.section.backend.AbstractSectionRenderer");
            Class<?> factoryRecord = Class.forName(PKG + "rendering.section.backend.AbstractSectionRenderer$Factory");
            Class<?> selector = Class.forName(PKG + "rendering.ViewportSelector");
            Class<?> mdic = Class.forName(PKG + "rendering.section.backend.mdic.MDICSectionRenderer");

            fPipeline = open(system, "pipeline");
            fViewportSelector = open(system, "viewportSelector");
            fProperties = open(system, "properties");
            fNodeManager = open(system, "nodeManager");
            fNodeCleaner = open(system, "nodeCleaner");
            fTraversal = open(system, "traversal");
            fModelService = open(system, "modelService");
            fGeometryData = open(system, "geometryData");
            fTraversalPipeline = open(traversal, "pipeline");

            mCreatePipeline = factoryCls.getMethod("createPipeline", properties, nodeManager,
                    nodeCleaner, traversal, BooleanSupplier.class);
            mSetupExtraModelBakeryData = abstractPipeline.getMethod("setupExtraModelBakeryData", modelBakery);
            mSetSectionRenderer = abstractPipeline.getMethod("setSectionRenderer", sectionRenderer);
            mFreePipeline = abstractPipeline.getMethod("free");
            mGetStore = modelBakery.getMethod("getStore");
            mFactoryCreate = factoryRecord.getMethod("create", abstractPipeline, modelStore, geometry);
            mCreateViewport = sectionRenderer.getMethod("createViewport");
            mFrexStillHasWork = system.getDeclaredMethod("frexStillHasWork");
            mFrexStillHasWork.setAccessible(true);
            mSelectorFree = selector.getMethod("free");
            cViewportSelector = selector.getConstructor(Supplier.class);
            sectionRendererFactory = mdic.getField("FACTORY").get(null);
            return true;
        } catch (Throwable t) {
            unavailable = true;
            GunMod.LOGGER.warn("[TACZ Scope] Voxy's renderer does not look the way this build expects, so "
                    + "the scope pass cannot get its own Voxy pipeline. Distant LOD terrain will be "
                    + "missing from the lens while ScopePipIsolatePipeline is on.", t);
            return false;
        }
    }

    /**
     * 当前 Iris 管线的 voxy 数据上是不是已经绑了一个 Voxy 管线。
     *
     * <p>用来在调用 {@code createPipeline} 之前把「会抛异常」的情况挡掉 ——
     * 原因见调用处的注释：那个异常会被 Voxy 吞掉并顺手关掉整个 Iris。
     *
     * <p>查不出来时返回 {@code true}（当作已绑）：宁可不建，也不能冒险去撞那个异常。
     */
    private static boolean irisDataAlreadyBound() {
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object manager = iris.getMethod("getPipelineManager").invoke(null);
            Object current = manager.getClass().getMethod("getPipelineNullable").invoke(manager);
            if (current == null) {
                return true;
            }
            Class<?> getData = Class.forName("me.cortex.voxy.client.iris.IGetIrisVoxyPipelineData");
            if (!getData.isInstance(current)) {
                return true;
            }
            Object data = getData.getMethod("voxy$getPipelineData").invoke(current);
            if (data == null) {
                return true;
            }
            Field bound = data.getClass().getField("thePipeline");
            return bound.get(data) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    private static Field open(Class<?> owner, String name) throws NoSuchFieldException {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /**
     * 为当前的瞄具 Iris 管线建好第二套 Voxy 渲染栈。
     *
     * <p><b>调用时机很关键</b>：必须在「瞄具那套 Iris 管线是当前管线」的时候调，
     * 因为 {@code RenderPipelineFactory.createPipeline} 内部就是取
     * {@code Iris.getPipelineManager().getPipelineNullable()} 来绑定的。
     *
     * @return 是否可用（建好了或早就建好了）
     */
    public static boolean ensureBuilt(Object voxySystem) {
        if (voxySystem == null || !resolveOnce() || unavailable) {
            return false;
        }
        try {
            Object mainPipeline = fPipeline.get(voxySystem);
            if (mainPipeline == null) {
                return false;
            }
            if (scopePipeline != null
                    && builtForSystem == voxySystem
                    && builtAlongsideMainPipeline == mainPipeline) {
                return true;
            }
            // 换人了（切世界／重载光影包／Voxy 重建）：先把旧的还回去再重建。
            disposeScopeStack();

            Object properties = fProperties.get(voxySystem);
            Object nodeManager = fNodeManager.get(voxySystem);
            Object nodeCleaner = fNodeCleaner.get(voxySystem);
            Object traversal = fTraversal.get(voxySystem);
            Object modelService = fModelService.get(voxySystem);
            Object geometryData = fGeometryData.get(voxySystem);

            BooleanSupplier frex = () -> {
                try {
                    return (Boolean) mFrexStillHasWork.invoke(voxySystem);
                } catch (Throwable ignored) {
                    return false;
                }
            };

            // 【必须先查，绝不能盲调 createPipeline】
            //
            // 若当前 Iris 管线的 voxy 数据上已经绑了一个管线，再建一个会抛
            // "Pipeline data already bound" —— 而 Voxy 的 createIrisPipeline 把它
            // catch 住之后会调 IrisUtil.disableIrisShaders()（RenderPipelineFactory.java:40），
            // 那会把整个 Iris 拆掉：ShaderStorageBufferHolder 的缓冲被释放，
            // 紧接着主画面那一遍的 Voxy 绘制就 NPE（this.buffers 为 null）当场崩游戏。
            //
            // 也就是说：这里一次误调的代价不是「建不出来」，而是「整局崩」。所以先查后建。
            if (irisDataAlreadyBound()) {
                return false;
            }
            Object pipeline = mCreatePipeline.invoke(null, properties, nodeManager, nodeCleaner,
                    traversal, frex);
            if (pipeline == null || pipeline == mainPipeline) {
                // 拿不到独立的一套（例如 Iris 那侧还没准备好）就别硬来。
                return false;
            }
            mSetupExtraModelBakeryData.invoke(pipeline, modelService);

            // 刻意【不】调 traversal.lateStageCompile(pipeline)：那会把共享的遍历器
            // 重新编译并改绑到瞄具这套上，反而把主画面弄坏。两套管线出自同一个
            // shaderpack，TAA 函数一致，编译结果等价 —— 需要的只是逐帧换绑
            // traversal.pipeline（见 swapIn），那才是它真正逐帧用到的东西。

            Object store = mGetStore.invoke(modelService);
            Object sectionRenderer = mFactoryCreate.invoke(sectionRendererFactory, pipeline, store, geometryData);
            mSetSectionRenderer.invoke(pipeline, sectionRenderer);

            Supplier<Object> viewportMaker = () -> {
                try {
                    return mCreateViewport.invoke(sectionRenderer);
                } catch (Throwable t) {
                    throw new IllegalStateException("scope viewport", t);
                }
            };
            Object selector = cViewportSelector.newInstance(viewportMaker);

            scopePipeline = pipeline;
            scopeViewportSelector = selector;
            builtForSystem = voxySystem;
            builtAlongsideMainPipeline = mainPipeline;
            if (!loggedBuilt) {
                loggedBuilt = true;
                GunMod.LOGGER.info("[TACZ Scope] Built a second Voxy render pipeline bound to the scope "
                        + "pass' Iris pipeline, so distant LOD terrain renders in the lens. The LOD "
                        + "geometry itself is shared, so this costs only a few small buffers.");
            }
            return true;
        } catch (Throwable t) {
            unavailable = true;
            disposeScopeStack();
            if (!loggedGaveUp) {
                loggedGaveUp = true;
                GunMod.LOGGER.warn("[TACZ Scope] Could not build a second Voxy pipeline for the scope "
                        + "pass; the lens will not show distant LOD terrain.", t);
            }
            return false;
        }
    }

    /**
     * Voxy 把整个 {@code VoxyRenderSystem} 拆了重建时调用（见
     * {@code LevelExtractorScopePassMixin}）。<b>必须立刻把第二套还回去</b>。
     *
     * <h3>不这么做会怎样（这就是「改完区块视距再开镜必崩」的病根）</h3>
     * {@code AbstractRenderPipeline} 在<b>构造时</b>就把
     * {@code traversal}/{@code nodeManager}/{@code nodeCleaner} 存成 final 字段
     * （AbstractRenderPipeline.java:58-83，源码实读）。而玩家一改区块视距，
     * {@code LevelExtractor.allChanged()} 就会让 Voxy 走
     * {@code voxy$shutdownRenderer()} + {@code voxy$createRenderer()}
     * （MixinLevelExtractor.java:25-29）—— 旧的 {@code VoxyRenderSystem.free()}
     * 把 {@code traversal} 连同它那些 GlBuffer 一起释放掉了。
     *
     * <p>我们那第二套却还攥着<b>已经被释放</b>的旧 traverser。下次开镜时
     * {@code doTraversal} 里第一句 {@code this.traversal.bind()} 就会撞上
     * {@code TrackedObject.assertNotFreed}：
     * <pre>Object me.cortex.voxy.client.core.gl.GlBuffer@… should not be free, but is</pre>
     *
     * <h3>为什么此刻释放是安全的</h3>
     * 我们释放的全是<b>自己建的</b>那些：{@code IrisVoxyRenderPipeline.free()} 只动
     * 自己的 fb/blit/uniform 并把 {@code data.thePipeline} 置回 null；
     * {@code AbstractRenderPipeline.free0()} 再释放我们建的 section renderer；
     * {@code MDICSectionRenderer.free()} 只释放它自己的缓冲与着色器。
     * <b>没有一处会去碰共享的 traversal / geometryData</b>，
     * 所以先后顺序无所谓 —— Voxy 是先拆后建还是先建后拆都不影响。
     */
    public static void onRendererRebuilt() {
        if (swapped) {
            // 理论不可达：这条路径由 allChanged() 驱动，而镜内那一遍期间 allChanged()
            // 会被整个取消掉（见 LevelExtractorScopePassMixin）。真发生了也不能在
            // 换上去的中途抽掉它 —— 留给 swapOut 收尾，下一帧自然会重建。
            return;
        }
        disposeScopeStack();
    }

    /** 把 Voxy 切到瞄具那套。必须与 {@link #swapOut(Object)} 成对，放在 try/finally 里。 */
    public static boolean swapIn(Object voxySystem) {
        if (swapped || scopePipeline == null || voxySystem == null) {
            return false;
        }
        // 【最后一道闸】只有确认这第二套<b>确实属于眼前这个 VoxyRenderSystem</b> 才换。
        //
        // 上面 onRendererRebuilt 已经在重建时清过一次，这里是兜底：万一哪天那个
        // 通知路径失灵（Voxy 换了重建时机、我们的注入点被别的 mod 抢掉），
        // 代价也只是「镜内看不到 LOD 远景」，而不是拿已释放的 GL 对象去画 —— 后者会
        // 让镜内那一遍抛异常，进而连累主画面（见 ScopePipRenderer 里 PreparedFrame 那段）。
        if (!isBuiltFor(voxySystem)) {
            return false;
        }
        try {
            Object traversal = fTraversal.get(voxySystem);
            savedPipeline = fPipeline.get(voxySystem);
            savedSelector = fViewportSelector.get(voxySystem);
            savedTraversalPipeline = fTraversalPipeline.get(traversal);

            fPipeline.set(voxySystem, scopePipeline);
            fViewportSelector.set(voxySystem, scopeViewportSelector);
            // 只在原本就绑着东西时才换：为 null 说明这个 pack 没有 TAA 函数，
            // 遍历器压根不会调 bindUniforms，换了反而是无中生有。
            if (savedTraversalPipeline != null) {
                fTraversalPipeline.set(traversal, scopePipeline);
            }
            swapped = true;
            return true;
        } catch (Throwable t) {
            // 换到一半失败：立刻尽力还原，绝不能把 Voxy 留在半换状态。
            swapped = true;
            swapOut(voxySystem);
            unavailable = true;
            GunMod.LOGGER.warn("[TACZ Scope] Failed to switch Voxy to the scope pipeline; disabling the "
                    + "second Voxy pipeline for this session.", t);
            return false;
        }
    }

    /** 还原 {@link #swapIn}。任何情况下都必须被调到。 */
    public static void swapOut(Object voxySystem) {
        if (!swapped) {
            return;
        }
        swapped = false;
        try {
            if (voxySystem != null) {
                if (savedPipeline != null) {
                    fPipeline.set(voxySystem, savedPipeline);
                }
                if (savedSelector != null) {
                    fViewportSelector.set(voxySystem, savedSelector);
                }
                if (savedTraversalPipeline != null) {
                    fTraversalPipeline.set(fTraversal.get(voxySystem), savedTraversalPipeline);
                }
            }
        } catch (Throwable t) {
            GunMod.LOGGER.error("[TACZ Scope] Failed to restore Voxy's main pipeline after the scope "
                    + "pass. Distant terrain may render incorrectly until the world is reloaded.", t);
        } finally {
            savedPipeline = null;
            savedSelector = null;
            savedTraversalPipeline = null;
        }
    }

    /** 释放我们自己建的那一套。只有我们持有它，所以只能由我们回收。 */
    private static void disposeScopeStack() {
        Object pipeline = scopePipeline;
        Object selector = scopeViewportSelector;
        scopePipeline = null;
        scopeViewportSelector = null;
        builtForSystem = null;
        builtAlongsideMainPipeline = null;
        if (selector != null) {
            try {
                mSelectorFree.invoke(selector);
            } catch (Throwable ignored) {
                // 回收失败没有补救手段，继续尝试释放管线
            }
        }
        if (pipeline != null) {
            try {
                // free() 会把 IrisVoxyRenderPipelineData.thePipeline 置回 null，
                // 少了这一步，下次为同一套 Iris 管线重建时会撞上
                // "Pipeline data already bound"。
                mFreePipeline.invoke(pipeline);
            } catch (Throwable ignored) {
            }
        }
    }
}
