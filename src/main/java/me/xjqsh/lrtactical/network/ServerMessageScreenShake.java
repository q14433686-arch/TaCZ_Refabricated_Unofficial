package me.xjqsh.lrtactical.network;

import me.xjqsh.lrtactical.EquipmentMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Server-to-client visual explosion shake; gameplay remains fully server authoritative. */
public record ServerMessageScreenShake(double durationTicks,
                                       double radius,
                                       double amplitude,
                                       Vec3 origin) implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(
            EquipmentMod.MOD_ID, "s2c_screen_shake");
    public static final CustomPacketPayload.Type<ServerMessageScreenShake> TYPE =
            new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageScreenShake> CODEC =
            StreamCodec.ofMember(ServerMessageScreenShake::write, ServerMessageScreenShake::new);

    public ServerMessageScreenShake(FriendlyByteBuf buffer) {
        this(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeDouble(Math.max(0.0, durationTicks));
        buffer.writeDouble(Math.max(0.0, radius));
        buffer.writeDouble(Math.max(0.0, amplitude));
        buffer.writeDouble(origin.x);
        buffer.writeDouble(origin.y);
        buffer.writeDouble(origin.z);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
