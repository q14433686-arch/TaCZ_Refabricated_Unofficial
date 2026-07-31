package com.tacz.guns.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.entity.EntityCasingDrop;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * 弹壳掉落实体渲染器。
 * <p>
 * 数据驱动：直接复用枪包内置的 shell 模型和贴图。
 * <p>
 * 渲染流程：
 * <ol>
 *   <li>从 EntityCasingDrop 获取 ammoId（通过枪械的 getAmmoId()）</li>
 *   <li>从 ClientAmmoIndex 获取 shellModel + shellTextureLocation</li>
 *   <li>使用 BedrockAmmoModel 渲染弹壳</li>
 * </ol>
 * <p>
 * 如果枪包未定义 shell 模型，则使用内置的默认弹壳模型。
 * <p>
 * 这就是"数据驱动调用枪包内置模型"的实现方式——
 * 不需要我们为每种口径硬编码模型，而是直接读取枪包作者在
 * ammo display JSON 中定义的 shell.model / shell.texture。
 */
public class EntityCasingDropRenderer extends EntityRenderer<EntityCasingDrop, EntityCasingDropRenderer.CasingRenderState> {

    public static class CasingRenderState extends EntityRenderState {
        public EntityCasingDrop casing;
        public float partialTicks;
    }

    public EntityCasingDropRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public CasingRenderState createRenderState() {
        return new CasingRenderState();
    }

    @Override
    public void extractRenderState(EntityCasingDrop casing, CasingRenderState state, float partialTicks) {
        super.extractRenderState(casing, state, partialTicks);
        state.casing = casing;
        state.partialTicks = partialTicks;
    }

    @Override
    public void submit(CasingRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {
        EntityCasingDrop casing = state.casing;
        if (casing == null) return;

        // 尝试从枪包的 ammo 数据中获取 shell 模型
        BedrockAmmoModel shellModel = null;
        Identifier shellTexture = null;

        // 方案1：优先使用 ammoId 查找（最可靠，与枪包 display JSON 直接对应）
        Identifier ammoId = casing.getAmmoId();
        if (ammoId != null) {
            Optional<com.tacz.guns.client.resource.index.ClientAmmoIndex> ammoIndexOpt =
                    TimelessAPI.getClientAmmoIndex(ammoId);
            if (ammoIndexOpt.isPresent()) {
                var ammoIndex = ammoIndexOpt.get();
                shellModel = ammoIndex.getShellModel();
                shellTexture = ammoIndex.getShellTextureLocation();
            }
        }

        // 方案2：如果 ammoId 没找到，尝试用 cartridgeType 作为备选
        if (shellModel == null) {
            Identifier cartridgeType = casing.getCartridgeType();
            if (cartridgeType != null) {
                try {
                    Optional<com.tacz.guns.client.resource.index.ClientAmmoIndex> fallbackOpt =
                            TimelessAPI.getClientAmmoIndex(cartridgeType);
                    if (fallbackOpt.isPresent()) {
                        var ammoIndex = fallbackOpt.get();
                        shellModel = ammoIndex.getShellModel();
                        shellTexture = ammoIndex.getShellTextureLocation();
                    }
                } catch (Exception ignored) {
                    // 查找失败，使用默认模型
                }
            }
        }

        // 渲染弹壳
        if (shellModel != null && shellTexture != null) {
            // 数据驱动渲染：使用枪包内置的 shell 模型
            poseStack.pushPose();
            float yaw = casing.getYRot();
            float pitch = casing.getXRot();
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw - 180.0F));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
            poseStack.translate(0, 1.5, 0);
            poseStack.scale(-1, -1, 1);

            // 弹壳较小，适当缩放
            float scale = 0.5f;
            poseStack.scale(scale, scale, scale);

            shellModel.submit(poseStack, ItemDisplayContext.GROUND, collector,
                    RenderTypes.entityCutout(shellTexture), state.lightCoords, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        } else {
            // 没有枪包模型时，使用简单的方块渲染作为备选
            renderFallbackCasing(state, poseStack, collector, casing);
        }
    }

    /**
     * 备选渲染：当没有枪包 shell 模型时，使用简单的方块形式渲染弹壳。
     * <p>
     * 颜色根据弹壳材质变化：
     * <ul>
     *   <li>黄铜(BRASS) → 金色</li>
     *   <li>钢(STEEL) → 银灰色</li>
     *   <li>铝(ALUMINUM) → 浅灰色</li>
     *   <li>聚合物(POLYMER) → 深灰色</li>
     * </ul>
     */
    private void renderFallbackCasing(CasingRenderState state, PoseStack poseStack,
                                       SubmitNodeCollector collector, EntityCasingDrop casing) {
        // 使用内置的默认弹壳纹理
        // 未来可以添加一个简单的内置弹壳模型
        // 目前使用 EntityBulletRenderer 的默认模型作为备选
        EntityBulletRenderer.getModel().ifPresent(model -> {
            poseStack.pushPose();
            float yaw = casing.getYRot();
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw - 180.0F));
            poseStack.translate(0, 1.5, 0);
            poseStack.scale(-0.3f, -0.3f, 0.3f);

            var texture = com.tacz.guns.client.resource.InternalAssetLoader.DEFAULT_BULLET_TEXTURE;
            var renderType = RenderTypes.entityTranslucent(texture);

            // 根据弹壳材质着色
            float r = 1.0f, g = 0.85f, b = 0.4f; // 默认黄铜色
            switch (casing.getCaseMaterial()) {
                case STEEL -> { r = 0.75f; g = 0.75f; b = 0.78f; }
                case ALUMINUM -> { r = 0.85f; g = 0.85f; b = 0.88f; }
                case POLYMER -> { r = 0.3f; g = 0.3f; b = 0.32f; }
                default -> {} // BRASS 保持金色
            }

            model.submit(poseStack, ItemDisplayContext.NONE, collector, renderType,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, r, g, b, 1.0f);
            poseStack.popPose();
        });
    }

    @Override
    protected int getBlockLightLevel(@NotNull EntityCasingDrop casing, @NotNull BlockPos pos) {
        return 10; // 弹壳稍亮一些，便于拾取
    }

    @Override
    public boolean shouldRender(EntityCasingDrop casing, Frustum camera, double camX, double camY, double camZ) {
        return true; // 弹壳很小，始终渲染（近距离才可见）
    }
}
