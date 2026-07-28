package me.xjqsh.lrtactical.api.collision;

import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 有向包围盒（Oriented Bounding Box）。
 *
 * <p>思路来自 AnECanSaiTin 的 HitboxAPI（上游即引用该实现）。
 *
 * @param center   旋转中心
 * @param extents  三个轴向上的半长
 * @param rotation 旋转
 *
 * <h2>26.2 移植改动：自行实现分离轴定理，不用 {@code Intersectionf.testObOb}</h2>
 * 上游用 JOML 的 {@code Intersectionf.testObOb(...)} 做 OBB-OBB 相交判定。
 * 本移植<b>改为自己实现分离轴定理（SAT）</b>，原因是<b>无法验证</b>：
 * <ul>
 *   <li>JOML 由 Minecraft 自带（{@code build.gradle} 里显式
 *       {@code exclude group: 'org.joml'}，说明依赖来自 MC 而非自行引入），
 *       但它<b>不在</b> {@code minecraft-merged} jar 内，
 *       沙盒中也找不到任何 joml jar，<b>字节码比对法用不上</b>；</li>
 *   <li>{@code testObOb} 有多个重载，参数表长达 30 个 float，
 *       且该方法在 JOML 各版本间签名有过调整 ——
 *       PORTING_NOTES 第 9 节的教训正是「查第三方库 API 必须认准对应版本」，
 *       而这里连版本都无从确认。</li>
 * </ul>
 *
 * <p>SAT 本身是标准算法（15 条分离轴：两个盒各 3 条面法线 + 9 条边叉积），
 * 只依赖 {@link Vector3f} 的基本运算（{@code add/sub/dot/mul/rotate/cross}）——
 * 这些是 JOML 最核心、跨版本最稳定的 API，且本仓库已在动画系统中大量使用。
 * <b>宁可多写 40 行确定能编译的代码，也不赌一个查不到签名的方法。</b>
 */
public record OBB(Vector3f center, Vector3f extents, Quaternionf rotation) {

    /**
     * OBB 的三个正交轴（已按 rotation 旋转）。
     *
     * <p>用 {@code Vector3f#rotate(Quaternionfc)} 而非
     * {@code Quaternionf#transform(Vector3f)}：前者的单参重载已在 26.2 原版
     * 调用点确认存在（{@code org.joml.Vector3f.rotate(org.joml.Quaternionfc)}），
     * 后者只查到 {@code transform(float,float,float,Vector3f)} 四参版本。
     * 两者语义相同，取能确认签名的那个。
     */
    public Vector3f[] getAxes() {
        return new Vector3f[]{
                new Vector3f(1, 0, 0).rotate(rotation),
                new Vector3f(0, 1, 0).rotate(rotation),
                new Vector3f(0, 0, 1).rotate(rotation)};
    }

    /** 与轴对齐包围盒的相交判定。 */
    public boolean interactsWithAABB(AABB aabb) {
        Vector3f otherCenter = aabb.getCenter().toVector3f();
        Vector3f otherExtents = new Vector3f(
                (float) (aabb.getXsize() / 2.0),
                (float) (aabb.getYsize() / 2.0),
                (float) (aabb.getZsize() / 2.0));
        // AABB 的轴就是世界坐标轴
        Vector3f[] otherAxes = new Vector3f[]{
                new Vector3f(1, 0, 0),
                new Vector3f(0, 1, 0),
                new Vector3f(0, 0, 1)};
        return satOverlap(this.center, this.getAxes(), this.extents,
                otherCenter, otherAxes, otherExtents);
    }

    /**
     * 分离轴定理：两个 OBB 相交 &lt;=&gt; 在所有候选分离轴上的投影区间都重叠。
     *
     * <p>候选轴共 15 条：A 的 3 条面法线、B 的 3 条面法线、以及两两叉积的 9 条边方向。
     * 只要找到<b>任意一条</b>轴使两者投影不重叠，即可判定为不相交。
     */
    private static boolean satOverlap(Vector3f ca, Vector3f[] axesA, Vector3f ea,
                                      Vector3f cb, Vector3f[] axesB, Vector3f eb) {
        // 只用「单参、就地修改」的 JOML 重载（已在 26.2 原版调用点确认存在）：
        //   Vector3f#sub(Vector3fc) / cross(Vector3fc) / lengthSquared() / dot(Vector3fc)
        // 带 dest 参数的双参重载在原版中查不到调用点、无法核实签名，故一律不用，
        // 改为先 new 一份副本再就地运算 —— 语义等价且可确定编译通过。
        Vector3f t = new Vector3f(cb).sub(ca);

        for (int i = 0; i < 3; i++) {
            if (isSeparated(t, axesA[i], axesA, ea, axesB, eb)) {
                return false;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (isSeparated(t, axesB[i], axesA, ea, axesB, eb)) {
                return false;
            }
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Vector3f axis = new Vector3f(axesA[i]).cross(axesB[j]);
                // 平行边叉积为零向量，不构成有效分离轴，跳过。
                // 同时避免对零向量做 normalize 产生 NaN。
                if (axis.lengthSquared() < 1.0e-6f) {
                    continue;
                }
                if (isSeparated(t, axis, axesA, ea, axesB, eb)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 在给定轴上两盒投影是否分离。
     *
     * <p><b>轴不要求已归一化</b>：距离与两个投影半径都线性正比于 |axis|，
     * 比较 {@code distance > radiusA + radiusB} 时该因子两边同时出现、可以约掉。
     * 这样就无需调用 {@code normalize()}（其单参重载在原版中查不到调用点），
     * 也顺带避免了对近零向量归一化产生 NaN。
     */
    private static boolean isSeparated(Vector3f t, Vector3f axis,
                                       Vector3f[] axesA, Vector3f ea,
                                       Vector3f[] axesB, Vector3f eb) {
        float distance = Math.abs(t.dot(axis));
        float radiusA = projectedRadius(axis, axesA, ea);
        float radiusB = projectedRadius(axis, axesB, eb);
        return distance > radiusA + radiusB;
    }

    /** 盒子在某轴上的投影半径。 */
    private static float projectedRadius(Vector3f axis, Vector3f[] axes, Vector3f extents) {
        return Math.abs(axes[0].dot(axis)) * extents.x
                + Math.abs(axes[1].dot(axis)) * extents.y
                + Math.abs(axes[2].dot(axis)) * extents.z;
    }
}
