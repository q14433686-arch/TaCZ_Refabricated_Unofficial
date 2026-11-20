package com.tacz.guns.client.model.listener.constraint;

import com.tacz.guns.api.client.animation.AnimationListener;
import com.tacz.guns.api.client.animation.AnimationListenerSupplier;
import com.tacz.guns.api.client.animation.ObjectAnimationChannel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.model.BonesItem;
import org.joml.Vector3f;

import javax.annotation.Nullable;

import static com.tacz.guns.client.model.BedrockAnimatedModel.CONSTRAINT_NODE;

public class ConstraintObject implements AnimationListenerSupplier {
    public Vector3f translationConstraint = new Vector3f(0, 0, 0);
    public Vector3f rotationConstraint = new Vector3f(0, 0, 0);
    /**
     * 约束节点（含根节点）；使用模型已转换的局部旋转。
     */
    public BedrockPart node;
    /**
     * 根约束使用原始 pivot 恢复位移；子节点则使用相对位移。
     */
    public BonesItem bonesItem;

    @Nullable
    @Override
    public AnimationListener supplyListeners(String nodeName, ObjectAnimationChannel.ChannelType type) {
        if (!nodeName.equals(CONSTRAINT_NODE)) {
            return null;
        }
        if (type.equals(ObjectAnimationChannel.ChannelType.ROTATION)) {
            return new ConstraintRotateListener(this);
        }
        if (type.equals(ObjectAnimationChannel.ChannelType.TRANSLATION)) {
            return new ConstraintTranslateListener(this);
        }
        return null;
    }
}
