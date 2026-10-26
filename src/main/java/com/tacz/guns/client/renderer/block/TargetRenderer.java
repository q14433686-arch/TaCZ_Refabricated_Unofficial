package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.GunMod;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.block.entity.TargetBlockEntity;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class TargetRenderer implements BlockEntityRenderer<TargetBlockEntity, TargetRenderer.TargetRenderState> {
    private static final String UPPER_NAME = "target_upper";
    private static final String HEAD_NAME = "head";

    /**
     * 原版头颅用的同一个皮肤缓存：内部按档案异步解析（{@code ResolvableProfile} → 皮肤），
     * 解析未完成或解析失败时直接给默认皮肤，调用方不需要任何兜底逻辑。
     */
    private final PlayerSkinRenderCache skinRenderCache;
    /** 皮肤解析失败只在客户端日志里报一次，避免每帧刷屏。 */
    private boolean skinFailureLogged;

    public TargetRenderer(BlockEntityRendererProvider.Context context) {
        this.skinRenderCache = context.playerSkinRenderCache();
    }

    /** Custom render state for target block entity */
    public static class TargetRenderState extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public float rot;
        public float oRot;
        /** 主人档案（可能只有名字）：{@code null} 表示这个标靶没有主人，不画头。 */
        public ResolvableProfile profile;
    }

    @Override
    public TargetRenderState createRenderState() {
        return new TargetRenderState();
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.TARGET_MODEL_LOCATION);
    }

    @Override
    public void extractRenderState(TargetBlockEntity blockEntity, TargetRenderState state, float partialTick, Vec3 cameraPos, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(blockEntity, state, crumblingOverlay);
        BlockState blockState = blockEntity.getBlockState();
        state.facing = blockState.getValue(TargetBlock.FACING);
        state.rot = blockEntity.rot;
        state.oRot = blockEntity.oRot;
        // 只把「档案」带进渲染状态，皮肤解析推迟到 submit（这一步每帧都会跑，
        // 任何阻塞式的查询都会卡住渲染线程 —— 那正是矿车标靶崩溃的同款问题）。
        state.profile = blockEntity.getOwnerProfile();
    }

    /**
     * 头像皮肤的渲染类型 —— 完全走原版的 {@link PlayerSkinRenderCache}。
     *
     * <p>它就是一个「档案 → 皮肤」的异步缓存：命中缓存直接返回；没命中时立刻返回
     * <b>默认皮肤</b>并在后台按名字/UUID 解析，解析完成后下一帧自动换成真皮肤。
     * 原版头颅（{@code SkullBlockRenderer}）用的就是这一条路径，所以我们不再需要
     * 自己判断「有没有 UUID」「要不要查会话服务」。</p>
     *
     * <p>兜底：任何意外都不允许崩渲染线程，退回缓存自带的默认皮肤渲染类型，
     * 并在客户端日志里留一条 WARN（只报一次，不是静默降级）。</p>
     */
    private RenderType skinRenderType(ResolvableProfile profile) {
        try {
            return this.skinRenderCache.getOrDefault(profile).renderType();
        } catch (RuntimeException e) {
            if (!this.skinFailureLogged) {
                this.skinFailureLogged = true;
                GunMod.LOGGER.warn("[TaCZ] Failed to resolve the target head skin for {}: {}, "
                        + "falling back to the default skin", profile, e.toString());
            }
            return PlayerSkinRenderCache.DEFAULT_PLAYER_SKIN_RENDER_TYPE;
        }
    }

    @Override
    public void submit(TargetRenderState state, PoseStack poseStack, SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {
        getModel().ifPresent(model -> {
            int combinedLightIn = state.lightCoords;
            int combinedOverlayIn = OverlayTexture.NO_OVERLAY;
            // partialTick is not available in submit; use 1.0f (latest tick state)
            float deg = -state.rot;

            BedrockPart headModel = model.getNode(HEAD_NAME);
            BedrockPart upperModel = model.getNode(UPPER_NAME);
            upperModel.xRot = (float) Math.toRadians(deg);
            headModel.visible = false;

            poseStack.pushPose();
            poseStack.translate(0.5, 0.225, 0.5);
            poseStack.mulPose(Axis.YN.rotationDegrees(state.facing.get2DDataValue() * 90));
            poseStack.mulPose(Axis.ZN.rotationDegrees(180));
            poseStack.translate(0, -1.275, 0.0125);
            RenderType renderType = RenderTypes.entityTranslucent(InternalAssetLoader.TARGET_TEXTURE_LOCATION);
            model.submit(poseStack, ItemDisplayContext.NONE, collector, renderType, combinedLightIn, combinedOverlayIn);

            if (state.profile != null) {
                poseStack.translate(0, 1.25, 0);
                poseStack.mulPose(Axis.XP.rotationDegrees(deg));
                headModel.visible = true;
                RenderType skullRenderType = this.skinRenderType(state.profile);
                // Use submitCustomGeometry to render the head part with a VertexConsumer
                collector.submitCustomGeometry(poseStack, skullRenderType, (entryPose, consumer) -> {
                    PoseStack working = new PoseStack();
                    working.last().pose().set(entryPose.pose());
                    working.last().normal().set(entryPose.normal());
                    headModel.render(working, ItemDisplayContext.NONE, consumer, combinedLightIn, combinedOverlayIn);
                });
            }
            poseStack.popPose();
        });
    }

    @Override
    public int getViewDistance() {
        return RenderConfig.TARGET_RENDER_DISTANCE.get();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}