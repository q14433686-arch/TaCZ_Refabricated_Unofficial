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
import me.xjqsh.lrtactical.api.animation.BaseAnimationStateContext;
import me.xjqsh.lrtactical.client.renderer.JumpSwayUtil;
import me.xjqsh.lrtactical.client.renderer.model.CustomBedrockModel;
import me.xjqsh.lrtactical.client.resource.display.MeleeDisplayInstance;
import me.xjqsh.lrtactical.item.index.MeleeWeaponIndex;
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
 * 近战武器的 Bedrock 模型 + 动画渲染。
 *
 * <h2>26.2 移植：这不是「逐行照搬」，管线本身变了</h2>
 * 先前的审计（{@code docs/COMPAT_AND_ROADMAP.md} 第七节）结论是「11 个依赖类全部存在、
 * 照搬即可、只需改一处接口调用」。<b>那个结论低估了工作量</b> ——
 * 类确实都在，但 {@code AnimateGeoItemRenderer} 的<b>方法签名与渲染模型</b>已被本仓库
 * 重写过一轮。逐条列出实际差异（均以本仓库 26.2 代码为准，非文档）：
 *
 * <table border="1">
 *   <caption>上游 1.21.1 → 本仓库 26.2</caption>
 *   <tr><th>上游</th><th>26.2</th></tr>
 *   <tr><td>{@code MultiBufferSource bufferSource}</td>
 *       <td>{@code SubmitNodeCollector collector}（延迟提交，不再是即时 buffer）</td></tr>
 *   <tr><td>{@code model.render(...)}</td>
 *       <td>{@code model.submit(...)} —— {@code render} 在 26.2 已是
 *           <b>标注 {@code @Deprecated} 的空实现</b>（no-op），
 *           照抄上游会得到「什么都不画」而非编译错误</td></tr>
 *   <tr><td>{@code RenderType.entityCutout}</td>
 *       <td>{@code RenderTypes.entityCutout}（类名多了 s，包也变了）</td></tr>
 *   <tr><td>{@code net.minecraft.client.renderer.block.model.ItemTransforms}</td>
 *       <td>{@code net.minecraft.client.renderer.block.model.ItemTransforms}</td></tr>
 *   <tr><td>{@code transform.apply(false, poseStack)}</td>
 *       <td>{@code apply(isLeftHand, poseStack.last())} —— 第二参是
 *           {@code PoseStack.Pose}；且其内部<b>已自带</b>
 *           {@code translate(-0.5,-0.5,-0.5)}，调用方不能再补</td></tr>
 *   <tr><td>{@code SLOT_MODEL.renderToBuffer(pose, buffer, light, overlay, 0xFFFFFFFF)}</td>
 *       <td>{@code renderToBuffer(pose, buffer, light, overlay, r,g,b,a)} 四个 float，
 *           且必须包在 {@code collector.submitCustomGeometry} 里</td></tr>
 *   <tr><td>{@code IClientItemExtensions.of(stack).getCustomRenderer()}</td>
 *       <td>{@code BuiltinItemRendererRegistry.INSTANCE.get(item)}（Fabric）</td></tr>
 *   <tr><td>第一人称由 NeoForge {@code RenderHandEvent} 驱动</td>
 *       <td>由 {@code ItemInHandRendererMixin#submitArmWithItem} 拦截驱动</td></tr>
 * </table>
 *
 * <h2>渲染快照：为什么 {@code submitCustomGeometry} 的回调必须用参数 {@code pose}</h2>
 * 26.2 的 collector 是「先收集、后统一绘制」。回调执行时，外层 {@code poseStack}
 * 早已被 {@code popPose()} 或复用 —— 直接闭包捕获它会画到<b>完全错误的位置</b>
 * （本仓库 {@code GunItemRendererWrapper#renderSlotTexture} 因此踩过
 * 「物品栏图标一片空白」的坑）。这里沿用同一套快照写法。
 *
 * <h2>无内容包时的行为</h2>
 * {@code getModel(stack)} 为 {@code null}（没装内容包，或内容包没提供这把刀的 display）时：
 * <ul>
 *   <li>第一人称：直接 return，交回 vanilla 画原版模型；</li>
 *   <li>其他视角：画 {@code MissingTextureAtlasSprite} 提示内容包缺资源。</li>
 * </ul>
 * <b>本移植不打包任何美术资源</b>（上游为 All Rights Reserved），
 * 因此默认情况下走的就是「无 display」这条路径。
 */
public class MeleeItemRenderer extends AnimateGeoItemRenderer<CustomBedrockModel, BaseAnimationStateContext> {
    private static final SlotModel SLOT_MODEL = new SlotModel();

    public static final Supplier<MeleeItemRenderer> INSTANCE = Suppliers.memoize(MeleeItemRenderer::new);

    @Override
    public BaseAnimationStateContext initContext(ItemStack stack, Player player, float partialTick) {
        BaseAnimationStateContext context = new BaseAnimationStateContext();
        this.updateContext(context, stack, player, partialTick);
        return context;
    }

    @Override
    public void updateContext(BaseAnimationStateContext context, ItemStack stack, Player player, float partialTick) {
        context.setPartialTicks(partialTick);
        // 上游漏了这一句：BaseAnimationStateContext 有 setCurrentItem，但 MeleeItemRenderer
        // 从不调用它，导致 Lua 侧 getStackCount()/getCurrentItem() 永远看到 EMPTY。
        // 补上后内容包脚本才能按物品状态分支（与 ThrowableItemRendererWrapper 的做法一致）。
        context.setCurrentItem(stack);
    }

    @Override
    @Nullable
    public Identifier getTextureLocation(ItemStack stack) {
        return LrTacticalAPI.getMeleeDisplay(stack).map(MeleeDisplayInstance::getTexture).orElse(null);
    }

    @Override
    @Nullable
    public LuaAnimationStateMachine<BaseAnimationStateContext> getStateMachine(ItemStack stack) {
        return LrTacticalAPI.getMeleeDisplay(stack).map(MeleeDisplayInstance::getStateMachine).orElse(null);
    }

    @Override
    @Nullable
    public CustomBedrockModel getModel(ItemStack stack) {
        return LrTacticalAPI.getMeleeDisplay(stack).map(MeleeDisplayInstance::getModel).orElse(null);
    }

    @Override
    public long getPutAwayTime(ItemStack stack) {
        // 数据层的 putAwayTime 单位是 tick，基类要求毫秒 —— 故 ×50
        return LrTacticalAPI.getMeleeIndex(stack)
                .map(MeleeWeaponIndex::getData)
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

        // 逆转原版施加在手上的视角延滞，改为写入模型动画数据（与 GunItemRendererWrapper 同款）
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

        // 只有第一人称才显示 1p_effect 组
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
        MeleeDisplayInstance display = LrTacticalAPI.getMeleeDisplay(stack).orElse(null);
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
            // 26.2 与上游的三处差异（同 GunSmithTableItemRenderer 的注释）：
            //   1) apply 第二参是 PoseStack.Pose，不是 PoseStack；
            //   2) apply 内部已自带 translate(-0.5,-0.5,-0.5)，调用方不再补最后那一次；
            //   3) 左手上下文需传 applyLeftHandFix=true —— 上游硬编码 false，左手镜像是错的。
            poseStack.translate(0.5F, 0.5F, 0.5F);
            transforms.getTransform(ctx).apply(BlockTransformParser.isLeftHand(ctx), poseStack.last());
        }

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
     * <p><b>回调里必须用参数 {@code pose} 而不是外层 {@code poseStack}</b> —— 见类注释。
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
