package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.experience.GunLevelImplementation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class ServerMessageLevelUp implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_levelup");
    public static final CustomPacketPayload.Type<ServerMessageLevelUp> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerMessageLevelUp> CODEC = StreamCodec.ofMember(ServerMessageLevelUp::write, ServerMessageLevelUp::new);

    private final ItemStack gun;
    private final int level;

    public ServerMessageLevelUp(RegistryFriendlyByteBuf buf) {
        this(ItemStack.STREAM_CODEC.decode(buf), buf.readInt());
    }

    public ServerMessageLevelUp(ItemStack gun, int level) {
        this.gun = gun;
        this.level = level;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        ItemStack.STREAM_CODEC.encode(buf, gun);
        buf.writeInt(level);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        onLevelUp(this);
    }

    @Environment(EnvType.CLIENT)
    private static void onLevelUp(ServerMessageLevelUp message) {
        int level = message.getLevel();
        ItemStack gun = message.getGun();
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Component subtitle = level >= GunLevelImplementation.MAX_LEVEL
                ? Component.translatable("toast.tacz.sub.final_level")
                : Component.translatable(GunLevelImplementation.isHandlingEnabled()
                        ? "toast.tacz.sub.handling_up" : "toast.tacz.sub.level_up");
        // 26.2 no longer exposes either old ToastManager accessor on
        // Minecraft. Keep the upgrade feedback authoritative and portable by
        // using the player message path already used by client key actions.
        player.sendSystemMessage(Component.translatable("toast.tacz.level_up")
                .append(Component.literal(" ")).append(subtitle));
    }

    public ItemStack getGun() {
        return this.gun;
    }

    public int getLevel() {
        return this.level;
    }
}
