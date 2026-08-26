package me.xjqsh.lrtactical.client.renderer.item;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.resource.pojo.display.block.BlockTransformParser;
import me.xjqsh.lrtactical.api.LrTacticalAPI;
import me.xjqsh.lrtactical.api.animation.ConsumableAnimationStateContext;
import me.xjqsh.lrtactical.client.renderer.JumpSwayUtil;
import me.xjqsh.lrtactical.client.renderer.model.CustomBedrockModel;
import me.xjqsh.lrtactical.client.resource.display.ConsumableDisplayInstance;
import me.xjqsh.lrtactical.client.resource.display.DisplayTransform;
import me.xjqsh.lrtactical.item.index.ConsumableIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Supplier;

import static net.minecraft.world.item.ItemDisplayContext.GUI;

/**
 * 消耗品的 Bedrock 模型 + 动画渲染。结构与 {@link MeleeItemRenderer} 平行；
 * 26.2 管线差异（{@code SubmitNodeCollector}、{@code model.submit}、{@code RenderTypes}、
 * {@code ItemTransforms} 新包名、左手 {@code applyLeftHandFix}、Fabric
 * {@code BuiltinItemRendererRegistry}）见该类注释。
 *
 * <p>与 {@link MeleeItemRenderer} 的差异：上下文换成
 * {@link ConsumableAnimationStateContext}（多了 using / usingTick，与官方 0.4.3 一致），
 * 且消耗品没有 {@code 1p_effect} 组，因此不调用 {@code model.setEffectVisible}。
 *
 * <p>无内容包时 {@code getModel(stack)} 返回 {@code null}：第一人称交回 vanilla，
 * 其他视角画 {@code MissingTextureAtlasSprite}。本移植不打包任何美术资源
 * （上游为 All Rights Reserved），默认走的就是「无 display」这条路径。
 */
