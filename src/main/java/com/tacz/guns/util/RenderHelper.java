package com.tacz.guns.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
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
    /** 【镜内裁手】日志只打一次：成功走了代理 / 代理不可用退回原 collector。 */
    private static volatile boolean LOGGED_ARM_CLIPPED = false;
    private static volatile boolean LOGGED_ARM_CLIP_FAILED = false;

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
     * <p><b>第 6 轮补充（2026-09-13，同步自 1.21.11 线 {@code 61ab4a0}）：</b>
     * 真正干活的是下面带 {@code clipToScopeExterior} 的重载，那里在每次 vanilla 手部调用之后
     * 追加了「把两条手臂 {@code zRot} 清零」的一步 —— 中和 vanilla 1.21.9+
     * {@code AvatarRenderer#renderHand} <b>新增</b>的 {@code ±0.1} 写入。
     * 那一步与本轮「不要还原 vanilla 整备状态」并不矛盾（同一机制、相反方向），
     * 理由逐条写在该重载的注释里。</p>
     */
    public static void renderFirstPersonArm(LocalPlayer player,
                                            HumanoidArm hand,
                                            PoseStack matrixStack,
                                            SubmitNodeCollector collector,
                                            int combinedLight) {
        renderFirstPersonArm(player, hand, matrixStack, collector, combinedLight, false);
    }

    /**
     * 带「镜内裁手」开关的第一人称手臂提交。
     *
     * <p>{@code clipToScopeExterior} 由 {@code LeftHandRender} / {@code RightHandRender} 在
     * <b>extract 期</b>算出（与 {@code MuzzleFlashRender} 同一判据：{@link ScopeRenderTypes#hasScheduledViewmodelAperture()}）
     * ——瞄具的目镜序列在枪身遍历之前登记（{@code BedrockGunModel#submit} 先提交瞄具再
     * {@code super.submit}），所以此刻的闸门就是本帧的真实状态。</p>
     *
     * <p><b>第 6 轮：中和 vanilla 1.21.9+ 第一人称手臂的 {@code zRot=±0.1}。</b>
     * 同步自 1.21.11 线 {@code 61ab4a0}（同一份 vanilla 代码，见下）。</p>
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
     * 精确还原 1.21.1 语义。依据正是上面 5 参重载记的第 5 轮机制：
     * {@code submitModelPart} 只拷贝矩阵、{@code ModelPart} 是活引用，
     * 旋转在 {@code renderHandsWithItems} 末尾的 {@code renderAllFeatures} 才读取
     * （26.1.2 侧同一结论见 {@code ItemInHandRendererMixin} 的字节码实测：@281
     * {@code renderAllFeatures} + @294 {@code endBatch}）——
     * 因此 submit 之后、flush 之前的写入决定了最终姿态。</p>
     *
     * <p>为什么必须两条一起清：vanilla 每次调用<i>同时</i>污染左右两条
     * （调右手也写左臂）。双持枪会连续提交两次，后一次调用会把前一条手臂重新污染，
     * 所以每次调用后都把两条清零才能保证 flush 时读到的全是 0。</p>
     *
     * <p>为什么不会破坏 vanilla 物品的手臂：本方法只在 TACZ 接管 viewmodel 时被调用
     * （{@code ItemInHandRendererMixin#tacz$submitArmWithAnimatedItem} 取消 vanilla 的
     * {@code renderArmWithItem} 之后），而 TACZ 接管的 flush 里不存在 vanilla 手臂提交
     * （主手接管时副手被拦、TACZ 从不渲染副手）；纯 vanilla 的 flush 根本走不到这里。</p>
     *
     * <p>与第 5 轮结论的关系：第 5 轮禁止的是"把 vanilla 整备好的
     * {@code visible}/pose 还原掉"（那会重现手臂残缺）；本轮中和的是 vanilla
     * <b>新增</b>的一笔写入，且只动 {@code zRot}、不动其它整备状态。
     * 同一机制、相反方向 —— 是刻意的，不是矛盾。</p>
     *
     * <p>与本线「镜内裁手」的关系：{@code clipToScopeExterior} 只决定 collector 是否套代理
     * （换 RenderType），与手臂骨骼姿态无关；清零这一步<b>无条件</b>执行，
     * 两种 collector 下都需要。</p>
     */
    public static void renderFirstPersonArm(LocalPlayer player,
                                            HumanoidArm hand,
                                            PoseStack matrixStack,
                                            SubmitNodeCollector collector,
                                            int combinedLight,
                                            boolean clipToScopeExterior) {
        if (player == null) {
            return;
        }
        AvatarRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
        var skinTexture = player.getSkin().body().texturePath();
        SubmitNodeCollector target = clipToScopeExterior
                ? wrapForScopeClip(collector, skinTexture)
                : collector;
        com.tacz.guns.compat.firstperson.FirstPersonAnimationCompat.beginDirectArmRender();
        try {
            if (hand == HumanoidArm.RIGHT) {
                renderer.renderRightHand(matrixStack, target, combinedLight, skinTexture,
                        player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
            } else {
                renderer.renderLeftHand(matrixStack, target, combinedLight, skinTexture,
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
     * <h2>为什么是代理而不是复刻提交</h2>
     * {@code AvatarRenderer#renderLeftHand/renderRightHand} 内部除了那一句
     * {@code submitModelPart(arm, pose, RenderTypes.entityTranslucent(skin), ..)}，还有
     * resetPose / 袖层可见性 / 手臂显隐一串模型状态整备 —— 复刻提交就得复刻这些 vanilla
     * 内部逻辑，版本一动就烂。代理让 vanilla 逻辑原样跑完，只在提交穿过时换掉 RenderType。
     *
     * <h2>为什么敢用 identity 比较认出手臂的 RenderType</h2>
     * {@code RenderTypes.entityTranslucent} 是按贴图 memoize 的（26.2 侧的字节码实读结论；
     * 本线为 {@code entityTranslucent(tex, true)}，{@code ScopeRenderTypes#createFlashTranslucentType}
     * 的注释亦按此对齐）。同一皮肤贴图永远拿到同一实例 —— 代理里 {@code ==} 即可精准命中，
     * 不会误伤同一次提交里的其他 RenderType。
     *
     * <h2>复用的就是火光那条管线</h2>
     * {@link ScopeRenderTypes#flashTranslucentClipped(Identifier)} 是
     * {@code entityTranslucent} 的逐状态克隆（含 vanilla 的
     * {@code affectsCrumbling() + sortOnUpload()}，后者不补会出现二层袖压一层臂的错序）
     * + 目镜孔径 discard（{@code ScopeDepthCopyState.Operation#MASK_OUTSIDE}）。
     * 手臂与火光在管线状态上无差别 —— 26.2 的 {@code ScopeBodyRenderTypes#armClipped}
     * 也是复用 {@code FLASH_TRANSLUCENT_CLIPPED_PIPELINE}，同一结论。
     *
     * <h2>失败哲学</h2>
     * 任一环节不满足（{@code SubmitNodeCollector} 不是接口、代理构造失败、闸门为假）都
     * <b>原样返回真 collector</b>：最坏回到「镜内见手臂」的现状，绝不画错手臂。
     */
    private static SubmitNodeCollector wrapForScopeClip(SubmitNodeCollector real, Identifier skinTexture) {
        if (real == null) {
            return null;
        }
        if (!SubmitNodeCollector.class.isInterface()) {
            if (!LOGGED_ARM_CLIP_FAILED) {
                LOGGED_ARM_CLIP_FAILED = true;
                GunMod.LOGGER.warn("[TACZ Scope] In-scope arm clipping unavailable: {} is not an interface, "
                                + "so the collector proxy cannot be built. Arms keep vanilla rendering.",
                        SubmitNodeCollector.class.getName());
            }
            return real;
        }
        try {
            final RenderType vanillaArm = RenderTypes.entityTranslucent(skinTexture);
            final RenderType clippedArm = ScopeRenderTypes.flashTranslucentClipped(skinTexture);
            SubmitNodeCollector proxy = (SubmitNodeCollector) Proxy.newProxyInstance(
                    SubmitNodeCollector.class.getClassLoader(),
                    new Class<?>[]{SubmitNodeCollector.class},
                    (p, method, args) -> {
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
            if (!LOGGED_ARM_CLIPPED) {
                LOGGED_ARM_CLIPPED = true;
                GunMod.LOGGER.info("[TACZ Scope] In-scope arm clipping engaged: first-person arms now discard "
                        + "inside the ocular (depth-aperture mode 2, reused flash-translucent pipeline).");
            }
            return proxy;
        } catch (Throwable t) {
            if (!LOGGED_ARM_CLIP_FAILED) {
                LOGGED_ARM_CLIP_FAILED = true;
                GunMod.LOGGER.warn("[TACZ Scope] In-scope arm clipping unavailable; arms keep vanilla "
                        + "rendering (no visual regression).", t);
            }
            return real;
        }
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
