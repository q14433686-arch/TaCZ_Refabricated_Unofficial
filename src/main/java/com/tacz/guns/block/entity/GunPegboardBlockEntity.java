package com.tacz.guns.block.entity;

import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.PegboardShape;
import com.tacz.guns.init.ModBlocks;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Set;

public class GunPegboardBlockEntity extends DisplayRackBlockEntity implements RenderDataBlockEntity {
    public static final int SLOT_COUNT = 2 * SLOTS_PER_FACE;
    public static final BlockEntityType<GunPegboardBlockEntity> TYPE = net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(GunPegboardBlockEntity::new, ModBlocks.GUN_PEGBOARD, ModBlocks.GLOW_GUN_PEGBOARD).build();
    private int color = -1;
    private boolean inheritedColor;

    public GunPegboardBlockEntity(BlockPos pos, BlockState state) { super(TYPE, pos, state, SLOT_COUNT); }
    @Override protected boolean active(BlockState state, int slot) { return GunPegboardSlotHelper.isSlotActive(state, slot); }
    @Override public Direction getSlotFacing(BlockState state, int slot) { return GunPegboardSlotHelper.getSlotFacing(state, slot); }
    @Override public boolean isGlowing() { return getBlockState().getBlock() instanceof GunPegboardBlock b && b.isGlowing(); }
    public int getColor() { return color; }
    public boolean hasInheritedColor() { return inheritedColor; }
    /** A chunk mesher gets an immutable color snapshot, never a live BE/inventory. */
    @Override public Integer getRenderData() { return color; }

    public void setColor(int rgb, boolean inherited) {
        int next = rgb < 0 ? -1 : rgb & 0xffffff;
        boolean linked = inherited && next >= 0;
        if (next == color && linked == inheritedColor) return;
        color = next;
        inheritedColor = linked;
        if (level != null && !level.isClientSide()) level.setBlock(worldPosition, getBlockState().setValue(GunPegboardBlock.DYED, color >= 0), Block.UPDATE_ALL);
        changedAndSync();
    }

    @Override public void setBlockState(BlockState state) {
        var previous = getBlockState();
        super.setBlockState(state);
        if (level == null || level.isClientSide() || previous.getValue(GunPegboardBlock.SHAPE) == state.getValue(GunPegboardBlock.SHAPE)) return;
        boolean changed = false;
        for (int slot = 0; slot < slotCount(); ++slot) {
            if (!items[slot].isEmpty() && (slot >= SLOTS_PER_FACE || !active(state, slot))) {
                removeSlot(slot, previous, true);
                changed = true;
            }
        }
        if (changed) changedAndSync();
    }

    @Override public void loadAdditional(ValueInput data) {
        super.loadAdditional(data);
        int oldColor = color;
        int rgb = data.getIntOr("Color", -1);
        color = rgb < 0 ? -1 : rgb & 0xffffff;
        inheritedColor = color >= 0 && data.getBooleanOr("InheritedColor", false);
        var shape = getBlockState().getValue(GunPegboardBlock.SHAPE);
        String savedShape = data.getStringOr("SlotShape", "");
        if (shape == PegboardShape.CORNER_LEFT && savedShape.equals("corner_right") || shape == PegboardShape.CORNER_RIGHT && savedShape.equals("corner_left")) {
            var original = items.clone();
            var angles = rotations.clone();
            for (int i = 0; i < slotCount(); ++i) {
                int mirror = i - i % GRID_SIZE + GRID_SIZE - 1 - i % GRID_SIZE;
                items[mirror] = original[i];
                rotations[mirror] = (byte) (-angles[i] & 7);
            }
        }
        if (color != oldColor && level != null && level.isClientSide()) level.sendBlockUpdated(worldPosition, null, null, 0);
    }

    @Override protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        if (color >= 0) data.putInt("Color", color);
        if (inheritedColor) data.putBoolean("InheritedColor", true);
        data.putString("SlotShape", getBlockState().getValue(GunPegboardBlock.SHAPE).getSerializedName());
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = super.getUpdateTag(registries);
        tag.remove("SlotShape"); // A client update can race a blockstate update; only structure loading mirrors slots.
        return tag;
    }
}
