package com.tacz.guns.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * 双方块的枪械工作台，1x2x1
 */
public class GunSmithTableBlockC extends AbstractGunSmithTableBlock {
    public static final MapCodec<GunSmithTableBlockC> CODEC = simpleCodec(GunSmithTableBlockC::new);
    public static final EnumProperty<TableHalf> HALF = EnumProperty.create("half", TableHalf.class);

    public GunSmithTableBlockC(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, TableHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends GunSmithTableBlockC> codec() {
        return CODEC;
    }


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }


    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getHorizontalDirection().getOpposite();
        BlockPos clickedPos = context.getClickedPos();
        BlockPos above = clickedPos.above();
        Level level = context.getLevel();
        if (level.getBlockState(above).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(above)) {
            return this.defaultBlockState().setValue(FACING, direction);
        }
        return null;
    }

    @Override
    public BlockPos getCompanionPos(BlockPos rootPos, BlockState rootState) {
        return rootPos.above();
    }

    @Override
    public BlockState getCompanionState(BlockState rootState) {
        return rootState.setValue(HALF, TableHalf.UPPER);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess, BlockPos currentPos, Direction facing, BlockPos facingPos, BlockState facingState, RandomSource random) {
        TableHalf half = state.getValue(HALF);

        if (facing.getAxis() == Direction.Axis.Y) {
            if (half == TableHalf.LOWER && facing == Direction.UP || half == TableHalf.UPPER && facing == Direction.DOWN) {
                // 拆一半另外一半跟着没
                if (!facingState.is(this) || facingState.getValue(HALF) == half) {
                    return Blocks.AIR.defaultBlockState();
                }
            }
        }

        return state;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState blockState, Player player) {
        // 用于抑制创造模式下摧毁upper方块时lower的掉落
        if (!level.isClientSide() && player.isCreative()) {
            TableHalf half = blockState.getValue(HALF);
            if (half == TableHalf.UPPER) {
                BlockPos blockpos = pos.below();
                BlockState blockstate = level.getBlockState(blockpos);
                if (blockstate.is(this) && blockstate.getValue(HALF) == TableHalf.LOWER) {
                    level.setBlock(blockpos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, blockpos, Block.getId(blockstate));
                }
            }
        }
        return super.playerWillDestroy(level, pos, blockState, player);
    }

    @Override
    public boolean isRoot(BlockState blockState) {
        return blockState.getValue(HALF) == TableHalf.LOWER;
    }

    @Override
    public BlockPos getRootPos(BlockPos pos, BlockState blockState) {
        return blockState.getValue(HALF) == TableHalf.LOWER ? pos : pos.below();
    }

    /**
     * Uses the same serialized names as vanilla's {@code DoubleBlockHalf} without sharing its
     * value class. Carry On 2.9.2 rejects every state with that vanilla value class before its
     * normal permission checks, even for blocks whose multi-block lifecycle is handled here.
     */
    public enum TableHalf implements StringRepresentable {
        LOWER("lower"),
        UPPER("upper");

        private final String serializedName;

        TableHalf(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
