package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.IFunctionalSubmitter;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.papi.PapiManager;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;

public class TextShowRender implements IFunctionalSubmitter {
    private final TextShow textShow;
    private final ItemStack gunStack;

    public TextShowRender(BedrockModel bedrockModel, TextShow textShow, ItemStack gunStack) {
        // Keep BedrockModel in the constructor contract used by gun-pack model registration. Text
        // itself is emitted as an immutable collector task and needs no mutable model reference.
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
}
