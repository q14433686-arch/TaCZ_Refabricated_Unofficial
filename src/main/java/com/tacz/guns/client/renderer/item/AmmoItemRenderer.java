package com.tacz.guns.client.renderer.item;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.client.industry.icon.IndustryIconManager;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.TransformScale;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Supplier;

import static net.minecraft.world.item.ItemDisplayContext.GUI;


public class AmmoItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final SlotModel SLOT_AMMO_MODEL = new SlotModel();

    public static final Supplier<AmmoItemRenderer> INSTANCE = Suppliers.memoize(AmmoItemRenderer::new);

    public AmmoItemRenderer() {
    }

    private static void applyPositioningNodeTransform(List<BedrockPart> nodePath, PoseStack poseStack, Vector3f scale) {
        if (nodePath == null) {
            return;
        }
        if (scale == null) {
            scale = new Vector3f(1, 1, 1);
        }
        // 应用定位组的反向位移、旋转，使定位组的位置就是渲染中心
        poseStack.translate(0, 1.5, 0);
        for (int i = nodePath.size() - 1; i >= 0; i--) {
            BedrockPart t = nodePath.get(i);
            poseStack.mulPose(Axis.XN.rotation(t.xRot));
            poseStack.mulPose(Axis.YN.rotation(t.yRot));
            poseStack.mulPose(Axis.ZN.rotation(t.zRot));
            if (t.getParent() != null) {
                poseStack.translate(-t.x * scale.x() / 16.0F, -t.y * scale.y() / 16.0F, -t.z * scale.z() / 16.0F);
            } else {
                poseStack.translate(-t.x * scale.x() / 16.0F, (1.5F - t.y / 16.0F) * scale.y(), -t.z * scale.z() / 16.0F);
            }
        }
        poseStack.translate(0, -1.5, 0);
    }

    @Override
    public void render(ItemStack itemStack, ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay) {
        renderByItem(itemStack, itemDisplayContext, poseStack, collector, light, overlay);
    }

    public void renderByItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext transformType, @Nonnull PoseStack poseStack, @Nonnull SubmitNodeCollector collector, int pPackedLight, int pPackedOverlay) {
        if (!(stack.getItem() instanceof IAmmo iAmmo)) {
            return;
        }
        Identifier ammoId = iAmmo.getAmmoId(stack);
        poseStack.pushPose();
        TimelessAPI.getClientAmmoIndex(ammoId).ifPresentOrElse(ammoIndex -> {
            // 先获取 3D 模型，如果为空，统一使用 GUI 渲染
            BedrockAmmoModel ammoModel = ammoIndex.getAmmoModel();
            Identifier modelTexture = ammoIndex.getModelTextureLocation();
            // The normal TACZ slot icon remains the fallback. A client resource
            // pack can replace only the NBT identity -> texture mapping without
            // touching the gun pack's ballistic/index data or its 3D model.
            Identifier slotTexture = IndustryIconManager.INSTANCE.resolveTexture(stack)
                    .orElse(ammoIndex.getSlotTextureLocation());
            // GUI 特殊渲染
            if (transformType == GUI || ammoModel == null || modelTexture == null) {
                poseStack.translate(0.5, 1.5, 0.5);
                poseStack.mulPose(Axis.ZN.rotationDegrees(180));
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(slotTexture), (pose, buffer) -> {
                    // 26.2: 必须使用回调参数 pose（= 提交那一刻 poseStack.last().copy() 的快照），
                    // 而不是外层 poseStack —— 回调执行时它早已被 popPose/复用，
                    // 结果就是图标被画到错误位置（物品栏一片空白）。
                    PoseStack tacz$snapshotPose = new PoseStack();
                    tacz$snapshotPose.last().pose().set(pose.pose());
                    tacz$snapshotPose.last().normal().set(pose.normal());
                    SLOT_AMMO_MODEL.renderToBuffer(tacz$snapshotPose, buffer, pPackedLight, pPackedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
                });
                return;
            }
            // 剩下的渲染
            // 移动到模型原点
            poseStack.translate(0.5, 2, 0.5);
            // 反转模型
            poseStack.scale(-1, -1, 1);
            // 应用定位组的变换（位移和旋转，不包括缩放）
            applyPositioningTransform(transformType, ammoIndex.getTransform().getScale(), ammoModel, poseStack);
            // 应用 display 数据中的缩放
            applyScaleTransform(transformType, ammoIndex.getTransform().getScale(), poseStack);
            // 渲染子弹盒模型
            RenderType renderType = RenderTypes.entityCutout(modelTexture);
            ammoModel.submit(poseStack, transformType, collector, renderType, pPackedLight, pPackedOverlay);
        }, () -> {
            // 没有这个 ammoID，渲染个错误材质提醒别人
            poseStack.translate(0.5, 1.5, 0.5);
            poseStack.mulPose(Axis.ZN.rotationDegrees(180));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(MissingTextureAtlasSprite.getLocation()), (pose, buffer) -> {
                // 26.2: 必须使用回调参数 pose（= 提交那一刻 poseStack.last().copy() 的快照），
                // 而不是外层 poseStack —— 回调执行时它早已被 popPose/复用，
                // 结果就是图标被画到错误位置（物品栏一片空白）。
                PoseStack tacz$snapshotPose = new PoseStack();
                tacz$snapshotPose.last().pose().set(pose.pose());
                tacz$snapshotPose.last().normal().set(pose.normal());
                SLOT_AMMO_MODEL.renderToBuffer(tacz$snapshotPose, buffer, pPackedLight, pPackedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
            });
        });
        poseStack.popPose();
    }

    private void applyPositioningTransform(ItemDisplayContext transformType, TransformScale scale, BedrockAmmoModel model, PoseStack poseStack) {
        switch (transformType) {
            case FIXED -> applyPositioningNodeTransform(model.getFixedOriginPath(), poseStack, scale.getFixed());
            case GROUND -> applyPositioningNodeTransform(model.getGroundOriginPath(), poseStack, scale.getGround());
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND ->
                    applyPositioningNodeTransform(model.getThirdPersonHandOriginPath(), poseStack, scale.getThirdPerson());
        }
    }

    private void applyScaleTransform(ItemDisplayContext transformType, TransformScale scale, PoseStack poseStack) {
        if (scale == null) {
            return;
        }
        Vector3f vector3f = null;
        switch (transformType) {
            case FIXED -> vector3f = scale.getFixed();
            case GROUND -> vector3f = scale.getGround();
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> vector3f = scale.getThirdPerson();
        }
        if (vector3f != null) {
            poseStack.translate(0, 1.5, 0);
            poseStack.scale(vector3f.x(), vector3f.y(), vector3f.z());
            poseStack.translate(0, -1.5, 0);
        }
    }
}
