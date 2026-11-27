package com.tacz.guns.block.entity;

import com.tacz.guns.config.util.GunPegboardItemConfigRead;
import net.fabricmc.fabric.api.serialization.v1.view.FabricReadView;
import net.fabricmc.fabric.api.serialization.v1.view.FabricWriteView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Arrays;

/** Shared inventory of the display blocks. One component-bearing item per active cell, not a hopper container. */
public abstract class DisplayRackBlockEntity extends BlockEntity implements Clearable {
    public static final int GRID_SIZE = 4;
    public static final int SLOTS_PER_FACE = GRID_SIZE * GRID_SIZE;
    protected final ItemStack[] items;
    protected final byte[] rotations;

    protected DisplayRackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int count) {
        super(type, pos, state);
        items = new ItemStack[count];
        Arrays.fill(items, ItemStack.EMPTY);
        rotations = new byte[count];
    }

    public final int slotCount() { return items.length; }
    protected boolean active(BlockState state, int slot) { return slot >= 0 && slot < slotCount(); }
    public abstract Direction getSlotFacing(BlockState state, int slot);
    public abstract boolean isGlowing();
    public final ItemStack getItem(int slot) { return active(getBlockState(), slot) ? items[slot] : ItemStack.EMPTY; }
    public final int getRotation(int slot) { return active(getBlockState(), slot) ? rotations[slot] & 7 : 0; }

    public final boolean setItem(int slot, ItemStack stack) {
        if (!active(getBlockState(), slot) || !items[slot].isEmpty() || !GunPegboardItemConfigRead.canPlace(stack)) return false;
        items[slot] = stack.copyWithCount(1);
        rotations[slot] = 0;
        changedAndSync();
        return true;
    }

    public final boolean rotateItem(int slot) {
        if (getItem(slot).isEmpty()) return false;
        rotations[slot] = (byte) ((rotations[slot] + 1) & 7);
        changedAndSync();
        return true;
    }

    public final boolean removeItem(int slot, boolean drop) {
        if (getItem(slot).isEmpty() || level == null || level.isClientSide()) return false;
        removeSlot(slot, getBlockState(), drop);
        changedAndSync();
        return true;
    }

    protected final void removeSlot(int slot, BlockState previous, boolean drop) {
        var removed = items[slot];
        items[slot] = ItemStack.EMPTY;
        rotations[slot] = 0;
        if (drop && !removed.isEmpty()) Block.popResource(level, worldPosition.relative(getSlotFacing(previous, slot)), removed);
    }

    @Override public final void clearContent() {
        Arrays.fill(items, ItemStack.EMPTY);
        Arrays.fill(rotations, (byte) 0);
        changedAndSync();
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
        super.preRemoveSideEffects(pos, oldState);
        if (level == null || level.isClientSide()) return;
        for (int i = 0; i < slotCount(); ++i) removeSlot(i, oldState, true);
    }

    protected final void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override public void loadAdditional(ValueInput data) {
        super.loadAdditional(data);
        Arrays.fill(items, ItemStack.EMPTY);
        Arrays.fill(rotations, (byte) 0);
        // Do not re-apply today's whitelist to saved contents: packs/config can change while a wall is loaded.
        for (var entry : data.childrenListOrEmpty("Items")) {
            int slot = entry.getByteOr("Slot", (byte) -1);
            if (slot < 0 || slot >= slotCount() || !items[slot].isEmpty()) continue;
            entry.read("Item", ItemStack.CODEC).ifPresent(stack -> items[slot] = stack.copyWithCount(1));
        }
        ((FabricReadView) data).getOptionalByteArray("Rotations").ifPresent(bytes -> {
            for (int i = 0; i < Math.min(bytes.length, slotCount()); ++i) rotations[i] = (byte) (bytes[i] & 7);
        });
    }

    @Override protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        var list = data.childrenList("Items");
        for (int i = 0; i < slotCount(); ++i) if (!items[i].isEmpty()) {
            var entry = list.addChild();
            entry.putByte("Slot", (byte) i);
            entry.store("Item", ItemStack.CODEC, items[i]);
        }
        ((FabricWriteView) data).putByteArray("Rotations", rotations.clone());
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
