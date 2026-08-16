package com.tacz.guns.mixin.carryon;

import com.tacz.guns.block.AbstractGunSmithTableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Resolves invisible companion halves to their root before Carry On performs a pickup. */
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PickupHandler", remap = false)
public abstract class CarryOnPickupHandlerMixin {
    @ModifyVariable(method = "tryPickUpBlock", at = @At("HEAD"), argsOnly = true,
            ordinal = 0, require = 0)
    private static BlockPos tacz$resolveRootTablePart(BlockPos pos, ServerPlayer player,
                                                       BlockPos originalPos, Level level) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AbstractGunSmithTableBlock table && !table.isRoot(state)) {
            // Carry On's pickupAllBlocks option bypasses its block-entity requirement. Redirecting
            // here makes all of its normal checks and removal operate on the real root instead.
            return table.getRootPos(pos, state);
        }
        return pos;
    }
}
