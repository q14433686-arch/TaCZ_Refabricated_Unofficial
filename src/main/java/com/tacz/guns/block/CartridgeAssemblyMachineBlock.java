package com.tacz.guns.block;

import com.mojang.serialization.MapCodec;
import com.tacz.guns.block.entity.CartridgeAssemblyMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Dedicated four-slot final cartridge assembler.
 *
 * <p>Unlike a Depot/Belt operation this block deliberately owns four distinct
 * input slots, so final ammunition assembly never asks Create to infer a
 * multi-input custom-NBT recipe from one workpiece or a Basin inventory.</p>
 */
public final class CartridgeAssemblyMachineBlock extends BaseEntityBlock {
    public static final MapCodec<CartridgeAssemblyMachineBlock> CODEC = simpleCodec(CartridgeAssemblyMachineBlock::new);

    public CartridgeAssemblyMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends CartridgeAssemblyMachineBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack usedStack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof CartridgeAssemblyMachineBlockEntity machine && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(machine);
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CartridgeAssemblyMachineBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof CartridgeAssemblyMachineBlockEntity machine) {
                Containers.dropContents(level, pos, machine);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
