package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.industry.maintenance.IndustryMaintenanceService;
import com.tacz.guns.util.ItemNbtUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Immediate authoritative snapshot for a server-created/cleared C.2/C.4 fault.
 *
 * <p>Vanilla inventory synchronization still follows normally. This small S2C
 * message closes the one-tick race in which the client might otherwise start
 * its ordinary automatic manual-bolt animation before it receives a feed
 * state, and makes a post-shot bench-only service lockout visible without a
 * client-created status. The supplied stack is always the server's exact
 * post-shot or post-bolt state.</p>
 */
public final class ServerMessageMaintenanceGunState implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_maintenance_gun_state");
    public static final CustomPacketPayload.Type<ServerMessageMaintenanceGunState> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerMessageMaintenanceGunState> CODEC = StreamCodec.ofMember(
            ServerMessageMaintenanceGunState::write, ServerMessageMaintenanceGunState::new
    );

    private final ItemStack gun;

    public ServerMessageMaintenanceGunState(RegistryFriendlyByteBuf buffer) {
        this(ItemStack.STREAM_CODEC.decode(buffer));
    }

    public ServerMessageMaintenanceGunState(ItemStack gun) {
        this.gun = gun.copy();
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        ItemStack.STREAM_CODEC.encode(buffer, gun);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        ItemStack held = player.getMainHandItem();
        if (!sameMaintenanceGun(held, gun)) {
            return;
        }
        // Apply only a same-instance server snapshot; this cannot overwrite a
        // gun selected after the original shot because every migrated gun owns
        // a persistent per-item maintenance seed.
        var inventory = player.getInventory();
        inventory.setItem(inventory.getSelectedSlot(), gun.copy());
        inventory.setChanged();
        // If a stale client auto-bolt raced before this snapshot arrived, stop
        // its local wait state. The server never accepted that normal bolt while
        // the feed fault existed.
        var localData = IClientPlayerGunOperator.fromLocalPlayer(player).getDataHolder();
        localData.isBolting = false;
        localData.isClearingFeedJam = false;
    }

    @Environment(EnvType.CLIENT)
    private static boolean sameMaintenanceGun(ItemStack local, ItemStack authoritative) {
        if (!(local.getItem() instanceof IGun localGun) || !(authoritative.getItem() instanceof IGun serverGun)) {
            return false;
        }
        Identifier localId = localGun.getGunId(local);
        Identifier serverId = serverGun.getGunId(authoritative);
        if (localId == null || !localId.equals(serverId)) {
            return false;
        }
        long localSeed = ItemNbtUtils.getTag(local).getLongOr(IndustryMaintenanceService.SEED_TAG, 0L);
        long serverSeed = ItemNbtUtils.getTag(authoritative).getLongOr(IndustryMaintenanceService.SEED_TAG, 0L);
        return localSeed != 0L && localSeed == serverSeed;
    }
}
