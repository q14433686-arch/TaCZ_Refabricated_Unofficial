package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.textures.GpuTexture;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisScopePipelineCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 【诊断】按帧脉冲统计瞄具/主管线各自持有的<b>GPU 纹理字节数</b>。
 *
 * <h2>为什么需要它 —— 姊妹分支探针没覆盖的那一格</h2>
 * 「光影下开镜帧率随时间衰减、重进存档重置」这个症状（{@code ScopePipIsolatePipeline=true}），
 * 姊妹分支已经把 CPU 侧能数的都数过了：管线身份是否每帧重建、pipelinesPerDimension 大小、
 * 激活 SSBO 数量、Blaze3D 保留集合、SodiumWorldRenderer 集合 —— 全部无果。
 * 而「显存侧」从来没有被量化过：每 scope pass 若在瞄具管线里留下一点 GPU 资源
 * （新纹理/新缓冲/新图像），CPU 侧的任何结构计数都看不见它，只有字节数会说话。
 *
 * <p>衰减的观感也与显存压力吻合：帧率一路下滑到 ~7 的地板、重进存档（Iris
 * destroyPipeline 整套释放）立刻重置、空闲时不复原（占着就是占着）。</p>
 *
 * <h2>它数什么</h2>
 * 反射走一遍瞄具管线 / 主管线的对象图（深度受限、身份去重），
 * 把所有 {@link GpuTexture} 的 {@code getMemorySize()} 加起来。每 600 帧打一行：
 * scope pass 累计次数、管线表大小、两套管线的纹理字节数。
 *
 * <p>判读：scopePasses 单调涨、scopePipelineTextureMiB 也跟着单调涨 ⇒ 瞄具管线在
 * 逐 pass 累积 GPU 纹理 ⇒ 根因坐实；两者不相关则转向主管线 / 驱动层。</p>
 *
 * <p>若运行环境没有 {@code GpuTexture#getMemorySize}（方法反射拿不到），字节数记 -1，
 * 此时请配合 F3 右上角的显存占用目测。</p>
 *
 * <p>只在 {@code ScopePipDebugGpuMem} 打开时工作，默认关闭。每 600 帧一次、预算封顶，
 * 稳态开销可忽略。</p>
 */
@Environment(EnvType.CLIENT)
public final class ScopePipGpuMemoryProbe {

    private static final int PULSE_FRAMES = 600;
    private static final int MAX_DEPTH = 3;
    private static final int NODE_BUDGET = 4000;

    private static int frameCounter = 0;
    private static long lastScopePasses = 0;
    private static Method getMemorySizeMethod;
    private static boolean memorySizeResolved;

    private ScopePipGpuMemoryProbe() {
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
            GunMod.LOGGER.info("[TACZ Scope][gpu-probe] frame={} scopePasses={} (delta={}) pipelineMapSize={} "
                            + "scopePipelineTextureMiB={} mainPipelineTextureMiB={}",
                    frameCounter, scopePasses, scopePasses - lastScopePasses, mapSize, scopeMiB, mainMiB);
        } catch (Throwable t) {
            GunMod.LOGGER.warn("[TACZ Scope][gpu-probe] Pulse failed; probing is best-effort.", t);
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
