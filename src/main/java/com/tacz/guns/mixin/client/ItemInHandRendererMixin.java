package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
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
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void tacz$submitArmWithGun(net.minecraft.client.player.AbstractClientPlayer player,
                                       float frameInterp,
                                       float xRot,
                                       net.minecraft.world.InteractionHand hand,
                                       float attack,
                                       ItemStack itemStack,
                                       float inverseArmHeight,
                                       PoseStack poseStack,
                                       net.minecraft.client.renderer.SubmitNodeCollector collector,
                                       int lightCoords,
                                       CallbackInfo ci) {
        if (!(player instanceof LocalPlayer localPlayer)) {
            return;
        }

        // 【第 5 轮】必须显式判定“当前确为第一人称”。
        //
        // 症状：第三人称持枪时身上出现两条多余且残缺的手臂；换成非枪械物品即消失；
        //      第一人称可“截获”该状态并持久化。
        //
        // 根因：ItemInHandRenderer 实例是全局共享的。GameRenderer#renderItemInHand 虽然有
        //      isFirstPerson() 门禁，但第三人称视角 mod（Shoulder Surfing 等）以及 26.2
        //      自身的某些 PIP/离屏路径仍可能进入 submitArmWithItem。一旦进入，TACZ 就会走
        //      renderFirstPerson -> Left/RightHandRender -> AvatarRenderer#renderHand，
        //      而后者会直接改写共享 PlayerModel（arm.visible=true、zRot=±0.1、袖子可见性）
        //      且从不还原。
        //
        //      关键：submitModelPart 存的是【活的 ModelPart 引用】（见 RenderHelper 注释），
        //      真正绘制发生在稍后的 renderAllFeatures，于是这些被强制打开的手臂部件
        //      会在第三人称玩家实体上再画一遍 —— 就是那两条“多余、残缺”的手臂。
        //
        // 修复：只在真正的第一人称接管；第三人称一律放行给 vanilla，
        //      TACZ 的第三人称枪械由 renderByItem + PlayerModelMixin 的手臂姿态负责。
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }
        // 延长渲染：切枪时保持上一把枪的模型，与 1.21.1 行为一致。
        ItemStack renderStack = itemStack;
        if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
            ItemStack kept = KeepingItemRenderer.getRenderer().getCurrentItem();
            if (kept != null && !kept.isEmpty()) {
                renderStack = kept;
            }
        }
        var renderer = cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry.INSTANCE.get(renderStack.getItem());
        if (!(renderer instanceof com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer<?, ?> geoRenderer)) {
            return;
        }

        // 【附属模块接入点】原先这里写死 IGun.getIGunOrNull(renderStack) == null 就 return，
        // 于是 LRTactical 的近战/投掷物即便注册了 AnimateGeoItemRenderer 也永远走不进
        // 第一人称渲染 —— 表现为「第三人称有 Bedrock 模型和动画，第一人称却是原版方块状物品」。
        //
        // 改为按【是否真的有可渲染的 Bedrock 模型】判定，理由：
        //   1. 这正是本方法接下来要做的事所需要的前提，比「是不是枪」更贴切；
        //   2. 对枪械完全等价 —— 枪的渲染器是 GunItemRendererWrapper，
        //      其 getModel 就是 GunDisplayInstance#getGunModel；
        //   3. 【关键】必须判 null 而不能只判「渲染器类型对不对」：
        //      本移植不打包美术资源，没装内容包时 LRTactical 的 getModel 返回 null。
        //      若此时仍然接管，renderFirstPerson 会直接 return，
        //      而下面的 ci.cancel() 已经把 vanilla 的渲染取消掉了 ——
        //      结果是【第一人称手里空无一物】，比不接管还糟。
        if (geoRenderer.getModel(renderStack) == null) {
            return;
        }

        // 上游语义：主手持动画物品时副手不走常规渲染（副手枪由 HumanoidOffhandRender 背挂显示）。
        if (hand == net.minecraft.world.InteractionHand.OFF_HAND) {
            ci.cancel();
            return;
        }

        net.minecraft.world.item.ItemDisplayContext ctx =
                player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT
                        ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                        : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

        if (geoRenderer.needReInit(renderStack)) {
            geoRenderer.tryInit(renderStack, localPlayer, frameInterp);
        }
        poseStack.pushPose();
        geoRenderer.renderFirstPerson(localPlayer, renderStack, ctx, poseStack, collector, lightCoords, frameInterp);
        poseStack.popPose();
        ci.cancel();
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
        long time = System.currentTimeMillis() - tacz$KeepTimestamp;
        if (time < tacz$KeepTimeMs) {
            return;
        }
        this.tacz$KeepTimeMs = timeMs;
        this.tacz$KeepTimestamp = System.currentTimeMillis();
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
