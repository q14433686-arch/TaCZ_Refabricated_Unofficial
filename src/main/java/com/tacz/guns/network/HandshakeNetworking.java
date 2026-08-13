package com.tacz.guns.network;

import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class HandshakeNetworking {
    private static final List<PacketInfo<? extends IHandshakeMessage>> HANDSHAKE_PACKETS = new ArrayList<>();
    private static final Map<Identifier, Class<? extends IHandshakeMessage.IResponsePacket>> RES_PACKETS = new HashMap<>();

    public static void init() {
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            for (PacketInfo<? extends IHandshakeMessage> packetInfo : HANDSHAKE_PACKETS) {
                try {
                    Class<? extends IHandshakeMessage> packetClass = packetInfo.packetClass;
                    IHandshakeMessage packet = packetClass.getDeclaredConstructor().newInstance();
                    FriendlyByteBuf buf = PacketByteBufs.create();
                    packet.write(buf);
                    sender.sendPacket(packet.getId(), buf);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                         InstantiationException e) {
                    GunMod.LOGGER.error("{} Handshake packet processing error", packetInfo.id().toString(), e);
                }
            }

        });
    }

    public static <T extends IHandshakeMessage.IResponsePacket> void register(@NotNull Identifier id, @NotNull Class<T> resPacketClass) {
        RES_PACKETS.put(id, resPacketClass);
    }

    public static <T extends IHandshakeMessage> void register(@NotNull Identifier id, @NotNull Class<T> packetClass, @NotNull Function<FriendlyByteBuf, T> reader) {
        HANDSHAKE_PACKETS.add(new PacketInfo<>(id, packetClass));
        ServerLoginNetworking.registerGlobalReceiver(id, (server, handler, understood, buf, synchronizer, responseSender) -> {
            if (understood) {
                try {
                    if (buf.readableBytes() > 0) {
                        Identifier packetId = buf.readIdentifier();
                        Class<? extends IHandshakeMessage.IResponsePacket> ackPacketClass = RES_PACKETS.get(packetId);
                        if (ackPacketClass == null) {
                            GunMod.LOGGER.error("{} Is the handshake response packet registered?", packetId.toString());
                            return;
                        }

                        IHandshakeMessage.IResponsePacket packet = ackPacketClass.getDeclaredConstructor().newInstance();
                        packet.read(buf);
                        packet.handle(responseSender);
                    }
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                         InstantiationException e) {
                    GunMod.LOGGER.error("Handshake response packet processing error in {}", id.toString(), e);
                }

            }
        });
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            NetworkHandler.registerHandshake(id, reader);
        }
    }

    public record PacketInfo<T extends IHandshakeMessage>(Identifier id, Class<T> packetClass) {
        public Identifier id() {
            return this.id;
        }

        public Class<T> packetClass() {
            return this.packetClass;
        }
    }
}
