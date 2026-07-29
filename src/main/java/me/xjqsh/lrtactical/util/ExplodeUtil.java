package me.xjqsh.lrtactical.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 手雷爆炸。
 *
 * <h2>为什么不移植上游的 {@code CustomExplosion}</h2>
 * 上游自建 {@code CustomExplosion extends Explosion}（185 行，含完整的
 * 方块破坏射线循环）。但 26.2 的 {@code Explosion} <b>已变成接口</b>
 * （字节码 {@code is_interface} 确认），实现类换成了 {@code ServerExplosion}，
 * 且 {@code getToBlow()} 之类的 API 已移除 —— 逐行硬翻要重写整个破坏循环。
 *
 * <p>本仓库为 TACZ 的 RPG/榴弹解决过<b>同一个问题</b>，结论见
 * {@code com.tacz.guns.util.ExplodeUtil}：直接用原版 {@code Level#explode}，
 * 由它统一负责客户端爆炸粒子/音效（{@code ClientboundExplodePacket}）、
 * 方块破坏与击退；伤害另按距离线性衰减自行施加。
 * 本类是那套做法在 lrtactical 侧的复用。
 *
 * <p><b>为什么伤害要自己再算一遍</b>：原版爆炸伤害公式约为
 * {@code (1-dist/radius)*radius}，与「手雷配置里写的 damage」不是一回事。
 * 若只调用原版 explode，配置的 damage 将完全不起作用。
 */
public final class ExplodeUtil {
    private ExplodeUtil() {
    }

    /**
     * @param owner   投掷者（用于伤害归属，可为 null）
     * @param exploder 爆炸实体本身
     * @param damage  枪包/数据包配置的爆炸伤害（中心值）
     * @param radius  爆炸半径
     * @param destroy 是否破坏方块
     * @param hitPos  爆炸中心
     */
    public static void createExplosion(@Nullable Entity owner, Entity exploder,
                                       float damage, float radius, boolean destroy, Vec3 hitPos) {
        // 只在服务端执行
        if (!(exploder.level() instanceof ServerLevel level)) {
            return;
        }

        Level.ExplosionInteraction interaction =
                destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        level.explode(exploder, hitPos.x(), hitPos.y(), hitPos.z(), radius, false, interaction);

        if (damage <= 0) {
            return;
        }

        DamageSource source = exploder.damageSources().explosion(exploder, owner);
        double reach = radius * 2.0;
        AABB area = new AABB(
                hitPos.x() - reach, hitPos.y() - reach, hitPos.z() - reach,
                hitPos.x() + reach, hitPos.y() + reach, hitPos.z() + reach);

        for (Entity entity : level.getEntities(exploder, area)) {
            if (entity == exploder) {
                continue;
            }
            double dist = Math.sqrt(entity.distanceToSqr(hitPos));
            if (dist > reach) {
                continue;
            }
            // 距离衰减：中心 100%，边缘 0
            float impact = (float) (1.0 - dist / reach);
            if (impact <= 0) {
                continue;
            }
            // 清无敌帧，确保自定义伤害不被原版爆炸伤害的无敌帧吃掉
            // （与 TACZ 侧同一手法）
            entity.invulnerableTime = 0;
            entity.hurt(source, damage * impact);
        }
    }
}
