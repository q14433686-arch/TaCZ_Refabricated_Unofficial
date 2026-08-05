package com.tacz.guns.block.entity;

import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.industry.salvage.IndustrialSalvageService;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.inventory.IndustrialSalvageMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Explicit one-input industrial recovery station for empty magazines, obsolete
 * dies and safely stripped industrial firearms.
 */
public final class IndustrialSalvageStationBlockEntity extends BlockEntity
        implements Container, WorldlyContainer, ExtendedMenuProvider<BlockPos> {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_START = 1;
    public static final int OUTPUT_COUNT = 6;
    public static final int SLOT_COUNT = OUTPUT_START + OUTPUT_COUNT;
    public static final int AUTO_PROCESS_TICKS = 40;

    private static final int[] INPUT_SLOTS = {INPUT_SLOT};
    private static final int[] OUTPUT_SLOTS = {1, 2, 3, 4, 5, 6};

    public static final BlockEntityType<IndustrialSalvageStationBlockEntity> TYPE = new BlockEntityType<>(
            IndustrialSalvageStationBlockEntity::new, Set.of(ModBlocks.INDUSTRIAL_SALVAGE_STATION)
    );

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setChanged() {
            IndustrialSalvageStationBlockEntity.this.setChanged();
        }
    };
    private int autoProgress;

    public IndustrialSalvageStationBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IndustrialSalvageStationBlockEntity station) {
        station.tickAutomation(level);
    }

    private void tickAutomation(Level level) {
        if (!IndustryProfileManager.isCreateFlyProfileActive() || !level.hasNeighborSignal(worldPosition)) {
            autoProgress = 0;
            return;
        }
        IndustrialSalvageService.Plan plan = findPlan();
        if (plan == null) {
            autoProgress = 0;
            return;
        }
        autoProgress++;
        if (autoProgress >= AUTO_PROCESS_TICKS) {
            complete(plan);
            autoProgress = 0;
        }
    }

    public int getAutoProgress() {
        return autoProgress;
    }

    /** Called only after the C2S menu id/type check. */
    public boolean salvage(ServerPlayer player) {
        SalvageResult result = trySalvage();
        switch (result.kind()) {
            case SUCCESS -> {
                player.containerMenu.broadcastFullState();
                return true;
            }
            case OUTPUT_BLOCKED -> player.sendSystemMessage(
                    Component.translatable("message.tacz.industrial_salvage.output_blocked"), true);
            case PROFILE_DISABLED -> player.sendSystemMessage(
                    Component.translatable("message.tacz.industrial_salvage.invalid"), true);
            case INVALID_INPUT -> {
                IndustrialSalvageService.Failure failure = result.failure();
                player.sendSystemMessage(Component.translatable(
                        failure == null ? "message.tacz.industrial_salvage.invalid" : failure.translationKey()), true);
            }
        }
        return false;
    }

    private SalvageResult trySalvage() {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            return SalvageResult.profileDisabled();
        }
        IndustrialSalvageService.Plan plan = IndustrialSalvageService.plan(getItem(INPUT_SLOT));
        if (!plan.isSuccess()) {
            return SalvageResult.invalid(plan.failure());
        }
        if (!canAcceptOutputs(plan.outputs())) {
            return SalvageResult.outputBlocked();
        }
        complete(plan);
        autoProgress = 0;
        return SalvageResult.success();
    }

    @Nullable
    private IndustrialSalvageService.Plan findPlan() {
        IndustrialSalvageService.Plan plan = IndustrialSalvageService.plan(getItem(INPUT_SLOT));
        return plan.isSuccess() && canAcceptOutputs(plan.outputs()) ? plan : null;
    }

    private boolean canAcceptOutputs(List<ItemStack> outputs) {
        List<ItemStack> simulated = new ArrayList<>(OUTPUT_COUNT);
        for (int slot = OUTPUT_START; slot < SLOT_COUNT; slot++) {
            simulated.add(getItem(slot).copy());
        }
        for (ItemStack rawOutput : outputs) {
            ItemStack remaining = rawOutput.copy();
            for (int index = 0; index < simulated.size() && !remaining.isEmpty(); index++) {
                ItemStack existing = simulated.get(index);
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remaining)) {
                    int accepted = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                    if (accepted > 0) {
                        existing.grow(accepted);
                        remaining.shrink(accepted);
                    }
                }
            }
            for (int index = 0; index < simulated.size() && !remaining.isEmpty(); index++) {
                if (simulated.get(index).isEmpty()) {
                    int accepted = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                    ItemStack inserted = remaining.copyWithCount(accepted);
                    simulated.set(index, inserted);
                    remaining.shrink(accepted);
                }
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void complete(IndustrialSalvageService.Plan plan) {
        ItemStack input = getItem(INPUT_SLOT);
        input.shrink(1);
        if (input.isEmpty()) {
            setItem(INPUT_SLOT, ItemStack.EMPTY);
        }
        for (ItemStack output : plan.outputs()) {
            insertOutput(output.copy());
        }
        setChanged();
    }

    private void insertOutput(ItemStack remaining) {
        for (int slot = OUTPUT_START; slot < SLOT_COUNT && !remaining.isEmpty(); slot++) {
            ItemStack existing = getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remaining)) {
                int accepted = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (accepted > 0) {
                    existing.grow(accepted);
                    remaining.shrink(accepted);
                }
            }
        }
        for (int slot = OUTPUT_START; slot < SLOT_COUNT && !remaining.isEmpty(); slot++) {
            if (getItem(slot).isEmpty()) {
                int accepted = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                setItem(slot, remaining.copyWithCount(accepted));
                remaining.shrink(accepted);
            }
        }
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return inventory.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return inventory.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = inventory.removeItem(slot, amount);
        if (!result.isEmpty()) {
            if (slot == INPUT_SLOT) {
                autoProgress = 0;
            }
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = inventory.removeItemNoUpdate(slot);
        if (!result.isEmpty()) {
            if (slot == INPUT_SLOT) {
                autoProgress = 0;
            }
            setChanged();
        }
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        inventory.setItem(slot, stack);
        if (slot == INPUT_SLOT) {
            autoProgress = 0;
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT_SLOT && IndustryProfileManager.isCreateFlyProfileActive()
                && IndustrialSalvageService.isPotentialInput(stack);
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return direction == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return direction != Direction.DOWN && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return direction == Direction.DOWN && slot >= OUTPUT_START && slot < SLOT_COUNT;
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        inventory.clearContent();
        autoProgress = 0;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.tacz.industrial_salvage_station");
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new IndustrialSalvageMenu(containerId, playerInventory, this, worldPosition);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Iterator<ItemStack> stacks = input.listOrEmpty("Inventory", ItemStack.OPTIONAL_CODEC).iterator();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setItem(slot, stacks.hasNext() ? stacks.next() : ItemStack.EMPTY);
        }
        autoProgress = 0;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput.TypedOutputList<ItemStack> stacks = output.list("Inventory", ItemStack.OPTIONAL_CODEC);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            stacks.add(inventory.getItem(slot));
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private enum ResultKind {
        SUCCESS,
        INVALID_INPUT,
        OUTPUT_BLOCKED,
        PROFILE_DISABLED
    }

    private record SalvageResult(ResultKind kind, @Nullable IndustrialSalvageService.Failure failure) {
        private static SalvageResult success() {
            return new SalvageResult(ResultKind.SUCCESS, null);
        }

        private static SalvageResult invalid(@Nullable IndustrialSalvageService.Failure failure) {
            return new SalvageResult(ResultKind.INVALID_INPUT, failure);
        }

        private static SalvageResult outputBlocked() {
            return new SalvageResult(ResultKind.OUTPUT_BLOCKED, null);
        }

        private static SalvageResult profileDisabled() {
            return new SalvageResult(ResultKind.PROFILE_DISABLED, null);
        }
    }
}
