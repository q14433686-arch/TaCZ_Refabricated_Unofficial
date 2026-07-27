package com.tacz.guns.client.renderer.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.entity.TargetMinecart;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.Optional;

/**
 * 标靶矿车渲染器。
 *
 * <p><b>第 8 轮修复：模型位置/朝向与碰撞箱对不上。</b></p>
 *
 * <p>上游 1.21.1 的写法是 {@code extends MinecartRenderer<TargetMinecart>}，
 * 只覆写 {@code renderMinecartContents(...)} —— 矿车的<b>位置插值、朝向（yRot/xRot）、
 * 沿轨道的姿态、受击摇晃</b>等全部由父类 {@code AbstractMinecartRenderer} 负责。</p>
 *
 * <p>移植时改成了直接 {@code extends EntityRenderer<TargetMinecart, 自定义 State>}，
 * 并在 {@code submit} 里手写 {@code translate + scale + 两个固定角度的 mulPose}。
 * 这就<b>丢掉了父类全部的定位与朝向逻辑</b>：</p>
 * <ul>
 *   <li>模型永远朝同一个方向（就是你观察到的"方向是固定的、疑似硬编码"）；</li>
 *   <li>模型不跟随矿车沿轨道的插值位置 —— 于是与碰撞箱错位
 *       （而碰撞箱与交互由服务端实体决定，所以是正确的）。</li>
 * </ul>
 *
 * <p>26.2 中 {@code AbstractMinecartRenderer} 仍在（javap 确认），
 * 只是渲染入口从 {@code render/renderMinecartContents} 改成了
 * {@code submit/submitMinecartContents}，且状态载体是 {@code MinecartRenderState}。
 * 这里恢复继承关系，仅覆写内容物提交，与上游语义一致。</p>
 */
@Environment(EnvType.CLIENT)
public class TargetMinecartRenderer extends AbstractMinecartRenderer<TargetMinecart, MinecartRenderState> {
    private static final String HEAD_NAME = "head";
    private static final String HEAD_2_NAME = "head2";

    /** 缓存本帧的 GameProfile：extractRenderState 阶段取，submit 阶段用。 */
    private static final String PROFILE_KEY = "tacz$profile";

    public TargetMinecartRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, ModelLayers.TNT_MINECART);
        this.shadowRadius = 0.25F;
    }

    @Override
    public MinecartRenderState createRenderState() {
        return new TargetMinecartRenderState();
    }

    /** 扩展 vanilla 状态，额外携带皮肤所需的 GameProfile。 */
    public static class TargetMinecartRenderState extends MinecartRenderState {
        public GameProfile gameProfile;
    }

    @Override
    public void extractRenderState(TargetMinecart entity, MinecartRenderState state, float partialTicks) {
        // 让父类填好位置、朝向、受击摇晃、沿轨道姿态等全部 vanilla 状态。
        super.extractRenderState(entity, state, partialTicks);
        if (state instanceof TargetMinecartRenderState targetState) {
            targetState.gameProfile = entity.getGameProfile();
        }
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.TARGET_MINECART_MODEL_LOCATION);
    }

    public Identifier getTextureLocation(TargetMinecart minecart) {
        return InternalAssetLoader.ENTITY_EMPTY_TEXTURE;
    }

    /**
     * 由父类在<b>已经套用完矿车位置/朝向</b>的 PoseStack 上调用。
     * 因此这里只需处理"车厢内容物"自身的局部变换，与上游 renderMinecartContents 一致。
     */
    @Override
    protected void submitMinecartContents(MinecartRenderState state,
                                          BlockModelRenderState blockModelRenderState,
                                          PoseStack stack,
                                          SubmitNodeCollector collector,
                                          int packedLight) {
        getModel().ifPresent(model -> {
            BedrockPart headModel = model.getNode(HEAD_NAME);
            BedrockPart head2Model = model.getNode(HEAD_2_NAME);
            if (headModel == null || head2Model == null) {
                return;
            }
            headModel.visible = false;
            head2Model.visible = false;

            stack.pushPose();
            // 局部变换与上游逐行一致（父类已处理世界位置与朝向）。
            stack.translate(0.5, 1.875, 0.5);
            stack.scale(1.5f, 1.5f, 1.5f);
            stack.mulPose(Axis.ZN.rotationDegrees(180));
            stack.mulPose(Axis.YN.rotationDegrees(90));

            RenderType renderType = RenderTypes.entityTranslucent(InternalAssetLoader.TARGET_MINECART_TEXTURE_LOCATION);
            model.submit(stack, ItemDisplayContext.NONE, collector, renderType, packedLight, OverlayTexture.NO_OVERLAY);

            GameProfile gameProfile = state instanceof TargetMinecartRenderState t ? t.gameProfile : null;
            if (gameProfile != null) {
                stack.translate(0, 1, -4.5 / 16d);
                Minecraft minecraft = Minecraft.getInstance();
                Identifier skin = minecraft.getSkinManager().createLookup(gameProfile, false).get().body().texturePath();
                RenderType skullRenderType = RenderTypes.entityTranslucent(skin);

                headModel.visible = true;
                collector.submitCustomGeometry(stack, skullRenderType, (entryPose, consumer) -> {
                    PoseStack working = new PoseStack();
                    working.last().pose().set(entryPose.pose());
                    working.last().normal().set(entryPose.normal());
                    headModel.render(working, ItemDisplayContext.NONE, consumer, packedLight, OverlayTexture.NO_OVERLAY);
                });

                head2Model.visible = true;
                stack.translate(0, 0, 0.01);
                collector.submitCustomGeometry(stack, skullRenderType, (entryPose, consumer) -> {
                    PoseStack working = new PoseStack();
                    working.last().pose().set(entryPose.pose());
                    working.last().normal().set(entryPose.normal());
                    head2Model.render(working, ItemDisplayContext.NONE, consumer, packedLight, OverlayTexture.NO_OVERLAY);
                });
            }
            stack.popPose();
        });
    }
}
