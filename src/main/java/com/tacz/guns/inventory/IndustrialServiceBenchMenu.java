package com.tacz.guns.inventory;

import com.tacz.guns.block.entity.IndustrialServiceBenchBlockEntity;
import com.tacz.guns.industry.service.IndustrialServiceBenchService;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModItems;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Client/server menu for the real thirteen-slot industrial service transaction. */
public final class IndustrialServiceBenchMenu extends AbstractContainerMenu {
    public static final ExtendedMenuType<IndustrialServiceBenchMenu, BlockPos> TYPE = new ExtendedMenuType<>(
            (containerId, inventory, pos) -> new IndustrialServiceBenchMenu(containerId, inventory, pos), BlockPos.STREAM_CODEC
    );

    private final Container bench;
    private final BlockPos pos;

    public IndustrialServiceBenchMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, new SimpleContainer(IndustrialServiceBenchBlockEntity.SLOT_COUNT), pos);
    }

    public IndustrialServiceBenchMenu(int containerId, Inventory playerInventory, Container bench, BlockPos pos) {
        super(TYPE, containerId);
        checkContainerSize(bench, IndustrialServiceBenchBlockEntity.SLOT_COUNT);
        this.bench = bench;
        this.pos = pos;

        addSlot(new Slot(bench, IndustrialServiceBenchBlockEntity.GUN_INPUT, 25, 43));
        addSlot(new Slot(bench, IndustrialServiceBenchBlockEntity.BLUEPRINT, 60, 25));
        addSlot(new Slot(bench, IndustrialServiceBenchBlockEntity.FIXTURE, 96, 25));
        addSlot(new Slot(bench, IndustrialServiceBenchBlockEntity.WRENCH, 132, 25));
        for (int index = 0; index < IndustrialServiceBenchBlockEntity.COMPONENT_COUNT; index++) {
            addSlot(new Slot(bench, IndustrialServiceBenchBlockEntity.COMPONENT_START + index, 52 + index * 18, 75));
        }
        addSlot(new Slot(bench, IndustrialServiceBenchBlockEntity.GUN_OUTPUT, 181, 43) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addSlot(new Slot(bench, IndustrialServiceBenchBlockEntity.STEEL_MATERIAL, 151, 75));
        addSlot(new Slot(bench, IndustrialServiceBenchBlockEntity.BRASS_MATERIAL, 187, 75));
        addSlot(new Slot(bench, IndustrialServiceBenchBlockEntity.CLEANING_MATERIAL, 25, 75));
        addPlayerInventory(playerInventory);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 24 + column * 18, 157 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 24 + column * 18, 215));
        }
    }

    public void service(net.minecraft.server.level.ServerPlayer player, int action) {
        if (bench instanceof IndustrialServiceBenchBlockEntity entity) {
            entity.service(player, action);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = sourceSlot.getItem();
        ItemStack copy = source.copy();
        int machineSlots = IndustrialServiceBenchBlockEntity.SLOT_COUNT;
        if (index < machineSlots) {
            if (!moveItemStackTo(source, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved;
            if (source.getItem() instanceof com.tacz.guns.api.item.IGun) {
                moved = moveItemStackTo(source, IndustrialServiceBenchBlockEntity.GUN_INPUT,
                        IndustrialServiceBenchBlockEntity.GUN_INPUT + 1, false);
            } else if (source.is(ModItems.GUN_BLUEPRINT)) {
                moved = moveItemStackTo(source, IndustrialServiceBenchBlockEntity.BLUEPRINT,
                        IndustrialServiceBenchBlockEntity.BLUEPRINT + 1, false);
            } else if (source.is(ModItems.PRESS_DIE)) {
                moved = moveItemStackTo(source, IndustrialServiceBenchBlockEntity.FIXTURE,
                        IndustrialServiceBenchBlockEntity.FIXTURE + 1, false);
            } else if (source.is(ModItems.ARMORER_WRENCH)) {
                moved = moveItemStackTo(source, IndustrialServiceBenchBlockEntity.WRENCH,
                        IndustrialServiceBenchBlockEntity.WRENCH + 1, false);
            } else if (IndustrialServiceBenchService.isServiceComponent(source)) {
                moved = moveItemStackTo(source, IndustrialServiceBenchBlockEntity.COMPONENT_START,
                        IndustrialServiceBenchBlockEntity.COMPONENT_START + IndustrialServiceBenchBlockEntity.COMPONENT_COUNT, false);
            } else if (IndustrialServiceBenchService.isSteelRepairMaterial(source)) {
                moved = moveItemStackTo(source, IndustrialServiceBenchBlockEntity.STEEL_MATERIAL,
                        IndustrialServiceBenchBlockEntity.STEEL_MATERIAL + 1, false);
            } else if (IndustrialServiceBenchService.isBrassRepairMaterial(source)) {
                moved = moveItemStackTo(source, IndustrialServiceBenchBlockEntity.BRASS_MATERIAL,
                        IndustrialServiceBenchBlockEntity.BRASS_MATERIAL + 1, false);
            } else if (IndustrialServiceBenchService.isCleaningMaterial(source)) {
                moved = moveItemStackTo(source, IndustrialServiceBenchBlockEntity.CLEANING_MATERIAL,
                        IndustrialServiceBenchBlockEntity.CLEANING_MATERIAL + 1, false);
            } else {
                moved = false;
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) sourceSlot.set(ItemStack.EMPTY); else sourceSlot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(ModBlocks.INDUSTRIAL_SERVICE_BENCH)
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64.0;
    }
}
