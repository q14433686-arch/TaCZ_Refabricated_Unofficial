package com.tacz.guns.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ExplodeUtil {
    public static void createExplosion(Entity owner, Entity exploder, float damage, float radius, boolean knockback, boolean destroy, Vec3 hitPos) {
        // 客户端不执行
        if (!(exploder.level() instanceof ServerLevel level)) {
            return;
        }
        // 26.2 修复: 使用原版 ServerLevel.explode() 处理:
        //   - 客户端爆炸粒子/音效 (ClientboundExplodePacket)
        //   - 方块破坏 (interactWithBlocks)
        //   - 击退 (hitPlayers → ClientboundExplodePacket.playerKnockback)
        Level.ExplosionInteraction interaction = destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        level.explode(exploder, hitPos.x(), hitPos.y(), hitPos.z(), radius, false, interaction);

        // 自定义爆炸伤害: 枪包配置的 explosion_damage 值，按距离线性衰减
        // 原版爆炸伤害公式为 (1-dist/radius)*radius，与枪包配置值不一致，
        // 此处额外应用 (1-dist/radius)*damage 作为枪包定义的真实伤害。
        // 原版伤害 (最大=radius) 作为近距附加伤害保留，不影响平衡（radius 通常 2~5，damage 通常 10~50+）
        if (damage > 0) {
            DamageSource source = exploder.damageSources().explosion(exploder, owner);
            double size = radius * 2.0;
            AABB area = new AABB(
                    hitPos.x() - size, hitPos.y() - size, hitPos.z() - size,
                    hitPos.x() + size, hitPos.y() + size, hitPos.z() + size);
            List<Entity> entities = level.getEntities(exploder, area);
            for (Entity entity : entities) {
                if (entity == exploder) continue;
                double dist = Math.sqrt(entity.distanceToSqr(hitPos));
                if (dist > radius * 2.0) continue;
                // 距离衰减: 中心=100%伤害, 边缘=0
                float impact = (float) (1.0 - dist / (radius * 2.0));
                if (impact <= 0) continue;
                // 取消无敌帧，确保自定义伤害生效（与 tacAttackEntity 相同手法）
                entity.invulnerableTime = 0;
                entity.hurt(source, damage * impact);
            }
        }
    }
}
