package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.IFunctionalSubmitter;
import com.tacz.guns.client.render.scope.ScopeClipHelper;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class LeftHandRender implements IFunctionalSubmitter {
    private final BedrockAnimatedModel bedrockGunModel;

    public LeftHandRender(BedrockAnimatedModel bedrockGunModel) {
        this.bedrockGunModel = bedrockGunModel;
    }

    @Override
    public void extract(ExtractionContext context) {
        if (!context.displayContext().firstPerson() || !bedrockGunModel.getRenderHand()) {
            return;
        }
        // 【镜内排除手臂】开镜且目镜掩码生效时隐藏手臂，见 RightHandRender 同名注释。
        if (ScopeClipHelper.isScopedMaskActive()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        PoseStack frozenPose = context.poseStack();
        frozenPose.mulPose(Axis.ZP.rotationDegrees(180f));
        int light = context.light();
        context.add(collector -> {
            PoseStack taskPose = new PoseStack();
            taskPose.last().pose().set(frozenPose.last().pose());
            taskPose.last().normal().set(frozenPose.last().normal());
            RenderHelper.renderFirstPersonArm(player, HumanoidArm.LEFT, taskPose, collector, light);
        });
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay) {
        if (transformType.firstPerson()) {
            if (!bedrockGunModel.getRenderHand()) {
                return;
            }
            // 与 extract 同一门禁：开镜 + 掩码生效时不提交手臂。
            if (ScopeClipHelper.isScopedMaskActive()) {
                return;
            }
            poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
            Matrix3f normal = new Matrix3f(poseStack.last().normal());
            Matrix4f pose = new Matrix4f(poseStack.last().pose());
            //和枪械模型共用顶点缓冲的都需要代理到渲染结束后渲染
            bedrockGunModel.delegateRender((poseStack1, vertexBuffer1, transformType1, light1, overlay1) -> {
                PoseStack poseStack2 = new PoseStack();
                poseStack2.last().normal().mul(normal);
                poseStack2.last().pose().mul(pose);
                RenderHelper.renderFirstPersonArm(Minecraft.getInstance().player, HumanoidArm.LEFT, poseStack2, light1);
            });
        }
    }
}
