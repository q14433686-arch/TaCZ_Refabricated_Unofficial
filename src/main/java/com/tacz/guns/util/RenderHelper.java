package com.tacz.guns.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopeRenderTypes;
import com.tacz.guns.compat.ar.ARCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
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

    /** @deprecated legacy immediate path cannot render an arm without a collector. */
    @Deprecated
    public static void renderFirstPersonArm(LocalPlayer player, HumanoidArm hand, PoseStack matrixStack, int combinedLight) {
        // Intentionally empty. All migrated callers use the collector overload above.
    }
}
