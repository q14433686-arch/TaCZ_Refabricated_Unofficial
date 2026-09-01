package com.tacz.guns.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
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

    /** @deprecated legacy immediate path cannot render an arm without a collector. */
    @Deprecated
    public static void renderFirstPersonArm(LocalPlayer player, HumanoidArm hand, PoseStack matrixStack, int combinedLight) {
        // Intentionally empty. All migrated callers use the collector overload above.
    }
}
