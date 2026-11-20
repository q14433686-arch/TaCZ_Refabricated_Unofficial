package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Map;
import java.util.WeakHashMap;

/** Render-thread CPU mirrors of 26.2 projection uploads. Both vanilla and Iris upload
 * through ProjectionMatrixBuffer, but Iris uses a separate depth-scaled, bobbed hand
 * projection. The bound slice identifies which matrix the mask pass will actually use.
 * No readback (these UBOs aren't readable), no driver stall, no ownership of the buffers. */
public final class ScopeProjection {
    private static final Map<GpuBufferSlice, Matrix4f> MIRRORS = new WeakHashMap<>();

    public static void uploaded(GpuBufferSlice slice, Matrix4fc matrix) {
        MIRRORS.computeIfAbsent(slice, ignored -> new Matrix4f()).set(matrix);
    }

    static @Nullable Matrix4fc current() {
        return MIRRORS.get(RenderSystem.getProjectionMatrixBuffer());
    }

    private ScopeProjection() {}
}
