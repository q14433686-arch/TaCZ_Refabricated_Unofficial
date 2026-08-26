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
import me.xjqsh.lrtactical.api.animation.ThrowableAnimationStateContext;
import me.xjqsh.lrtactical.client.renderer.JumpSwayUtil;
import me.xjqsh.lrtactical.client.renderer.model.CustomBedrockModel;
import me.xjqsh.lrtactical.client.resource.display.DisplayTransform;
import me.xjqsh.lrtactical.client.resource.display.ThrowableDisplayInstance;
import me.xjqsh.lrtactical.item.index.ThrowableIndex;
import me.xjqsh.lrtactical.item.throwable.ThrowableData;
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
 * 投掷物的 Bedrock 模型 + 动画渲染。
 *
 * <p>结构与 {@link MeleeItemRenderer} 平行，26.2 管线差异的完整对照表见该类注释，
 * 此处只说两点<b>本类特有</b>的东西。
 *
 * <h2>1. 上下文多了「使用状态」</h2>
 * 手雷的拔销 / 蓄力 / 投出是靠 {@code isUsing()} 与 {@code getUsingTick()} 驱动的，
 * 每帧都要从玩家身上重新读（{@code Player#isUsingItem} / {@code getTicksUsingItem}）。
 * 这也是投掷物必须有自己的 context 类型、不能共用 {@code BaseAnimationStateContext} 的原因。
 *
 * <h2>2. {@code putAwayTime} 单位与近战<b>不同</b>，不要「统一」它</h2>
 * <ul>
 *   <li>{@code MeleeWeaponData#getPutAwayTime()} 返回 {@code int}，单位 <b>tick</b>
 *       → {@code MeleeItemRenderer} 里要 {@code × 50L} 换成毫秒；</li>
 *   <li>{@code ThrowableData#getPutAwayTime()} 返回 {@code long}，单位<b>已经是毫秒</b>
 *       → 这里<b>直接返回</b>，再乘 50 就会变成 50 倍长的收起动画。</li>
 * </ul>
 * 上游两处正是这么写的（一处乘、一处不乘），看起来像笔误，实则是数据层单位不一致。
 * 移植时若「顺手统一」反而会引入 bug —— 故在此显式记录。
 */
public class ThrowableItemRendererWrapper
        extends AnimateGeoItemRenderer<CustomBedrockModel, ThrowableAnimationStateContext> {
    private static final SlotModel SLOT_MODEL = new SlotModel();

    public static final Supplier<ThrowableItemRendererWrapper> INSTANCE =
            Suppliers.memoize(ThrowableItemRendererWrapper::new);

    @Override
    public ThrowableAnimationStateContext initContext(ItemStack stack, Player player, float partialTick) {
        ThrowableAnimationStateContext context = new ThrowableAnimationStateContext();
        this.updateContext(context, stack, player, partialTick);
        return context;
    }

    @Override
    public void updateContext(ThrowableAnimationStateContext context, ItemStack stack, Player player, float partialTick) {
        context.setUsing(player.isUsingItem());
        context.setUsingTick(player.getTicksUsingItem());
        context.setPartialTicks(partialTick);
        context.setCurrentItem(stack);
    }

    @Override
    @Nullable
    public Identifier getTextureLocation(ItemStack stack) {
        return LrTacticalAPI.getThrowableDisplay(stack).map(ThrowableDisplayInstance::getTexture).orElse(null);
    }

    @Override
    @Nullable
    public LuaAnimationStateMachine<ThrowableAnimationStateContext> getStateMachine(ItemStack stack) {
        return LrTacticalAPI.getThrowableDisplay(stack).map(ThrowableDisplayInstance::getStateMachine).orElse(null);
    }

    @Override
    @Nullable
    public CustomBedrockModel getModel(ItemStack stack) {
        return LrTacticalAPI.getThrowableDisplay(stack).map(ThrowableDisplayInstance::getModel).orElse(null);
    }

    @Override
    public long getPutAwayTime(ItemStack stack) {
        // 见类注释：ThrowableData 的 putAwayTime 单位已经是毫秒，【不要】再乘 50
        return LrTacticalAPI.getThrowableIndex(stack)
                .map(ThrowableIndex::getData)
                .map(ThrowableData::getPutAwayTime)
                .orElse(0L);
    }

    @Override
    public void renderFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack,
                                  SubmitNodeCollector collector, int light, float partialTick) {
        CustomBedrockModel model = getModel(stack);
        if (model == null) {
            // 没有内容包提供的模型：交回 vanilla
            return;
        }
        poseStack.pushPose();

        var stateMachine = getStateMachine(stack);
        if (stateMachine != null) {
            stateMachine.processContextIfExist(context -> updateContext(context, stack, player, partialTick));
            stateMachine.update();
        }

        // 逆转原版施加在手上的视角延滞，改为写入模型动画数据
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

        // 从渲染原点 (0, 24, 0) 移动到模型原点 (0, 0, 0)
        poseStack.translate(0, 1.5f, 0);
        // 基岩版模型是上下颠倒的，需要翻转过来
        poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
        doExtraTransforms(poseStack, model, stack);

        // 第一人称：手里的手雷是「完整」的（拉环等 entity_hide 组要显示）
        model.setEntityRendering(false);
        model.submit(poseStack, ctx, collector, getRenderType(stack), light, OverlayTexture.NO_OVERLAY);

        model.cleanAnimationTransform();
        poseStack.popPose();
    }

    @Override
    public void applyItemInHandCameraAnimation(BeforeRenderHandEvent event, ItemStack stack, float multiplier) {
        super.applyItemInHandCameraAnimation(event, stack, multiplier);
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
        ThrowableDisplayInstance display = LrTacticalAPI.getThrowableDisplay(stack).orElse(null);
        if (display == null) {
            submitSlotTexture(poseStack, collector, light, overlay, MissingTextureAtlasSprite.getLocation());
            return;
        }

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
            // 26.2 三处差异见 MeleeItemRenderer#renderByItem 的注释
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
