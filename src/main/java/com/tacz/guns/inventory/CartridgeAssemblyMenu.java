package com.tacz.guns.inventory;

import com.tacz.guns.block.entity.CartridgeAssemblyMachineBlockEntity;
import com.tacz.guns.init.ModBlocks;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu for the dedicated four-input cartridge assembly machine. */
public final class CartridgeAssemblyMenu extends AbstractContainerMenu {
    public static final ExtendedMenuType<CartridgeAssemblyMenu, BlockPos> TYPE = new ExtendedMenuType<>(
            (containerId, inventory, pos) -> new CartridgeAssemblyMenu(containerId, inventory, pos),
            BlockPos.STREAM_CODEC
    );

    private final Container machineInventory;
    private final BlockPos machinePos;
    private int clientAutoProgress;

    /** Client constructor: slot data is synchronized by the vanilla menu protocol. */
    public CartridgeAssemblyMenu(int containerId, Inventory playerInventory, BlockPos machinePos) {
        this(containerId, playerInventory, new SimpleContainer(CartridgeAssemblyMachineBlockEntity.SLOT_COUNT), machinePos);
    }

    public CartridgeAssemblyMenu(int containerId, Inventory playerInventory, Container machineInventory, BlockPos machinePos) {
        super(TYPE, containerId);
        checkContainerSize(machineInventory, CartridgeAssemblyMachineBlockEntity.SLOT_COUNT);
        this.machineInventory = machineInventory;
        this.machinePos = machinePos;

        addSlot(new Slot(machineInventory, CartridgeAssemblyMachineBlockEntity.CASE_SLOT, 26, 35));
        addSlot(new Slot(machineInventory, CartridgeAssemblyMachineBlockEntity.PROJECTILE_SLOT, 62, 35));
        addSlot(new Slot(machineInventory, CartridgeAssemblyMachineBlockEntity.PRIMER_SLOT, 26, 62));
        addSlot(new Slot(machineInventory, CartridgeAssemblyMachineBlockEntity.PROPELLANT_SLOT, 62, 62));
        addSlot(new Slot(machineInventory, CartridgeAssemblyMachineBlockEntity.OUTPUT_SLOT, 132, 48) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return machineInventory instanceof CartridgeAssemblyMachineBlockEntity machine
                        ? machine.getAutoProgress() : clientAutoProgress;
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

    public void assemble(ServerPlayer player) {
        if (machineInventory instanceof CartridgeAssemblyMachineBlockEntity machine) {
            machine.assemble(player);
        }
    }

    public int getAutoProgress() {
        return machineInventory instanceof CartridgeAssemblyMachineBlockEntity machine
                ? machine.getAutoProgress() : clientAutoProgress;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty = ItemStack.EMPTY;
        if (index < 0 || index >= slots.size()) {
            return empty;
        }
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) {
            return empty;
        }
        ItemStack source = sourceSlot.getItem();
        ItemStack copy = source.copy();
        int machineSlots = CartridgeAssemblyMachineBlockEntity.SLOT_COUNT;
        if (index < machineSlots) {
            if (!moveItemStackTo(source, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(source, 0, CartridgeAssemblyMachineBlockEntity.OUTPUT_SLOT, false)) {
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
        return player.level().getBlockState(machinePos).is(ModBlocks.CARTRIDGE_ASSEMBLY_MACHINE)
                && player.distanceToSqr(machinePos.getX() + 0.5, machinePos.getY() + 0.5, machinePos.getZ() + 0.5) <= 64.0;
    }
}
