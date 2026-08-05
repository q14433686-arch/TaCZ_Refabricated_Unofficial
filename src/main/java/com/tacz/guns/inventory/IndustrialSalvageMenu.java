package com.tacz.guns.inventory;

import com.tacz.guns.block.entity.IndustrialSalvageStationBlockEntity;
import com.tacz.guns.init.ModBlocks;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu for one-input, multi-output industrial recovery. */
public final class IndustrialSalvageMenu extends AbstractContainerMenu {
    public static final ExtendedMenuType<IndustrialSalvageMenu, BlockPos> TYPE = new ExtendedMenuType<>(
            (containerId, inventory, pos) -> new IndustrialSalvageMenu(containerId, inventory, pos),
            BlockPos.STREAM_CODEC
    );

    private final Container stationInventory;
    private final BlockPos stationPos;
    private int clientAutoProgress;

    public IndustrialSalvageMenu(int containerId, Inventory playerInventory, BlockPos stationPos) {
        this(containerId, playerInventory, new SimpleContainer(IndustrialSalvageStationBlockEntity.SLOT_COUNT), stationPos);
    }

    public IndustrialSalvageMenu(int containerId, Inventory playerInventory, Container stationInventory, BlockPos stationPos) {
        super(TYPE, containerId);
        checkContainerSize(stationInventory, IndustrialSalvageStationBlockEntity.SLOT_COUNT);
        this.stationInventory = stationInventory;
        this.stationPos = stationPos;

        addSlot(new Slot(stationInventory, IndustrialSalvageStationBlockEntity.INPUT_SLOT, 29, 43));
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 3; column++) {
                int output = IndustrialSalvageStationBlockEntity.OUTPUT_START + column + row * 3;
                addSlot(new Slot(stationInventory, output, 100 + column * 18, 28 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        }

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return stationInventory instanceof IndustrialSalvageStationBlockEntity station
                        ? station.getAutoProgress() : clientAutoProgress;
            }

            @Override
            public void set(int value) {
                clientAutoProgress = value;
            }
        });
        addPlayerInventory(playerInventory);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 104 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 162));
        }
    }

    public int getAutoProgress() {
        return stationInventory instanceof IndustrialSalvageStationBlockEntity station
                ? station.getAutoProgress() : clientAutoProgress;
    }

    public void salvage(net.minecraft.server.level.ServerPlayer player) {
        if (stationInventory instanceof IndustrialSalvageStationBlockEntity station) {
            station.salvage(player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = sourceSlot.getItem();
        ItemStack copy = source.copy();
        int machineSlots = IndustrialSalvageStationBlockEntity.SLOT_COUNT;
        if (index < machineSlots) {
            if (!moveItemStackTo(source, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(source, IndustrialSalvageStationBlockEntity.INPUT_SLOT,
                IndustrialSalvageStationBlockEntity.OUTPUT_START, false)) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(stationPos).is(ModBlocks.INDUSTRIAL_SALVAGE_STATION)
                && player.distanceToSqr(stationPos.getX() + 0.5, stationPos.getY() + 0.5, stationPos.getZ() + 0.5) <= 64.0;
    }
}
