package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class ClientMessagePlayerReloadGun implements CustomPacketPayload {
    /** -1 preserves best-magazine selection; non-negative is a wheel-selected inventory slot. */
    private final int preferredMagazineSlot;
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_player_reload");
    public static final CustomPacketPayload.Type<ClientMessagePlayerReloadGun> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessagePlayerReloadGun> CODEC = StreamCodec.ofMember(ClientMessagePlayerReloadGun::write, ClientMessagePlayerReloadGun::new);

    public ClientMessagePlayerReloadGun() {
        this(-1);
    }

    public ClientMessagePlayerReloadGun(int preferredMagazineSlot) {
        this.preferredMagazineSlot = preferredMagazineSlot;
    }

    public ClientMessagePlayerReloadGun(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(preferredMagazineSlot);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        // The server consumes the slot once at reload start and validates the
        // exact ItemStack again at the real animation feed transition.
        operator.getDataHolder().preferredPhysicalMagazineSlot = preferredMagazineSlot;
        operator.reload();
    }
}
