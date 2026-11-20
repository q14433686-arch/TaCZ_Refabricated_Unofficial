package com.tacz.guns.block.entity;

import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public class GunDisplayCaseBlockEntity extends DisplayRackBlockEntity {
    public static final BlockEntityType<GunDisplayCaseBlockEntity> TYPE = net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(
            GunDisplayCaseBlockEntity::new, ModBlocks.GUN_DISPLAY_CASE).build();

    public GunDisplayCaseBlockEntity(BlockPos pos, BlockState state) { super(TYPE, pos, state, SLOTS_PER_FACE); }
    @Override public Direction getSlotFacing(BlockState state, int slot) { return state.getValue(GunDisplayCaseBlock.FACING); }
    @Override public boolean isGlowing() { return true; }
}
