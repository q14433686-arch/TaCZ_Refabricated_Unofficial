package com.tacz.guns.block.entity;

import com.tacz.guns.GunMod;
import com.tacz.guns.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Set;

import static com.tacz.guns.block.StatueBlock.FACING;

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
        this.gunItem = stack.copyWithCount(1); // placement debits one item, even for a custom stackable gun
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
        this.setChanged();
    }

    public void dropItem() {
        dropItem(getBlockState());
    }

    private void dropItem(BlockState state) {
        if (gunItem.isEmpty() || level == null || level.isClientSide()) {
            return;
        }
        Direction direction = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        Block.popResource(level, worldPosition.relative(direction).above(), gunItem);
        this.gunItem = ItemStack.EMPTY;
        BlockState currentState = level.getBlockState(worldPosition);
        level.sendBlockUpdated(worldPosition, currentState, currentState, Block.UPDATE_ALL);
        this.setChanged();
    }

    /** Drop the stored gun whenever the statue block entity is removed (break, explosion, piston, etc.). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        dropItem(state);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try {
            this.gunItem = input.read(ITEM_TAG, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        } catch (RuntimeException ex) {
            GunMod.LOGGER.warn("Cannot decode the stored statue item; leaving the display empty", ex);
            this.gunItem = ItemStack.EMPTY;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store(ITEM_TAG, ItemStack.OPTIONAL_CODEC, gunItem);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        // As on disk: use this world's registries for components with holder references.
        // Bare NbtOps can silently omit the entire displayed stack on the client.
        return saveWithoutMetadata(provider);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
