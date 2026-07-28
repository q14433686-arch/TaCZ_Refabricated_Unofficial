package me.xjqsh.lrtactical.api.collision;

import com.google.gson.annotations.SerializedName;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 射线命中区域 —— 用于突刺类武器，可配置穿透几个目标。
 *
 * <p>{@code penetration} 为 0 表示只打最近的一个。
 */
public class RayFilter implements ITargetFilter {
    @SerializedName("max_range")
    private double maxRange = 2.5d;

    @SerializedName("penetration")
    private int penetration = 0;

    public RayFilter(double maxRange, int penetration) {
        this.maxRange = maxRange;
        this.penetration = penetration;
    }

    @Override
    public @NotNull List<Entity> filterTargets(LivingEntity attacker, Vec3 origin, Vec3 direction) {
        Vec3 to = origin.add(direction.normalize().scale(maxRange));
        return findEntitiesOnPath(attacker, origin, to).stream()
                .limit(penetration + 1L)
                .map(EntityHitResult::getEntity)
                .toList();
    }

    /** 返回路径上的实体，<b>按距离由近到远</b>排序。 */
    @NotNull
    public List<RayEntityHitResult> findEntitiesOnPath(LivingEntity attacker, Vec3 startVec, Vec3 endVec) {
        List<RayEntityHitResult> hitEntities = new ArrayList<>();
        AABB area = attacker.getBoundingBox()
                .expandTowards(attacker.getViewVector(1.0f).scale(maxRange)).inflate(1.0);

        for (Entity entity : attacker.level().getEntities(attacker, area, EntitySelector.NO_SPECTATORS)) {
            if (entity.equals(attacker) || entity.equals(attacker.getVehicle()) || !entity.isAlive()
                    || !ITargetFilter.hasLineOfSight(attacker, entity)) {
                continue;
            }
            Optional<Vec3> clip = entity.getBoundingBox().clip(startVec, endVec);
            if (clip.isPresent()) {
                hitEntities.add(new RayEntityHitResult(entity, clip.get(), startVec));
            } else if (entity.getBoundingBox().contains(startVec)) {
                // 起点已在目标体内（贴脸），clip 会返回 empty，需单独处理
                hitEntities.add(new RayEntityHitResult(entity, startVec, startVec));
            }
        }
        // 上游用 (int)(a - b) 作比较器：距离差小于 1 时会被截断成 0（误判为相等），
        // 且 double 差值转 int 有溢出风险。改用标准的 Comparator.comparingDouble，
        // 行为更正确且无溢出 —— 这是移植时的有意修正，不是照搬。
        hitEntities.sort(Comparator.comparingDouble(RayEntityHitResult::getDistanceSqr));
        return hitEntities;
    }

    public static class RayEntityHitResult extends EntityHitResult {
        private final Vec3 source;
        private final double distanceSqr;

        public RayEntityHitResult(Entity entity, Vec3 location, Vec3 source) {
            super(entity, location);
            this.source = source;
            this.distanceSqr = source.distanceToSqr(location);
        }

        public Vec3 getSource() {
            return source;
        }

        public double getDistanceSqr() {
            return distanceSqr;
        }
    }

    @Override
    public double getMaxRange() {
        return maxRange;
    }
}
