package com.tacz.guns.block;

import com.mojang.serialization.MapCodec;
import com.tacz.guns.block.entity.CartridgeAssemblyMachineBlockEntity;
import com.tacz.guns.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
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
    /** Matches the four source-pack blockstate variants for the detailed machine model. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CartridgeAssemblyMachineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends CartridgeAssemblyMachineBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return !level.isClientSide()
                ? createTickerHelper(blockEntityType, ModBlocks.CARTRIDGE_ASSEMBLY_MACHINE_BE,
                CartridgeAssemblyMachineBlockEntity::serverTick)
                : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 26.2 removed the old {@code onRemove(..., newState, movedByPiston)}
     * hook. {@link Block#destroy(LevelAccessor, BlockPos, BlockState)} is the
     * lifecycle point invoked while the old block entity still exists, so use
     * it to preserve machine contents on any block destruction path.
     */
    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        if (level instanceof Level actualLevel && !actualLevel.isClientSide()) {
            BlockEntity blockEntity = actualLevel.getBlockEntity(pos);
            if (blockEntity instanceof CartridgeAssemblyMachineBlockEntity machine) {
                Containers.dropContents(actualLevel, pos, machine);
            }
        }
        super.destroy(level, pos, state);
    }
}
