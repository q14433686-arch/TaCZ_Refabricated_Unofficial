package cn.sh1rocu.tacz.api.extension;

import com.tacz.guns.GunMod;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.List;

// Porting_Lib
public interface IEntityAdditionalSpawnData {
    Identifier EXTRA_DATA_PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "extra_entity_spawn_data");
    CustomPacketPayload.Type<ExtraSpawnDataPayload> EXTRA_DATA_TYPE = new CustomPacketPayload.Type<>(EXTRA_DATA_PACKET_ID);

    StreamCodec<RegistryFriendlyByteBuf, ExtraSpawnDataPayload> EXTRA_DATA_CODEC =
            StreamCodec.ofMember(
                    (payload, buf) -> {
                        buf.writeVarInt(payload.entityId());
                        buf.writeByteArray(payload.data());
                    },
                    buf -> new ExtraSpawnDataPayload(buf.readVarInt(), buf.readByteArray())
            );

    static void registerPayload() {
        PayloadTypeRegistry.clientboundPlay().register(EXTRA_DATA_TYPE, EXTRA_DATA_CODEC);
    }

    void readSpawnData(FriendlyByteBuf buf);

    void writeSpawnData(FriendlyByteBuf buf);

    static Packet<ClientGamePacketListener> getEntitySpawningPacket(Entity entity) {
        return getEntitySpawningPacket(entity, new ClientboundAddEntityPacket(entity, 0, entity.blockPosition()));
    }

    static Packet<ClientGamePacketListener> getEntitySpawningPacket(Entity entity, Packet<ClientGamePacketListener> base) {
        if (entity instanceof IEntityAdditionalSpawnData extra) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            // BUG FIX: Do NOT write entity.getId() here — the EXTRA_DATA_CODEC already
            // encodes entityId separately. Writing it again corrupts readSpawnData() which
            // reads the redundant varint as its first float field (xRot), causing every
            // subsequent field to desync and eventually "Not enough bytes in buffer".
            extra.writeSpawnData(buf);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            buf.release();
            ExtraSpawnDataPayload payload = new ExtraSpawnDataPayload(entity.getId(), data);
            var extraPacket = ServerPlayNetworking.createClientboundPacket(payload);
            return new ClientboundBundlePacket(List.of(base, extraPacket));
        }
        return base;
    }

    record ExtraSpawnDataPayload(int entityId, byte[] data) implements CustomPacketPayload {
        @Override
        public Type<ExtraSpawnDataPayload> type() {
            return EXTRA_DATA_TYPE;
        }
    }
}