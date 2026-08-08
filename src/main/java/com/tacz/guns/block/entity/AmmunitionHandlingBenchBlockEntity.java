package com.tacz.guns.block.entity;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.industry.ammo.AmmoProfileService;
import com.tacz.guns.industry.magazine.MagazineItemDataAccessor;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.inventory.AmmunitionHandlingBenchMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
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
import java.util.UUID;

/**
 * Server-authoritative, ten-slot station for ordered physical-round handling.
 * A press starts one timed transaction; continuous mode only repeats that same
 * one-round transaction and never bulk-moves a stack.
 */
public final class AmmunitionHandlingBenchBlockEntity extends BlockEntity
        implements Container, ExtendedMenuProvider<BlockPos> {
    public static final int CARRIER_SLOT = 0;
    public static final int INPUT_START = 1;
    public static final int INPUT_COUNT = 4;
    public static final int TOOL_SLOT = INPUT_START + INPUT_COUNT;
    public static final int OUTPUT_START = TOOL_SLOT + 1;
    public static final int OUTPUT_COUNT = 4;
    public static final int SLOT_COUNT = OUTPUT_START + OUTPUT_COUNT;

    public static final int ACTION_LOAD_ONE = 0;
    public static final int ACTION_LOAD_CONTINUOUS = 1;
    public static final int ACTION_UNLOAD_ONE = 2;
    public static final int ACTION_UNLOAD_CONTINUOUS = 3;
    public static final int ACTION_CANCEL = 4;

    public static final BlockEntityType<AmmunitionHandlingBenchBlockEntity> TYPE = new BlockEntityType<>(
            AmmunitionHandlingBenchBlockEntity::new, Set.of(ModBlocks.AMMUNITION_HANDLING_BENCH)
    );

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setChanged() {
            AmmunitionHandlingBenchBlockEntity.this.setChanged();
        }
    };
    @Nullable
    private HandlingOperation operation;
    private int operationProgress;
    private int operationDuration;
    private boolean mutatingOperation;

    public AmmunitionHandlingBenchBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AmmunitionHandlingBenchBlockEntity bench) {
        bench.tickOperation(level);
    }

    /** Called only after the C2S menu-id check. */
    public boolean start(ServerPlayer player, int actionId, int inputIndex) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            player.sendSystemMessage(Component.translatable("message.tacz.ammo_handling.disabled"), true);
            return false;
        }
        HandlingAction action = HandlingAction.byId(actionId);
        if (action == HandlingAction.CANCEL) {
            cancelOperation();
            player.containerMenu.broadcastFullState();
            return true;
        }
        if (action == null || operation != null) {
            return false;
        }
        int sourceSlot = INPUT_START + inputIndex;
        if (action.isLoad() && (inputIndex < 0 || inputIndex >= INPUT_COUNT || !canLoadFrom(sourceSlot))) {
            player.sendSystemMessage(Component.translatable("message.tacz.ammo_handling.invalid_load"), true);
            return false;
        }
        if (!action.isLoad() && !canUnload()) {
            player.sendSystemMessage(Component.translatable("message.tacz.ammo_handling.invalid_unload"), true);
            return false;
        }
        operation = new HandlingOperation(player.getUUID(), action, action.isLoad() ? sourceSlot : -1);
        operationProgress = 0;
        operationDuration = getDuration(action);
        setChanged();
        player.containerMenu.broadcastFullState();
        return true;
    }

    private void tickOperation(Level level) {
        HandlingOperation active = operation;
        if (active == null) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(active.owner());
        if (!isOwnerStillOperating(owner)) {
            cancelOperation();
            return;
        }
        if (!canContinue(active)) {
            cancelOperation();
            owner.containerMenu.broadcastFullState();
            return;
        }
        operationProgress++;
        if (operationProgress < operationDuration) {
            return;
        }

        boolean completed = active.action().isLoad() ? loadOne(owner, active.sourceSlot()) : unloadOne();
        if (!completed) {
            cancelOperation();
            owner.containerMenu.broadcastFullState();
            return;
        }
        owner.playSound(active.action().isLoad() ? SoundEvents.BUNDLE_INSERT : SoundEvents.BUNDLE_REMOVE_ONE,
                0.8F, 0.85F + level.getRandom().nextFloat() * 0.25F);
        owner.containerMenu.broadcastFullState();
        if (active.action().isContinuous() && canContinue(active)) {
            operationProgress = 0;
            operationDuration = getDuration(active.action());
            setChanged();
        } else {
            cancelOperation();
            owner.containerMenu.broadcastFullState();
        }
    }

    private boolean isOwnerStillOperating(@Nullable ServerPlayer player) {
        if (player == null || player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) > 64.0D) {
            return false;
        }
        return player.containerMenu instanceof AmmunitionHandlingBenchMenu menu && menu.isFor(worldPosition);
    }

    private boolean canContinue(HandlingOperation active) {
        return active.action().isLoad() ? canLoadFrom(active.sourceSlot()) : canUnload();
    }

    private boolean canLoadFrom(int sourceSlot) {
        ItemStack carrier = getItem(CARRIER_SLOT);
        ItemStack source = getItem(sourceSlot);
        if (!(carrier.getItem() instanceof MagazineItemDataAccessor magazine) || !magazine.isConfigured(carrier)
                || !(source.getItem() instanceof IAmmo ammo) || source.isEmpty()) {
            return false;
        }
        IdentifierPair pair = new IdentifierPair(magazine.getAmmoId(carrier), ammo.getAmmoId(source));
        return magazine.getAmmoCount(carrier) < magazine.getCapacity(carrier)
                && AmmoProfileService.isLoadedAmmoIdentity(pair.roundAmmo())
                && AmmoProfileService.isSameCaliber(pair.carrierCaliber(), pair.roundAmmo());
    }

    private boolean canUnload() {
        ItemStack carrier = getItem(CARRIER_SLOT);
        if (!(carrier.getItem() instanceof MagazineItemDataAccessor magazine) || !magazine.isConfigured(carrier)
                || magazine.getAmmoCount(carrier) <= 0) {
            return false;
        }
        var roundAmmo = magazine.getNextRoundAmmoId(carrier);
        return !DefaultAssets.EMPTY_AMMO_ID.equals(roundAmmo)
                && AmmoProfileService.isLoadedAmmoIdentity(roundAmmo)
                && findOutputSlot(AmmoItemBuilder.create().setId(roundAmmo).setCount(1).build()) >= 0;
    }

    private boolean loadOne(ServerPlayer owner, int sourceSlot) {
        if (!canLoadFrom(sourceSlot)) {
            return false;
        }
        ItemStack carrier = getItem(CARRIER_SLOT);
        ItemStack source = getItem(sourceSlot);
        MagazineItemDataAccessor magazine = (MagazineItemDataAccessor) carrier.getItem();
        IAmmo ammo = (IAmmo) source.getItem();
        if (!magazine.pushRound(carrier, ammo.getAmmoId(source))) {
            return false;
        }
        mutatingOperation = true;
        if (IGunOperator.fromLivingEntity(owner).needCheckAmmo()) {
            source.shrink(1);
            if (source.isEmpty()) {
                inventory.setItem(sourceSlot, ItemStack.EMPTY);
            }
        }
        inventory.setItem(CARRIER_SLOT, carrier);
        mutatingOperation = false;
        setChanged();
        return true;
    }

    private boolean unloadOne() {
        if (!canUnload()) {
            return false;
        }
        ItemStack carrier = getItem(CARRIER_SLOT);
        MagazineItemDataAccessor magazine = (MagazineItemDataAccessor) carrier.getItem();
        ItemStack loose = AmmoItemBuilder.create().setId(magazine.getNextRoundAmmoId(carrier)).setCount(1).build();
        int outputSlot = findOutputSlot(loose);
        if (outputSlot < 0) {
            return false;
        }
        if (DefaultAssets.EMPTY_AMMO_ID.equals(magazine.popNextRound(carrier))) {
            return false;
        }
        mutatingOperation = true;
        ItemStack output = getItem(outputSlot);
        if (output.isEmpty()) {
            inventory.setItem(outputSlot, loose);
        } else {
            output.grow(1);
        }
        inventory.setItem(CARRIER_SLOT, carrier);
        mutatingOperation = false;
        setChanged();
        return true;
    }

    private int findOutputSlot(ItemStack candidate) {
        for (int slot = OUTPUT_START; slot < OUTPUT_START + OUTPUT_COUNT; slot++) {
            ItemStack output = getItem(slot);
            if (output.isEmpty() || ItemStack.isSameItemSameComponents(output, candidate)
                    && output.getCount() < output.getMaxStackSize()) {
                return slot;
            }
        }
        return -1;
    }

    private int getDuration(HandlingAction action) {
        int base = action.isLoad() ? configLoadTicks() : configUnloadTicks();
        if (action.isLoad() && getItem(TOOL_SLOT).is(ModItems.MAGAZINE_LOADER)) {
            double multiplier = SyncConfig.INDUSTRY_MAGAZINE_LOADER_TIME_MULTIPLIER == null
                    ? 0.75D : SyncConfig.INDUSTRY_MAGAZINE_LOADER_TIME_MULTIPLIER.get();
            return Math.max(4, (int) Math.ceil(base * multiplier));
        }
        return base;
    }

    private static int configLoadTicks() {
        return SyncConfig.INDUSTRY_ROUND_LOAD_TICKS == null ? 10 : SyncConfig.INDUSTRY_ROUND_LOAD_TICKS.get();
    }

    private static int configUnloadTicks() {
        return SyncConfig.INDUSTRY_ROUND_UNLOAD_TICKS == null ? 8 : SyncConfig.INDUSTRY_ROUND_UNLOAD_TICKS.get();
    }

    public int getOperationProgress() {
        return operationProgress;
    }

    public int getOperationDuration() {
        return operationDuration;
    }

    public int getOperationActionId() {
        return operation == null ? -1 : operation.action().id();
    }

    private void cancelOperation() {
        operation = null;
        operationProgress = 0;
        operationDuration = 0;
        setChanged();
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
        if (!mutatingOperation) {
            cancelOperation();
        }
        ItemStack result = inventory.removeItem(slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (!mutatingOperation) {
            cancelOperation();
        }
        ItemStack result = inventory.removeItemNoUpdate(slot);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!mutatingOperation) {
            cancelOperation();
        }
        inventory.setItem(slot, stack);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            return false;
        }
        if (slot == CARRIER_SLOT) {
            return stack.getItem() instanceof MagazineItemDataAccessor magazine && magazine.isConfigured(stack);
        }
        if (slot >= INPUT_START && slot < INPUT_START + INPUT_COUNT) {
            return stack.getItem() instanceof IAmmo;
        }
        return slot == TOOL_SLOT && stack.is(ModItems.MAGAZINE_LOADER);
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        cancelOperation();
        inventory.clearContent();
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.tacz.ammunition_handling_bench");
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AmmunitionHandlingBenchMenu(containerId, playerInventory, this, worldPosition);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Iterator<ItemStack> stacks = input.listOrEmpty("Inventory", ItemStack.OPTIONAL_CODEC).iterator();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setItem(slot, stacks.hasNext() ? stacks.next() : ItemStack.EMPTY);
        }
        operation = null;
        operationProgress = 0;
        operationDuration = 0;
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

    private enum HandlingAction {
        LOAD_ONE(ACTION_LOAD_ONE, true, false),
        LOAD_CONTINUOUS(ACTION_LOAD_CONTINUOUS, true, true),
        UNLOAD_ONE(ACTION_UNLOAD_ONE, false, false),
        UNLOAD_CONTINUOUS(ACTION_UNLOAD_CONTINUOUS, false, true),
        CANCEL(ACTION_CANCEL, false, false);

        private final int id;
        private final boolean load;
        private final boolean continuous;

        HandlingAction(int id, boolean load, boolean continuous) {
            this.id = id;
            this.load = load;
            this.continuous = continuous;
        }

        private int id() {
            return id;
        }

        private boolean isLoad() {
            return load;
        }

        private boolean isContinuous() {
            return continuous;
        }

        @Nullable
        private static HandlingAction byId(int id) {
            for (HandlingAction action : values()) {
                if (action.id == id) {
                    return action;
                }
            }
            return null;
        }
    }

    private record HandlingOperation(UUID owner, HandlingAction action, int sourceSlot) {
    }

    /** Keeps source/canonical values together when testing a one-round load. */
    private record IdentifierPair(net.minecraft.resources.Identifier carrierCaliber,
                                  net.minecraft.resources.Identifier roundAmmo) {
    }
}
