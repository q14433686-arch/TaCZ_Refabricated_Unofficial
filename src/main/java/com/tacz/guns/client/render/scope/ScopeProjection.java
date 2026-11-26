package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Render-thread CPU mirrors of projection uploads. Two index spaces coexist:
 *
 * <p><b>切片索引</b>（{@link #uploaded}/{@link #current}）：按当前绑定的 {@link GpuBufferSlice}
 * 反查矩阵，供 1.21.11 深度孔径掩码 pass（{@link ScopeLensFrame#ocular}）读取当帧实际绑定的
 * 手部投影。Both vanilla and Iris upload through ProjectionMatrixBuffer, but Iris uses a
 * separate depth-scaled, bobbed hand projection. No readback (these UBOs aren't readable),
 * no driver stall, no ownership of the buffers.</p>
 *
 * <p><b>实例索引</b>（{@link #mirrored}/{@link #lastOf}/{@link #mirroredLevel}）：1.21.11 的
 * {@code CachedPerspectiveProjectionMatrixBuffer}（3D HUD/手部，矩阵自算且不暴露）与
 * {@code PerspectiveProjectionMatrixBuffer}（关卡，矩阵外部传入）是<b>两个类</b>；按缓冲区
 * 实例抄一份，分别供测距仪落点预测（手部）与散布/表面光源像素换算（关卡
 * {@link #levelProjectionM11}）使用。与 26.2 的 {@code CameraRenderState#projectionMatrix}
 * 差异见 {@link #mirroredLevel} 注释。</p> */
public final class ScopeProjection {
    /** 按上传切片索引的镜像（掩码 pass 用当前绑定切片反查）。 */
    private static final Map<GpuBufferSlice, Matrix4f> SLICE_MIRRORS = new WeakHashMap<>();
    /** 按缓冲区实例索引的镜像（1.21.11 版，替代 26.2 的切片反查）。 */
    private static final Map<Object, Matrix4f> MIRRORS = new IdentityHashMap<>();
    /** 最近一次写进 {@code PerspectiveProjectionMatrixBuffer} 的矩阵 —— 也就是<b>关卡投影</b>。 */
    private static @Nullable Matrix4f levelProjection;

    public static void uploaded(GpuBufferSlice slice, Matrix4fc matrix) {
        SLICE_MIRRORS.computeIfAbsent(slice, ignored -> new Matrix4f()).set(matrix);
    }

    public static @Nullable Matrix4fc current() {
        return SLICE_MIRRORS.get(RenderSystem.getProjectionMatrixBuffer());
    }

    /**
     * 由 {@link com.tacz.guns.mixin.client.ScopeProjectionUploadMixin} 在
     * {@code PerspectiveProjectionMatrixBuffer#getBuffer} 处调用。
     *
     * <p>26.2 的 {@code CameraRenderState#projectionMatrix} 字段在 1.21.11 的
     * {@code CameraRenderState} 上不存在（那个版本把投影留在渲染系统里，只把 UBO 切片交给驱动）。
     * 本线有三个消费点要用它的 {@code m11()}：散布像素换算（{@code GunSpread}）、表面光源的
     * 像素级间隙（{@code SurfaceLights}）、测距仪落点预测的斜率投影（{@code ImpactMarkerRender}）。
     * 这三处都发生在关卡提取/渲染期间，此时 {@code GameRenderer} 刚把当帧的关卡投影交给
     * {@code PerspectiveProjectionMatrixBuffer}（手部投影走的是另一个类
     * {@code CachedPerspectiveProjectionMatrixBuffer}），所以镜像无歧义。</p>
     */
    public static void mirroredLevel(Object buffer, Matrix4fc matrix) {
        mirrored(buffer, matrix);
        if (levelProjection == null) {
            levelProjection = new Matrix4f(matrix);
        } else {
            levelProjection.set(matrix);
        }
    }

    /** 当帧关卡投影的 {@code m11()}；从未写过则为 0（调用方的除法都有下限保护）。 */
    public static float levelProjectionM11() {
        return levelProjection == null ? 0.0f : levelProjection.m11();
    }

    /** 由 {@link com.tacz.guns.mixin.client.CachedPerspectiveProjectionMixin} 在其 {@code createProjectionMatrix} 返回处调用。 */
    public static void mirrored(Object buffer, Matrix4fc matrix) {
        Matrix4f mirror = MIRRORS.get(buffer);
        if (mirror == null) {
            MIRRORS.put(buffer, new Matrix4f(matrix));
        } else {
            mirror.set(matrix);
        }
    }

    /** 该缓冲区最近一次算出的投影矩阵；从未算过则为 {@code null}。 */
    public static @Nullable Matrix4fc lastOf(Object buffer) {
        return MIRRORS.get(buffer);
    }

    /** 资源重载/退出世界时丢弃镜像，避免持有旧矩阵。 */
    public static void clear() {
        SLICE_MIRRORS.clear();
        MIRRORS.clear();
        levelProjection = null;
    }

    private ScopeProjection() {}
}
