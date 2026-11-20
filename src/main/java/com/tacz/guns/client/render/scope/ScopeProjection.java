package com.tacz.guns.client.render.scope;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jetbrains.annotations.TestOnly;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 渲染线程上的"投影矩阵 → 上传内容"CPU 镜像（1.21.11 版）。
 *
 * <p><b>为什么需要：</b>测距仪落点预测框要把一条世界斜率换算成屏幕 NDC，必须知道<b>手部那一遍</b>
 * 实际绑定的投影矩阵（{@code GameRenderer#hud3dProjectionMatrixBuffer}，视野与近远裁剪面都和
 * 关卡投影不同；Iris 又会用自己那套手部投影）。1.21.11 的
 * {@code CachedPerspectiveProjectionMatrixBuffer} <b>不暴露</b>它算出的矩阵，只把 UBO 切片
 * 交给驱动；UBO 不可读，所以只能在它算出来的时候抄一份。</p>
 *
 * <p><b>与 26.2 的差异（刻意）：</b>26.2 的对应实现
 * （{@code ScopeProjection#uploaded(GpuBufferSlice, Matrix4fc)} + {@code ProjectionMatrixBuffer#writeBuffer}
 * 的 mixin）用"当前绑定的切片"反查矩阵 —— 那是给 26.2 掩码 pass 里读 {@code RenderSystem}
 * 绑定投影用的。本线的掩码走深度孔径路径，不需要那条反查，故这里按<b>缓冲区实例</b>索引：
 * {@code CachedPerspectiveProjectionMatrixBuffer} 只在宽/高/FOV 变化时重算，因此"该实例最近一次
 * 算出的矩阵"就是当帧手部投影，无需读回、不会驱动停顿、也不持有缓冲区所有权。</p>
 */
public final class ScopeProjection {
    private static final Map<Object, Matrix4f> MIRRORS = new IdentityHashMap<>();

    /** 由 {@code CachedPerspectiveProjectionMatrixBufferMixin} 在其 {@code createProjectionMatrix} 返回处调用。 */
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
        MIRRORS.clear();
    }

    /** 仅测试/诊断用：当前镜像条目数。 */
    @TestOnly
    public static int mirrorCount() {
        return MIRRORS.size();
    }

    private ScopeProjection() {
    }
}
