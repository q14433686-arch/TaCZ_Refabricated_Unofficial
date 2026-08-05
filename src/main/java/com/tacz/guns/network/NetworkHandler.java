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
        PayloadTypeRegistry.serverboundPlay().register(ClientMessagePlayerShoot.TYPE, ClientMessagePlayerShoot.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessagePlayerReloadGun.TYPE, ClientMessagePlayerReloadGun.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessagePlayerCancelReload.TYPE, ClientMessagePlayerCancelReload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessagePlayerFireSelect.TYPE, ClientMessagePlayerFireSelect.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessagePlayerAim.TYPE, ClientMessagePlayerAim.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessagePlayerCrawl.TYPE, ClientMessagePlayerCrawl.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessagePlayerDrawGun.TYPE, ClientMessagePlayerDrawGun.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessageCraft.TYPE, ClientMessageCraft.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessageAssembleCartridge.TYPE, ClientMessageAssembleCartridge.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessageSalvageIndustry.TYPE, ClientMessageSalvageIndustry.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessagePlayerZoom.TYPE, ClientMessagePlayerZoom.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessageRefitGun.TYPE, ClientMessageRefitGun.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessageUnloadAttachment.TYPE, ClientMessageUnloadAttachment.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessagePlayerBoltGun.TYPE, ClientMessagePlayerBoltGun.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessagePlayerMelee.TYPE, ClientMessagePlayerMelee.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessageSyncBaseTimestamp.TYPE, ClientMessageSyncBaseTimestamp.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientMessageLaserColor.TYPE, ClientMessageLaserColor.CODEC);
        // S2C (clientbound play)
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageSound.TYPE, ServerMessageSound.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageCraft.TYPE, ServerMessageCraft.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageRefreshRefitScreen.TYPE, ServerMessageRefreshRefitScreen.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageSwapItem.TYPE, ServerMessageSwapItem.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageLevelUp.TYPE, ServerMessageLevelUp.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageGunHurt.TYPE, ServerMessageGunHurt.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageGunKill.TYPE, ServerMessageGunKill.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageUpdateEntityData.TYPE, ServerMessageUpdateEntityData.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageSyncGunPack.TYPE, ServerMessageSyncGunPack.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageGunDraw.TYPE, ServerMessageGunDraw.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageGunFire.TYPE, ServerMessageGunFire.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageGunFireSelect.TYPE, ServerMessageGunFireSelect.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageGunMelee.TYPE, ServerMessageGunMelee.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageGunReload.TYPE, ServerMessageGunReload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageGunShoot.TYPE, ServerMessageGunShoot.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageSyncBaseTimestamp.TYPE, ServerMessageSyncBaseTimestamp.CODEC);
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
        ServerPlayNetworking.registerGlobalReceiver(ClientMessageAssembleCartridge.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ServerPlayNetworking.registerGlobalReceiver(ClientMessageSalvageIndustry.TYPE, (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
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
            FriendlyByteBuf response = FriendlyByteBufs.create();
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
            for (ServerPlayer player : PlayerLookup.level(serverLevel)) {
                ServerPlayNetworking.send(player, message);
            }
        }
    }
}
