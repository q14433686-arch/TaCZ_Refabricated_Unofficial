package com.tacz.guns.block.entity;

import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.industry.recipe.CartridgeAssemblyDefinition;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.inventory.CartridgeAssemblyMenu;
import com.tacz.guns.resource.CommonAssetsManager;
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

import java.util.Iterator;
import java.util.Set;

/**
 * Persistent inventory and server-side authority for the cartridge assembly GUI.
 *
 * <p>Manual assembly remains available through the GUI. A redstone signal also
 * starts a timed automatic cycle, allowing hoppers and Create logistics to feed
 * the four independently validated bays without asking a Depot/Basin to infer a
 * multi-NBT recipe.</p>
 */
public final class CartridgeAssemblyMachineBlockEntity extends BlockEntity
        implements Container, WorldlyContainer, ExtendedMenuProvider<BlockPos> {
    public static final int CASE_SLOT = 0;
    public static final int PROJECTILE_SLOT = 1;
    public static final int PRIMER_SLOT = 2;
    public static final int PROPELLANT_SLOT = 3;
    public static final int OUTPUT_SLOT = 4;
    public static final int SLOT_COUNT = 5;
    /** Two seconds at the normal 20 TPS rate. */
    public static final int AUTO_PROCESS_TICKS = 40;

    private static final int[] INPUT_SLOTS = {CASE_SLOT, PROJECTILE_SLOT, PRIMER_SLOT, PROPELLANT_SLOT};
    private static final int[] OUTPUT_SLOTS = {OUTPUT_SLOT};

    public static final BlockEntityType<CartridgeAssemblyMachineBlockEntity> TYPE = new BlockEntityType<>(
            CartridgeAssemblyMachineBlockEntity::new, Set.of(ModBlocks.CARTRIDGE_ASSEMBLY_MACHINE)
    );

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setChanged() {
            CartridgeAssemblyMachineBlockEntity.this.setChanged();
        }
    };
    private int autoProgress;

    public CartridgeAssemblyMachineBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    /** Server ticker installed by the block; redstone enables logistics automation. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CartridgeAssemblyMachineBlockEntity machine) {
        machine.tickAutomation(level);
    }

    private void tickAutomation(Level level) {
        if (!IndustryProfileManager.isCreateFlyProfileActive() || !level.hasNeighborSignal(worldPosition)) {
            autoProgress = 0;
            return;
        }
        AssemblyPlan plan = findPlan();
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

    /**
     * Called exclusively from the server-side menu packet. The menu id check
     * happens before this method; recipe lookup and all extraction remain here
     * so a client can never mint ammo by spoofing a result stack.
     */
    public boolean assemble(ServerPlayer player) {
        AssemblyResult result = tryAssemble();
        switch (result) {
            case SUCCESS -> {
                player.containerMenu.broadcastFullState();
                return true;
            }
            case OUTPUT_BLOCKED -> player.sendSystemMessage(
                    Component.translatable("message.tacz.cartridge_assembly.output_blocked"), true);
            case INVALID_INPUT, PROFILE_DISABLED -> player.sendSystemMessage(
                    Component.translatable("message.tacz.cartridge_assembly.invalid"), true);
        }
        return false;
    }

    private AssemblyResult tryAssemble() {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            return AssemblyResult.PROFILE_DISABLED;
        }
        CartridgeAssemblyDefinition definition = findMatchingDefinition();
        if (definition == null || CommonAssetsManager.get().getAmmoIndex(definition.getAmmo()) == null) {
            return AssemblyResult.INVALID_INPUT;
        }
        ItemStack result = definition.createResult();
        if (result.isEmpty()) {
            return AssemblyResult.INVALID_INPUT;
        }
        if (!canAcceptOutput(result)) {
            return AssemblyResult.OUTPUT_BLOCKED;
        }
        complete(new AssemblyPlan(result));
        autoProgress = 0;
        return AssemblyResult.SUCCESS;
    }

    @Nullable
    private AssemblyPlan findPlan() {
        CartridgeAssemblyDefinition definition = findMatchingDefinition();
        if (definition == null || CommonAssetsManager.get().getAmmoIndex(definition.getAmmo()) == null) {
            return null;
        }
        ItemStack result = definition.createResult();
        if (result.isEmpty() || !canAcceptOutput(result)) {
            return null;
        }
        return new AssemblyPlan(result);
    }

    @Nullable
    private CartridgeAssemblyDefinition findMatchingDefinition() {
        return CommonAssetsManager.get().getAllCartridgeAssemblyRecipes().stream()
                .map(java.util.Map.Entry::getValue)
                .filter(recipe -> recipe != null && recipe.matches(
                        getItem(CASE_SLOT), getItem(PROJECTILE_SLOT), getItem(PRIMER_SLOT), getItem(PROPELLANT_SLOT)
                ))
                .findFirst()
                .orElse(null);
    }

    private boolean canAcceptOutput(ItemStack result) {
        ItemStack output = getItem(OUTPUT_SLOT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void complete(AssemblyPlan plan) {
        consumeOne(CASE_SLOT);
        consumeOne(PROJECTILE_SLOT);
        consumeOne(PRIMER_SLOT);
        consumeOne(PROPELLANT_SLOT);
        ItemStack output = getItem(OUTPUT_SLOT);
        if (output.isEmpty()) {
            setItem(OUTPUT_SLOT, plan.result().copy());
        } else {
            output.grow(plan.result().getCount());
        }
        setChanged();
    }

    private void consumeOne(int slot) {
        ItemStack stack = getItem(slot);
        stack.shrink(1);
        if (stack.isEmpty()) {
            setItem(slot, ItemStack.EMPTY);
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
            autoProgress = 0;
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = inventory.removeItemNoUpdate(slot);
        if (!result.isEmpty()) {
            autoProgress = 0;
            setChanged();
        }
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        inventory.setItem(slot, stack);
        if (slot != OUTPUT_SLOT) {
            autoProgress = 0;
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < CASE_SLOT || slot >= OUTPUT_SLOT || !IndustryProfileManager.isCreateFlyProfileActive()) {
            return false;
        }
        return CommonAssetsManager.get().getAllCartridgeAssemblyRecipes().stream()
                .map(java.util.Map.Entry::getValue)
                .filter(java.util.Objects::nonNull)
                .anyMatch(definition -> switch (slot) {
                    case CASE_SLOT -> definition.matchesCase(stack);
                    case PROJECTILE_SLOT -> definition.matchesProjectile(stack);
                    case PRIMER_SLOT -> definition.matchesPrimer(stack);
                    case PROPELLANT_SLOT -> definition.matchesPropellant(stack);
                    default -> false;
                });
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
        return direction == Direction.DOWN && slot == OUTPUT_SLOT;
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
        return Component.translatable("block.tacz.cartridge_assembly_machine");
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CartridgeAssemblyMenu(containerId, playerInventory, this, worldPosition);
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

    private enum AssemblyResult {
        SUCCESS,
        INVALID_INPUT,
        OUTPUT_BLOCKED,
        PROFILE_DISABLED
    }

    private record AssemblyPlan(ItemStack result) {
    }
}
