package com.tacz.guns.block.entity;

import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.industry.service.IndustrialServiceBenchService;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.inventory.IndustrialServiceBenchMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Actual thirteen-slot industrial service station. The five component bays are
 * outputs during strip-down and inputs during reassembly; dedicated steel,
 * brass, and named-cleaner bays keep repair and C.3 cleaning material semantics
 * visible. The server operation itself decides mode atomically, never a
 * client-side visual toggle.
 */
public final class IndustrialServiceBenchBlockEntity extends BlockEntity implements Container, ExtendedMenuProvider<BlockPos> {
    public static final int GUN_INPUT = 0;
    public static final int BLUEPRINT = 1;
    public static final int FIXTURE = 2;
    public static final int WRENCH = 3;
    public static final int COMPONENT_START = 4;
    public static final int COMPONENT_COUNT = 5;
    public static final int GUN_OUTPUT = COMPONENT_START + COMPONENT_COUNT;
    /** Non-powered bench repair material bays: steel structural stock + brass fine-fit stock. */
    public static final int STEEL_MATERIAL = GUN_OUTPUT + 1;
    public static final int BRASS_MATERIAL = STEEL_MATERIAL + 1;
    /** C.3 named Basin-made cleaner; never accepts raw cleaning ingredients. */
    public static final int CLEANING_MATERIAL = BRASS_MATERIAL + 1;
    public static final int SLOT_COUNT = CLEANING_MATERIAL + 1;

