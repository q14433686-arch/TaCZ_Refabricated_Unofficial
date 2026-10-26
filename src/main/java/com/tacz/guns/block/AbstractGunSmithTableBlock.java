package com.tacz.guns.block;

import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 多方块枪械工作台的公共基类（A 单格、B 2×1×1、C 1×2×1）。
 *
 * <h2>掉落只由战利品表负责 —— 不要在 {@code playerWillDestroy} 里手工补掉</h2>
 *
 * 26.2 的 {@code Block#updateOrDestroy} 在邻居 {@code updateShape} 返回空气时调用的是
 * {@code level.destroyBlock(pos, dropResources = true, ...)}（只要标志位里没有
 * {@code UPDATE_SUPPRESS_DROPS}）。也就是说<b>被连带清掉的另一半自己会走它自己的战利品表</b>
 * —— 原版高花、床正是靠这条路径做到「挖上半也只掉一份」。
 * 我们的 {@code data/tacz/loot_table/blocks/workbench_b.json} / {@code workbench_c.json}
 * 用 {@code block_state_property} 把产出限制在 {@code part=foot} / {@code half=lower}，
 * 并从<b>根</b>的方块实体拷 {@code BlockId}，于是：
 *
 * <ul>
 *   <li>挖根半边（foot / lower）：根自己产出 1 份；另一半被邻接更新清掉但战利品表不产出 → 合计 1 份。</li>
 *   <li>挖非根半边（head / upper）：它自己不产出；根半边经上面的 {@code destroyBlock} 产出 1 份
 *       （此时根的方块实体还没被删，{@code copy_custom_data} 仍能拿到 {@code BlockId}）→ 合计 1 份。</li>
 * </ul>
 *
 * 旧代码曾在非根半边的 {@code playerWillDestroy} 里额外 {@code popResource} 一份
 * {@link #getCloneItemStack} 克隆，与上面第二条叠加，导致<b>挖上半 / 头部爆出两个工作台</b>
 * （26.2 玩家实测报告）。26.3 线先一步删掉了同一段代码（其 commit {@code 021b546}），
 * 本分支于 2026-10-26 对齐。要验证掉落请只改战利品表，别在这里补 {@code popResource}。
 */
public abstract class AbstractGunSmithTableBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public AbstractGunSmithTableBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack usedStack, BlockState pState, Level level, BlockPos pos, Player player, InteractionHand pHand, BlockHitResult pHit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        } else {
            BlockEntity blockEntity = level.getBlockEntity(getRootPos(pos, pState));
            if (blockEntity instanceof GunSmithTableBlockEntity gunSmithTable && player instanceof ServerPlayer serverPlayer) {
                // 26.2: GunSmithTableBlockEntity 实现 ExtendedMenuProvider<Identifier>，必须直接传递以保留额外数据
                serverPlayer.openMenu(gunSmithTable);
            }
            return InteractionResult.CONSUME;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState blockState) {
        // Only the root owns the table identity and menu. Giving an invisible companion
        // its own block entity also makes Carry On treat that half as an independent block.
        return isRoot(blockState) ? new GunSmithTableBlockEntity(pos, blockState) : null;
    }

    /**
     * Returns the second position of a multi-block table, or {@code null} for a single-block table.
     * The supplied state is always the root state.
     */
    @Nullable
    public BlockPos getCompanionPos(BlockPos rootPos, BlockState rootState) {
        return null;
    }

    /** Returns the state that belongs at {@link #getCompanionPos(BlockPos, BlockState)}. */
    @Nullable
    public BlockState getCompanionState(BlockState rootState) {
        return null;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide() || state.is(oldState.getBlock()) || !isRoot(state)) {
            return;
        }

        BlockPos companionPos = getCompanionPos(pos, state);
        BlockState companionState = getCompanionState(state);
        if (companionPos != null && companionState != null
                && level.getWorldBorder().isWithinBounds(companionPos)
                && !level.isOutsideBuildHeight(companionPos)
                && level.getBlockState(companionPos).canBeReplaced()) {
            // Carry On restores blocks through setBlockAndUpdate and never calls setPlacedBy.
            // Rebuild the companion here so every block-state placement path remains complete.
            level.setBlock(companionPos, companionState, Block.UPDATE_ALL);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState pState) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (!world.isClientSide()) {
            if (stack.getItem() instanceof BlockItemDataAccessor accessor) {
                Identifier id = accessor.getBlockId(stack);
                BlockEntity blockentity = world.getBlockEntity(pos);
                if (blockentity instanceof GunSmithTableBlockEntity e) {
                    e.setId(id);
                }
            }
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeEntity) {
        BlockPos blockPos = getRootPos(pos, state);
        BlockEntity blockentity = level.getBlockEntity(blockPos);
        if (blockentity instanceof GunSmithTableBlockEntity e) {
            if (e.getId() != null) {
                return BlockItemBuilder.create(this).setId(e.getId()).build();
            }
            return new ItemStack(this);
        }
        return super.getCloneItemStack(level, pos, state, false);
    }

    public abstract boolean isRoot(BlockState blockState);

    public float parseRotation(Direction direction) {
        return 90.0F * (3 - direction.get2DDataValue()) - 90;
    }

    public abstract BlockPos getRootPos(BlockPos pos, BlockState blockState);
}