public class ConsumableItemRenderer
        extends AnimateGeoItemRenderer<CustomBedrockModel, ConsumableAnimationStateContext> {
    private static final SlotModel SLOT_MODEL = new SlotModel();

    public static final Supplier<ConsumableItemRenderer> INSTANCE =
            Suppliers.memoize(ConsumableItemRenderer::new);

    @Override
    public ConsumableAnimationStateContext initContext(ItemStack stack, Player player, float partialTick) {
        ConsumableAnimationStateContext context = new ConsumableAnimationStateContext();
        this.updateContext(context, stack, player, partialTick);
        return context;
    }

    @Override
    public void updateContext(ConsumableAnimationStateContext context, ItemStack stack, Player player, float partialTick) {
        context.setCurrentItem(stack);
        context.setUsing(player.isUsingItem());
        context.setUsingTick(player.getTicksUsingItem());
        context.setPartialTicks(partialTick);
    }

    @Override
    @Nullable
    public Identifier getTextureLocation(ItemStack stack) {
        return LrTacticalAPI.getConsumableDisplay(stack).map(ConsumableDisplayInstance::getTexture).orElse(null);
    }

    @Override
    @Nullable
    public LuaAnimationStateMachine<ConsumableAnimationStateContext> getStateMachine(ItemStack stack) {
        return LrTacticalAPI.getConsumableDisplay(stack).map(ConsumableDisplayInstance::getStateMachine).orElse(null);
    }

    @Override
    @Nullable
    public CustomBedrockModel getModel(ItemStack stack) {
        return LrTacticalAPI.getConsumableDisplay(stack).map(ConsumableDisplayInstance::getModel).orElse(null);
    }

    @Override
    public long getPutAwayTime(ItemStack stack) {
        // 数据层的 putAwayTime 单位是 tick，基类要求毫秒 —— 故 ×50
        return LrTacticalAPI.getConsumableIndex(stack)
                .map(ConsumableIndex::getData)
                .map(data -> data.getPutAwayTime() * 50L)
                .orElse(0L);
    }

    @Override
    public void renderFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack,
                                  SubmitNodeCollector collector, int light, float partialTick) {
        CustomBedrockModel model = getModel(stack);
        if (model == null) {
            // 没有内容包提供的模型：交回 vanilla，不要画一个空壳
            return;
        }
        poseStack.pushPose();

        var stateMachine = getStateMachine(stack);
        if (stateMachine != null) {
            stateMachine.processContextIfExist(context -> updateContext(context, stack, player, partialTick));
            stateMachine.update();
        }

        // 逆转原版施加在手上的视角延滞，改为写入模型动画数据（与 MeleeItemRenderer 同款）
        float xRotOffset = Mth.lerp(partialTick, player.xBobO, player.xBob);
        float yRotOffset = Mth.lerp(partialTick, player.yBobO, player.yBob);
        float xRot = player.getViewXRot(partialTick) - xRotOffset;
        float yRot = player.getViewYRot(partialTick) - yRotOffset;
        poseStack.mulPose(Axis.XP.rotationDegrees(xRot * -0.1F));
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot * -0.1F));
        BedrockPart rootNode = model.getRootNode();
        if (rootNode != null) {
            xRot = (float) Math.tanh(xRot / 25) * 25;
            yRot = (float) Math.tanh(yRot / 25) * 25;
            rootNode.offsetX += yRot * 0.1F / 16F / 3F;
            rootNode.offsetY += -xRot * 0.1F / 16F / 3F;
            rootNode.additionalQuaternion.mul(Axis.XP.rotationDegrees(xRot * 0.05F));
            rootNode.additionalQuaternion.mul(Axis.YP.rotationDegrees(yRot * 0.05F));
        }

        // 从渲染原点 (0, 24, 0) 移动到模型原点 (0, 0, 0)，再翻转基岩版模型
        poseStack.translate(0, 1.5f, 0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
        doExtraTransforms(poseStack, model, stack);

        model.submit(poseStack, ctx, collector, getRenderType(stack), light, OverlayTexture.NO_OVERLAY);
        model.cleanAnimationTransform();
        poseStack.popPose();
    }

    @Override
    public void applyItemInHandCameraAnimation(BeforeRenderHandEvent event, ItemStack stack, float multiplier) {
        super.applyItemInHandCameraAnimation(event, stack, multiplier);
        // 摄像机动画数据到这里已消费完毕，清掉以免累积
        CustomBedrockModel model = this.getModel(stack);
        if (model != null) {
            model.cleanCameraAnimationTransform();
        }
    }

    @Override
    public void doExtraTransforms(PoseStack poseStack, CustomBedrockModel model, ItemStack stack) {
        super.doExtraTransforms(poseStack, model, stack);
        // 26.2：Minecraft#getTimer() 已改名 getDeltaTracker()（字节码确认）
        JumpSwayUtil.applyJumpingSway(model,
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack,
                             SubmitNodeCollector collector, int light, int overlay) {
        if (ctx.firstPerson()) {
            return;
        }
        ConsumableDisplayInstance display = LrTacticalAPI.getConsumableDisplay(stack).orElse(null);
        if (display == null) {
            submitSlotTexture(poseStack, collector, light, overlay, MissingTextureAtlasSprite.getLocation());
            return;
        }

        // GUI 用平面 slot 贴图，而不是把 3D 模型塞进 16×16 的槽位
        if (ctx == GUI && display.getSlotTexture() != null) {
            submitSlotTexture(poseStack, collector, light, overlay, display.getSlotTexture());
            return;
        }

        CustomBedrockModel model = display.getModel();
        if (model == null) {
            submitSlotTexture(poseStack, collector, light, overlay, MissingTextureAtlasSprite.getLocation());
            return;
        }

        poseStack.pushPose();
        ItemTransforms transforms = display.getTransforms();
        if (transforms != null && transforms != ItemTransforms.NO_TRANSFORMS) {
            // 26.2 与上游的三处差异（同 MeleeItemRenderer#renderByItem 的注释）
            poseStack.translate(0.5F, 0.5F, 0.5F);
            transforms.getTransform(ctx).apply(BlockTransformParser.isLeftHand(ctx), poseStack.last());
        }

        DisplayTransform.applyOffset(poseStack, display.getDisplayOffset());

        // 从渲染原点移动到模型原点，并翻转基岩版模型
        poseStack.translate(0.5, 1.5f, 0.5);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180f));

        RenderType renderType = RenderTypes.entityCutout(display.getTexture());
        model.submit(poseStack, ctx, collector, renderType, light, overlay);
        poseStack.popPose();
    }

    /**
     * 画一张 1×1 格的平面贴图（GUI 图标 / 缺资源提示）。
     *
     * <p><b>回调里必须用参数 {@code pose} 而不是外层 {@code poseStack}</b> —— 见
     * {@link MeleeItemRenderer} 类注释的「渲染快照」段。
     */
    private static void submitSlotTexture(PoseStack poseStack, SubmitNodeCollector collector,
                                          int light, int overlay, Identifier texture) {
        poseStack.pushPose();
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Axis.ZN.rotationDegrees(180));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture), (pose, buffer) -> {
            PoseStack snapshot = new PoseStack();
            snapshot.last().pose().set(pose.pose());
            snapshot.last().normal().set(pose.normal());
            SLOT_MODEL.renderToBuffer(snapshot, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        });
        poseStack.popPose();
    }
}
