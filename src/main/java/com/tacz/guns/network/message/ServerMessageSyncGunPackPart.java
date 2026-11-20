package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.network.GunPackSync;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** New channel rather than reinterpreting an R1-hotfix3 payload as compressed bytes. */
public record ServerMessageSyncGunPackPart(GunPackSync.Part part) implements CustomPacketPayload {
    public static final Type<ServerMessageSyncGunPackPart> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_sync_gunpack_part"));
    public static final StreamCodec<FriendlyByteBuf, ServerMessageSyncGunPackPart> CODEC = StreamCodec.ofMember(
            ServerMessageSyncGunPackPart::write, buf -> new ServerMessageSyncGunPackPart(new GunPackSync.Part(
                    buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(GunPackSync.CHUNK_SIZE))));
    private static final int LEGACY_MAX_BYTES = 1024 * 1024;
    private static final GunPackSync.Receiver RECEIVER = new GunPackSync.Receiver();

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(part.transferId());
        buf.writeVarInt(part.index());
        buf.writeVarInt(part.compressedSize());
        buf.writeVarInt(part.uncompressedSize());
        buf.writeByteArray(part.data());
    }

    @Override public Type<ServerMessageSyncGunPackPart> type() { return TYPE; }

    public record Snapshot(List<ServerMessageSyncGunPackPart> messages, @Nullable ServerMessageSyncGunPack legacy) {
        public void send(ServerPlayer player) {
            if (ServerPlayNetworking.canSend(player, TYPE)) {
                messages.forEach(message -> NetworkHandler.sendToClientPlayer(message, player));
            } else if (legacy != null && ServerPlayNetworking.canSend(player, ServerMessageSyncGunPack.TYPE)) {
                NetworkHandler.sendToClientPlayer(legacy, player);
            } else {
                player.connection.disconnect(Component.translatable("tacz.network.pack_sync_incompatible"));
            }
        }
    }

    public static Snapshot prepare(Map<DataType, Map<Identifier, String>> input) {
        var cache = new EnumMap<DataType, Map<Identifier, String>>(DataType.class);
        input.forEach((type, entries) -> cache.put(type, Map.copyOf(entries)));
        var buf = new FriendlyByteBuf(Unpooled.buffer(256, GunPackSync.MAX_SYNC_SIZE));
        try {
            ServerMessageSyncGunPack.writeCache(buf, cache, GunPackSync.MAX_SYNC_SIZE);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            var parts = GunPackSync.compressAndSplit(data);
            GunMod.LOGGER.info("Prepared gun pack sync: {} bytes -> {} compressed bytes, {} part(s)",
                    data.length, parts.getFirst().compressedSize(), parts.size());
            boolean fitsLegacy = data.length <= LEGACY_MAX_BYTES && cache.values().stream().flatMap(map -> map.values().stream())
                    .allMatch(json -> json.length() <= Short.MAX_VALUE);
            return new Snapshot(parts.stream().map(ServerMessageSyncGunPackPart::new).toList(),
                    fitsLegacy ? new ServerMessageSyncGunPack(cache) : null);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot prepare gun pack snapshot", e);
        } finally {
            buf.release();
        }
    }

    @Environment(EnvType.CLIENT)
    public static void clearPendingSync() { RECEIVER.clear(); }

    @Environment(EnvType.CLIENT)
    public void handle(Connection connection) {
        Minecraft.getInstance().execute(() -> {
            if (!ServerMessageSyncGunPack.isCurrent(connection)) return;
            try {
                byte[] data = RECEIVER.accept(part);
                if (data == null) return;
                var buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
                try {
                    ServerMessageSyncGunPack.installCache(ServerMessageSyncGunPack.readCache(buf, GunPackSync.MAX_SYNC_SIZE), connection);
                } finally {
                    buf.release();
                }
            } catch (IOException | RuntimeException e) {
                RECEIVER.clear();
                GunMod.LOGGER.error("Failed to synchronize gun pack data", e);
                connection.disconnect(Component.literal("Failed to synchronize gun pack data"));
            }
        });
    }
}
