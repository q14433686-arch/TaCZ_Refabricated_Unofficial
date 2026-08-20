package com.tacz.guns.compat.sodium;

import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Sodium 兼容：同步它自己那份地形投影矩阵快照。
 *
 * <h2>为什么需要这个</h2>
 * 「镜内二次渲染」要用一个更窄的 FOV 再画一遍世界。原版路径靠
 * {@code RenderSystem.setProjectionMatrix(...)} 就能改，但 <b>Sodium 不看它</b>：
 *
 * <pre>
 * Sodium GameRendererMixin
 *   @WrapOperation ProjectionMatrixBuffer#getBuffer(Matrix4f)   // 在 renderLevel 里
 *       → this.projection.set(那个矩阵)                          // 存进自己的字段
 *   GameRendererStorage.sodium$getProjectionMatrix() → 该字段
 *
 * Sodium LevelRendererMixin
 *   new ChunkRenderMatrices(sodium$getProjectionMatrix(), modelView)
 *       → SodiumWorldRenderer.drawChunkLayer(...)                // 地形就用这份
 * </pre>
 *
 * 第一版 PIP 只改了 {@code RenderSystem} 那一份，于是<b>地形用宽 FOV、
 * 原版路径的实体用窄 FOV</b>，镜内两套比例叠在一起 —— 这正是当时实测到的
 * 「部分物件放大、大部分方块没放大，画面糊在一起」。
 *
 * <h2>做法</h2>
 * {@code sodium$getProjectionMatrix()} 返回的是那个字段<b>本身</b>（声明类型
 * {@code Matrix4fc}，实际对象是可变的 {@code Matrix4f}），所以可以就地改写，
 * 用完再写回原值。这是在动别人的内部状态，因此：
 * <ul>
 *   <li>全程反射 + {@code Throwable} 兜底，Sodium 不在、改名、换实现都只会安静降级；</li>
 *   <li>返回值不是 {@code Matrix4f}（比如哪天换成不可变实现）就直接放弃，不硬来；</li>
 *   <li>调用方必须用 try/finally 保证 {@link #restoreProjection} 一定执行。</li>
 * </ul>
 * 降级的后果是「镜内地形不跟着放大」，与第一版的表现一致 —— 难看，但不崩。
 */
@Environment(EnvType.CLIENT)
public final class SodiumCompat {

    private static final String STORAGE_INTERFACE = "net.caffeinemc.mods.sodium.client.util.GameRendererStorage";
    private static final String GETTER = "sodium$getProjectionMatrix";

    private static boolean loggedFailure = false;
    private static boolean loggedSuccess = false;

    /** 改写前的原值备份；{@code null} 表示本次没有改写成功，restore 时不必做事。 */
    @Nullable
    private static Matrix4f patchedField;
    private static final Matrix4f SAVED = new Matrix4f();

    private SodiumCompat() {
    }

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded("sodium");
    }

    /**
     * 把 Sodium 的地形投影快照临时换成 {@code narrow}。
     *
     * <p>必须与 {@link #restoreProjection()} 成对使用（try/finally）。
     *
     * @return 是否真的改写成功（false = Sodium 不在或反射失败，镜内地形将不跟随放大）
     */
    public static boolean overrideProjection(Matrix4fc narrow) {
        patchedField = null;
        if (!isLoaded()) {
            return false;
        }
        try {
            Object gameRenderer = Minecraft.getInstance().gameRenderer;
            Class<?> storage = Class.forName(STORAGE_INTERFACE);
            if (!storage.isInstance(gameRenderer)) {
                logFailureOnce("GameRenderer does not implement " + STORAGE_INTERFACE);
                return false;
            }
            Method getter = storage.getMethod(GETTER);
            Object current = getter.invoke(gameRenderer);
            if (!(current instanceof Matrix4f live)) {
                // 声明类型是 Matrix4fc；若哪天真的换成不可变实现，就地改写不再成立。
                logFailureOnce(GETTER + " did not return a mutable Matrix4f");
                return false;
            }
            SAVED.set(live);
            live.set(narrow);
            patchedField = live;
            if (!loggedSuccess) {
                loggedSuccess = true;
                GunMod.LOGGER.info("[TACZ Scope] Sodium terrain projection is being synced for the scope pass.");
            }
            return true;
        } catch (Throwable t) {
            logFailureOnce("reflection failed: " + t);
            return false;
        }
    }

    /** 还原 {@link #overrideProjection} 改写过的快照。没改写成功时是空操作。 */
    public static void restoreProjection() {
        if (patchedField != null) {
            patchedField.set(SAVED);
            patchedField = null;
        }
    }

    /**
     * 让 Sodium 相信「本帧还没上传过区块 uniform」，好让紧随其后的 vanilla 那一遍重新上传。
     *
     * <h2>这修的是什么 —— 也是「溢出」的真正病因</h2>
     * {@code UniformBufferManager.update} 的第一句就是一道<b>每帧只做一次</b>的闸：
     * <pre>
     * public void update(ChunkRenderMatrices m, FogParameters fog) {
     *     if (this.hasUpdatedThisFrame) return;     // ← 字节码偏移 0-7
     *     this.hasUpdatedThisFrame = true;
     *     ... 把 m.projection() 上传到 uniform buffer ...
     * }
     * public void prepareFrame() { this.hasUpdatedThisFrame = false; }
     * </pre>
     * 一帧里跑两遍世界渲染时，<b>镜内那一遍先到</b>：它带着窄 FOV 矩阵调 update，
     * 上传成功并把闸关上。接着 vanilla 那一遍带着正常矩阵再调 update ——
     * <b>直接 return</b>。于是主画面里 Sodium 的地形<b>全部用镜内那份窄投影绘制</b>。
     *
     * <p>这就是长期被当成「镜内画面溢出到镜外」的东西的真相：<b>根本不是溢出</b>。
     * 没有任何像素跑出镜片，是<b>主画面自己的地形被用错了投影</b>画出来，
     * 于是近处的水和冰柱被拉伸放大，而远处由 Voxy LOD 负责的地形（另一套 uniform 通路）
     * 完好无损 —— 用户截图里「远山正常、旁边的冰柱却是放大的」正是这个签名。
     *
     * <p>之前所有「谁把画面画到主 target 上」的排查方向从一开始就找错了对象：
     * 渲染目标的重定向<b>一直是对的</b>（诊断日志里镜内那一遍全程解析到离屏 target，
     * 一次 MAIN 都没有）。错的是投影矩阵的<b>上传时机</b>。
     *
     * <p>解法就是在两遍之间调一次 {@code prepareFrame()} 把闸重新打开。
     * 该方法是 public 的；拿到实例要走 {@code SodiumWorldRenderer.instanceNullable()}
     * 再反射取 private 的 {@code uniformBufferManager} 字段。
     * 反射失败就安静降级 —— 后果是回到当前这个已知症状，不会更糟。
     */
    public static void resetChunkUniformUpload() {
        if (!isLoaded()) {
            return;
        }
        try {
            Class<?> worldRendererClass = Class.forName(
                    "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
            Object worldRenderer = worldRendererClass.getMethod("instanceNullable").invoke(null);
            if (worldRenderer == null) {
                return;
            }
            java.lang.reflect.Field field = worldRendererClass.getDeclaredField("uniformBufferManager");
            field.setAccessible(true);
            Object manager = field.get(worldRenderer);
            if (manager == null) {
                return;
            }
            manager.getClass().getMethod("prepareFrame").invoke(manager);
            if (!loggedResetSuccess) {
                loggedResetSuccess = true;
                GunMod.LOGGER.info("[TACZ Scope] Sodium chunk uniforms will be re-uploaded for the main pass "
                        + "(this is what kept the main view on the scope's projection).");
            }
        } catch (Throwable t) {
            logResetFailureOnce(t);
        }
    }

    private static boolean loggedResetSuccess = false;
    private static boolean loggedResetFailure = false;

    private static void logResetFailureOnce(Throwable t) {
        if (loggedResetFailure) {
            return;
        }
        loggedResetFailure = true;
        GunMod.LOGGER.warn("[TACZ Scope] Could not reset Sodium's per-frame chunk uniform flag ({}). "
                + "The main view's terrain will keep rendering with the scope's projection.", t.toString());
    }

    private static void logFailureOnce(String detail) {
        if (loggedFailure) {
            return;
        }
        loggedFailure = true;
        GunMod.LOGGER.warn("[TACZ Scope] Could not sync Sodium's terrain projection ({}). "
                + "The scope's second render will show terrain at the wrong magnification.", detail);
    }
}
