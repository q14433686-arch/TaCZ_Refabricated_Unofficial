package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class ClientMessagePlayerShoot implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_player_shoot");
    public static final CustomPacketPayload.Type<ClientMessagePlayerShoot> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessagePlayerShoot> CODEC = StreamCodec.ofMember(ClientMessagePlayerShoot::write, ClientMessagePlayerShoot::new);

    /**
     * 这里的 timestamp 应该是基于 base timestamp 的相对值
     */
    private final long timestamp;
    private float chargeProgress;

    public ClientMessagePlayerShoot(long timestamp) {
        this(timestamp, 0f);
    }

    public ClientMessagePlayerShoot(long timestamp, float chargeProgress) {
        this.timestamp = timestamp;
        this.chargeProgress = chargeProgress;
    }

    public ClientMessagePlayerShoot(FriendlyByteBuf buf) {
        this(buf.readLong(), buf.readFloat());
    }

        public void write(FriendlyByteBuf buf) {
        buf.writeLong(timestamp);
        buf.writeFloat(chargeProgress);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        IGunOperator.fromLivingEntity(player).shoot(player::getXRot, player::getYRot, timestamp, chargeProgress);
    }
}
