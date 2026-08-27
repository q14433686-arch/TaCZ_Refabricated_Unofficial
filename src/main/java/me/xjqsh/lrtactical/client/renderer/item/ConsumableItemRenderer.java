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
 * 消耗品（药品 / 食物）的 Bedrock 模型 + Lua 动画渲染。结构与 {@link MeleeItemRenderer} 平行。
 *
 * <h2>为什么需要它</h2>
 * 本仓此前只有消耗品的<b>服务端</b>半边：{@code ConsumableItem} 的效果结算、
 * {@code data/lrtactical/index/consumable/*} 的索引、甚至
 * {@code assets/lrtactical/scripts/consumable_state_machine.lua} 都已就位，
 * 但没有任何渲染通道把它们接起来 —— 那份 Lua 一直是死代码，
 * 手里永远只显示原版占位模型。官方 0.4.3 提供了
 * {@code ConsumableItemRenderer} + {@code display/consumable} 通道，本轮同步补齐。
 *
 * <p>没装内容包时<b>行为与同步前完全一致</b>：{@code items/consumable.json} 用
 * {@code minecraft:condition} + {@code lrtactical:has_custom_display} 分流，
 * 条件为假就走原版占位模型，本类根本不会被调用。</p>
 *
 * <h2>与近战渲染器的差异（照实记录，不是遗漏）</h2>
 * <ul>
 *   <li>不做 {@code setEffectVisible(true/false)} 的成对开合 —— 那是近战
 *       {@code 1p_effect} 组（挥砍拖影）专用的，消耗品模型没有这一组；
 *       姊妹仓的对应实现同样没有。</li>
 *   <li>上下文用 {@link ConsumableAnimationStateContext}（多 using / usingTick），
 *       与官方 0.4.3 的方法名一致。</li>
 *   <li>26.2 管线差异（{@code SubmitNodeCollector} / {@code model.submit} /
 *       {@code RenderTypes} 带 s / {@code apply(isLeftHand, poseStack.last())}）
 *       与 {@link MeleeItemRenderer} 完全相同，详见该类注释，此处不重复。</li>
 * </ul>
 */
public class ConsumableItemRenderer
        extends AnimateGeoItemRenderer<CustomBedrockModel, ConsumableAnimationStateContext> {
    private static final SlotModel SLOT_MODEL = new SlotModel();

    /** 与近战/投掷物渲染器同款：全局单例，由 {@code ConsumableItem#getCustomRenderer} 取出。 */
    public static final Supplier<ConsumableItemRenderer> INSTANCE =
            Suppliers.memoize(ConsumableItemRenderer::new);

    @Override
    public ConsumableAnimationStateContext initContext(ItemStack stack, Player player, float partialTick) {
        ConsumableAnimationStateContext context = new ConsumableAnimationStateContext();
        this.updateContext(context, stack, player, partialTick);
        return context;
    }

    @Override
    public void updateContext(ConsumableAnimationStateContext context, ItemStack stack,
                              Player player, float partialTick) {
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
        // ConsumableData#getPutAwayTime() 返回 int、单位 tick（与近战同族），
        // 基类要毫秒 —— 故 ×50。注意投掷物那份数据单位已经是毫秒、【不能】乘，
        // 详见 ThrowableItemRendererWrapper 的类注释。
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

        // 从渲染原点 (0, 24, 0) 移动到模型原点，并翻转上下颠倒的基岩版模型
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
        // 26.2：Minecraft#getTimer() 已改名 getDeltaTracker()
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
            poseStack.translate(0.5F, 0.5F, 0.5F);
            transforms.getTransform(ctx).apply(BlockTransformParser.isLeftHand(ctx), poseStack.last());
        }

        // 官方 0.4.3 display_offset：位置理由见 MeleeItemRenderer#renderByItem 的同名注释
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
