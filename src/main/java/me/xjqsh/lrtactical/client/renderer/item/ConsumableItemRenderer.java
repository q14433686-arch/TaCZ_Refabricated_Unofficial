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
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Supplier;

import static net.minecraft.world.item.ItemDisplayContext.GUI;

/**
 * 消耗品（药品 / 食物 / 针剂）的 Bedrock 模型 + 动画渲染。
 *
 * <p>结构与 {@link MeleeItemRenderer} <b>逐段平行</b>；渲染管线的全部要点
 * （{@code SubmitNodeCollector} 取代 {@code MultiBufferSource}、{@code submit} 取代
 * 已成 no-op 的 {@code render}、{@code RenderTypes} 的包名与复数、
 * {@code ItemTransform#apply} 的签名/语义变化、以及
 * {@code submitCustomGeometry} 回调必须使用参数 {@code pose} 的快照要求）
 * 都在那个类的注释里有完整论证，此处不重复。
 *
 * <p>与近战的唯一实质差别是动画上下文：消耗品要把
 * {@code using / usingTick} 喂给状态机，内容包脚本据此播放「拔盖 → 注射 → 收针」
 * 分段动画。这与官方 0.4.3 的 {@code ConsumableItemRenderer} 一致。
 *
 * <h2>无内容包时的行为</h2>
 * 与近战完全相同：第一人称直接 return 交回 vanilla，其余视角画
 * {@code MissingTextureAtlasSprite}。<b>本移植不打包任何美术资源</b>，
 * 所以默认走的就是这条路径 —— 物品 JSON 里的
 * {@code lrtactical:has_custom_display} 条件为假，压根不会进到这里。
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
        context.setPartialTicks(partialTick);
        context.setCurrentItem(stack);
        context.setUsing(player.isUsingItem());
        context.setUsingTick(player.getTicksUsingItem());
    }

    @Override
    @Nullable
    public Identifier getTextureLocation(ItemStack stack) {
        return LrTacticalAPI.getConsumableDisplay(stack)
                .map(ConsumableDisplayInstance::getTexture).orElse(null);
    }

    @Override
    @Nullable
    public LuaAnimationStateMachine<ConsumableAnimationStateContext> getStateMachine(ItemStack stack) {
        return LrTacticalAPI.getConsumableDisplay(stack)
                .map(ConsumableDisplayInstance::getStateMachine).orElse(null);
    }

    @Override
    @Nullable
    public CustomBedrockModel getModel(ItemStack stack) {
        return LrTacticalAPI.getConsumableDisplay(stack)
                .map(ConsumableDisplayInstance::getModel).orElse(null);
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

        // 逆转原版施加在手上的视角延滞，改为写入模型动画数据（与 MeleeItemRenderer 同款）。
        // 【不要】给这里的 xBob/yBob 再乘任何瞄准/倍率系数：官方就是未缩放的 *0.1，
        // 姊妹仓与本仓都各自试过按 ADS 缩放并被实测打回。
        float xRotOffset = Mth.lerp(partialTick, player.xBobO, player.xBob);
        float yRotOffset = Mth.lerp(partialTick, player.yBobO, player.yBob);
        float xRot = player.getViewXRot(partialTick) - xRotOffset;
        float yRot = player.getViewYRot(partialTick) - yRotOffset;
        poseStack.mulPose(Axis.XP.rotationDegrees(xRot * -0.1F));
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot * -0.1F));
        BedrockPart rootNode = model.getRootNode();
        if (rootNode != null) {
            // tanh 饱和限幅：防止快速转身时模型飞出画面
            xRot = (float) Math.tanh(xRot / 25) * 25;
            yRot = (float) Math.tanh(yRot / 25) * 25;
            rootNode.offsetX += yRot * 0.1F / 16F / 3F;
            rootNode.offsetY += -xRot * 0.1F / 16F / 3F;
            rootNode.additionalQuaternion.mul(Axis.XP.rotationDegrees(xRot * 0.05F));
            rootNode.additionalQuaternion.mul(Axis.YP.rotationDegrees(yRot * 0.05F));
        }

        // 从渲染原点 (0, 24, 0) 移动到模型原点 (0, 0, 0)
        poseStack.translate(0, 1.5f, 0);
        // 基岩版模型是上下颠倒的，需要翻转过来
        poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
        doExtraTransforms(poseStack, model, stack);

        // 只有第一人称才显示 1p_effect 组（与近战一致）
        model.setEffectVisible(true);
        model.submit(poseStack, ctx, collector, getRenderType(stack), light, OverlayTexture.NO_OVERLAY);
        model.setEffectVisible(false);

        // 渲染结束后清除动画变换，避免影响其他视角/其他实体手里的同一份模型
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
            // 差异说明见 MeleeItemRenderer#renderByItem 的注释
            poseStack.translate(0.5F, 0.5F, 0.5F);
            transforms.getTransform(ctx).apply(BlockTransformParser.isLeftHand(ctx), poseStack.last());
        }

        DisplayTransform.applyOffset(poseStack, display.getDisplayOffset());

        poseStack.translate(0.5, 1.5f, 0.5);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180f));

        RenderType renderType = RenderTypes.entityCutout(display.getTexture());
        model.submit(poseStack, ctx, collector, renderType, light, overlay);
        poseStack.popPose();
    }

    /** 见 {@link MeleeItemRenderer} 类注释：回调必须用参数 {@code pose} 而非外层 poseStack。 */
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
