package com.tacz.guns.mixin.carryon;

import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.compat.carryon.CarryOnReflection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiFunction;

/** Adds the companion-half preflight that Carry On cannot infer from a single saved block state. */
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PlacementHandler", remap = false)
public abstract class CarryOnPlacementHandlerMixin {
    @Inject(method = "tryPlaceBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tacz$requireSpaceForCompleteTable(ServerPlayer player, BlockPos pos, Direction facing,
                                                          BiFunction<BlockPos, BlockState, Boolean> placementCallback,
                                                          CallbackInfoReturnable<Boolean> cir) {
        BlockState carriedState = CarryOnReflection.getCarriedBlock(player);
        if (!(carriedState != null && carriedState.getBlock() instanceof AbstractGunSmithTableBlock table)) {
            return;
        }
        if (!table.isRoot(carriedState)) {
            denyPlacement(player, cir);
            return;
        }

        Level level = player.level();
        BlockPos rootPos = resolveRootPos(player, level, pos, facing);
        BlockPlaceContext rootContext = createContext(player, rootPos, facing);
        BlockState placementState = table.getStateForPlacement(rootContext);
        if (placementState == null || placementState.getBlock() != table) {
            denyPlacement(player, cir);
            return;
        }

        BlockPos companionPos = table.getCompanionPos(rootPos, placementState);
        BlockState companionState = table.getCompanionState(placementState);
        if (companionPos == null || companionState == null) {
            return;
        }

        BlockPlaceContext companionContext = createContext(player, companionPos, facing);
        boolean canPlaceCompanion = level.getWorldBorder().isWithinBounds(companionPos)
                && !level.isOutsideBuildHeight(companionPos)
                && level.mayInteract(player, companionPos)
                && level.getBlockState(companionPos).canBeReplaced(companionContext)
                && level.isUnobstructed(companionState, companionPos, CollisionContext.of(player));
        if (!canPlaceCompanion) {
            // Cancel before Carry On executes scripts, mutates the world, or clears CarryOnData.
            // The player therefore keeps carrying the complete table and can retry elsewhere.
            denyPlacement(player, cir);
        }
    }

    private static BlockPos resolveRootPos(ServerPlayer player, Level level, BlockPos pos, Direction facing) {
        BlockPlaceContext context = createContext(player, pos, facing);
        return level.getBlockState(pos).canBeReplaced(context) ? pos : pos.relative(facing);
    }

    private static BlockPlaceContext createContext(ServerPlayer player, BlockPos pos, Direction facing) {
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY,
                BlockHitResult.miss(player.position(), facing, pos));
    }

    private static void denyPlacement(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.5F, 0.5F);
        cir.setReturnValue(false);
    }
}
