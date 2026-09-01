package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.IFunctionalSubmitter;
import com.tacz.guns.client.render.scope.ScopeRenderTypes;
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
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        PoseStack frozenPose = context.poseStack();
        frozenPose.mulPose(Axis.ZP.rotationDegrees(180f));
        int light = context.light();
        // 【镜内裁手】与枪口火光同一判据（extract 期）：瞄具的目镜序列在枪身遍历
        // 之前登记（BedrockGunModel#submit 先提交瞄具再 super.submit），此刻的闸门
        // 就是本帧的真实状态。闸门还带倍率下限（ScopePipMinMagnification，默认 4×）：
        // 低倍镜/组合镜的低倍档不裁 —— 没有镜内画面可让位，挖洞只会像破图。
        // 不满足时手臂走 vanilla entityTranslucent（现状）。
        boolean clipToScopeExterior = ScopeRenderTypes.viewmodelFxClipApplies();
        context.add(collector -> {
            PoseStack taskPose = new PoseStack();
            taskPose.last().pose().set(frozenPose.last().pose());
            taskPose.last().normal().set(frozenPose.last().normal());
            RenderHelper.renderFirstPersonArm(player, HumanoidArm.LEFT, taskPose, collector, light,
                    clipToScopeExterior);
        });
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay) {
        if (transformType.firstPerson()) {
            if (!bedrockGunModel.getRenderHand()) {
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
