package com.tacz.guns.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.render.scope.ScopeRenderTypes;
import com.tacz.guns.compat.ar.ARCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

@Environment(EnvType.CLIENT)
public final class RenderHelper {
    // BufferUploader-era helpers were removed during the completed 26.1.2 Feature Rendering
    // migration. GUI work uses GuiGraphics and model work uses SubmitNodeCollector.



    /**
     * Collector-aware 26.1.2 first-person arm submission.
     *
     * <p><b>第 5 轮更正：这里<u>绝不能</u>在 submit 之后还原 PlayerModel。</b></p>
     *
     * <p>第 4 轮为修"第三人称残缺手臂"加过"快照 + finally 还原"，方向错误，反而加重了症状。
     * 反编译 {@code SubmitNodeCollection#submitModel}：</p>
     * <pre>
     * Pose pose = poseStack.last().copy();                        // 只拷贝<b>矩阵</b>
     * Submit&lt;S&gt; submit = new Submit(renderType, pose, model, ...); // model 是<b>引用</b>
     * </pre>
     * <p>而 {@code submitModelPart} 内部是 {@code new Model.Simple(modelPart, ...)}，
     * 持有<b>活的 ModelPart 根引用</b>；真正遍历顶点发生在稍后的
     * {@code FeatureRenderDispatcher#renderAllFeatures}。</p>
     *
     * <p>也就是说：矩阵被快照了，<b>骨骼姿态没有</b>。若在 submit 之后立刻把
     * {@code arm.visible}/{@code zRot}/pose 还原，等到真正绘制时读到的就是被还原后的状态
     * —— 正是"手臂残缺"的直接来源。</p>
     *
     * <p>这里恢复 vanilla 语义（写完即走）。第三人称的污染改在<b>源头</b>杜绝：
     * 见 {@code ItemInHandRendererMixin} 的第一人称视角门禁。</p>
     *
     * <p><b>第 6 轮：中和 vanilla 1.21.9+ 第一人称手臂的 {@code zRot=±0.1}。</b></p>
     *
     * <p>症状（全枪械第一人称手部错位）：所有手枪整体偏左、手没握住枪；
     * 两把默认双管换弹时手部绑定/动画错位、弹药悬浮在手上方；错位量恒定、非常有规律。
     * 1.21.1 上游无此问题，26.2/26.1.2/1.21.11 全分支复现。</p>
     *
     * <p>根因是 vanilla 在 1.21.1 → 1.21.9 的渲染重构里给
     * {@code AvatarRenderer#renderHand} 加了两行（1.21.11 反编译源码逐行确认，
     * 1.21.9/1.21.10/26.1.2 同样存在；1.21.1 的 {@code PlayerRenderer#renderArm}
     * 没有这两行，手臂是笔直渲染的）：</p>
     * <pre>
     * model.leftArm.zRot = -0.1F;   // 约 -5.7°
     * model.rightArm.zRot = 0.1F;   // 约 +5.7°
     * </pre>
     * <p>而 TACZ 全部枪模的手部定位（{@code righthand_pos}/{@code lefthand_pos}）
     * 都是按 1.21.1 的 {@code zRot=0} 姿态 authored 的。手臂网格绕肩部 pivot
     * 凭空多转 ±5.7°，手相对枪恒定偏转 —— 正是"错位了一点点、很规律"的来源。
     * 其余链路已逐项排除：入口变换（0.1 倾斜/bobView/相机基）与上游等价、
     * 枪空间数学/动画采样/手部提取与上游一致、手臂 pivot（±5,2,0）与几何体未变、
     * 本方法的 vanilla 调用与 vanilla 自身调用点逐字节一致。</p>
     *
     * <p>修复：每次调用之后把<b>两条</b>手臂的 {@code zRot} 清零，
     * 精确还原 1.21.1 语义。依据正是上面第 5 轮记录的机制：
     * {@code submitModelPart} 只拷贝矩阵、{@code ModelPart} 是活引用，
     * 旋转在 {@code renderHandsWithItems} 末尾的 {@code renderAllFeatures} 才读取 ——
     * 因此 submit 之后、flush 之前的写入决定了最终姿态。</p>
     *
     * <p>为什么必须两条一起清：vanilla 每次调用<i>同时</i>污染左右两条
     * （调右手也写左臂）。双持枪会连续提交两次，后一次调用会把前一条手臂重新污染，
     * 所以每次调用后都把两条清零才能保证 flush 时读到的全是 0。</p>
     *
     * <p>为什么不会破坏 vanilla 物品的手臂：本方法只在 TACZ 接管 viewmodel 时被调用，
     * 而 TACZ 接管的 flush 里不存在 vanilla 手臂提交（主手接管时副手被拦、
     * TACZ 从不渲染副手）；纯 vanilla 的 flush 根本走不到这里。</p>
     *
     * <p>与第 5 轮结论的关系：第 5 轮禁止的是"把 vanilla 整备好的
     * {@code visible}/pose 还原掉"（那会重现手臂残缺）；本轮中和的是 vanilla
     * <b>新增</b>的一笔写入，且只动 {@code zRot}、不动其它整备状态。
     * 同一机制、相反方向 —— 是刻意的，不是矛盾。</p>
     */
    public static void renderFirstPersonArm(LocalPlayer player,
                                            HumanoidArm hand,
                                            PoseStack matrixStack,
                                            SubmitNodeCollector collector,
                                            int combinedLight) {
        if (player == null) {
            return;
        }
        AvatarRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
        var skinTexture = player.getSkin().body().texturePath();
        // 【镜内裁手】与枪身/火光同一个深度孔径门禁。手臂的 RenderType 是
        // AvatarRenderer#renderHand 内部自己挑的 entityTranslucent(skin)，无法在调用点直接换，
        // 因此在提交穿过时由代理把该类型原地替换成 ScopeRenderTypes.armClipped(skin)。
        // 失败方向仍与枪身/火光一致：孔径未就绪时原样返回真 collector，最坏回到「镜内见手臂」。
        collector = wrapForScopeClip(collector, skinTexture);
        com.tacz.guns.compat.firstperson.FirstPersonAnimationCompat.beginDirectArmRender();
        try {
            if (hand == HumanoidArm.RIGHT) {
                renderer.renderRightHand(matrixStack, collector, combinedLight, skinTexture,
                        player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
            } else {
                renderer.renderLeftHand(matrixStack, collector, combinedLight, skinTexture,
                        player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
            }
            // 【手臂对齐修复】中和 vanilla 1.21.9+ 的 zRot=±0.1，见本方法第 6 轮注释。
            resetFirstPersonArmLean(renderer);
        } finally {
            com.tacz.guns.compat.firstperson.FirstPersonAnimationCompat.endDirectArmRender();
        }
    }

    /**
     * 【镜内裁手】给手臂提交套上「镜内 discard」的 collector 代理。
     *
     * <p>为什么用代理而不是复刻提交：{@code AvatarRenderer#renderHand} 内部除了那句
     * {@code submitModelPart}，还有 resetPose/袖层可见性/手臂显隐一串模型状态整备 ——
     * 复刻提交就得复刻这些 vanilla 内部逻辑，版本一动就烂。代理让 vanilla 逻辑原样跑完，
     * 只在提交穿过时把 RenderType 换成 {@code ScopeRenderTypes.armClipped}。</p>
     *
     * <p>为什么敢用 identity 比较认出手臂的 RenderType：{@code RenderTypes.entityTranslucent}
     * 按贴图 memoize，同一皮肤贴图永远拿到同一实例（26.2 实读），因此 {@code ==} 即可精准命中，
     * 不会误伤同一次提交里的其它 RenderType。</p>
     */
    private static SubmitNodeCollector wrapForScopeClip(SubmitNodeCollector real, Identifier skinTexture) {
        if (!ScopeRenderTypes.shouldClipViewmodel()) {
            return real;
        }
        final RenderType vanillaArm = RenderTypes.entityTranslucent(skinTexture);
        final RenderType clippedArm = ScopeRenderTypes.armClipped(skinTexture);
        return (SubmitNodeCollector) Proxy.newProxyInstance(
                SubmitNodeCollector.class.getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class},
                (proxy, method, args) -> {
                    if (args != null) {
                        for (int i = 0; i < args.length; i++) {
                            if (args[i] == vanillaArm) {
                                args[i] = clippedArm;
                            }
                        }
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        // 把真实异常还原抛出，别让调用方看到一层反射包装。
                        throw e.getCause() != null ? e.getCause() : e;
                    }
                });
    }

    /**
     * 中和 vanilla 1.21.9+ {@code AvatarRenderer#renderHand} 写入的
     * {@code leftArm.zRot = -0.1F} / {@code rightArm.zRot = 0.1F}，
     * 把第一人称手臂精确还原成 1.21.1 的笔直姿态（TACZ 枪模的手部定位是按该姿态
     * authored 的）。必须在<b>每次</b> vanilla 手部调用之后、flush 之前执行，
     * 且两条手臂一起清（vanilla 每次调用会同时污染左右两条）。
     * 详见 {@code renderFirstPersonArm} 的第 6 轮注释。
     */
    private static void resetFirstPersonArmLean(AvatarRenderer<?> renderer) {
        if (renderer.getModel() instanceof PlayerModel playerModel) {
            playerModel.leftArm.zRot = 0.0F;
            playerModel.rightArm.zRot = 0.0F;
        }
    }

    /** @deprecated legacy immediate path cannot render an arm without a collector. */
    @Deprecated
    public static void renderFirstPersonArm(LocalPlayer player, HumanoidArm hand, PoseStack matrixStack, int combinedLight) {
        // Intentionally empty. All migrated callers use the collector overload above.
    }
}
