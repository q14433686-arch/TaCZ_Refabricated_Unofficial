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
     * 最近 100ms 内是否有过 GUI（枪匠桌等）渲染标记。
     *
     * <p>公开给 poly_mesh 的语境闸门用（{@code TaczPolyMeshGunModel#isWorldGpuContext}）：
     * {@code FIXED}/{@code HEAD} 是双面语境 —— 世界里的展示框/雕像与枪匠桌 GUI 预览共用
     * 同一个 transformType，需要这个标记把后者挡在世界 GPU 表外。它是个时间戳窗口，
     * 代价只是「枪匠桌开着的瞬间世界雕像回退 collector」，比反向泄漏便宜得多。</p>
     */
    public static boolean isGuiRender() {
        return System.currentTimeMillis() - GUI_RENDER_TIMESTAMP < 100;
    }
}
