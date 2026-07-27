package com.tacz.guns.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.compat.ar.ARCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;

@Environment(EnvType.CLIENT)
public final class RenderHelper {
// TODO[26.2]: BufferUploader removed     // 26.2 迁移: blit/innerBlit 使用的 Tesselator/BufferUploader/RenderSystem.setShader 已全部移除
    // 26.2 使用延迟渲染管线 (SubmitNodeCollector)，不再支持即时模式渲染
    // 如需 2D blit 渲染，请通过 GuiGraphics 或 SubmitNodeCollector.submitCustomGeometry 实现

    public static void enableItemEntityStencilTest() {
        // 26.2 Vulkan 兼容: 原 GL11.GL_STENCIL_TEST 在 Vulkan 后端不可用
        // 暂时改为 no-op，后续需通过 GpuFormat.D24_UNORM_S8_UINT + RenderPipeline depth/stencil state 重实现
        // 如果需要支持 OpenGL 后端，可在此检测后端类型：
        // boolean isVulkan = Minecraft.getInstance().getGpuDevice().getDeviceInfo().backendName().contains("vulkan");
        RenderSystem.assertOnRenderThread();
        // no-op for now
    }

    public static void disableItemEntityStencilTest() {
        RenderSystem.assertOnRenderThread();
        // no-op for Vulkan compatibility
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
        if (hand == HumanoidArm.RIGHT) {
            renderer.renderRightHand(matrixStack, collector, combinedLight, skinTexture,
                    player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
        } else {
            renderer.renderLeftHand(matrixStack, collector, combinedLight, skinTexture,
                    player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
        }
    }

    /** @deprecated legacy immediate path cannot render an arm without a collector. */
    @Deprecated
    public static void renderFirstPersonArm(LocalPlayer player, HumanoidArm hand, PoseStack matrixStack, int combinedLight) {
        // Intentionally empty. All migrated callers use the collector overload above.
    }
}
