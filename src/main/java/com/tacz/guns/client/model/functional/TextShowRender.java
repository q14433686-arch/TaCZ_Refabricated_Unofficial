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
    /**
     * 是否把文字裁进目镜掩码（26.2 {@code 9d036594} 的 clipToScopeMask 旗，语义相同）。
     * 挂在瞄具 ocular 子树上的文字（弹药计数等）传 true：延迟覆盖层 flush 时走
     * {@link com.tacz.guns.client.render.scope.ScopeTextSubmitter} 的掩码管线，溢出圆孔的
     * 像素被孔径深度裁掉；枪身文字保持 false —— vanilla 管线按场景内容正常处理。
     */
    private final boolean clipToScopeMask;

    public TextShowRender(BedrockModel bedrockModel, TextShow textShow, ItemStack gunStack) {
        this(bedrockModel, textShow, gunStack, false);
    }

    public TextShowRender(BedrockModel bedrockModel, TextShow textShow, ItemStack gunStack,
                          boolean clipToScopeMask) {
        // Keep BedrockModel in the constructor contract used by gun-pack model registration. Text
        // itself is emitted as an immutable collector task and needs no mutable model reference.
        this.textShow = textShow;
        this.gunStack = gunStack;
        this.clipToScopeMask = clipToScopeMask;
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
        // 快照矩阵后进入任务：掩码路径（ScopeTextSubmitter）与 vanilla 路径共用同一冻结位姿。
        // 掩码不可用（本帧没有孔径拷贝周期）时 submit 返回 false，回退 vanilla submitText ——
        // 与 26.2 相同的失败语义：不丢字、不画错，最差回到「边缘可能溢出」的旧行为。
        PoseStack taskPose = new PoseStack();
        taskPose.last().pose().set(frozenPose.last().pose());
        taskPose.last().normal().set(frozenPose.last().normal());
        if (clipToScopeMask) {
            context.add(collector -> {
                if (!com.tacz.guns.client.render.scope.ScopeTextSubmitter.submit(collector,
                        taskPose, -xOffset, -font.lineHeight / 2f, sequence, shadow,
                        packedLight, color)) {
                    collector.submitText(taskPose, -xOffset, -font.lineHeight / 2f, sequence, shadow,
                            Font.DisplayMode.NORMAL, packedLight, color, 0, 0);
                }
            });
        } else {
            context.add(collector ->
                    collector.submitText(taskPose, -xOffset, -font.lineHeight / 2f, sequence, shadow,
                            Font.DisplayMode.NORMAL, packedLight, color, 0, 0));
        }
    }
}
