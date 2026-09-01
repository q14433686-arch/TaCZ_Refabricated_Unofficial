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
     * 最近 100ms 内是否有 GUI（枪匠桌等）标记过渲染时间戳。
     *
     * <p>公开给 meshloader 的近距离全模豁免用：FIXED/HEAD 语境既出现在
     * 世界（展示台雕像、物品展示框、背枪）也出现在 GUI 预览（枪匠桌界面），
     * 只有非 GUI 的那一侧才允许按相机距离豁免顶点预算——否则高模会被
     * 全量画进 GUI 图标。</p>
     */
    public static boolean isGuiRender() {
        return System.currentTimeMillis() - GUI_RENDER_TIMESTAMP < 100;
    }
}
