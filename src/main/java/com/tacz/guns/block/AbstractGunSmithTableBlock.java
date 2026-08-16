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

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // 多方块工作台只有 root 方块实体保存 BlockId。生存模式破坏 head/upper
        // 等非 root 部分时，原版 loot table 只能看到被破坏方块，无法从 root
        // 方块实体复制自定义工作台 id；这里手动按 root 方块实体构造掉落物。
        if (!level.isClientSide() && !player.isCreative() && !isRoot(state)) {
            ItemStack drop = getCloneItemStack(level, pos, state, true);
            if (!drop.isEmpty()) {
                popResource(level, pos, drop);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    public abstract boolean isRoot(BlockState blockState);

    public float parseRotation(Direction direction) {
        return 90.0F * (3 - direction.get2DDataValue()) - 90;
    }

    public abstract BlockPos getRootPos(BlockPos pos, BlockState blockState);
}
