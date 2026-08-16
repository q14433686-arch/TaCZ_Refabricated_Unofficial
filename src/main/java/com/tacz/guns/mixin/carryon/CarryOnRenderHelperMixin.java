package com.tacz.guns.mixin.carryon;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.compat.carryon.CarryOnReflection;
import com.tacz.guns.item.GunSmithTableItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restores the custom table identity that Carry On omits from its temporary render stack. */
@Pseudo
@Mixin(targets = "tschipp.carryon.client.render.CarryRenderHelper", remap = false)
public abstract class CarryOnRenderHelperMixin {
    @Inject(method = "getRenderItemStack", at = @At("RETURN"), cancellable = true, require = 0)
    private static void tacz$restoreTableRenderIdentity(Player player,
                                                        CallbackInfoReturnable<ItemStack> cir) {
        ItemStack renderStack = cir.getReturnValue();
        if (!(renderStack.getItem() instanceof GunSmithTableItem)
                || !(renderStack.getItem() instanceof BlockItemDataAccessor accessor)
                || !DefaultAssets.EMPTY_BLOCK_ID.equals(accessor.getBlockId(renderStack))) {
            return;
        }

        BlockEntity carriedBlockEntity = CarryOnReflection.getCarriedBlockEntity(
                player, player.blockPosition(), player.level().registryAccess());
        if (carriedBlockEntity instanceof GunSmithTableBlockEntity tableBlockEntity) {
            Identifier blockId = tableBlockEntity.getId();
            if (blockId != null && !DefaultAssets.EMPTY_BLOCK_ID.equals(blockId)) {
                accessor.setBlockId(renderStack, blockId);
                cir.setReturnValue(renderStack);
            }
        }
    }
}
