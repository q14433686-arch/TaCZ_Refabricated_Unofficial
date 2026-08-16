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

    /**
     * 上下半块的半块标记。
     *
     * <p>有意<b>不</b>使用原版 {@code BlockStateProperties.DOUBLE_BLOCK_HALF}
     * （{@code EnumProperty<DoubleBlockHalf>}）：Carry On 2.x 的拾取检查按「属性值类型」
     * 拒绝一切与门相同的 double block 属性（{@code PickupHandler.hasPropertyType(state,
     * DoorBlock.HALF)} 比较的是 value class），会导致配件工作台（workbench_c）无法搬运。
     * 改用自定义枚举后，属性名仍是 {@code "half"}、值名仍是 {@code lower/upper}，
     * 与 blockstate JSON 及已保存世界完全兼容；仅 Java 侧类型不同，从而绕过该检查。</p>
     */
    public enum TableHalf implements StringRepresentable {
        LOWER("lower"),
        UPPER("upper");

        private final String name;

        TableHalf(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // 多方块工作台的「自愈补全」：Carry On 等模组直接用 setBlock/setBlockAndUpdate
        // 放置方块，不会调用 setPlacedBy，UPPER 半块永远不会生成。把补全逻辑放在
        // onPlace 里，任何 setBlock 型放置（搬运放置、/setblock、结构方块）都会重建
        // 完整的两格结构；原版物品放置路径（先 getStateForPlacement 校验）行为不变。
        if (!level.isClientSide() && state.getValue(HALF) == TableHalf.LOWER && !state.is(oldState.getBlock())) {
            BlockPos above = pos.above();
            if (level.getBlockState(above).canBeReplaced()) {
                level.setBlock(above, state.setValue(HALF, TableHalf.UPPER), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess, BlockPos currentPos, Direction facing, BlockPos facingPos, BlockState facingState, RandomSource random) {
        TableHalf half = state.getValue(HALF);

        if (facing.getAxis() == Direction.Axis.Y) {
            if (half == TableHalf.LOWER && facing == Direction.UP || half == TableHalf.UPPER && facing == Direction.DOWN) {
                // 拆一半另外一半跟着没
                if (!facingState.is(this)) {
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
}
