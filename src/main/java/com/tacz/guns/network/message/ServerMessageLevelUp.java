package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class ServerMessageLevelUp implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_levelup");
    public static final CustomPacketPayload.Type<ServerMessageLevelUp> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerMessageLevelUp> CODEC = StreamCodec.ofMember(ServerMessageLevelUp::write, ServerMessageLevelUp::new);

    private final ItemStack gun;
    private final int level;

    public ServerMessageLevelUp(RegistryFriendlyByteBuf buf) {
        this(ItemStack.STREAM_CODEC.decode(buf), buf.readInt());
    }

    public ServerMessageLevelUp(ItemStack gun, int level) {
        this.gun = gun;
        this.level = level;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        ItemStack.STREAM_CODEC.encode(buf, gun);
        buf.writeInt(level);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        onLevelUp(this);
    }

    @Environment(EnvType.CLIENT)
    private static void onLevelUp(ServerMessageLevelUp message) {
        /*
         * Reserved protocol from upstream TACZ 1.1.8. There is no GunLevelManager, no experience
         * writer and no server-side sender in either the official 1.20.1 tree or Refabricated
         * 1.21.1, so inventing a toast here would falsely imply that progression exists. Keep the
         * payload decoder for binary/API compatibility, but deliberately perform no client action.
         */
    }

    public ItemStack getGun() {
        return this.gun;
    }

    public int getLevel() {
        return this.level;
    }
}
