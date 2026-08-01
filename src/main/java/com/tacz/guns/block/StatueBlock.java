package com.tacz.guns.block;

import com.mojang.serialization.MapCodec;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.entity.StatueBlockEntity;
import com.tacz.guns.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public class StatueBlock extends BaseEntityBlock {
    public static final MapCodec<StatueBlock> CODEC = simpleCodec(StatueBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public StatueBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return state.getValue(HALF).equals(DoubleBlockHalf.LOWER) && level.isClientSide() ? createTickerHelper(blockEntityType, ModBlocks.STATUE_BE, StatueBlockEntity::clientTick) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return pState.getValue(HALF) == DoubleBlockHalf.LOWER ? new StatueBlockEntity(pPos, pState) : null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState pState, Level level, BlockPos pos, Player player, InteractionHand pHand, BlockHitResult pHit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return useStatue(stack, pState, level, pos);
    }

    /**
     * 空手交互（取回雕像上的枪）。
     *
     * <p>原 mod 的 {@code use} 在 1.20.1 同时处理「持物品」与「空手」，之后原版把
     * 交互拆成了 {@code useItemOn} / {@code useWithoutItem} 两个回调。本移植原本只覆写了
     * {@code useItemOn}，若所在版本对空手点击不再回退调用 {@code useItemOn}，
     * 「空手取枪」分支就会变成死代码 —— 这里显式补上，确保与上游语义一致。</p>
     *
     * <p>注意守卫：手持任何物品时必须放行（返回 {@code PASS}），否则在
     * 「先调 useWithoutItem」的调度顺序下会抢在 {@code useItemOn}（放枪）之前触发取枪。</p>
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState pState, Level level, BlockPos pos, Player player, BlockHitResult pHit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return useStatue(ItemStack.EMPTY, pState, level, pos);
    }

    /** 上游 1.20.1 {@code use} 的完整逻辑：放枪 / 空手取枪 */
    private static InteractionResult useStatue(ItemStack handStack, BlockState pState, Level level, BlockPos pos) {
        if (pState.getValue(HALF) == DoubleBlockHalf.UPPER) {
            pos = pos.below();
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof StatueBlockEntity statueBlockEntity) {
            if (handStack.getItem() instanceof IGun) {
                statueBlockEntity.setGun(handStack);
                handStack.shrink(1);
                return InteractionResult.SUCCESS;
            }

            if (handStack.isEmpty()) {
                statueBlockEntity.dropItem();
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.CONSUME;
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
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (!world.isClientSide()) {
            BlockPos above = pos.above();
            world.setBlock(above, state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess, BlockPos currentPos, Direction facing, BlockPos facingPos, BlockState facingState, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);

        if (facing.getAxis() == Direction.Axis.Y) {
            if (half.equals(DoubleBlockHalf.LOWER) && facing == Direction.UP || half.equals(DoubleBlockHalf.UPPER) && facing == Direction.DOWN) {
                // 拆一半另外一半跟着没
                if (!facingState.is(this)) {
                    return Blocks.AIR.defaultBlockState();
                }
            }
        }

        return state;
    }

    // 26.2: onRemove 已移除，方块实体移除时自动清理
    // 如需掉落物品，在 StatueBlockEntity.setRemoved() 中处理

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * 【第 39 轮】从自建的 {@code IBlockExtension#tacz$onBlockExploded} + {@code ExplosionMixin}
     * 迁移到 <b>26.2 原版官方扩展点</b>。
     *
     * <h2>为什么原来的 mixin 必须废弃</h2>
     * 旧实现 {@code @Mixin(Explosion.class)} 注入 {@code finalizeExplosion}，
     * 但 26.2 里 {@code net.minecraft.world.level.Explosion} <b>已经变成接口</b>
     * （{@code extends Object}、零字段、方法全是 {@code level()}/{@code radius()} 这类访问器）：
     * <ul>
     *   <li>{@code finalizeExplosion} —— 不存在；</li>
     *   <li>{@code @Shadow @Final public Level level} —— 接口没有字段，无从 shadow。</li>
     * </ul>
     * 真正干活的实现类是新增的 {@code ServerExplosion}。
     *
     * <h2>为什么不改注入 {@code ServerExplosion}，而是直接覆写</h2>
     * 反汇编 {@code ServerExplosion#interactWithBlocks} 可见它对每个方块调用的是：
     * <pre>
     *   BlockState.onExplosionHit(ServerLevel, BlockPos, Explosion, BiConsumer&lt;ItemStack,BlockPos&gt;)
     * </pre>
     * 而 {@code onExplosionHit} 正是 {@code BlockBehaviour} 上的 <b>public 可覆写方法</b>，
     * 原版已有 9 个方块在用它做同类定制（{@code DoorBlock}、{@code BellBlock}、
     * {@code BeehiveBlock}、{@code AbstractCandleBlock} 等）。
     * 也就是说 26.2 已经<b>官方提供</b>了这个扩展点，再用 mixin 属于多此一举
     * —— 少一个 mixin 就少一处版本升级时会断的地方。
     *
     * <p>行为等价性：旧的 {@code tacz$onBlockExploded} 默认实现做的是
     * 「设为空气 + {@code wasExploded}」，而这正是父类默认实现的核心部分
     * （{@code BlockBehaviour#onExplosionHit} 会按 {@code dropFromExplosion} /
     * {@code hasBlockEntity} 处理掉落后置空方块并回调 {@code wasExploded}）。
     * 因此这里直接调 {@code super} 即可，语义不变。</p>
     *
     * <p>雕像的物品掉落仍由 {@code StatueBlockEntity#setRemoved()} 负责，与本方法无关。</p>
     */
    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
                                  BiConsumer<ItemStack, BlockPos> dropConsumer) {
        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }
}
