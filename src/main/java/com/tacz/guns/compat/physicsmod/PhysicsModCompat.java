package com.tacz.guns.compat.physicsmod;

import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.lang.reflect.Method;

/**
 * 让 Physics Mod 的物理方块在「镜内那一遍」跟着用窄投影。
 *
 * <h2>它解决的症状</h2>
 * 二次渲染模式下，草、灯笼、门、旗帜这些<b>被 Physics Mod 接管的可动方块</b>
 * 在镜内<b>不跟着放大</b>，而且叠在放大后的画面之上，看着像一层错位的贴片。
 * 其余一切（地形、实体、Voxy LOD）都正常。
 *
 * <h2>为什么会这样：这是第 4 份独立的投影</h2>
 * Physics Mod 不读任何一处公共投影，它<b>自己存了一份</b>：
 * <pre>
 * MixinGameRenderer → LevelRendererAccessor.physicsmod$getMainRenderer()
 *                   → MainRenderer.storeProjectionMatrix(...)     每帧存一次
 * MixinLevelRenderer → MainRenderer.getStoredProjectionMatrix()   渲染时取用
 * </pre>
 * 而它存那一份的时机在 {@code GameRenderer} 里、<b>早于</b>我们的镜内那一遍，
 * 于是镜内那遍取到的仍是宽 FOV 的那份。
 *
 * <p>至此，一次镜内渲染要同步的投影共有<b>四处</b>，缺一处就有一类东西留在宽 FOV：
 * <ol>
 *   <li>{@code RenderSystem.setProjectionMatrix} —— 原版路径：实体、粒子、天空</li>
 *   <li>{@code sodium$getProjectionMatrix} —— Sodium 地形，<b>以及 Iris 的 gbuffer 投影</b></li>
 *   <li>{@code CameraRenderState.projectionMatrix} —— Voxy 的 LOD 地形</li>
 *   <li><b>本类</b> —— Physics Mod 的可动方块</li>
 * </ol>
 * 这条清单值得记住：每加一个接管渲染的 mod，就可能多一份要同步的投影。
 * 症状总是同一个形态 ——「某一类东西没跟着放大」。
 *
 * <p>全程反射，Physics Mod 不在时静默无操作；任何一步失败都只是这一类方块不跟随，
 * 不影响其余渲染。</p>
 */
@Environment(EnvType.CLIENT)
public final class PhysicsModCompat {

    private static final String MOD_ID = "physicsmod";
    private static final String ACCESSOR = "net.diebuddies.minecraft.LevelRendererAccessor";

    private static boolean resolved;
    private static boolean available;
    private static Method getMainRenderer;
    private static Method getStoredProjection;
    private static Method storeProjection;

    /** 覆盖期间保存的原值；{@code null} = 当前没有覆盖。 */
    private static Matrix4f savedProjection;

    private static boolean loggedFailure;

    private PhysicsModCompat() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
                return;
            }
            Class<?> accessor = Class.forName(ACCESSOR);
            getMainRenderer = accessor.getMethod("physicsmod$getMainRenderer");
            Class<?> mainRenderer = getMainRenderer.getReturnType();
            getStoredProjection = mainRenderer.getMethod("getStoredProjectionMatrix");
            // 形参是具体的 Matrix4f（不是 Matrix4fc），照签名取，别用 Matrix4fc 去 getMethod。
            storeProjection = mainRenderer.getMethod("storeProjectionMatrix", Matrix4f.class);
            available = true;
            GunMod.LOGGER.info("[TACZ Scope] Physics Mod detected; its movable blocks will follow the "
                    + "scope pass projection.");
        } catch (Throwable t) {
            available = false;
            logOnce("resolve Physics Mod renderer", t);
        }
    }

    /**
     * 把 Physics Mod 存着的那份投影换成窄投影。
     *
     * @return 是否真的换上了（换上了才需要调 {@link #restoreProjection()}）
     */
    public static boolean overrideProjection(Matrix4fc narrow) {
        resolve();
        if (!available || narrow == null) {
            return false;
        }
        try {
            Object main = mainRenderer();
            if (main == null) {
                return false;
            }
            Object current = getStoredProjection.invoke(main);
            if (!(current instanceof Matrix4f live)) {
                return false;
            }
            // 必须拷一份：getStoredProjectionMatrix 返回的是活对象本身，
            // 直接留着引用，等下面写进去之后「原值」就跟着变了，还原就成了空操作。
            savedProjection = new Matrix4f(live);
            storeProjection.invoke(main, new Matrix4f(narrow));
            return true;
        } catch (Throwable t) {
            savedProjection = null;
            logOnce("override Physics Mod projection", t);
            return false;
        }
    }

    /** 还原 {@link #overrideProjection} 换掉的那份投影。 */
    public static void restoreProjection() {
        if (savedProjection == null) {
            return;
        }
        Matrix4f restore = savedProjection;
        savedProjection = null;
        try {
            Object main = mainRenderer();
            if (main != null) {
                storeProjection.invoke(main, restore);
            }
        } catch (Throwable t) {
            logOnce("restore Physics Mod projection", t);
        }
    }

    private static Object mainRenderer() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.levelRenderer == null) {
            return null;
        }
        return getMainRenderer.invoke(mc.levelRenderer);
    }

    private static void logOnce(String what, Throwable t) {
        if (loggedFailure) {
            return;
        }
        loggedFailure = true;
        GunMod.LOGGER.warn("[TACZ Scope] Failed to {} — Physics Mod's movable blocks will not follow "
                + "the scope pass. Everything else is unaffected.", what, t);
    }
}
