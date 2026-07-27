package com.tacz.guns.network.message.event;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

public class ServerMessageGunKill implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_gunkill");
    public static final CustomPacketPayload.Type<ServerMessageGunKill> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageGunKill> CODEC = StreamCodec.ofMember(ServerMessageGunKill::write, ServerMessageGunKill::new);

    private final int bulletId;
    private final int killEntityId;
    private final int attackerId;
    private final Identifier gunId;
    private final Identifier gunDisplayId;
    private final boolean isHeadShot;
    private final float baseDamage;
    private final float headshotMultiplier;

    public ServerMessageGunKill(FriendlyByteBuf buf) {
        this(
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readIdentifier(), buf.readIdentifier(),
                buf.readFloat(), buf.readBoolean(), buf.readFloat()
        );
    }

    public ServerMessageGunKill(int bulletId, int killEntityId, int attackerId, Identifier gunId, Identifier gunDisplayId, float baseDamage, boolean isHeadShot, float headshotMultiplier) {
        this.bulletId = bulletId;
        this.killEntityId = killEntityId;
        this.attackerId = attackerId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
        this.baseDamage = baseDamage;
        this.isHeadShot = isHeadShot;
        this.headshotMultiplier = headshotMultiplier;
    }

        public void write(FriendlyByteBuf buf) {
        buf.writeInt(bulletId);
        buf.writeInt(killEntityId);
        buf.writeInt(attackerId);
        buf.writeIdentifier(gunId);
        buf.writeIdentifier(gunDisplayId);
        buf.writeFloat(baseDamage);
        buf.writeBoolean(isHeadShot);
        buf.writeFloat(headshotMultiplier);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        onKill(this);
    }

    @Environment(EnvType.CLIENT)
    private static void onKill(ServerMessageGunKill message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        @Nullable Entity bullet = level.getEntity(message.bulletId);
        @Nullable LivingEntity killedEntity = level.getEntity(message.killEntityId) instanceof LivingEntity livingEntity ? livingEntity : null;
        @Nullable LivingEntity attacker = level.getEntity(message.attackerId) instanceof LivingEntity livingEntity ? livingEntity : null;
        EntityKillByGunEvent event = new EntityKillByGunEvent(bullet, killedEntity, attacker, message.gunId, message.gunDisplayId, message.baseDamage, null, message.isHeadShot, message.headshotMultiplier, LogicalSide.CLIENT);
        EntityKillByGunEvent.CALLBACK.invoker().post(event);
    }
}
