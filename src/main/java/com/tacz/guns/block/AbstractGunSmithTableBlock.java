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
import net.minecraft.world.level.material.PushReaction;
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
                // 26.2: GunSmithTableBlockEntity 实现 ExtendedScreenHandlerFactory<Identifier>，必须直接传递以保留额外数据
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
        // 只有 root（foot/lower）部分持有方块实体。非 root 部分不需要也不持有：
        // 1) 渲染/菜单/掉落都从 root 方块实体读取（getRootPos）；
        // 2) Carry On 2.x 默认只搬运「有方块实体的方块」，非 root 部分无方块实体后，
        //    搬运半块会被拒绝（右击回归打开菜单），避免把 invisible 的 HEAD/UPPER
        //    半块单独搬走产生「有碰撞箱的空气」幽灵方块。
        return isRoot(blockState) ? new GunSmithTableBlockEntity(pos, blockState) : null;
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