    public static final BlockEntityType<IndustrialServiceBenchBlockEntity> TYPE = new BlockEntityType<>(
            IndustrialServiceBenchBlockEntity::new, Set.of(ModBlocks.INDUSTRIAL_SERVICE_BENCH)
    );

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setChanged() {
            IndustrialServiceBenchBlockEntity.this.setChanged();
        }
    };

    public IndustrialServiceBenchBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    public boolean service(ServerPlayer player, int action) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            player.sendSystemMessage(Component.translatable("message.tacz.industrial_service.disabled"), true);
            return false;
        }
        return switch (action) {
            case 0 -> disassemble(player);
            case 1 -> reassemble(player);
            case 2 -> repairComponents(player);
            case 3 -> cleanGun(player);
            default -> false;
        };
    }

    private boolean disassemble(ServerPlayer player) {
        if (!getItem(GUN_OUTPUT).isEmpty() || !componentBaysEmpty()) {
            player.sendSystemMessage(Component.translatable("message.tacz.industrial_service.output_blocked"), true);
            return false;
        }
        IndustrialServiceBenchService.DisassemblyPlan plan = IndustrialServiceBenchService.planDisassembly(
                getItem(GUN_INPUT), getItem(BLUEPRINT), getItem(FIXTURE), getItem(WRENCH)
        );
        if (!plan.success()) {
            reportFailure(player, plan.failure());
            return false;
        }
        setItem(GUN_INPUT, ItemStack.EMPTY);
        List<ItemStack> components = plan.components();
        for (int index = 0; index < COMPONENT_COUNT; index++) {
            setItem(COMPONENT_START + index, components.get(index).copy());
        }
        IndustrialServiceBenchService.damageWrench(getItem(WRENCH));
        finishTransaction(player);
        return true;
    }

    private boolean reassemble(ServerPlayer player) {
        if (!getItem(GUN_INPUT).isEmpty() || !getItem(GUN_OUTPUT).isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.tacz.industrial_service.output_blocked"), true);
            return false;
        }
        List<ItemStack> components = java.util.stream.IntStream.range(0, COMPONENT_COUNT)
                .mapToObj(index -> getItem(COMPONENT_START + index)).toList();
        IndustrialServiceBenchService.ReassemblyPlan plan = IndustrialServiceBenchService.planReassembly(
                components, getItem(BLUEPRINT), getItem(FIXTURE), getItem(WRENCH)
        );
        if (!plan.success()) {
            reportFailure(player, plan.failure());
            return false;
        }
        for (int index = 0; index < COMPONENT_COUNT; index++) {
            setItem(COMPONENT_START + index, ItemStack.EMPTY);
        }
        setItem(GUN_OUTPUT, plan.gun().copy());
        IndustrialServiceBenchService.damageWrench(getItem(WRENCH));
        finishTransaction(player);
        return true;
    }

    private boolean repairComponents(ServerPlayer player) {
        if (!getItem(GUN_INPUT).isEmpty() || !getItem(GUN_OUTPUT).isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.tacz.industrial_service.output_blocked"), true);
            return false;
        }
        List<ItemStack> components = java.util.stream.IntStream.range(0, COMPONENT_COUNT)
                .mapToObj(index -> getItem(COMPONENT_START + index)).toList();
        IndustrialServiceBenchService.RepairPlan plan = IndustrialServiceBenchService.planRepair(
                components, getItem(BLUEPRINT), getItem(FIXTURE), getItem(WRENCH)
        );
        if (!plan.success()) {
            reportFailure(player, plan.failure());
            return false;
        }
        ItemStack steel = getItem(STEEL_MATERIAL);
        ItemStack brass = getItem(BRASS_MATERIAL);
        if (!IndustrialServiceBenchService.isSteelRepairMaterial(steel)
                || steel.getCount() < plan.steelPlates()
                || (plan.brassSheets() > 0 && (!IndustrialServiceBenchService.isBrassRepairMaterial(brass)
                || brass.getCount() < plan.brassSheets()))) {
            player.sendSystemMessage(Component.translatable("message.tacz.industrial_service.repair_materials",
                    plan.steelPlates(), plan.brassSheets()), true);
            return false;
        }
        steel.shrink(plan.steelPlates());
        if (plan.brassSheets() > 0) {
            brass.shrink(plan.brassSheets());
        }
        List<ItemStack> repaired = IndustrialServiceBenchService.repairComponents(components);
        for (int index = 0; index < COMPONENT_COUNT; index++) {
            setItem(COMPONENT_START + index, repaired.get(index));
        }
        IndustrialServiceBenchService.damageWrench(getItem(WRENCH));
        player.sendSystemMessage(Component.translatable("message.tacz.industrial_service.repair_success",
                plan.repairedComponents(), plan.steelPlates(), plan.brassSheets()), true);
        finishTransaction(player);
        return true;
    }

    /**
     * C.3 assembled-gun cleaning is a separate visible multi-slot transaction:
     * safe industrial gun + matching template/fixture + wrench + named Basin
     * cleaner. It preserves Condition and does not clear a feed/lockout fault.
     */
    private boolean cleanGun(ServerPlayer player) {
        if (!getItem(GUN_OUTPUT).isEmpty() || !componentBaysEmpty()) {
            player.sendSystemMessage(Component.translatable("message.tacz.industrial_service.output_blocked"), true);
            return false;
        }
        IndustrialServiceBenchService.CleaningPlan plan = IndustrialServiceBenchService.planCleaning(
                getItem(GUN_INPUT), getItem(BLUEPRINT), getItem(FIXTURE), getItem(WRENCH)
        );
        if (!plan.success()) {
            reportFailure(player, plan.failure());
            return false;
        }
        ItemStack cleaner = getItem(CLEANING_MATERIAL);
        if (!IndustrialServiceBenchService.isCleaningMaterial(cleaner) || cleaner.getCount() < plan.cleaningKits()) {
            player.sendSystemMessage(Component.translatable("message.tacz.industrial_service.cleaning_materials",
                    plan.cleaningKits()), true);
            return false;
        }
        ItemStack cleaned = IndustrialServiceBenchService.cleanGun(getItem(GUN_INPUT));
        cleaner.shrink(plan.cleaningKits());
        setItem(GUN_INPUT, ItemStack.EMPTY);
        setItem(GUN_OUTPUT, cleaned);
        IndustrialServiceBenchService.damageWrench(getItem(WRENCH));
        player.sendSystemMessage(Component.translatable("message.tacz.industrial_service.clean_success",
                plan.foulingRemoved(), plan.cleaningKits()), true);
        finishTransaction(player);
        return true;
    }

    private void finishTransaction(ServerPlayer player) {
        setChanged();
        player.containerMenu.broadcastFullState();
    }

    private void reportFailure(ServerPlayer player, @Nullable IndustrialServiceBenchService.Failure failure) {
        player.sendSystemMessage(Component.translatable(failure == null
                ? "message.tacz.industrial_service.invalid_gun" : failure.key()), true);
    }

    private boolean componentBaysEmpty() {
        for (int index = 0; index < COMPONENT_COUNT; index++) {
            if (!getItem(COMPONENT_START + index).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return inventory.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return inventory.getItem(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { ItemStack result = inventory.removeItem(slot, amount); setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return inventory.removeItemNoUpdate(slot); }
    @Override public void setItem(int slot, ItemStack stack) { inventory.setItem(slot, stack); setChanged(); }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case GUN_INPUT -> stack.getItem() instanceof com.tacz.guns.api.item.IGun;
            case BLUEPRINT -> stack.is(ModItems.GUN_BLUEPRINT);
            case FIXTURE -> stack.is(ModItems.PRESS_DIE);
            case WRENCH -> stack.is(ModItems.ARMORER_WRENCH);
            case COMPONENT_START, COMPONENT_START + 1, COMPONENT_START + 2, COMPONENT_START + 3, COMPONENT_START + 4 -> IndustrialServiceBenchService.isServiceComponent(stack);
            case STEEL_MATERIAL -> IndustrialServiceBenchService.isSteelRepairMaterial(stack);
            case BRASS_MATERIAL -> IndustrialServiceBenchService.isBrassRepairMaterial(stack);
            case CLEANING_MATERIAL -> IndustrialServiceBenchService.isCleaningMaterial(stack);
            default -> false;
        };
    }

    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }
    @Override public void clearContent() { inventory.clearContent(); setChanged(); }
    @Override public Component getDisplayName() { return Component.translatable("block.tacz.industrial_service_bench"); }
    @Override public BlockPos getScreenOpeningData(ServerPlayer player) { return worldPosition; }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new IndustrialServiceBenchMenu(containerId, playerInventory, this, worldPosition);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Iterator<ItemStack> stacks = input.listOrEmpty("Inventory", ItemStack.OPTIONAL_CODEC).iterator();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setItem(slot, stacks.hasNext() ? stacks.next() : ItemStack.EMPTY);
        }
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
}
