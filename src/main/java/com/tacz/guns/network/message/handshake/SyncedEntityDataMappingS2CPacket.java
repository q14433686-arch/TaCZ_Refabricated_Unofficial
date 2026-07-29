package com.tacz.guns.network.message.handshake;

import com.tacz.guns.GunMod;
import com.tacz.guns.entity.sync.core.SyncedDataKey;
import com.tacz.guns.entity.sync.core.SyncedEntityData;
import com.tacz.guns.network.IHandshakeMessage;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.*;
import java.util.function.Consumer;

public class SyncedEntityDataMappingS2CPacket implements IHandshakeMessage {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("tacz", "synced_entity_data_mapping");
    private static final Marker HANDSHAKE = MarkerFactory.getMarker("TACZ_HANDSHAKE");
    private Map<Identifier, List<Pair<Identifier, Integer>>> keyMap;

    public SyncedEntityDataMappingS2CPacket() {

    }

    public SyncedEntityDataMappingS2CPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        this.keyMap = new HashMap<>();

        for (int i = 0; i < size; ++i) {
            Identifier classId = buf.readIdentifier();
            Identifier keyId = buf.readIdentifier();
            int id = buf.readVarInt();
            this.keyMap.computeIfAbsent(classId, (k) -> new ArrayList<>()).add(Pair.of(keyId, id));
        }

    }

    public void write(FriendlyByteBuf buf) {
        Set<SyncedDataKey<?, ?>> keys = SyncedEntityData.instance().getKeys();
        buf.writeInt(keys.size());
        keys.forEach((key) -> {
            int id = SyncedEntityData.instance().getInternalId(key);
            buf.writeIdentifier(key.classKey().id());
            buf.writeIdentifier(key.id());
            buf.writeVarInt(id);
        });
    }

    public IHandshakeMessage.IResponsePacket handle(Connection connection, Consumer<ChannelFutureListener> listenerAdder) {
        GunMod.LOGGER.debug(HANDSHAKE, "Received synced key mappings from server");
        if (!SyncedEntityData.instance().updateMappings(this.keyMap)) {
            connection.disconnect(Component.literal("Connection closed - [TacZ] Received unknown synced data keys."));
        }

        return new AcknowledgeC2SPacket();
    }

    public Identifier getId() {
        return ID;
    }
}
