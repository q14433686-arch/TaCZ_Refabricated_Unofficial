package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalSubmitter;
import com.tacz.guns.client.render.scope.ScopeRenderTypes;
import com.tacz.guns.client.renderer.item.AttachmentItemRenderer;
import com.tacz.guns.util.RenderDistance;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;


public class AttachmentRender implements IFunctionalSubmitter {
    private final BedrockGunModel bedrockGunModel;
    private final AttachmentType type;

    public AttachmentRender(BedrockGunModel bedrockGunModel, AttachmentType type) {
        this.bedrockGunModel = bedrockGunModel;
        this.type = type;
    }



    public static void submitAttachment(ItemStack attachmentItem,
                                        ItemStack gunItem,
                                        PoseStack poseStack,
                                        ItemDisplayContext transformType,
                                        SubmitNodeCollector collector,
                                        int light,
                                        int overlay) {
        poseStack.translate(0, -1.5, 0);
        if (!(attachmentItem.getItem() instanceof IAttachment iAttachment)) {
            return;
        }
        Identifier attachmentId = iAttachment.getAttachmentId(attachmentItem);
        TimelessAPI.getClientAttachmentIndex(attachmentId).ifPresentOrElse(attachmentIndex -> {
            BedrockAttachmentModel model = attachmentIndex.getAttachmentModel();
            Identifier texture = attachmentIndex.getModelTexture();
            if (model != null && texture != null) {
                Pair<BedrockAttachmentModel, Identifier> lodModel = attachmentIndex.getLodModel();
                if (lodModel != null && !RenderDistance.inRenderHighPolyModelDistance(poseStack) && !transformType.firstPerson()) {
                    model = lodModel.getLeft();
                    texture = lodModel.getRight();
                }
                RenderType renderType = RenderTypes.entityCutout(texture);
                // The scope itself reaches this call before it marks the aperture, so it keeps its
                // dedicated depth-body sequence. Non-scope attachments are traversed afterwards and
                // use the same screen-space outside mask as the gun body when the aperture is active.
                renderType = ScopeRenderTypes.clipForViewmodel(renderType, texture,
                        transformType != null && transformType.firstPerson());
                model.submit(attachmentItem, gunItem, poseStack, transformType, collector,
                        renderType, texture, light, overlay);
            }
        }, () -> collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(MissingTextureAtlasSprite.getLocation()),
                (pose, buffer) -> AttachmentItemRenderer.SLOT_ATTACHMENT_MODEL.renderToBuffer(
                        poseStack, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F
                )
        ));
    }

    @Override
    public void extract(ExtractionContext context) {
        ItemStack attachmentItem = bedrockGunModel.getCurrentAttachmentItem().get(type);
        if (attachmentItem == null || attachmentItem.isEmpty()) {
            return;
        }
        ItemStack frozenAttachment = attachmentItem.copy();
        ItemStack frozenGun = bedrockGunModel.getCurrentGunItem().copy();
        PoseStack frozenPose = context.poseStack();
        ItemDisplayContext displayContext = context.displayContext();
        int light = context.light();
        int overlay = context.overlay();
        context.add(collector -> {
            PoseStack taskPose = new PoseStack();
            taskPose.last().pose().set(frozenPose.last().pose());
            taskPose.last().normal().set(frozenPose.last().normal());
            submitAttachment(frozenAttachment, frozenGun, taskPose, displayContext, collector, light, overlay);
        });
    }

    /**
     * 【r44】legacy VertexConsumer 路径，在 26.2 <b>已无实际作用</b>，故清空实现。
     *
     * <p>它原先经 {@code bedrockGunModel.delegateRender(...)} 把配件渲染排到枪械模型之后。
     * 但 26.2 的 {@code BedrockModel#submit}（现行路径）里，{@code delegateRenderers}
     * 是被<b>直接清空、从不执行</b>的（该方法自带注释说明：legacy delegate renderer
     * 无法安全地从 VertexConsumer 回调里提交嵌套 RenderType）。
     * 唯一还会消费 delegate 的 {@code renderInto(...)} 属于旧 render 链，
     * 而该链的入口 {@code BedrockGunModel#render} 已随本轮清理一并删除。</p>
     *
     * <p>配件的实际渲染走 {@link #submitAttachment}，由 {@code IFunctionalCollectorRenderer}
     * 的 {@code submit(...)} 驱动 —— 见本类上方那个方法。</p>
     *
     * <p>保留空实现是因为本方法是 {@code IFunctionalRenderer} 的接口约定，
     * 直接删掉会破坏实现关系。</p>
     */
    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay) {
        // no-op：见上方 javadoc。配件渲染统一走 submitAttachment。
    }
}
