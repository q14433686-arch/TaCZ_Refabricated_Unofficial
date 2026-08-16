package com.tacz.guns.mixin.carryon;

import com.tacz.guns.block.AbstractGunSmithTableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiFunction;

/** Prevents invisible companion halves from becoming independently carried ghost blocks. */
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PickupHandler", remap = false)
public abstract class CarryOnPickupHandlerMixin {
    @Inject(method = "tryPickUpBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tacz$rejectNonRootTablePart(ServerPlayer player, BlockPos pos, Level level,
                                                    BiFunction<BlockState, BlockPos, Boolean> pickupCallback,
                                                    CallbackInfoReturnable<Boolean> cir) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AbstractGunSmithTableBlock table && !table.isRoot(state)) {
            // This must be explicit: Carry On's pickupAllBlocks option bypasses its normal
            // block-entity requirement and would otherwise pick up the invisible half.
            cir.setReturnValue(false);
        }
    }
}
