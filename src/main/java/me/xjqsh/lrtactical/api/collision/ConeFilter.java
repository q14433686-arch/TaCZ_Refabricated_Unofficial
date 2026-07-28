package me.xjqsh.lrtactical.api.collision;

import com.google.gson.annotations.SerializedName;
import me.xjqsh.lrtactical.util.VectorUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 锥形命中区域 —— 最常用的近战判定（挥砍）。
 *
 * <p>语义与本仓库 TACZ 侧 {@code ModernKineticGunItem#doMelee}（枪托击打）一致：
 * 先用 AABB 粗筛，再按夹角精筛，最后做遮挡检查。
 * 那份实现已在 26.2 上验证可用，本类的判定逻辑与之同源。
 *
 * <p>{@code exclude_self} 默认 true —— 否则挥刀会打到自己。
 */
public class ConeFilter implements ITargetFilter {
    @SerializedName("max_range")
    private double maxRange = 2.5d;

    @SerializedName("max_angle")
    private double maxAngle = 90d;

    @SerializedName("exclude_self")
    private boolean excludeSelf = true;

    public ConeFilter(double maxRange, double maxAngle) {
        this.maxRange = maxRange;
        this.maxAngle = maxAngle;
    }

    public ConeFilter(double maxRange, double maxAngle, boolean excludeSelf) {
        this.maxRange = maxRange;
        this.maxAngle = maxAngle;
        this.excludeSelf = excludeSelf;
    }

    @Override
    public @NotNull List<Entity> filterTargets(LivingEntity attacker, Vec3 origin, Vec3 direction) {
        List<Entity> targets = new ArrayList<>();
        AABB area = attacker.getBoundingBox().inflate(maxRange * 2, maxRange, maxRange * 2);
        // 配置里写的是「总张角」，判定用半角
        double halfAngle = maxAngle / 2.0;

        for (Entity candidate : attacker.level().getEntitiesOfClass(Entity.class, area)) {
            // marker 盔甲架是纯逻辑标记，不该被打到
            boolean notMarker = !(candidate instanceof ArmorStand armorStand) || !armorStand.isMarker();
            boolean self = this.excludeSelf && candidate == attacker;
            if (self || !notMarker) {
                continue;
            }
            if (VectorUtil.isInAngle(origin, direction, candidate, halfAngle, maxRange)
                    && ITargetFilter.hasLineOfSight(attacker, candidate)) {
                targets.add(candidate);
            }
        }
        return targets;
    }

    @Override
    public double getMaxRange() {
        return maxRange;
    }
}
