package com.tacz.guns.network.message;

import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.particles.BulletHoleOption;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** One impact notification drives the existing decal/surface/sound provider exactly once.
 * Unlike vanilla's particle broadcast, the configurable range keeps the shooter as an exception. */
public record ServerMessageBulletHitBlock(Vec3 hit, Direction face, BlockPos block,
        String ammo, String gun, String display) implements CustomPacketPayload {
    public static final Type<ServerMessageBulletHitBlock> TYPE = new Type<>(Identifier.parse("tacz:server_message_bullet_hit_block"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerMessageBulletHitBlock> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeDouble(p.hit.x); buf.writeDouble(p.hit.y); buf.writeDouble(p.hit.z);
                buf.writeEnum(p.face); buf.writeBlockPos(p.block); buf.writeUtf(p.ammo); buf.writeUtf(p.gun); buf.writeUtf(p.display); },
            buf -> new ServerMessageBulletHitBlock(new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),
                    buf.readEnum(Direction.class),buf.readBlockPos(),buf.readUtf(),buf.readUtf(),buf.readUtf()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static boolean shouldSend(int range, boolean shooter, double squaredDistance) {
        return shooter || range <= 0 || squaredDistance <= (double)range * range;
    }
    public void broadcast(ServerLevel level, @Nullable Entity shooter) {
        int range = SyncConfig.BULLET_HIT_BLOCK_EFFECT_SEND_DISTANCE.get();
        for (var player : level.players()) {
            if (shouldSend(range, player == shooter, player.distanceToSqr(hit)) && ServerPlayNetworking.canSend(player, TYPE))
                NetworkHandler.sendToClientPlayer(this, player);
        }
    }
    @Environment(EnvType.CLIENT)
    public static void handle(ServerMessageBulletHitBlock packet, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            var mc = context.client();
            if (mc.level == null || !Double.isFinite(packet.hit.x) || !Double.isFinite(packet.hit.y) || !Double.isFinite(packet.hit.z)) return;
            // Provider already separates disabled decals from fragments and sound. Do not spawn
            // a second vanilla particle event or play a duplicate hit sound here.
            mc.particleEngine.createParticle(new BulletHoleOption(packet.face,packet.block,packet.ammo,packet.gun,packet.display),
                    packet.hit.x,packet.hit.y,packet.hit.z,0,0,0);
        });
    }
}
