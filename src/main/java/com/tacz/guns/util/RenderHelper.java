package com.tacz.guns.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.render.scope.ScopeBodyRenderTypes;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.compat.firstperson.FirstPersonAnimationCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.lang.reflect.Proxy;

@Environment(EnvType.CLIENT)
public final class RenderHelper {
    private RenderHelper() {
    }

    /**
     * Collector-aware 26.2 first-person arm submission.
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
        // 【镜内裁手】高倍镜掩码就绪时，把手臂提交改走「镜内 discard」管线。
        // 手臂的 RenderType 是 AvatarRenderer#renderHand 内部自己挑的
        // （entityTranslucent(skin)，字节码实读），无法在调用点直接换 ——
        // 用 collector 代理在提交穿过时原地替换。判定放在这里（submit task
        // 执行期）而不是 extract 期：掩码清单登记发生在瞄具提交内部，
        // 只有此刻的 maskReadyForViewmodel 才反映本帧真实状态。
        collector = wrapForScopeClip(collector, skinTexture);
        // NEA normally raises this guard from ItemInHandRenderer#renderPlayerArm. TACZ calls
        // AvatarRenderer directly, so bridge the same guard to keep third-person smoothing and
        // action poses from being layered over the authored gun-hand bones.
        FirstPersonAnimationCompat.beginDirectArmRender();
        try {
            if (hand == HumanoidArm.RIGHT) {
                renderer.renderRightHand(matrixStack, collector, combinedLight, skinTexture,
                        player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
            } else {
                renderer.renderLeftHand(matrixStack, collector, combinedLight, skinTexture,
                        player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
            }
        } finally {
            FirstPersonAnimationCompat.endDirectArmRender();
        }
    }

    /** @deprecated legacy immediate path cannot render an arm without a collector. */
    @Deprecated
    public static void renderFirstPersonArm(LocalPlayer player, HumanoidArm hand, PoseStack matrixStack, int combinedLight) {
        // Intentionally empty. All migrated callers use the collector overload above.
    }

    /**
     * 【镜内裁手】给手臂提交套上「镜内 discard」的 collector 代理。
     *
     * <h2>为什么是代理而不是复刻提交</h2>
     * {@code AvatarRenderer#renderHand} 内部除了那一句 submitModelPart，
     * 还有 resetPose/袖层可见性/手臂显隐一串模型状态整备（字节码实读）——
     * 复刻提交就得复刻这些 vanilla 内部逻辑，版本一动就烂。代理让 vanilla
     * 逻辑原样跑完，只在提交穿过时换掉 RenderType。
     *
     * <h2>为什么敢用 identity 比较认出手臂的 RenderType</h2>
     * {@code RenderTypes.entityTranslucent} 是按贴图 memoize 的
     * （ENTITY_TRANSLUCENT 是 Util.memoize 的 BiFunction，字节码实读），
     * 同一皮肤贴图永远拿到同一实例 —— 代理里 {@code ==} 即可精准命中，
     * 不会误伤同一次提交里的其他 RenderType。
     *
     * <h2>为什么用 {@link Proxy} 而不是手写实现类</h2>
     * {@code SubmitNodeCollector} 继承 vanilla {@code OrderedSubmitNodeCollector}
     * 外加两个 Fabric 注入接口 —— 手写实现要跟着这三个接口的每次增删陪跑。
     * 动态代理自动覆盖全部方法面，反射开销无关紧要：每帧只有两次手臂提交
     * 穿过它，各自个位数方法调用。
     *
     * <p>掩码未就绪（低倍镜/光影/配置关闭）时原样返回真 collector ——
     * 与枪身/火光同一失败哲学，最坏回到「镜内见手臂」的现状。</p>
     */
    private static SubmitNodeCollector wrapForScopeClip(SubmitNodeCollector real, Identifier skinTexture) {
        if (!ScopeBodyRenderTypes.maskReadyForViewmodel(true)) {
            return real;
        }
        final RenderType vanillaArm = net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(skinTexture);
        final RenderType clippedArm = ScopeBodyRenderTypes.armClipped(skinTexture);
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
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        // 把真实异常还原抛出，别让调用方看到一层反射包装。
                        throw e.getCause() != null ? e.getCause() : e;
                    }
                });
    }
}
