package com.tacz.guns.block.entity;

import com.tacz.guns.GunMod;
import com.tacz.guns.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

import static com.tacz.guns.block.StatueBlock.FACING;

/**
 * 雕像方块实体：保存「摆在雕像上的那把枪」。
 *
 * <h2>为什么必须覆写 {@link #preRemoveSideEffects}</h2>
 *
 * 上游 1.20.1 的雕像是在 {@code Block#onRemove} 里调用 {@code dropItem()} 的。1.21.11 里
 * {@code Block#onRemove} 已被<b>整个删除</b>（{@code BlockBehaviour} 里只剩
 * {@code affectNeighborsAfterRemoval}），也<b>没有</b>任何「方块实体被移除时自动清理 / 掉落」的
 * 默认行为 —— {@code BlockEntity#preRemoveSideEffects} 的默认实现只会倒空 {@code Container}，
 * 雕像不是容器。本线移植时只留下了「方块实体移除时自动清理」的注释，枪就随雕像静默消失
 * （26.2 线 2026-10-26 玩家实测确认；本线代码在修前除 {@code TYPE} 的构造写法外与 26.2 逐字等价，
 * 离线审计 {@code scripts/verify_block_entity_persistence.py} 在本线修前同样报出这一项；
 * 本线本身未实机复现，见 {@code docs/lineage/SYNC_REVIEW_262_TO_1211_20261027.md}）。
 *
 * <p>正确的扩展点是 {@code BlockEntity#preRemoveSideEffects(BlockPos, BlockState)}：
 * 原版容器（箱子、木桶、潜影盒……）都靠它在方块被移除前把内容物倒出来。它由
 * {@code LevelChunk#setBlockState} 在「旧状态带方块实体、方块种类变了、且标志位里没有
 * {@code UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS}」时于<b>服务端</b>调用（1.21.11 反编译源码核对），
 * 覆盖玩家挖掘 / 爆炸（{@code onExplosionHit} 的 {@code setBlock(AIR)}）/ 活塞顶掉 /
 * {@code /setblock} 覆盖。也正因为原版容器走这条路径，方块自己的战利品表里<b>不能</b>
 * 再写一份物品掉落，否则就是双份 —— 这里的枪同理，只由本方法掉。</p>
 */
public class StatueBlockEntity extends BlockEntity {
    public static final BlockEntityType<StatueBlockEntity> TYPE = net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(StatueBlockEntity::new, ModBlocks.STATUE).build();
    private static final String ITEM_TAG = "Item";
    private ItemStack gunItem = ItemStack.EMPTY;

    public StatueBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(TYPE, pPos, pBlockState);
    }

    public static void clientTick(Level level, BlockPos blockPos, BlockState state, StatueBlockEntity statueBlockEntity) {
        if (level.getGameTime() % 100 == 0 && !statueBlockEntity.gunItem.isEmpty()) {
            Direction direction = state.getValue(FACING);

            double x = blockPos.getX() + direction.getStepX() * 0.75 + 0.5;
            double z = blockPos.getZ() + direction.getStepZ() * 0.75 + 0.5;

            double dx = -0.02 + level.getRandom().nextDouble() * 0.04;
            double dz = -0.02 + level.getRandom().nextDouble() * 0.04;
            double dy = -0.02 + level.getRandom().nextDouble() * 0.04;

            level.addParticle(ParticleTypes.END_ROD, x, blockPos.getY() + 2.25, z, dx, dy, dz);
        }
    }

    public ItemStack getGunItem() {
        return gunItem;
    }

    public void setGun(ItemStack stack) {
        this.dropItem();
        this.gunItem = stack.copy();
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
        this.setChanged();
    }

    public void dropItem() {
        this.dropItem(this.getBlockState());
    }

    /**
     * 把枪以掉落物形式弹出，并清空自身。
     *
     * <p>不能直接用 {@code getBlockState()}：{@link #preRemoveSideEffects} 在方块已被
     * 替换/移除的时点被调用，此时方块实体的缓存状态可能已经是空气，对它取
     * {@code FACING} 会抛 {@code IllegalArgumentException}（"Cannot get property
     * facing from air"）。因此由调用方传入可用的状态，并对没有该属性的状态回退到
     * {@link Direction#NORTH}（扔在雕像正前方一格上方）。</p>
     */
    private void dropItem(BlockState state) {
        if (!gunItem.isEmpty() && level != null) {
            Direction direction = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
            Block.popResource(level, worldPosition.relative(direction).above(), gunItem);
            this.gunItem = ItemStack.EMPTY;
            BlockState current = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, current, current, Block.UPDATE_ALL);
            this.setChanged();
        }
    }

    /**
     * 方块实体被移除时的收尾（1.21.11 的容器掉落扩展点）。
     *
     * <p>原版调用点本身已限定服务端；这里的 {@code isClientSide} 判断是防御性的，
     * 防止其它模组/未来版本在客户端也走到这里时凭空生成幽灵掉落物。
     * {@code super} 必须调 —— 父类在这里处理 {@code Container} 的内容物。</p>
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) {
            this.dropItem(state);
        }
    }

    @Override
    public void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        // 26.2 data migration: ItemStack.OPTIONAL_CODEC may fail on legacy NBT data
        // where Item was stored as COMPOUND instead of the new string-based format.
        // Gracefully handle both formats.
        try {
            this.gunItem = input.read(ITEM_TAG, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            GunMod.LOGGER.warn("Failed to load statue gun item (legacy format?), resetting to empty", e);
            this.gunItem = ItemStack.EMPTY;
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
                output.store(ITEM_TAG, ItemStack.OPTIONAL_CODEC, gunItem);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        ItemStack.OPTIONAL_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, gunItem)
                .result().ifPresent(t -> tag.put(ITEM_TAG, t));
        return tag;
    }

    // In Minecraft 26.2+, getRenderBoundingBox() is handled differently
    // Rendering bounds are now typically managed through BlockEntityRenderers

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
