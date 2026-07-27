package com.tacz.guns.client.renderer.other;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.pojo.display.gun.LayerGunShow;
import com.tacz.guns.util.math.MathUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 渲染"背在身上"的枪：副手枪 + 快捷栏里未手持的枪。
 *
 * <p><b>26.2 迁移说明（对齐反编译源）</b></p>
 *
 * <p>1.21.1 的实现走 {@code ItemRenderer#renderStatic(stack, ctx, light, overlay, poseStack,
 * MultiBufferSource, level, seed)}。26.2 已经没有该方法：实体层渲染改为"先 extract 出
 * {@link ItemStackRenderState}，再 submit 到 {@link SubmitNodeCollector}"的两段式。</p>
 *
 * <p>等价链路（均由 javap / 反编译确认）：</p>
 * <ul>
 *   <li>{@code Minecraft#getItemModelResolver()} → {@link ItemModelResolver}</li>
 *   <li>{@code ItemModelResolver#updateForTopItem(ItemStackRenderState, ItemStack,
 *       ItemDisplayContext, Level, ItemOwner, int)} —— 填充 render state；
 *       它内部会 {@code output.clear()} 并写入 {@code displayContext}</li>
 *   <li>{@code ItemStackRenderState#submit(PoseStack, SubmitNodeCollector, int, int, int)}
 *       —— 与 vanilla {@code ItemInHandLayer#submitArmWithItem} 结尾调用的是同一个方法</li>
 * </ul>
 *
 * <p>这里刻意<b>不</b>使用 {@code updateForLiving}：后者的 seed 是
 * {@code entity.getId() + displayContext.ordinal()}，同一实体上的多把枪（副手 + 多个快捷栏槽位）
 * 会算出同一个 seed。改用 {@code updateForTopItem} 并把槽位编号混入 seed，保证每把枪独立。</p>
 *
 * <p>坐标变换与 1.21.1 逐行一致（translate → scale(-x,-y,z) → 欧拉转四元数），
 * 只替换渲染提交方式，不改变几何语义。</p>
 */
public class HumanoidOffhandRender {
    /** 副手 seed 的偏移量，避开 0..8 的快捷栏槽位编号。 */
    private static final int OFFHAND_SEED_OFFSET = 100;

    /**
     * 由 {@code ItemInHandLayerMixin} 在 {@code ItemInHandLayer#submit} 的 TAIL 调用。
     *
     * <p>26.2 的实体层拿到的是 render state 而不是实体本身，因此需要用
     * {@code state.id} 反查实体。GUI/展示柜等场景可能没有真实实体，此时直接跳过。</p>
     */
    public static void renderGun(ArmedEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        // ArmedEntityRenderState 自身没有 id 字段（javap 已确认），id 定义在 AvatarRenderState 上。
        // 这里通过 render state 的实际类型取回实体。
        LivingEntity entity = resolveEntity(state);
        if (entity == null) {
            return;
        }
        renderOffhandGun(entity, poseStack, collector, packedLight);
        renderHotbarGun(entity, poseStack, collector, packedLight);
    }

    private static LivingEntity resolveEntity(ArmedEntityRenderState state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatarState) {
            if (minecraft.level.getEntity(avatarState.id) instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
        }
        return null;
    }

    private static void renderOffhandGun(LivingEntity entity, PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        ItemStack itemStack = entity.getOffhandItem();
        if (itemStack.isEmpty()) {
            return;
        }
        if (IGun.getIGunOrNull(itemStack) == null) {
            return;
        }
        TimelessAPI.getGunDisplay(itemStack).ifPresent(index -> {
            LayerGunShow offhandShow = index.getOffhandShow();
            if (offhandShow == null) {
                return;
            }
            renderGunItem(entity, poseStack, collector, packedLight, itemStack, offhandShow, OFFHAND_SEED_OFFSET);
        });
    }

    private static void renderHotbarGun(LivingEntity entity, PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        if (!(entity instanceof Player player)) {
            return;
        }
        Inventory inventory = player.getInventory();
        // 26.2: Inventory#selected 字段已改为 getSelectedSlot() 访问器。
        int selected = inventory.getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            if (i == selected) {
                continue;
            }
            renderHotbarGun(entity, poseStack, collector, packedLight, inventory.getItem(i), i);
        }
    }

    private static void renderHotbarGun(LivingEntity entity, PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                        ItemStack itemStack, int inventoryIndex) {
        if (itemStack.isEmpty()) {
            return;
        }
        if (IGun.getIGunOrNull(itemStack) == null) {
            return;
        }
        TimelessAPI.getGunDisplay(itemStack).ifPresent(display -> {
            Int2ObjectArrayMap<LayerGunShow> hotbarShow = display.getHotbarShow();
            if (hotbarShow == null || hotbarShow.isEmpty()) {
                return;
            }
            if (!hotbarShow.containsKey(inventoryIndex)) {
                return;
            }
            renderGunItem(entity, poseStack, collector, packedLight, itemStack, hotbarShow.get(inventoryIndex), inventoryIndex);
        });
    }

    /**
     * 变换部分与 1.21.1 逐行等价；提交部分改为 26.2 的 extract → submit 两段式。
     */
    private static void renderGunItem(LivingEntity entity, PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                      ItemStack itemStack, LayerGunShow gunShow, int seedSalt) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemModelResolver resolver = minecraft.getItemModelResolver();
        if (resolver == null) {
            return;
        }

        Vector3f pos = gunShow.getPos();
        Vector3f rotate = gunShow.getRotate();
        Vector3f scale = gunShow.getScale();

        poseStack.pushPose();
        poseStack.translate(-pos.x() / 16f, 1.5 - pos.y() / 16f, pos.z() / 16f);
        poseStack.scale(-scale.x(), -scale.y(), scale.z());
        Quaternionf rotation = new Quaternionf();
        MathUtil.toQuaternion((float) Math.toRadians(rotate.x), (float) Math.toRadians(rotate.y), (float) Math.toRadians(rotate.z), rotation);
        poseStack.mulPose(rotation);

        // 26.2 等价于旧的 ItemRenderer#renderStatic(..., ItemDisplayContext.FIXED, ...)。
        // seed 混入 seedSalt，避免同一实体上多把枪共用 seed。
        ItemStackRenderState renderState = new ItemStackRenderState();
        resolver.updateForTopItem(renderState, itemStack, ItemDisplayContext.FIXED, entity.level(), entity,
                entity.getId() + seedSalt * 31);
        renderState.submit(poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, 0);

        poseStack.popPose();
    }
}
