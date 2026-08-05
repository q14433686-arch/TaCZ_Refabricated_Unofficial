package com.tacz.guns.block.entity;

import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.industry.recipe.CartridgeAssemblyDefinition;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.inventory.CartridgeAssemblyMenu;
import com.tacz.guns.resource.CommonAssetsManager;
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
import java.util.Set;

/** Persistent inventory and server-side authority for the cartridge assembly GUI. */
public final class CartridgeAssemblyMachineBlockEntity extends BlockEntity
        implements Container, ExtendedMenuProvider<BlockPos> {
    public static final int CASE_SLOT = 0;
    public static final int PROJECTILE_SLOT = 1;
    public static final int PRIMER_SLOT = 2;
    public static final int PROPELLANT_SLOT = 3;
    public static final int OUTPUT_SLOT = 4;
    public static final int SLOT_COUNT = 5;

    public static final BlockEntityType<CartridgeAssemblyMachineBlockEntity> TYPE = new BlockEntityType<>(
            CartridgeAssemblyMachineBlockEntity::new, Set.of(ModBlocks.CARTRIDGE_ASSEMBLY_MACHINE)
    );

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setChanged() {
            CartridgeAssemblyMachineBlockEntity.this.setChanged();
        }
    };

    public CartridgeAssemblyMachineBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    /**
     * Called exclusively from the server-side menu packet. The menu id check
     * happens before this method; recipe lookup and all extraction remain here
     * so a client can never mint ammo by spoofing a result stack.
     */
    public boolean assemble(ServerPlayer player) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            player.sendSystemMessage(Component.translatable("message.tacz.cartridge_assembly.invalid"), true);
            return false;
        }
        CartridgeAssemblyDefinition definition = CommonAssetsManager.get().getAllCartridgeAssemblyRecipes().stream()
                .map(java.util.Map.Entry::getValue)
                .filter(recipe -> recipe != null && recipe.matches(
                        getItem(CASE_SLOT), getItem(PROJECTILE_SLOT), getItem(PRIMER_SLOT), getItem(PROPELLANT_SLOT)
                ))
                .findFirst()
                .orElse(null);
        if (definition == null || CommonAssetsManager.get().getAmmoIndex(definition.getAmmo()) == null) {
            player.sendSystemMessage(Component.translatable("message.tacz.cartridge_assembly.invalid"), true);
            return false;
        }

        ItemStack result = definition.createResult();
        if (result.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.tacz.cartridge_assembly.invalid"), true);
            return false;
        }
        ItemStack output = getItem(OUTPUT_SLOT);
        if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, result)
                || output.getCount() + result.getCount() > output.getMaxStackSize())) {
            player.sendSystemMessage(Component.translatable("message.tacz.cartridge_assembly.output_blocked"), true);
            return false;
        }

        consumeOne(CASE_SLOT);
        consumeOne(PROJECTILE_SLOT);
        consumeOne(PRIMER_SLOT);
        consumeOne(PROPELLANT_SLOT);
        if (output.isEmpty()) {
            setItem(OUTPUT_SLOT, result.copy());
        } else {
            output.grow(result.getCount());
            setChanged();
        }
        setChanged();
        player.containerMenu.broadcastFullState();
        return true;
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
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return inventory.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        inventory.setItem(slot, stack);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // Hoppers and other vanilla Container callers may fill the four input
        // bays, but never the server-owned result bay.
        return slot >= CASE_SLOT && slot < OUTPUT_SLOT;
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        inventory.clearContent();
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
