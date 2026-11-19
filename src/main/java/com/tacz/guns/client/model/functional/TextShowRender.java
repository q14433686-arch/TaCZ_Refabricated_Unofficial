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
     * 【镜内文字】true = 优先走目镜掩码裁剪管线（{@code ScopeTextSubmitter}），
     * 把文字约束在目镜投影内；掩码不可用时自动回退 vanilla submitText。
     *
     * <p>只有<b>瞄具</b>模型（{@code BedrockAttachmentModel}）用 true ——
     * 枪身上的文字（弹匣计数等）不在镜筒上，天然不该被目镜裁剪。</p>
     */
    private final boolean clipToScopeMask;

    public TextShowRender(BedrockModel bedrockModel, TextShow textShow, ItemStack gunStack) {
        this(bedrockModel, textShow, gunStack, false);
    }

    public TextShowRender(BedrockModel bedrockModel, TextShow textShow, ItemStack gunStack, boolean clipToScopeMask) {
        this.textShow = textShow;
        this.gunStack = gunStack;
        this.clipToScopeMask = clipToScopeMask;
    }

    @Override
    public void extract(ExtractionContext context) {
        if (!context.displayContext().firstPerson()) {
            return;
        }
        String text = PapiManager.getTextShow(textShow.getTextKey(), gunStack, textShow.getRangefinder());
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
        frozenPose.rotate(Axis.ZP.rotationDegrees(180f));
        frozenPose.scale(2 / 300f * scale, -2 / 300f * scale, -2 / 300f);
        var sequence = Component.literal(text).getVisualOrderText();
        boolean clip = this.clipToScopeMask;
        context.add(collector -> {
            PoseStack taskPose = new PoseStack();
            taskPose.last().pose().set(frozenPose.last().pose());
            taskPose.last().normal().set(frozenPose.last().normal());
            // 【镜内文字】瞄具文字优先走掩码裁剪管线：文字被约束在目镜投影内，
            // 不再穿出镜筒（MK5HD 弹药计数一案）。submit 返回 false 表示本帧
            // 掩码不可用（配置关闭/光影/target 失败），回退 vanilla 路径 ——
            // 行为退回「开镜门禁 + 可能溢出」的已验证现状，绝不丢字。
            if (clip && com.tacz.guns.client.render.scope.ScopeTextSubmitter.submit(
                    collector, taskPose, -xOffset, -font.lineHeight / 2f, sequence,
                    shadow, packedLight, color)) {
                return;
            }
            collector.submitText(taskPose, -xOffset, -font.lineHeight / 2f, sequence, shadow,
                    Font.DisplayMode.NORMAL, packedLight, color, 0, 0);
        });
    }

}
