package com.tacz.guns.network;

import cn.sh1rocu.tacz.api.extension.IEntityAdditionalSpawnData;
import com.tacz.guns.network.message.*;
import com.tacz.guns.network.message.event.*;
import com.tacz.guns.network.message.handshake.AcknowledgeC2SPacket;
import com.tacz.guns.network.message.handshake.SyncedEntityDataMappingS2CPacket;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.mixin.networking.client.accessor.ClientHandshakePacketListenerImplAccessor;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class NetworkHandler {
    /**
     * Register all payload types with the PayloadTypeRegistry.
     * Must be called on both client and server (common init).
     */
    public static void registerPayloads() {
        // C2S (serverbound play)
        PayloadTypeRegistry.playC2S().register(ClientMessagePlayerShoot.TYPE, ClientMessagePlayerShoot.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessagePlayerReloadGun.TYPE, ClientMessagePlayerReloadGun.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessagePlayerCancelReload.TYPE, ClientMessagePlayerCancelReload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessagePlayerFireSelect.TYPE, ClientMessagePlayerFireSelect.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessagePlayerAim.TYPE, ClientMessagePlayerAim.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessagePlayerCrawl.TYPE, ClientMessagePlayerCrawl.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessagePlayerDrawGun.TYPE, ClientMessagePlayerDrawGun.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessageCraft.TYPE, ClientMessageCraft.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessagePlayerZoom.TYPE, ClientMessagePlayerZoom.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessageRefitGun.TYPE, ClientMessageRefitGun.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessageUnloadAttachment.TYPE, ClientMessageUnloadAttachment.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessagePlayerBoltGun.TYPE, ClientMessagePlayerBoltGun.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessagePlayerMelee.TYPE, ClientMessagePlayerMelee.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessageSyncBaseTimestamp.TYPE, ClientMessageSyncBaseTimestamp.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientMessageLaserColor.TYPE, ClientMessageLaserColor.CODEC);
        // S2C (clientbound play)
        PayloadTypeRegistry.playS2C().register(ServerMessageSound.TYPE, ServerMessageSound.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageCraft.TYPE, ServerMessageCraft.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageRefreshRefitScreen.TYPE, ServerMessageRefreshRefitScreen.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageSwapItem.TYPE, ServerMessageSwapItem.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageLevelUp.TYPE, ServerMessageLevelUp.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageGunHurt.TYPE, ServerMessageGunHurt.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageGunKill.TYPE, ServerMessageGunKill.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageUpdateEntityData.TYPE, ServerMessageUpdateEntityData.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageSyncGunPack.TYPE, ServerMessageSyncGunPack.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageGunDraw.TYPE, ServerMessageGunDraw.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageGunFire.TYPE, ServerMessageGunFire.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageGunFireSelect.TYPE, ServerMessageGunFireSelect.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageGunMelee.TYPE, ServerMessageGunMelee.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageGunReload.TYPE, ServerMessageGunReload.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageGunShoot.TYPE, ServerMessageGunShoot.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerMessageSyncBaseTimestamp.TYPE, ServerMessageSyncBaseTimestamp.CODEC);
        // Extra spawn data payload
        IEntityAdditionalSpawnData.registerPayload();
    }

    public static void registerC2SPackets() {
        registerPayloads();

        ServerPlayNetworking.registerGlobalReceiver(ClientMessagePlayerShoot.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessagePlayerReloadGun.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessagePlayerCancelReload.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessagePlayerFireSelect.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessagePlayerAim.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessagePlayerCrawl.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessagePlayerDrawGun.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessageCraft.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessagePlayerZoom.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessageRefitGun.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessageUnloadAttachment.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessagePlayerBoltGun.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessagePlayerMelee.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessageSyncBaseTimestamp.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessageLaserColor.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));

        HandshakeNetworking.register(AcknowledgeC2SPacket.ID, AcknowledgeC2SPacket.class);
        HandshakeNetworking.register(SyncedEntityDataMappingS2CPacket.ID, SyncedEntityDataMappingS2CPacket.class, SyncedEntityDataMappingS2CPacket::new);
    }

    @Environment(EnvType.CLIENT)
    public static void registerS2CPackets() {
        ClientPlayNetworking.registerGlobalReceiver(IEntityAdditionalSpawnData.EXTRA_DATA_TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Entity entity = Objects.requireNonNull(context.client().level).getEntity(payload.entityId());
                if (entity instanceof IEntityAdditionalSpawnData extra) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
                    extra.readSpawnData(buf);
                    buf.release();
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ServerMessageSound.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageCraft.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageRefreshRefitScreen.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageSwapItem.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageLevelUp.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageGunHurt.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageGunKill.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageUpdateEntityData.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageSyncGunPack.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageGunDraw.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageGunFire.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageGunFireSelect.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageGunMelee.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageGunReload.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageGunShoot.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageSyncBaseTimestamp.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
    }

    @SuppressWarnings("UnstableApiUsage")
    @Environment(EnvType.CLIENT)
    static <T extends IHandshakeMessage> void registerHandshake(Identifier id, Function<FriendlyByteBuf, T> reader) {
        ClientLoginNetworking.registerGlobalReceiver(id, (client, handler, buf, listenerAdder) -> {
            T packet = reader.apply(buf);
            Connection connection = ((ClientHandshakePacketListenerImplAccessor) handler).getConnection();
            IHandshakeMessage.IResponsePacket responsePacket = packet.handle(connection, listenerAdder);
            FriendlyByteBuf response = PacketByteBufs.create();
            if (responsePacket != null) {
                response.writeIdentifier(responsePacket.getId());
                responsePacket.write(response);
            }
            return CompletableFuture.completedFuture(response);
        });
    }

    public static void sendToClientPlayer(CustomPacketPayload message, ServerPlayer player) {
        ServerPlayNetworking.send(player, message);
    }

    /**
     * 发送给所有监听此实体的玩家
     */
    public static void sendToTrackingEntityAndSelf(Entity centerEntity, CustomPacketPayload message) {
        if (centerEntity instanceof ServerPlayer player) {
            sendToClientPlayer(message, player);
        }
        sendToTrackingEntity(message, centerEntity);
    }

    public static void sendToAllPlayers(CustomPacketPayload message, MinecraftServer server) {
        for (ServerPlayer player : PlayerLookup.all(server)) {
            ServerPlayNetworking.send(player, message);
        }
    }

    public static void sendToTrackingEntity(CustomPacketPayload message, final Entity centerEntity) {
        for (ServerPlayer player : PlayerLookup.tracking(centerEntity)) {
            ServerPlayNetworking.send(player, message);
        }
    }

    public static void sendToDimension(CustomPacketPayload message, final Entity centerEntity) {
        if (centerEntity.level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : PlayerLookup.world(serverLevel)) {
                ServerPlayNetworking.send(player, message);
            }
        }
    }
}
