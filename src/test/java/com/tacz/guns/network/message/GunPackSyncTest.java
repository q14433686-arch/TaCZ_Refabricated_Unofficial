package com.tacz.guns.network.message;

import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.network.GunPackSync;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GunPackSyncTest {
    @Test void roundTripAndConnectionReset() throws Exception {
        byte[] bytes = new byte[GunPackSync.CHUNK_SIZE * 2];
        new Random(120).nextBytes(bytes);
        var parts = GunPackSync.compressAndSplit(bytes);
        assertTrue(parts.size() >= 2);
        var receiver = new GunPackSync.Receiver();
        assertNull(receiver.accept(parts.getFirst()));
        receiver.clear();
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(parts.get(1)));
        for (var part : parts.subList(0, parts.size() - 1)) assertNull(receiver.accept(part));
        assertArrayEquals(bytes, receiver.accept(parts.getLast()));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(parts.getLast()));
    }

    @Test void rejectsMalformedLengths() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new GunPackSync.Part(UUID.randomUUID(), 0, 1,
                GunPackSync.MAX_SYNC_SIZE + 1, new byte[1]));
        var part = GunPackSync.compressAndSplit(new byte[]{1, 2, 3}).getFirst();
        var receiver = new GunPackSync.Receiver();
        assertThrows(java.io.IOException.class, () -> receiver.accept(new GunPackSync.Part(part.transferId(),
                part.index(), part.compressedSize(), 2, part.data())));
        assertArrayEquals(new byte[]{1, 2, 3}, receiver.accept(part));
    }

    @Test void actualPayloadCodecsAndLegacyLimit() throws Exception {
        var id = Identifier.parse("fixture:gun");
        var json = "x".repeat(Short.MAX_VALUE + 1);
        var cache = Map.of(DataType.GUN_DATA, Map.of(id, json));
        var snapshot = ServerMessageSyncGunPackPart.prepare(cache);
        assertNull(snapshot.legacy(), "old readUtf cannot accept this entry");
        var small = ServerMessageSyncGunPackPart.prepare(Map.of(DataType.GUN_DATA, Map.of(id, "{}")));
        assertNotNull(small.legacy());
        var wire = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ServerMessageSyncGunPack.CODEC.encode(wire, small.legacy());
            assertEquals(small.legacy().getCache(), ServerMessageSyncGunPack.CODEC.decode(wire).getCache());
            wire.clear();
            ServerMessageSyncGunPackPart.CODEC.encode(wire, snapshot.messages().getFirst());
            var part = ServerMessageSyncGunPackPart.CODEC.decode(wire).part();
            byte[] data = new GunPackSync.Receiver().accept(part);
            wire.clear();
            wire.writeBytes(data);
            assertEquals(cache, ServerMessageSyncGunPack.readCache(wire, GunPackSync.MAX_SYNC_SIZE));
        } finally { wire.release(); }
    }
}
