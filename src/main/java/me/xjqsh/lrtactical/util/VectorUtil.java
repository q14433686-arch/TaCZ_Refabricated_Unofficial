package me.xjqsh.lrtactical.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 向量小工具 —— 近战索敌用。
 *
 * <p>与上游逐行一致；所用 API（{@link Vec3}/{@link AABB}）在 26.2 均无变化
 * （已对字节码核实 {@code normalize/dot/subtract/add/length}）。
 */
public final class VectorUtil {
    private VectorUtil() {
    }

    /**
     * 点到 AABB 的最短路径向量。
     *
     * <p>点在盒内时三个分量都为 0（返回零向量），调用方据此判定「距离为 0」。
     */
    public static Vec3 distanceVector(Vec3 point, AABB box) {
        double dx = 0;
        if (box.minX > point.x) {
            dx = box.minX - point.x;
        } else if (box.maxX < point.x) {
            dx = box.maxX - point.x;
        }
        double dy = 0;
        if (box.minY > point.y) {
            dy = box.minY - point.y;
        } else if (box.maxY < point.y) {
            dy = box.maxY - point.y;
        }
        double dz = 0;
        if (box.minZ > point.z) {
            dz = box.minZ - point.z;
        } else if (box.maxZ < point.z) {
            dz = box.maxZ - point.z;
        }
        return new Vec3(dx, dy, dz);
    }

    /** 两向量夹角（度）。 */
    public static double angleBetween(Vec3 v1, Vec3 v2) {
        return Math.toDegrees(Math.acos(v1.normalize().dot(v2.normalize())));
    }

    /**
     * 目标是否落在以 {@code origin} 为顶点、{@code view} 为轴的锥形范围内。
     *
     * <p>判定「最短路方向」或「实体中心方向」<b>任一</b>落在夹角内即算命中 ——
     * 与上游一致：只按中心判会让贴脸的大体型怪打不中。
     */
    public static boolean isInAngle(Vec3 origin, Vec3 view, Entity target, double maxAngle, double maxDistance) {
        Vec3 positionVector = target.position().add(0, target.getBbHeight() / 2F, 0).subtract(origin);

        Vec3 distanceVector = distanceVector(origin, target.getBoundingBox());
        double distance = distanceVector.length();

        // 距离为 0 表示 origin 在目标包围盒内部，直接判命中
        if (distance == 0) {
            return true;
        }
        if (distance > maxDistance) {
            return false;
        }
        return angleBetween(view, distanceVector) <= maxAngle
                || angleBetween(view, positionVector) <= maxAngle;
    }
}
