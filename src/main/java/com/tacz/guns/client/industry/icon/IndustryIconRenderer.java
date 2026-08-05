package com.tacz.guns.client.industry.icon;

import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.model.SlotModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Flat 32×32-style renderer for NBT-generic magazines and industrial parts.
 *
 * <p>Those items were already ordinary generated-item quads before the icon
 * mapping layer.  Rendering a supplied texture through the same slot quad used
 * by {@link com.tacz.guns.client.renderer.item.AmmoItemRenderer} therefore
 * preserves their non-3D semantics while allowing a resource pack to select a
 * visual variant from NBT.</p>
 */
@Environment(EnvType.CLIENT)
public final class IndustryIconRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final SlotModel SLOT_MODEL = new SlotModel();

    public static final Supplier<IndustryIconRenderer> INSTANCE = Suppliers.memoize(IndustryIconRenderer::new);

    private IndustryIconRenderer() {
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                       SubmitNodeCollector collector, int light, int overlay) {
        if (stack.isEmpty()) {
            return;
        }
        Identifier texture = IndustryIconManager.INSTANCE.resolveTexture(stack).orElseGet(() -> fallbackTexture(stack));
        poseStack.pushPose();
        poseStack.translate(0.5D, 1.5D, 0.5D);
        poseStack.mulPose(Axis.ZN.rotationDegrees(180.0F));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture), (pose, buffer) -> {
            // SubmitNodeCollector is deferred in 26.2. Copy the pose supplied
            // to the callback instead of retaining the mutable outer stack.
            PoseStack snapshot = new PoseStack();
            snapshot.last().pose().set(pose.pose());
            snapshot.last().normal().set(pose.normal());
            SLOT_MODEL.renderToBuffer(snapshot, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        });
        poseStack.popPose();
    }

    /**
     * Keep every existing TACZ generated-item texture as a safe runtime fallback.
     * A missing/removed mapping never turns a magazine or a cartridge case into
     * an invisible special model.
     */
    private static Identifier fallbackTexture(ItemStack stack) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null && GunMod.MOD_ID.equals(itemId.getNamespace())) {
            return Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "item/" + itemId.getPath());
        }
        return MissingTextureAtlasSprite.getLocation();
    }
}
