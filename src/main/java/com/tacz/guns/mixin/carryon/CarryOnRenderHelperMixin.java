package com.tacz.guns.mixin.carryon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.compat.carryon.CarryOnReflection;
import com.tacz.guns.item.GunSmithTableItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Restores a carried gun-smith table's identity after Carry On materializes its render template.
 *
 * <p>Carry On 2.11 returns an immutable {@link ItemStackTemplate}; its renderer immediately calls
 * {@link ItemStackTemplate#create()} before resolving the item model. Redirecting that creation is
 * the 26.2-safe write point: the actual mutable stack receives TACZ's {@code BlockId} before the
 * model is submitted, without guessing at private template-component APIs.</p>
 */
@Pseudo
@Mixin(targets = "tschipp.carryon.client.render.CarriedObjectRender", remap = false)
public abstract class CarryOnRenderHelperMixin {
    @Redirect(method = "drawBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStackTemplate;create()Lnet/minecraft/world/item/ItemStack;"),
            require = 0)
    private static ItemStack tacz$createRenderStackWithTableIdentity(ItemStackTemplate template, Player player,
                                                                       PoseStack matrix, int light,
                                                                       SubmitNodeCollector nodeCollector,
                                                                       boolean firstPerson, float partialTicks) {
        ItemStack renderStack = template.create();
        if (!(renderStack.getItem() instanceof GunSmithTableItem)
                || !(renderStack.getItem() instanceof BlockItemDataAccessor accessor)
                || !DefaultAssets.EMPTY_BLOCK_ID.equals(accessor.getBlockId(renderStack))) {
            return renderStack;
        }

        BlockEntity carriedBlockEntity = CarryOnReflection.getCarriedBlockEntity(
                player, player.blockPosition(), player.level().registryAccess());
        if (carriedBlockEntity instanceof GunSmithTableBlockEntity tableBlockEntity) {
            Identifier blockId = tableBlockEntity.getId();
            if (blockId != null && !DefaultAssets.EMPTY_BLOCK_ID.equals(blockId)) {
                accessor.setBlockId(renderStack, blockId);
            }
        }
        return renderStack;
    }
}
