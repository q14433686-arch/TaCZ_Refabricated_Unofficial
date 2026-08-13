package me.xjqsh.lrtactical.network;

import me.xjqsh.lrtactical.resource.CommonAssetsManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * LRTactical 的网络层。
 *
 * <p>结构照抄本仓库 {@code com.tacz.guns.network.NetworkHandler} 的三段式：
 * <ol>
 *   <li>{@link #registerPayloads()} —— 载荷类型注册，<b>两端都要调</b>；</li>
 *   <li>{@link #registerS2CPackets()} —— 客户端接收器，仅客户端调；</li>
 *   <li>{@link #syncToPlayer} —— 服务端发送。</li>
 * </ol>
 *
 * <p><b>为什么 {@code registerPayloads} 必须在公共入口调用</b>：
 * {@code PayloadTypeRegistry.playS2C().register} 是<b>编解码器注册表</b>，
 * 服务端要用它来编码、客户端要用它来解码，缺一端就会在握手/发包时报
 * 「Unknown payload id」。TACZ 侧同样是在 {@code TaCZFabric#onInitialize}
 * （公共入口）里调 {@code registerPayloads}，而不是只在客户端调。
 */
public final class LrNetworkHandler {
    private LrNetworkHandler() {
    }

    /** 载荷类型注册 —— 必须在<b>公共</b>入口调用（服务端编码 + 客户端解码都依赖它）。 */
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(
                ServerMessageSyncLrPack.TYPE, ServerMessageSyncLrPack.CODEC);
        PayloadTypeRegistry.playS2C().register(
                ServerMessageCustomCooldown.TYPE, ServerMessageCustomCooldown.CODEC);
        PayloadTypeRegistry.playS2C().register(
                ServerMessageScreenShake.TYPE, ServerMessageScreenShake.CODEC);
        // C2S：近战攻击请求。serverboundPlay 同样两端都要注册
        // （客户端编码、服务端解码），只在一端注册会报 Unknown payload id。
        PayloadTypeRegistry.playC2S().register(
                ClientMessagePrepareMeleeAttack.TYPE, ClientMessagePrepareMeleeAttack.CODEC);
    }

    /** 服务端接收器注册 —— 必须在<b>公共</b>入口调用（专用服务器没有客户端入口）。 */
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(ClientMessagePrepareMeleeAttack.TYPE,
                (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
    }

    /** 客户端接收器注册。 */
    @Environment(EnvType.CLIENT)
    public static void registerS2CPackets() {
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageSyncLrPack.TYPE,
                (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageCustomCooldown.TYPE,
                (msg, ctx) -> msg.handle(ctx.player(), ctx.responseSender()));
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageScreenShake.TYPE,
                (msg, ctx) -> ctx.client().execute(() ->
                        me.xjqsh.lrtactical.client.camera.ScreenShakeState.start(
                                msg.durationTicks(), msg.radius(), msg.amplitude(), msg.origin())));
    }

    /**
     * 把当前索引发给某个玩家。
     *
     * <p>由 {@code ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS} 触发 ——
     * 该事件在<b>玩家登入</b>与<b>数据包重载</b>时都会发，
     * 正是 TACZ 侧 {@code CommonAssetsManager#OnDatapackSync} 挂的同一个钩子，
     * 时机已被本仓库验证过。
     */
    public static void syncToPlayer(ServerPlayer player, boolean joined) {
        ServerPlayNetworking.send(player, new ServerMessageSyncLrPack(
                CommonAssetsManager.get().getThrowableIndexManager().getNetworkCache(),
                CommonAssetsManager.get().getMeleeIndexManager().getNetworkCache(),
                CommonAssetsManager.get().getConsumableIndexManager().getNetworkCache()));
    }

    public static void syncCooldown(ServerPlayer player, net.minecraft.resources.Identifier id,
                                    int duration) {
        ServerPlayNetworking.send(player,
                new ServerMessageCustomCooldown(id, Math.max(0, duration)));
    }

    /** Sends an explosion shake only to players who can actually perceive the blast. */
    public static void sendScreenShake(ServerLevel level,
                                       net.minecraft.world.phys.Vec3 origin,
                                       double radius,
                                       double durationTicks,
                                       double amplitude) {
        if (radius <= 0.0 || durationTicks <= 0.0 || amplitude <= 0.0) {
            return;
        }
        double radiusSqr = radius * radius;
        ServerMessageScreenShake message = new ServerMessageScreenShake(
                durationTicks, radius, amplitude, origin);
        for (ServerPlayer player : PlayerLookup.world(level)) {
            if (player.distanceToSqr(origin) <= radiusSqr) {
                ServerPlayNetworking.send(player, message);
            }
        }
    }
}
