package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.compat.firstperson.FirstPersonAnimationCompat;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin implements KeepingItemRenderer {
    @Shadow
    private float mainHandHeight;
    @Shadow
    private float oMainHandHeight;
    @Shadow
    private ItemStack mainHandItem;
    @Unique
    private ItemStack tacz$KeepItem;
    @Unique
    private long tacz$KeepTimeMs;
    @Unique
    private long tacz$KeepTimestamp;

    /**
     * 26.1.2 兼容: renderHandsWithItems 在 26.2 被重命名为 submitHandsWithItems
     * 新签名 (26.1.2): renderHandsWithItems(float, PoseStack, SubmitNodeCollector, LocalPlayer, int)
     */
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    public void beforeHandRender(float pPartialTicks, PoseStack pMatrixStack, net.minecraft.client.renderer.SubmitNodeCollector pCollector, LocalPlayer pPlayerEntity, int pCombinedLight, CallbackInfo ci) {
        BeforeRenderHandEvent.CALLBACK.invoker().post(new BeforeRenderHandEvent(pMatrixStack));
    }

    /**
     * poly_mesh GPU 路径的绘制点：<b>本方法自己的收尾 flush 之后</b>。
     *
     * <p>1.21.11 的手部几何不是延迟到世界渲染末尾统一 flush 的 —— {@code renderHandsWithItems}
     * 末尾就是 {@code featureRenderDispatcher.renderAllFeatures()} + {@code bufferSource.endBatch()}
     * （Iris 也恰恰是 hook 这两个调用来接管手部绘制）。已用 CI 上 1.21.11 的真实字节码核实：
     * 该方法共 143 行、<b>只有 1 个 return</b>，那两个 flush 调用就是倒数第二条与最后一条指令，
     * 故本注入点必然命中且必然在 flush 之后。在这里画 GPU 骨骼的意义：</p>
     * <ul>
     *   <li>ModelView / Projection / 输出目标覆写都是「刚被原版手部批次用过」的那一份，
     *       不需要在任何调用点之外偷拍矩阵；</li>
     *   <li>光影下 Iris 正是从本方法内部（{@code iris$renderHandsWithCustomRenderer} →
     *       {@code HandRenderer#endRender}）做那次 flush 的，所以同一个 RETURN 注入点天然落在
     *       Iris 的手部渲染阶段内 —— gbuffer 已绑定、{@code gbuffers_hand} 生效，常驻 VBO
     *       因此能收光影照明（第 2 步 v2，见 {@code docs/TML_GPU_STEP2_HANDFLUSH_20260831.md}）；</li>
     *   <li>不 patch {@code RenderType#draw} 这种全局热点，也不 mixin Iris 内部类。</li>
     * </ul>
     *
     * <p>{@code require = 0}：映射漂移到最坏是本钩子不注入 —— GPU submit 侧的存活证明
     * （{@code PolyMeshGpuRenderer#handFlushAlive}）随即失败，下一帧自动回 collector。</p>
     */
    @Inject(method = "renderHandsWithItems", at = @At(value = "RETURN"), require = 0)
    private void tacz$drawMeshGpuAfterHandFeatureFlush(CallbackInfo ci) {
        PolyMeshGpuRenderer.renderAtHandFlush();
    }

    /**
     * 第一人称枪械渲染入口。<b>这是修复"枪相对摄像机位置/大小不对 + 移动时抖动"的关键。</b>
     *
     * <p><b>问题背景</b></p>
     *
     * <p>上游 1.21.1 依赖 SimpleBedrockModel 的 {@code RenderHandEvent}，而 SBM 的 mixin
     * （已核对 {@code Sh1roCu/SimpleBedrockModel-Fabric} 源码）注入在
     * {@code ItemInHandRenderer#renderArmWithItem} 的 <b>HEAD</b> 并 {@code ci.cancel()}，
     * 也就是说 TACZ 拿到的 PoseStack 是<b>只经过 renderHandsWithItems 的视角回摆</b>、
     * <b>尚未经过任何手臂变换</b>的干净矩阵。</p>
     *
     * <p>26.1.2 移植时改走客户端 ItemModel（{@code tacz:dynamic_item}）路径，
     * 渲染发生在 {@code renderItem(...)} 内部 —— 那时 vanilla 已经额外施加了：</p>
     * <ol>
     *   <li>{@code applyItemArmTransform}：{@code translate(±0.56, -0.52 + 装备高度*-0.6, -0.72)}
     *       —— 这就是"位置偏了"和 ADS 尤其明显的直接来源；</li>
     *   <li>{@code swingArm(...)} / {@code SpearAnimations.firstPersonAttack(...)}
     *       挥动动画 —— 与 TACZ 自己的动画状态机叠加，表现为<b>移动/奔跑时手与枪抖动、动画不连贯</b>；</li>
     *   <li>装备切换的 {@code inverseArmHeight} 抬手动画 —— 同样与 TACZ 的收放枪动画打架。</li>
     * </ol>
     *
     * <p><b>修复</b>：在 {@code renderArmWithItem} 的 HEAD 拦截并取消，改为在这里直接调用
     * TACZ 的第一人称渲染 —— 与 SBM 的注入点、取消语义完全一致，从而拿到与 1.21.1
     * 相同语义的干净 PoseStack。</p>
     *
     * <p>注意：{@code renderHandsWithItems} 里的
     * {@code mulPose(XP(viewXRot - xBob) * 0.1)} / {@code mulPose(YP(viewYRot - yBob) * 0.1)}
     * 视角回摆<b>仍然保留</b>（它在本方法之前执行），这正是
     * {@code GunItemRendererWrapper#renderFirstPerson} 开头那段"逆转原版延滞效果"所预期的输入。</p>
     */
    @WrapOperation(
            method = "renderHandsWithItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
            )
    )
    private void tacz$submitArmWithAnimatedItem(ItemInHandRenderer instance,
                                                AbstractClientPlayer player,
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
        if (!(player instanceof LocalPlayer localPlayer)
                || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            original.call(instance, player, frameInterp, xRot, hand, attack, itemStack,
                    inverseArmHeight, poseStack, collector, lightCoords);
            return;
        }

        ItemStack mainRenderStack = FirstPersonAnimationCompat.getMainRenderStack(localPlayer);
        boolean mainHandOwnedByTacz = FirstPersonAnimationCompat.isTaczViewmodel(mainRenderStack);

        if (hand == InteractionHand.OFF_HAND && mainHandOwnedByTacz) {
            return;
        }

        ItemStack renderStack = hand == InteractionHand.MAIN_HAND ? mainRenderStack : itemStack;
        var renderer = cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry.INSTANCE
                .get(renderStack.getItem());
        if (!(renderer instanceof AnimateGeoItemRenderer<?, ?> geoRenderer)
                || geoRenderer.getModel(renderStack) == null) {
            original.call(instance, player, frameInterp, xRot, hand, attack, itemStack,
                    inverseArmHeight, poseStack, collector, lightCoords);
            return;
        }

        if (!IrisCompat.shouldRenderInCurrentHandPhase(renderStack)) {
            return;
        }

        if (hand == InteractionHand.OFF_HAND) {
            return;
        }

        ItemDisplayContext context = player.getMainArm() == HumanoidArm.RIGHT
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

    /**
     * <b>刻意留空</b> —— 与上游 1.21.1 完全一致。
     *
     * <h2>为什么这里必须什么都不做</h2>
     * 上游同名注入点整段是<b>注释掉</b>的（{@code ItemInHandRendererMixin} 第 38-59 行，
     * 逐行核对过），也就是说 TACZ 从来不干预 vanilla 的装备进度。
     * 移植时这段被「还原」成了可执行代码，反而制造了切枪动画的 bug。
     *
     * <h2>它为什么会打断/加速切枪动画</h2>
     * {@code mainHandHeight} / {@code oMainHandHeight} 正是 vanilla
     * {@code ItemInHandRenderer#tick} 用来推进<b>换手动画</b>的状态量：
     * <pre>
     * // vanilla tick(): 每 tick 朝目标值逼近，产生"落下-抬起"的过渡
     * this.oMainHandHeight = this.mainHandHeight;
     * this.mainHandHeight += Mth.clamp(target - this.mainHandHeight, -0.4F, 0.4F);
     * </pre>
     * 而 {@code mainHandItem} 决定"现在该画哪把枪"、何时切换到新枪。
     *
     * <p>原先的实现在 HEAD 把这三个量<b>每 tick 强制写死</b>
     * （高度恒为 1.0、物品恒为当前主手物）：
     * <ul>
     *   <li>高度被钉死 → vanilla 的过渡插值失去意义，动画表现为<b>被打断或瞬间完成</b>；</li>
     *   <li>{@code mainHandItem} 被立刻改写成新枪 → 旧枪的收枪动画还没播完就被换掉，
     *       表现为<b>不显示动画</b>；</li>
     *   <li>连续快速切换两把枪时，{@code tacz$KeepItem} 的时间窗与这里的强制写入互相打架
     *       （keep 窗口内写 keepItem、窗口外立刻写新物品），于是出现<b>异常加速</b>。</li>
     * </ul>
     * 这与用户实测「不断切换两把不同的枪时会打断/异常加速甚至不显示动画」完全吻合。
     *
     * <p>TACZ 自己的切枪动画由状态机负责（{@code LocalPlayerDraw#doPutAway} →
     * {@code AnimateGeoItemRenderer#tryExit} 触发 {@code INPUT_PUT_AWAY}，
     * 再由 {@code TickAnimationEvent}/{@code needReInit} 驱动 {@code INPUT_DRAW}），
     * <b>不需要也不应该</b>去改 vanilla 的装备进度。
     *
     * <p>保留这个空注入点而不是整个删掉，是为了留住上面这段说明 ——
     * 避免后来者再次「看到空方法就顺手实现它」。
     */
    @Inject(method = "tick", at = @At("HEAD"))
    public void cancelEquippedProgress(CallbackInfo ci) {
    }

    @Unique
    @Override
    public void keep(ItemStack itemStack, long timeMs) {
        // 【2026-09-02 语义修正】原守卫是「窗口未过期就直接 return」，后果是连续快速切枪时
        // 第二次收枪**接管不了**窗口：上一把枪的剩余窗口继续生效，第二把枪的 put_away 一帧
        // 都画不出来，而且窗口比它需要的短。现改为**最新一次收枪接管**，只保留原守卫里良性
        // 的那一半——同一把枪、且新请求不会延长窗口时不动它，免得把正在播放的动画截断。
        //
        // 「接管不会用一个静止视模顶掉正在播放的动画」由调用点保证：
        // LocalPlayerDraw#doPutAway 只在 AnimateGeoItemRenderer#hasInitializedStateMachine
        // 成立（旧枪确实一直在被渲染、INPUT_PUT_AWAY 确实已触发）时才调 keep。
        long now = System.currentTimeMillis();
        boolean sameKeptItem = tacz$KeepItem != null
                && ItemStack.isSameItemSameComponents(tacz$KeepItem, itemStack);
        if (sameKeptItem && now + timeMs <= tacz$KeepTimestamp + tacz$KeepTimeMs) {
            return;
        }
        this.tacz$KeepTimeMs = timeMs;
        this.tacz$KeepTimestamp = now;
        this.tacz$KeepItem = itemStack;
        this.mainHandItem = itemStack;
    }

    @Override
    public ItemStack getCurrentItem() {
        if (Minecraft.getInstance().player == null) {
            return mainHandItem;
        }
        if (tacz$KeepItem != null) {
            long time = System.currentTimeMillis() - tacz$KeepTimestamp;
            if (time < tacz$KeepTimeMs) {
                return tacz$KeepItem;
            } else {
                tacz$KeepItem = null;
            }
        }
        return mainHandItem;
    }
}
