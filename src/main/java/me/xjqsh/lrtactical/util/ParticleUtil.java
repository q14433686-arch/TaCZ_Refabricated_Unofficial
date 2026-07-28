package me.xjqsh.lrtactical.util;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端向客户端下发粒子的工具方法。
 *
 * <h2>26.2 变更</h2>
 * {@code ServerLevel#sendParticles} 的玩家定向重载现在是
 * {@code (ServerPlayer, ParticleOptions, boolean overrideLimiter, boolean alwaysShow, DDD, int, DDD, D)}
 * —— <b>两个 boolean</b>（字节码确认）。上游只传一个 {@code force}。
 *
 * <p>参照 {@code Level#addParticle} 的 {@code ClientLevel} LocalVariableTable，
 * 这两个 boolean 依次是 {@code overrideLimiter}（无视粒子数量上限）与
 * {@code alwaysShow}（无视距离/粒子设置强制显示）。
 * 上游的 {@code force} 语义对应 {@code overrideLimiter}，
 * 故 {@code alwaysShow} 固定传 {@code false} 以<b>保持原行为</b>，
 * 而不是两个都填 {@code true} 从而放大表现。
 */
public final class ParticleUtil {
    private ParticleUtil() {
    }

    public static <T extends ParticleOptions> void sendParticle(ServerLevel level, T particle,
                                                                double x, double y, double z, int count,
                                                                double xOffset, double yOffset, double zOffset,
                                                                double speed, boolean force) {
        for (ServerPlayer viewer : level.players()) {
            sendParticle(level, particle, x, y, z, count, xOffset, yOffset, zOffset, speed, force, viewer);
        }
    }

    public static <T extends ParticleOptions> void sendParticle(ServerLevel level, T particle,
                                                                double x, double y, double z, int count,
                                                                double xOffset, double yOffset, double zOffset,
                                                                double speed, boolean force, ServerPlayer viewer) {
        level.sendParticles(viewer, particle, force, false, x, y, z, count, xOffset, yOffset, zOffset, speed);
    }
}
