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

public class RightHandRender implements IFunctionalSubmitter {
    private final BedrockAnimatedModel bedrockGunModel;

    public RightHandRender(BedrockAnimatedModel bedrockGunModel) {
        this.bedrockGunModel = bedrockGunModel;
    }

    @Override
    public void extract(ExtractionContext context) {
        if (!context.displayContext().firstPerson() || !bedrockGunModel.getRenderHand()) {
            return;
        }
        // 【镜内排除手臂】开镜且目镜掩码生效时隐藏手臂。
        // 说明：vanilla 的 AvatarRenderer 手臂路径内部硬编码 entityTranslucent(skin)，
        // 无法在不动 vanilla 的前提下给手臂套上掩码裁剪 RenderType（那是后续需实机验证的 mixin）。
        // 这里用 mod 侧最稳的等效手段：开镜时直接不提交手臂，同样满足「镜内不出现手臂」。
        // 安全退化：未装瞄具/机瞄时掩码为全黑，本判定返回 false，手臂照常渲染。
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
            RenderHelper.renderFirstPersonArm(player, HumanoidArm.RIGHT, taskPose, collector, light);
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
                RenderHelper.renderFirstPersonArm(Minecraft.getInstance().player, HumanoidArm.RIGHT, poseStack2, light1);
            });
        }
    }
}
