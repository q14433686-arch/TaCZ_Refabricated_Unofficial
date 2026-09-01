package com.tacz.guns.compat.sodium;

import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.lang.reflect.Method;

/**
 * Sodium 兼容：同步它自己那份<b>地形投影矩阵快照</b>，并重开它的<b>区块 uniform 上传闸</b>。
 * （26.1.2 同名类的移植；纯反射对 Sodium 自有类，不含任何 Minecraft 版本 API，
 * 因此版本差异只可能来自 Sodium 本身的改名 —— 全部安静降级。）
 *
 * <h2>为什么光影隔离还非得带上它</h2>
 * Fabric 上 <b>Iris 硬依赖 Sodium</b>：能装光影就基本必然在跑 Sodium 的世界渲染通道。
 * 「镜内二次渲染」要用更窄的 FOV 再画一遍世界，原版路径靠 {@code RenderSystem.setProjectionMatrix(...)}
 * 就能改，但 <b>Sodium 不看它</b>：
 *
 * <pre>
 * Sodium GameRendererMixin
 *   &#64;WrapOperation ProjectionMatrixBuffer#getBuffer(Matrix4f)   // 在 renderLevel 里
 *       → this.projection.set(那个矩阵)                          // 存进自己的字段
 *   GameRendererStorage.sodium$getProjectionMatrix() → 该字段
 *
 * Sodium LevelRendererMixin
 *   new ChunkRenderMatrices(sodium$getProjectionMatrix(), modelView)
 *       → SodiumWorldRenderer.drawChunkLayer(...)                // 地形就用这份
 * </pre>
 *
 * 我们的窄遍传给 {@code RenderSystem.setProjectionMatrix} 的是<b>自建</b>
 * {@code PerspectiveProjectionMatrixBuffer} 实例的 slice —— 不经过 vanilla {@code renderLevel}
 * 里那个被 Sodium 包住的调用点，所以 Sodium 的快照纹丝不动。后果：<b>镜内地形用宽 FOV（快照）、
 * 原版实体用窄 FOV（槽位）</b>，两套比例糊在一起 —— 实机表现即「镜内实体相对镜内世界错位」。
 *
 * <h2>另一条：区块 uniform 的每帧一闸</h2>
 * {@code UniformBufferManager.update} 第一句是 {@code if (hasUpdatedThisFrame) return;}。一帧跑两遍
 * 世界渲染时<b>镜内那遍先到</b>：它带着窄矩阵上传成功并把闸关上；接着 vanilla 那遍带着正常矩阵再调
 * update —— <b>直接 return</b> ⇒ 主画面的 Sodium 地形继续用镜内那遍上传的 uniform（"镜内画面溢出到全屏"
 * 的真正病因）。所以两遍之间必须调一次 {@code prepareFrame()} 把闸重新打开。
 *
 * <h2>做法的分寸</h2>
 * {@code sodium$getProjectionMatrix()} 返回的是那个字段<b>本身</b>（声明类型 {@code Matrix4fc}，
 * 实际是可变的 {@code Matrix4f}），所以可以就地改写、用完写回。这是在动别人的内部状态，因此：
 * <ul>
 *   <li>全程反射 + {@code Throwable} 兜底，Sodium 不在 / 改名 / 换实现都只会安静降级；</li>
 *   <li>返回值不是 {@code Matrix4f}（哪天换成不可变实现）就直接放弃，不硬来；</li>
 *   <li>调用方必须 try/finally 保证 {@link #restoreProjection()} 一定执行。</li>
 * </ul>
 * 降级的后果是「镜内地形不跟着放大」——难看，但不崩。两条路径各有一次性日志，
 * 命中与否可由日志直接判定（与 Iris 侧同一套"把静默失效变成回执"的做法）。
 */
@Environment(EnvType.CLIENT)
public final class SodiumCompat {

    private static final String STORAGE_INTERFACE = "net.caffeinemc.mods.sodium.client.util.GameRendererStorage";
    private static final String WORLD_RENDERER = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer";
    private static final String GETTER = "sodium$getProjectionMatrix";

    private static boolean loggedFailure = false;
    private static boolean loggedSuccess = false;
    private static boolean loggedResetSuccess = false;
    private static boolean loggedResetFailure = false;

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
     * 把 Sodium 的地形投影快照临时换成 {@code narrow}。必须与 {@link #restoreProjection()} 成对使用。
     *
     * @return 真的改写成功才 true（false = Sodium 不在或反射失败，镜内地形将不跟随放大）
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
            logFailureOnce(t);
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
     * 只在两遍之间调一次；反射失败就安静降级（后果是回到"主画面地形沿用镜内 uniform"这个已知症状）。
     */
    public static void resetChunkUniformUpload() {
        if (!isLoaded()) {
            return;
        }
        try {
            Class<?> worldRendererClass = Class.forName(WORLD_RENDERER);
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
                        + "(this is what kept the main view on the scope pass's uniforms).");
            }
        } catch (Throwable t) {
            logResetFailureOnce(t);
        }
    }

    private static void logFailureOnce(String why) {
        if (loggedFailure) {
            return;
        }
        loggedFailure = true;
        GunMod.LOGGER.warn("[TACZ Scope] Sodium projection snapshot could not be synced ({}). The lens world "
                + "will stay at the wide FOV while entities follow the narrow one -- cosmetic, not fatal.", why);
    }

    private static void logFailureOnce(Throwable t) {
        if (loggedFailure) {
            return;
        }
        loggedFailure = true;
        GunMod.LOGGER.warn("[TACZ Scope] Sodium projection snapshot could not be synced; the lens world will "
                + "stay at the wide FOV while entities follow the narrow one (cosmetic, not fatal).", t);
    }

    private static void logResetFailureOnce(Throwable t) {
        if (loggedResetFailure) {
            return;
        }
        loggedResetFailure = true;
        GunMod.LOGGER.warn("[TACZ Scope] Could not reopen Sodium' chunk-uniform upload gate; the main view may "
                + "keep drawing terrain with the scope pass' uniforms this frame.", t);
    }
}
