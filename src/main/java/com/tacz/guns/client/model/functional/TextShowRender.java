package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.IFunctionalSubmitter;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.papi.PapiManager;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class TextShowRender implements IFunctionalSubmitter {
    private final BedrockModel bedrockModel;
    private final TextShow textShow;
    private final ItemStack gunStack;

    public TextShowRender(BedrockModel bedrockModel, TextShow textShow, ItemStack gunStack) {
        this.bedrockModel = bedrockModel;
        this.textShow = textShow;
        this.gunStack = gunStack;
    }

    @Override
    public void extract(ExtractionContext context) {
        if (!context.displayContext().firstPerson()) {
            return;
        }
        String text = PapiManager.getTextShow(textShow.getTextKey(), gunStack);
        if (StringUtils.isBlank(text)) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        boolean shadow = textShow.isShadow();
        int color = textShow.getColorInt();
        float scale = textShow.getScale();
        int packedLight = LightCoordsUtil.pack(textShow.getTextLight(), textShow.getTextLight());
        int width = font.width(text);
        int xOffset = switch (textShow.getAlign()) {
            case CENTER -> width / 2;
            case RIGHT -> width;
            default -> 0;
        };

        PoseStack frozenPose = context.poseStack();
        frozenPose.mulPose(Axis.ZP.rotationDegrees(180f));
        frozenPose.scale(2 / 300f * scale, -2 / 300f * scale, -2 / 300f);
        var sequence = Component.literal(text).getVisualOrderText();
        context.add(collector -> {
            PoseStack taskPose = new PoseStack();
            taskPose.last().pose().set(frozenPose.last().pose());
            taskPose.last().normal().set(frozenPose.last().normal());
            collector.submitText(taskPose, -xOffset, -font.lineHeight / 2f, sequence, shadow,
                    Font.DisplayMode.NORMAL, packedLight, color, 0, 0);
        });
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay) {
        if (!transformType.firstPerson()) {
            return;
        }
        String text = PapiManager.getTextShow(textShow.getTextKey(), gunStack);
        if (StringUtils.isBlank(text)) {
            return;
        }
        // 【2026-08-11 更正】此前这里的 TODO 写「文字显示未实现，待重写」——是错的，
        // 本审计按调用链核实：文字显示【已经实现】，在上方 extract() 里经
        // collector.submitText(...) 提交（26.2 的 OrderedSubmitNodeCollector 有
        // submitText(PoseStack,F,F,FormattedCharSequence,Z,Font$DisplayMode,I,I,I,I)V，
        // 字节码已验证签名逐参一致）。本 render(...) 是旧即时渲染链的复写，
        // 整条旧链（BedrockModel.renderInto/delegateRenderers）在 26.2 已无任何调用方，
        // 本方法永远不会被执行，保持空实现即可。
    }
}
