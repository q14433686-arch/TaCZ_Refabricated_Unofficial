package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.entity.sync.core.DataEntry;
import com.tacz.guns.entity.sync.core.SyncedEntityData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class ServerMessageUpdateEntityData implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_update_entity_data");
    public static final CustomPacketPayload.Type<ServerMessageUpdateEntityData> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageUpdateEntityData> CODEC = StreamCodec.ofMember(ServerMessageUpdateEntityData::write, ServerMessageUpdateEntityData::new);

    private final int entityId;
    private final List<DataEntry<?, ?>> entries;

    private static List<DataEntry<?, ?>> readEntries(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<DataEntry<?, ?>> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            entries.add(DataEntry.read(buf));
        }
        return entries;
    }

    public ServerMessageUpdateEntityData(FriendlyByteBuf buf) {
        this(buf.readVarInt(), readEntries(buf));
    }

    public ServerMessageUpdateEntityData(int entityId, List<DataEntry<?, ?>> entries) {
        this.entityId = entityId;
        this.entries = entries;
    }

        public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeVarInt(entries.size());
        entries.forEach(entry -> entry.write(buffer));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        onHandle(this);
    }

    @Environment(EnvType.CLIENT)
    private static void onHandle(ServerMessageUpdateEntityData message) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(message.entityId);
        if (entity == null) {
            return;
        }
        SyncedEntityData instance = SyncedEntityData.instance();
        message.entries.forEach(entry -> instance.set(entity, entry.getKey(), entry.getValue()));
    }
}
