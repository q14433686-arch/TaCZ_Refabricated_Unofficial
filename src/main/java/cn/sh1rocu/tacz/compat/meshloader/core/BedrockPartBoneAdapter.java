package cn.sh1rocu.tacz.compat.meshloader.core;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockPart;

import java.util.ArrayList;
import java.util.List;

/**
 * 活的 {@link BedrockPart} 适配器：动画每帧改写变换，submit 时读当帧值。
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public class BedrockPartBoneAdapter implements IPolyMeshBone {

    private final BedrockPart part;
    private List<IPolyMeshBone> cachedChildren;

    public BedrockPartBoneAdapter(BedrockPart part) {
        this.part = part;
    }

    @Override public String getName()        { return part.name == null ? "" : part.name; }
    @Override public float getPivotX()       { return part.x; }
    @Override public float getPivotY()       { return part.y; }
    @Override public float getPivotZ()       { return part.z; }
    @Override public float getRotX()         { return part.xRot; }
    @Override public float getRotY()         { return part.yRot; }
    @Override public float getRotZ()         { return part.zRot; }
    @Override public float getScaleX()       { return part.xScale == 0 ? 1f : part.xScale; }
    @Override public float getScaleY()       { return part.yScale == 0 ? 1f : part.yScale; }
    @Override public float getScaleZ()       { return part.zScale == 0 ? 1f : part.zScale; }
    @Override public boolean isVisible()     { return part.visible; }
    @Override public boolean isIlluminated() { return part.illuminated; }

    @Override
    public List<? extends IPolyMeshBone> getChildren() {
        if (cachedChildren != null) {
            return cachedChildren;
        }
        cachedChildren = new ArrayList<>();
        if (part.children != null) {
            for (BedrockPart child : part.children) {
                cachedChildren.add(new BedrockPartBoneAdapter(child));
            }
        }
        return cachedChildren;
    }

    @Override
    public void applyTransform(PoseStack poseStack) {
        part.translateAndRotateAndScale(poseStack);
    }
}
