package cn.sh1rocu.tacz.compat.meshloader.api;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import java.util.List;

/**
 * poly_mesh 骨骼抽象接口。
 *
 * <p>实现类直接包装 TacZ 的 {@code BedrockPart}（见 {@code BedrockPartBoneAdapter}），
 * 使 {@code PolyMeshModel} 不依赖 TacZ 内部实现。26.2 移植版与上游一致：
 * 每帧 submit 时读取骨骼的实时变换并冻结成快照。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)，包名与注释本地化。</p>
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

    /** illuminated 后缀（或祖先 illuminated 传播）的骨骼会以满亮度绘制。 */
    default boolean isIlluminated() {
        return false;
    }

    List<? extends IPolyMeshBone> getChildren();

    /**
     * 把本骨骼的变换应用到 PoseStack 上。
     * 适配器实现应委托给 {@code BedrockPart.translateAndRotateAndScale()}，
     * 以保证与立方体路径完全一致。
     */
    default void applyTransform(PoseStack poseStack) {
        poseStack.translate(getPivotX() / 16.0, getPivotY() / 16.0, getPivotZ() / 16.0);
        if (getRotZ() != 0f) poseStack.mulPose(Axis.ZP.rotation(getRotZ()));
        if (getRotY() != 0f) poseStack.mulPose(Axis.YP.rotation(getRotY()));
        if (getRotX() != 0f) poseStack.mulPose(Axis.XP.rotation(getRotX()));
        float sx = getScaleX(), sy = getScaleY(), sz = getScaleZ();
        if (sx != 1f || sy != 1f || sz != 1f) poseStack.scale(sx, sy, sz);
    }
}
