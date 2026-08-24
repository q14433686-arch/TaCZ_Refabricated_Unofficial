package cn.sh1rocu.tacz.compat.meshloader.core;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockPart;

import java.util.ArrayList;
import java.util.List;

/**
 * TacZ {@link BedrockPart} → {@link IPolyMeshBone} 共享适配器。
 *
 * <p>持有的是活的 BedrockPart 引用：动画每帧改写其变换，
 * submit 时的快照采集读到的是当帧实时值。子骨骼列表在模型加载后
 * 固定不变，缓存以避免重复构建。</p>
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
