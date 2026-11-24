package com.tacz.guns.network.message.event;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.event.common.GunFinishReloadEvent;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Observations, never action requests. FIRE is already predicted on the shooter's client. */
public record ServerMessageGunActionPost(Kind kind, int entityId, ItemStack stack) implements CustomPacketPayload {
    public enum Kind { FIRE, FINISH_RELOAD }
    public static final Type<ServerMessageGunActionPost> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_gun_action_post"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerMessageGunActionPost> CODEC = StreamCodec.ofMember(
            (message, buf) -> {
                buf.writeEnum(message.kind);
                buf.writeVarInt(message.entityId);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, message.stack);
            }, buf -> new ServerMessageGunActionPost(buf.readEnum(Kind.class), buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

    @Override public Type<ServerMessageGunActionPost> type() { return TYPE; }

    public static void notifyClients(Kind kind, LivingEntity shooter, ItemStack gun) {
        var message = new ServerMessageGunActionPost(kind, shooter.getId(), gun.copy());
        for (var player : PlayerLookup.tracking(shooter)) {
            if (player != shooter && ServerPlayNetworking.canSend(player, TYPE)) ServerPlayNetworking.send(player, message);
        }
        if (kind == Kind.FINISH_RELOAD && shooter instanceof ServerPlayer player && ServerPlayNetworking.canSend(player, TYPE)) {
            ServerPlayNetworking.send(player, message);
        }
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player) {
        if (!(player.level().getEntity(entityId) instanceof LivingEntity shooter)) return;
        switch (kind) {
            case FIRE -> GunFirePostEvent.EVENT.invoker().post(new GunFirePostEvent(shooter, stack, LogicalSide.CLIENT));
            case FINISH_RELOAD -> GunFinishReloadEvent.EVENT.invoker().post(new GunFinishReloadEvent(shooter, stack, LogicalSide.CLIENT));
        }
    }
}
