package com.tacz.guns.inventory;

import com.tacz.guns.block.entity.AmmunitionHandlingBenchBlockEntity;
import com.tacz.guns.industry.magazine.MagazineItemDataAccessor;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModItems;
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

/** Menu for the real ten-slot timed ammunition handling bench. */
public final class AmmunitionHandlingBenchMenu extends AbstractContainerMenu {
    public static final ExtendedMenuType<AmmunitionHandlingBenchMenu, BlockPos> TYPE = new ExtendedMenuType<>(
            (containerId, inventory, pos) -> new AmmunitionHandlingBenchMenu(containerId, inventory, pos),
            BlockPos.STREAM_CODEC
    );

    private final Container benchInventory;
    private final BlockPos benchPos;
    private int clientProgress;
    private int clientDuration;
    private int clientAction = -1;

    public AmmunitionHandlingBenchMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, new SimpleContainer(AmmunitionHandlingBenchBlockEntity.SLOT_COUNT), pos);
    }

    public AmmunitionHandlingBenchMenu(int containerId, Inventory playerInventory, Container bench, BlockPos pos) {
        super(TYPE, containerId);
        checkContainerSize(bench, AmmunitionHandlingBenchBlockEntity.SLOT_COUNT);
        this.benchInventory = bench;
        this.benchPos = pos;

        addSlot(new Slot(bench, AmmunitionHandlingBenchBlockEntity.CARRIER_SLOT, 24, 42));
        for (int index = 0; index < AmmunitionHandlingBenchBlockEntity.INPUT_COUNT; index++) {
            addSlot(new Slot(bench, AmmunitionHandlingBenchBlockEntity.INPUT_START + index,
                    64 + index * 18, 42));
        }
        addSlot(new Slot(bench, AmmunitionHandlingBenchBlockEntity.TOOL_SLOT, 24, 102));
        for (int index = 0; index < AmmunitionHandlingBenchBlockEntity.OUTPUT_COUNT; index++) {
            addSlot(new Slot(bench, AmmunitionHandlingBenchBlockEntity.OUTPUT_START + index,
                    64 + index * 18, 102) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        addDataSlot(dataSlot(0));
        addDataSlot(dataSlot(1));
        addDataSlot(dataSlot(2));
        addPlayerInventory(playerInventory);
    }

    private DataSlot dataSlot(int field) {
        return new DataSlot() {
            @Override
            public int get() {
                if (benchInventory instanceof AmmunitionHandlingBenchBlockEntity bench) {
                    return switch (field) {
                        case 0 -> bench.getOperationProgress();
                        case 1 -> bench.getOperationDuration();
                        default -> bench.getOperationActionId();
                    };
                }
                return switch (field) {
                    case 0 -> clientProgress;
                    case 1 -> clientDuration;
                    default -> clientAction;
                };
            }

            @Override
            public void set(int value) {
                switch (field) {
                    case 0 -> clientProgress = value;
                    case 1 -> clientDuration = value;
                    default -> clientAction = value;
                }
            }
        };
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 24 + column * 18, 151 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 24 + column * 18, 209));
        }
    }

    public void start(ServerPlayer player, int action, int inputIndex) {
        if (benchInventory instanceof AmmunitionHandlingBenchBlockEntity bench) {
            bench.start(player, action, inputIndex);
        }
    }

    public boolean isFor(BlockPos pos) {
        return benchPos.equals(pos);
    }

    public int getOperationProgress() {
        return benchInventory instanceof AmmunitionHandlingBenchBlockEntity bench
                ? bench.getOperationProgress() : clientProgress;
    }

    public int getOperationDuration() {
        return benchInventory instanceof AmmunitionHandlingBenchBlockEntity bench
                ? bench.getOperationDuration() : clientDuration;
    }

    public int getOperationActionId() {
        return benchInventory instanceof AmmunitionHandlingBenchBlockEntity bench
                ? bench.getOperationActionId() : clientAction;
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
        int benchSlots = AmmunitionHandlingBenchBlockEntity.SLOT_COUNT;
        if (index < benchSlots) {
            if (!moveItemStackTo(source, benchSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved;
            if (source.getItem() instanceof MagazineItemDataAccessor magazine && magazine.isConfigured(source)) {
                moved = moveItemStackTo(source, AmmunitionHandlingBenchBlockEntity.CARRIER_SLOT,
                        AmmunitionHandlingBenchBlockEntity.CARRIER_SLOT + 1, false);
            } else if (source.is(ModItems.MAGAZINE_LOADER)) {
                moved = moveItemStackTo(source, AmmunitionHandlingBenchBlockEntity.TOOL_SLOT,
                        AmmunitionHandlingBenchBlockEntity.TOOL_SLOT + 1, false);
            } else if (source.getItem() instanceof com.tacz.guns.api.item.IAmmo) {
                moved = moveItemStackTo(source, AmmunitionHandlingBenchBlockEntity.INPUT_START,
                        AmmunitionHandlingBenchBlockEntity.INPUT_START + AmmunitionHandlingBenchBlockEntity.INPUT_COUNT, false);
            } else {
                moved = false;
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
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
        return player.level().getBlockState(benchPos).is(ModBlocks.AMMUNITION_HANDLING_BENCH)
                && player.distanceToSqr(benchPos.getX() + 0.5D, benchPos.getY() + 0.5D, benchPos.getZ() + 0.5D) <= 64.0D;
    }
}
