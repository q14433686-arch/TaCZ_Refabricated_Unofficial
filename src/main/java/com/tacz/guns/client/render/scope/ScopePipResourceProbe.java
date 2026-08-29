package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.textures.GpuTexture;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisScopePipelineCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 【诊断】按帧脉冲统计瞄具/主管线各自的 GPU 纹理字节数 + <b>Java 堆与 GC</b>。
 *
 * <h2>为什么需要它 —— 姊妹分支探针没覆盖的两格</h2>
 * 「光影下开镜帧率随时间衰减、重进存档重置」这个症状（{@code ScopePipIsolatePipeline=true}），
 * 姊妹分支把 CPU 侧能数的结构都数过了（管线身份、pipelinesPerDimension 大小、
 * 激活 SSBO 数量与字节、Blaze3D 保留集合、SodiumWorldRenderer 集合）—— 全部无果。
 * 但有<b>两格从来没有被量化过</b>：
 * <ol>
 *   <li><b>GPU 侧字节数</b>：结构探针数的是「条目数」不是「字节数」；</li>
 *   <li><b>Java 堆与 GC</b>：用户实测「衰减与开镜时长无关、只与距第一次开镜的时间有关，
 *       7fps 地板、不崩溃、重进存档重置」—— 这是<b>堆持续增长 → GC 饱和</b>的经典签名，
 *       而不是显存耗尽（显存耗尽在本仓有案底：直接 {@code GpuOutOfMemoryException} 崩游戏，
 *       见 {@code ScopePipTrace} 类注释）。</li>
 * </ol>
 *
 * <p>判读（每 600 帧一行）：</p>
 * <ul>
 *   <li>scopePasses 涨且 scopePipelineTextureMiB 同步涨 ⇒ 瞄具管线在逐 pass 累积 GPU 纹理
 *       （但用户「与开镜时长无关」的观察与此矛盾，需先验证）；</li>
 *   <li>heapUsedMiB 随 frame 单调爬升、gcTimeMs 窗口增量同步涨 ⇒ 堆泄漏/GC 饱和，
 *       继续用 F3 的 Mem% 与 gcTime 曲线定位触发点；</li>
 *   <li>两列都平 ⇒ 既不是 GPU 纹理也不是堆，转队列积压反馈（首镜砍半帧率 → 积压 → 地板）。</li>
 * </ul>
 *
 * <p>字节数取 {@code GpuTexture#getMemorySize}（反射），版本没有该方法的记 -1，
 * 此时以 F3 右上角显存为准。堆数据直接读 {@link Runtime} 与 GC MXBean，无需权限。</p>
 *
 * <p>只在 {@code ScopePipDebugGpuMem} 打开时工作，默认关闭。每 600 帧一次、预算封顶，
 * 稳态开销可忽略。</p>
 */
@Environment(EnvType.CLIENT)
public final class ScopePipResourceProbe {

    private static final int PULSE_FRAMES = 600;
    private static final int MAX_DEPTH = 3;
    private static final int NODE_BUDGET = 4000;

    private static int frameCounter = 0;
    private static long lastScopePasses = 0;
    private static long lastGcCount = -1;
    private static long lastGcTimeMs = -1;
    private static Method getMemorySizeMethod;
    private static boolean memorySizeResolved;
    private static long scopePassStartNs = 0;
    private static long lastScopeHeapLogFrame = -1000;

    private ScopePipResourceProbe() {
    }

    /** 镜内那一遍开始（renderScopeView 调用点）；只记时间，开销一次 nanoTime。 */
    public static void onScopePassBegin() {
        if (RenderConfig.SCOPE_PIP_DEBUG_GPU_MEM != null && RenderConfig.SCOPE_PIP_DEBUG_GPU_MEM.get()) {
            scopePassStartNs = System.nanoTime();
        }
    }

    /**
     * 镜内那一遍结束；每约 120 帧打一行「本次 pass 耗时 + 当前堆占用」，
     * 给出比 600 帧窗口均值更细的 (pass 耗时, heap) 配对曲线 —— 判「衰减是
     * GC 拖累还是 pass 自身变贵」就用这两列的走势。
     */
    public static void onScopePassEnd() {
        if (scopePassStartNs == 0) {
            return;
        }
        long start = scopePassStartNs;
        scopePassStartNs = 0;
        if (frameCounter - lastScopeHeapLogFrame < 120) {
            return;
        }
        lastScopeHeapLogFrame = frameCounter;
        Runtime runtime = Runtime.getRuntime();
        long heapUsedMiB = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
        GunMod.LOGGER.info("[TACZ Scope][probe] scopePass#{} took {}ms, heapUsedMiB={}",
                ScopePipRenderer.scopePassCount(), (System.nanoTime() - start) / 1_000_000L, heapUsedMiB);
    }

    /** 挂在 {@code GameRenderer#extract} HEAD（与瞄具其它帧首归零一起）。 */
    public static void beginFrame() {
        if (RenderConfig.SCOPE_PIP_DEBUG_GPU_MEM == null || !RenderConfig.SCOPE_PIP_DEBUG_GPU_MEM.get()) {
            return;
        }
        if (++frameCounter % PULSE_FRAMES != 0) {
            return;
        }
        long scopePasses = ScopePipRenderer.scopePassCount();
        int mapSize = IrisScopePipelineCompat.pipelineMapSize();
        try {
            Object scope = IrisScopePipelineCompat.scopePipeline();
            Object main = IrisScopePipelineCompat.mainPipeline();
            long scopeMiB = scope == null ? -1L : sumTextureBytes(scope) / (1024L * 1024L);
            long mainMiB = main == null ? -1L : sumTextureBytes(main) / (1024L * 1024L);
            Runtime runtime = Runtime.getRuntime();
            long heapUsedMiB = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
            long heapMaxMiB = runtime.maxMemory() / (1024L * 1024L);
            long gcCount = 0;
            long gcTimeMs = 0;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcCount += bean.getCollectionCount();
                gcTimeMs += bean.getCollectionTime();
            }
            long gcCountDelta = lastGcCount < 0 ? 0 : gcCount - lastGcCount;
            long gcTimeDeltaMs = lastGcTimeMs < 0 ? 0 : gcTimeMs - lastGcTimeMs;
            GunMod.LOGGER.info("[TACZ Scope][probe] frame={} scopePasses={} (delta={}) pipelineMapSize={} "
                            + "scopePipelineTextureMiB={} mainPipelineTextureMiB={} "
                            + "heapUsedMiB={} heapMaxMiB={} gcCountDelta={} gcTimeDeltaMs={}",
                    frameCounter, scopePasses, scopePasses - lastScopePasses, mapSize, scopeMiB, mainMiB,
                    heapUsedMiB, heapMaxMiB, gcCountDelta, gcTimeDeltaMs);
            lastGcCount = gcCount;
            lastGcTimeMs = gcTimeMs;
        } catch (Throwable t) {
            GunMod.LOGGER.warn("[TACZ Scope][probe] Pulse failed; probing is best-effort.", t);
        }
        lastScopePasses = scopePasses;
    }

    /** 深度受限的对象图遍历，把沿途所有 GpuTexture 的字节数加起来。 */
    private static long sumTextureBytes(Object root) {
        long total = 0;
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Object[]> queue = new ArrayDeque<>();
        queue.add(new Object[]{root, 0});
        visited.add(root);
        int budget = NODE_BUDGET;
        while (!queue.isEmpty() && budget-- > 0) {
            Object[] pair = queue.poll();
            Object obj = pair[0];
            int depth = (Integer) pair[1];
            if (obj instanceof GpuTexture texture) {
                long bytes = textureBytes(texture);
                if (bytes > 0) {
                    total += bytes;
                }
                continue;
            }
            if (depth >= MAX_DEPTH || isLeaf(obj)) {
                continue;
            }
            if (obj.getClass().isArray()) {
                int length = Array.getLength(obj);
                for (int i = 0; i < length && budget-- > 0; i++) {
                    Object item = Array.get(obj, i);
                    enqueue(item, depth, visited, queue);
                }
                continue;
            }
            Class<?> clazz = obj.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.getName().startsWith("this$")) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        enqueue(field.get(obj), depth, visited, queue);
                    } catch (Throwable ignored) {
                        // 单个字段读不出来只少一项计数。
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }
        return total;
    }

    private static void enqueue(Object value, int depth, Set<Object> visited, ArrayDeque<Object[]> queue) {
        if (value != null && !visited.contains(value)) {
            visited.add(value);
            queue.add(new Object[]{value, depth + 1});
        }
    }

    private static boolean isLeaf(Object o) {
        return o instanceof String || o instanceof Number || o instanceof Boolean
                || o instanceof Character || o instanceof Enum;
    }

    /** 反射取 {@code GpuTexture#getMemorySize()}；版本里没有就恒返回 -1。 */
    private static long textureBytes(GpuTexture texture) {
        if (!memorySizeResolved) {
            memorySizeResolved = true;
            try {
                getMemorySizeMethod = GpuTexture.class.getMethod("getMemorySize");
            } catch (Throwable ignored) {
                getMemorySizeMethod = null;
            }
        }
        if (getMemorySizeMethod == null) {
            return -1L;
        }
        try {
            return ((Number) getMemorySizeMethod.invoke(texture)).longValue();
        } catch (Throwable ignored) {
            return -1L;
        }
    }
}
