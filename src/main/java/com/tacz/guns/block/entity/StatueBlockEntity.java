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

public class StatueBlockEntity extends BlockEntity {
    public static final BlockEntityType<StatueBlockEntity> TYPE = new BlockEntityType<>(StatueBlockEntity::new, ModBlocks.STATUE);
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
        if (!gunItem.isEmpty() && level != null) {
            Direction direction = getBlockState().getValue(FACING);
            Block.popResource(level, worldPosition.relative(direction).above(), gunItem);
            this.gunItem = ItemStack.EMPTY;
            if (level != null) {
                BlockState state = level.getBlockState(worldPosition);
                level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
            }
            this.setChanged();
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
