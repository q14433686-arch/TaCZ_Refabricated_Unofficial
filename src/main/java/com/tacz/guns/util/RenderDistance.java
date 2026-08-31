package com.tacz.guns.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class RenderDistance {
    private static long GUI_RENDER_TIMESTAMP = -1L;

    public static boolean inRenderHighPolyModelDistance(PoseStack poseStack) {
        if (isGuiRender()) {
            return true;
        }
        int distance = RenderConfig.GUN_LOD_RENDER_DISTANCE.get();
        if (distance <= 0) {
            return false;
        }
        Matrix4f matrix4f = poseStack.last().pose();
        float viewDistance = matrix4f.m30() * matrix4f.m30() + matrix4f.m31() * matrix4f.m31() + matrix4f.m32() * matrix4f.m32();
        return viewDistance < distance * distance;
    }

    public static void markGuiRenderTimestamp() {
        GUI_RENDER_TIMESTAMP = System.currentTimeMillis();
    }

    /**
     * 「最近 100ms 内渲染过 GUI」的时间戳标记（枪匠桌 / GUI 预览语境的粗粒度标记）。
     *
     * <p>26.1.2 起改为 public：poly_mesh 世界 GPU 路径（{@code TaczPolyMeshGunModel}
     * 的 {@code isWorldGpuContext}）需要按它把 {@code FIXED}/{@code HEAD} 这两个双面语境
     * 里的「GUI 内嵌预览」那半拒收在 WORLD_DRAWS 表外 —— 与 1211 分支的用法一致
     * （{@code ScreenRenderTracker} 拦的是 Screen 提取窗口，枪匠桌这种 100ms 标记语境
     * 只能靠这里）。</p>
     */
    public static boolean isGuiRender() {
        return System.currentTimeMillis() - GUI_RENDER_TIMESTAMP < 100;
    }
}
