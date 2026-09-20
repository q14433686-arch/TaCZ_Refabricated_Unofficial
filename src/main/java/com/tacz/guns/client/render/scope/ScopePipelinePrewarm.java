package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 瞄具自定义管线【预热器】（2026-09-20，光影开镜崩溃一案）。
 *
 * <h2>它防的是哪个崩溃</h2>
 * 我们的 scope 管线（镜身/准星/裁字/火光/mesh 等十几条）是<b>类加载时静态构建</b>的，
 * 进 vanilla 编译缓存的时机是【首次使用】。26.3 的按需编译不是即用的：
 * {@code RenderSystem#getCompiledPipelineNullable} 在缓存 miss 或异步编译未完成时
 * 返回 {@code null} —— 而 Iris 26.3 挂在该方法的
 * {@code redirectIrisProgram} 处理器<b>不做 null 检查</b>，直接
 * {@code cir.getReturnValue().backendRenderPipeline()} —— 光影开启后第一次开镜，
 * 第一次 scope draw（在 {@code HandRenderer#renderSolid} 里）必撞
 * {@code NullPointerException: Cannot invoke FrontendRenderPipeline.backendRenderPipeline()}
 * （2026-09-20 实机崩溃日志，RenderSystem.java:581）。
 *
 * <h2>修法</h2>
 * 在客户端 tick（主线程、任何 render pass 之外）周期性地对【我们的全部自定义管线】
 * 调用 {@code getCompiledPipeline}，让 vanilla 提前把它们编进缓存。
 * 开镜那一刻缓存必热，Iris 处理器永远拿不到 null。
 * 冷热抖动（资源重载/换 shaderpack 会把缓存翻掉）最多在 1 tick 内补回。
 *
 * <h2>为什么必须带 Iris bypass</h2>
 * 预热调用本身就走在开光影的状态里 —— 同一个 Iris 处理器也会拦这发调用，
 * 首次 {@code getCompiledPipeline} 的 miss 会【当场】触发同款 NPE
 * （它连「先查有没有 override」都排在取 oldProgram 之后）。因此每发调用都用
 * Iris 自己的 {@code ImmediateState.bypass} 开关包一层：置位期间 Iris 处理器
 * 直接放行，vanilla 编译路径自己跑完。该字段是 Iris 的 public static，
 * 但为防未来改名/删字段，一切反射都做了缺失降级（拿不到就裸调，行为退化为
 * 「仅无光影安全」）。
 */
@Environment(EnvType.CLIENT)
public final class ScopePipelinePrewarm {

    /** 上次快照与本次不同的逐管线状态：OK / PENDING（miss，编译中）/ FAILED（抛异常）。 */
    private static final Map<RenderPipeline, String> LAST_STATE = new LinkedHashMap<>();
    /** 失败退避：同一条管线失败后隔 200 tick 再试（防止每 tick 重编刷屏+卡帧）。 */
    private static final Map<RenderPipeline, Long> FAILED_BACKOFF = new LinkedHashMap<>();
    private static long tickCount = 0;

    private static boolean bypassFieldChecked = false;
    @Nullable
    private static Field bypassField;

    private ScopePipelinePrewarm() {
    }

    /**
     * 客户端 tick 入口（{@code TaCZFabricClient} 注册在 {@code END_CLIENT_TICK}）。
     */
    public static void tick(@Nullable Minecraft mc) {
        if (mc == null || mc.level == null) {
            return;
        }
        tickCount++;
        ScopeBodyRenderTypes.prewarmCompiledPipelines();
        ScopeTextRenderTypes.prewarmCompiledPipelines();
        ScopeMaskRenderer.prewarmCompiledPipelines();
        ScopePipRenderer.prewarmCompiledPipelines();
        cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer.prewarmCompiledPipelines();
        logStateChangeIfAny();
    }

    /**
     * 编译（或确认已编译）单条管线。由各持管线的类在自己的
     * {@code prewarmCompiledPipelines()} 里逐条调用。
     */
    public static void touch(@Nullable RenderPipeline pipeline) {
        if (pipeline == null) {
            return;
        }
        Long backoffUntil = FAILED_BACKOFF.get(pipeline);
        if (backoffUntil != null && tickCount < backoffUntil) {
            return;
        }
        boolean bypassToggled = enterIrisBypass();
        try {
            // var：getCompiledPipeline 的确切返回类型（CompiledRenderPipeline /
            // BackendRenderPipeline）随版本漂移过，var 写法天然免疫。
            var compiled = RenderSystem.getCompiledPipeline(pipeline);
            setState(pipeline, compiled != null ? "OK" : "PENDING");
        } catch (Throwable t) {
            // 编译硬失败（比如着色器源缺失 —— 崩溃日志里见过的
            // "Couldn't find source for VERTEX shader" 一族）。
            // 交给退避机制，不打断其他管线。
            FAILED_BACKOFF.put(pipeline, tickCount + 200L);
            setState(pipeline, "FAILED (" + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()).lines().findFirst().orElse("") + ")");
        } finally {
            exitIrisBypass(bypassToggled);
        }
    }

    private static void setState(RenderPipeline pipeline, String state) {
        LAST_STATE.put(pipeline, state);
    }

    /**
     * 只在「快照变化」时打一行小结 —— 常态全 OK 不打；有管线从 OK 变成
     * PENDING/FAILED（资源重载抖掉缓存）或恢复时各打一行。
     */
    private static void logStateChangeIfAny() {
        if (LAST_STATE.isEmpty()) {
            return;
        }
        StringBuilder bad = new StringBuilder();
        int ok = 0;
        for (Map.Entry<RenderPipeline, String> e : LAST_STATE.entrySet()) {
            String state = e.getValue();
            if ("OK".equals(state)) {
                ok++;
            } else {
                if (bad.length() > 0) {
                    bad.append("; ");
                }
                bad.append(e.getKey().getLocation()).append(" -> ").append(state);
            }
        }
        String signature = ok + "|" + bad;
        if (!signature.equals(lastSignature)) {
            lastSignature = signature;
            if (bad.isEmpty()) {
                GunMod.LOGGER.info("[TACZ Scope] Pipeline prewarm: all {} custom pipelines compiled.", ok);
            } else {
                GunMod.LOGGER.warn("[TACZ Scope] Pipeline prewarm: {} ok, outstanding: {}", ok, bad);
            }
        }
    }

    private static String lastSignature = null;

    private static boolean enterIrisBypass() {
        Field f = bypassField();
        if (f == null) {
            return false;
        }
        try {
            if (!f.getBoolean(null)) {
                f.setBoolean(null, true);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void exitIrisBypass(boolean toggled) {
        if (!toggled) {
            return;
        }
        Field f = bypassField();
        if (f == null) {
            return;
        }
        try {
            f.setBoolean(null, false);
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    private static Field bypassField() {
        if (!bypassFieldChecked) {
            bypassFieldChecked = true;
            try {
                // net.irisshaders.iris.vertices.ImmediateState#bypass（public static boolean，
                // 26.3 Iris 源码实读确认存在；redirectIrisProgram 的第一道开关就是它）。
                Class<?> cls = Class.forName("net.irisshaders.iris.vertices.ImmediateState");
                Field f = cls.getDeclaredField("bypass");
                f.setAccessible(true);
                bypassField = f;
            } catch (Throwable t) {
                GunMod.LOGGER.info("[TACZ Scope] Iris ImmediateState.bypass not found; pipeline prewarm will run without the bypass (crash-safe only with shaders off).");
            }
        }
        return bypassField;
    }
}
