package cn.sh1rocu.tacz.compat.meshloader.api;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import java.util.List;

/**
 * poly_mesh 骨骼抽象。实现类包装 TacZ 的 {@code BedrockPart}，
 * 使解析层不依赖 Bedrock 内部类型。
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public interface IPolyMeshBone {

    String getName();

    float getPivotX();

    float getPivotY();

    float getPivotZ();

    float getRotX();

    float getRotY();

    float getRotZ();

    default float getScaleX() {
        return 1f;
    }

    default float getScaleY() {
        return 1f;
    }

    default float getScaleZ() {
        return 1f;
    }

    boolean isVisible();

    default boolean isIlluminated() {
        return false;
    }

    List<? extends IPolyMeshBone> getChildren();

    /**
     * 默认实现按 pivot/rot/scale 套变换。适配器应改走
     * {@code BedrockPart.translateAndRotateAndScale()}，与立方体路径一致。
     */
    default void applyTransform(PoseStack poseStack) {
        poseStack.translate(getPivotX() / 16.0, getPivotY() / 16.0, getPivotZ() / 16.0);
        if (getRotZ() != 0f) {
            poseStack.mulPose(Axis.ZP.rotation(getRotZ()));
        }
        if (getRotY() != 0f) {
            poseStack.mulPose(Axis.YP.rotation(getRotY()));
        }
        if (getRotX() != 0f) {
            poseStack.mulPose(Axis.XP.rotation(getRotX()));
        }
        float sx = getScaleX(), sy = getScaleY(), sz = getScaleZ();
        if (sx != 1f || sy != 1f || sz != 1f) {
            poseStack.scale(sx, sy, sz);
        }
    }
}
