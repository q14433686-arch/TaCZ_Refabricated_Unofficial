package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.compat.firstperson.FirstPersonAnimationCompat;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称枪械渲染的接管点（26.3 拆分后的<b>渲染侧</b>）。
 *
 * <p>26.3 把 {@code ItemInHandRenderer} 拆成了状态类
 * {@code net.minecraft.client.player.FirstPersonHandsAndItems} 与渲染类
 * {@code net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer}。
 * 收枪保持（{@code KeepingItemRenderer}）随 {@code mainHandItem} 字段留在
 * {@link FirstPersonHandsAndItemsMixin}；本类只负责 submit 系列。</p>
 *
 * <h2>26.3 签名变化（已对照 vanilla 反编译源码核实）</h2>
 * <pre>
 * 26.2: submitHandsWithItems(float, PoseStack, SubmitNodeCollector, LocalPlayer, int)
 * 26.3: submitHandsWithItems(float, PoseStack, SubmitNodeCollector,
 *                            PlayerRenderState, FirstPersonHandsAndItemsRenderState)
 *
 * 26.2: submitArmWithItem(AbstractClientPlayer, float, float, InteractionHand, float,
 *                         ItemStack, float, PoseStack, SubmitNodeCollector, int)
 * 26.3: submitArmWithItem(PlayerRenderState, FirstPersonHandsAndItemsRenderState, float, float,
 *                         InteractionHand, float, ItemStack, float, PoseStack,
 *                         SubmitNodeCollector, int)
 * </pre>
 *
 * <p><b>玩家实体不再作为参数传入</b>：26.3 走渲染状态对象。TACZ 的渲染链路
 * （{@code AnimateGeoItemRenderer#renderFirstPerson}、状态机、动画事件）仍以
 * {@code LocalPlayer} 为输入，且第一人称本来就只可能是本地玩家，因此这里从
 * {@code Minecraft.getInstance().player} 取回；取不到就原样放行给 vanilla。</p>
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class FirstPersonHandsAndItemsRendererMixin {

    /**
     * 26.2 迁移: renderHandsWithItems → submitHandsWithItems
     * 26.3 迁移: 尾部两参由 (LocalPlayer, int) 变为
     * (PlayerRenderState, FirstPersonHandsAndItemsRenderState)。
     */
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    public void beforeHandRender(float pPartialTicks, PoseStack pMatrixStack, SubmitNodeCollector pCollector,
                                 PlayerRenderState pPlayerState, FirstPersonHandsAndItemsRenderState pHandState,
                                 CallbackInfo ci) {
        BeforeRenderHandEvent.CALLBACK.invoker().post(new BeforeRenderHandEvent(pMatrixStack));
    }

    /**
     * 第一人称枪械渲染入口。<b>这是修复"枪相对摄像机位置/大小不对 + 移动时抖动"的关键。</b>
     *
     * <p><b>问题背景</b></p>
     *
     * <p>上游 1.21.1 依赖 SimpleBedrockModel 的 {@code RenderHandEvent}，而 SBM 的 mixin
     * （已核对 {@code Sh1roCu/SimpleBedrockModel-Fabric} 源码）注入在
     * {@code ItemInHandRenderer#renderArmWithItem} 的 <b>HEAD</b> 并 {@code ci.cancel()}，
     * 也就是说 TACZ 拿到的 PoseStack 是<b>只经过 submitHandsWithItems 的视角回摆</b>、
     * <b>尚未经过任何手臂变换</b>的干净矩阵。</p>
     *
     * <p>26.2 移植时改走客户端 ItemModel（{@code tacz:dynamic_item}）路径，
     * 渲染发生在 {@code renderItem(...)} 内部 —— 那时 vanilla 已经额外施加了：</p>
     * <ol>
     *   <li>{@code applyItemArmTransform}：{@code translate(±0.56, -0.52 + 装备高度*-0.6, -0.72)}
     *       —— 这就是"位置偏了"和 ADS 尤其明显的直接来源；</li>
     *   <li>{@code swingArm(...)} / {@code SpearAnimations.firstPersonAttack(...)}
     *       挥动动画 —— 与 TACZ 自己的动画状态机叠加，表现为<b>移动/奔跑时手与枪抖动、动画不连贯</b>；</li>
     *   <li>装备切换的 {@code inverseArmHeight} 抬手动画 —— 同样与 TACZ 的收放枪动画打架。</li>
     * </ol>
     *
     * <p><b>修复</b>：在 {@code submitHandsWithItems} 调用 {@code submitArmWithItem}
     * 的位置包裹调用，遇到 TACZ 动画物品时不进入后者，直接执行第一人称渲染。
     * 这既保持 SBM 的取消语义和干净 PoseStack，也不会被其他 Mod 对
     * {@code submitArmWithItem} 的覆盖或手部变换抢走。</p>
     *
     * <p>注意：{@code submitHandsWithItems} 里的
     * {@code rotateDegrees(XP, (viewXRot - xBob) * 0.1)} /
     * {@code rotateDegrees(YP, (viewYRot - yBob) * 0.1)}
     * 视角回摆<b>仍然保留</b>（它在本方法之前执行），这正是
     * {@code GunItemRendererWrapper#renderFirstPerson} 开头那段"逆转原版延滞效果"所预期的输入。</p>
     *
     * <p><b>26.3 待实测</b>：{@code submitArmWithItem} 在 26.3 是 {@code private}。
     * {@code @WrapOperation} 包裹的是 {@code submitHandsWithItems} 内部的 {@code INVOKE}
     * 指令，私有不影响 wrap（调用点仍在字节码里），但这一点只有运行期能确证。</p>
     */
    @WrapOperation(
            method = "submitHandsWithItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
            )
    )
    private void tacz$submitArmWithAnimatedItem(FirstPersonHandsAndItemsRenderer instance,
                                                PlayerRenderState playerState,
                                                FirstPersonHandsAndItemsRenderState handState,
                                                float frameInterp,
                                                float xRot,
                                                InteractionHand hand,
                                                float attack,
                                                ItemStack itemStack,
                                                float inverseArmHeight,
                                                PoseStack poseStack,
                                                SubmitNodeCollector collector,
                                                int lightCoords,
                                                Operation<Void> original) {
        // 26.3: 玩家实体不再随参数传入。第一人称渲染的对象只可能是本地玩家；
        // 取不到（例如渲染线程早于玩家就绪）就原样放行，绝不吞掉 vanilla 的调用。
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null
                || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            original.call(instance, playerState, handState, frameInterp, xRot, hand, attack, itemStack,
                    inverseArmHeight, poseStack, collector, lightCoords);
            return;
        }

        // Wrap the invocation in submitHandsWithItems instead of injecting into
        // submitArmWithItem itself. Viewmodel Changer overwrites the latter, while Hide Hands,
        // SkyHands and the swing-animation family inject inside it. Owning the call site lets
        // TACZ bypass all of those transforms only for its animated viewmodels; ordinary items
        // still execute the complete downstream modded method.
        ItemStack mainRenderStack = FirstPersonAnimationCompat.getMainRenderStack(localPlayer);
        boolean mainHandOwnedByTacz = FirstPersonAnimationCompat.isTaczViewmodel(mainRenderStack);

        // Match upstream: a TACZ main-hand viewmodel contains both authored arms, so the
        // separate vanilla/modded offhand pass must not draw a duplicate arm or item.
        if (hand == InteractionHand.OFF_HAND && mainHandOwnedByTacz) {
            return;
        }

        ItemStack renderStack = hand == InteractionHand.MAIN_HAND ? mainRenderStack : itemStack;
        var renderer = cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry.INSTANCE
                .get(renderStack.getItem());
        if (!(renderer instanceof AnimateGeoItemRenderer<?, ?> geoRenderer)
                || geoRenderer.getModel(renderStack) == null) {
            original.call(instance, playerState, handState, frameInterp, xRot, hand, attack, itemStack,
                    inverseArmHeight, poseStack, collector, lightCoords);
            return;
        }

        // Animated TACZ/LRTactical items are main-hand viewmodels. Preserve the previous
        // behavior of suppressing a custom animated item placed in the offhand.
        if (hand == InteractionHand.OFF_HAND) {
            return;
        }

        // 【光影枪身闪烁 · 实验开关】Iris 26.x HandRenderer 一帧跑两遍手部 pass：
        // renderSolid（→ gbuffers_hand）与 renderTranslucent（→ gbuffers_hand_water）。
        // Iris 对实心物品的半透明遍取消放在 submitArmWithItem 的 HEAD
        // （iris$skipTranslucentHands），但 TACZ 用 WrapOperation 替换了
        // submitArmWithItem 的调用点本身，该取消对 TACZ 视模<b>永远不生效</b>：
        // 枪身（entityCutout）会被提交进两遍、动画状态机一帧推进两次。
        // 光影包的 hand water 遍在 labPBR/SEUS PBR 下与实心遍照明不同，
        // 两层叠加 = 枪身反射光源处的整块明暗闪烁（仅第一人称、仅 PBR 开启）。
        // 开启后：视模只提交实心遍，复刻 Iris 对普通实心物品的语义；
        // 枪口火光/抛壳/掩码登记均在实心遍完成（它们本就指派到 HAND program），
        // 画中画（PIP）在掩码之后合成，不依赖半透明遍的枪身提交。
        if (RenderConfig.IRIS_HAND_PHASE_SPLIT_FIX != null && RenderConfig.IRIS_HAND_PHASE_SPLIT_FIX.get()
                && IrisCompat.isHandRendererActive()
                && !IrisCompat.isHandRenderingSolid()) {
            return;
        }

        ItemDisplayContext context = localPlayer.getMainArm() == HumanoidArm.RIGHT
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        if (geoRenderer.needReInit(renderStack)) {
            geoRenderer.tryInit(renderStack, localPlayer, frameInterp);
        }
        poseStack.pushPose();
        geoRenderer.renderFirstPerson(localPlayer, renderStack, context, poseStack,
                collector, lightCoords, frameInterp);
        poseStack.popPose();
    }
}
