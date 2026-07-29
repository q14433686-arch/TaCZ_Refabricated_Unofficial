package com.tacz.guns.network;

import io.netty.channel.ChannelFutureListener;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface IHandshakeMessage {
    void write(FriendlyByteBuf buf);

    Identifier getId();

    @Nullable IResponsePacket handle(Connection connection, Consumer<ChannelFutureListener> consumer);

    interface IResponsePacket {
        void write(FriendlyByteBuf buf);

        void read(FriendlyByteBuf buf);

        void handle(PacketSender sender);

        Identifier getId();
    }
}
