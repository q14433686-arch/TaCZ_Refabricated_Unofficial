package com.tacz.guns.client.render.scope;

import com.tacz.guns.client.model.bedrock.BedrockCube;

/**
 * Shared per-cube size filter for reticle submissions.
 *
 * <h2>为什么蚀刻与发光两类准星都必须过这个筛子</h2>
 * {@code division} 系节点里混着<b>遮光板</b>（挡住镜外视野的大面），上游靠 stencil 把它们裁在
 * 目镜圆外；本移植不用 stencil，屏幕空间 mask 生效时会整块 discard 它们，但 mask 链路降级
 * （FBO 不符、Iris 重载等）时，若不做 CPU 过滤，遮光板会直接外露上大屏幕。
 *
 * <p>此前只有 {@link EtchedReticleRenderer} 过滤；实测 {@code scope_vudu} 把遮光板放进了
 * <b>发光</b>节点：{@code division_illuminated} 共 6 个 cube，其中 5 块是
 * {@code [50,50,0] / [50,100,0]×2 / [100,250,0]×2}，被 {@code IlluminatedReticleRenderer}
 * 整树提交成大块亮面 —— 因此过滤抽到这里共用。</p>
 *
 * <h2>阈值的出处（默认枪包全量 73 个准星 cube 的实测分布）</h2>
 * 按「AABB 第二长轴」排序后，全包存在一个干净的分界：
 * <pre>
 *   最大的合法标线 middle = 5.804   （scope_qmk152 的圆环弧段 [3.40021, 4.80769, 0]）
 *   最小的遮光板   middle = 8.800   （scope_98k 的 [9.218, 8.8, 0.044]）
 * </pre>
 * 阈值取两者之间的 {@value #THIN_MARK_MAX_EXTENT}：既能保住 QKM 的整圈弧段
 * （旧阈值 4.0 会误杀 middle 4.67~5.80 的弧段，圆环出现缺口），
 * 又能拦下全部遮光板（98k / 1873 / aug / qmk / vudu 的最小面板都在 8.8 以上）。
 *
 * <p>坐标单位是模型 json 单位（1 单位 = 1/16 格），与 {@code BedrockCube} 顶点存储一致。</p>
 */
final class ReticleMarkFilter {
    /** 准星标线允许的 AABB 第二长轴上限（模型单位）。 */
    static final float THIN_MARK_MAX_EXTENT = 6.0F;

    private ReticleMarkFilter() {
    }

    /**
     * Division 树把细的准星标线与巨大的遮光板混在一起。只保留「三条 AABB 轴中第二长轴
     * 不超过 {@value #THIN_MARK_MAX_EXTENT}」的线/环类 cube，整版面一律拒绝。
     *
     * <p>退化占位面不参与测量：只声明了部分 UV 面（例如只有 {@code south}）的 cube 会给
     * 未声明的面生成四个顶点全部钉在 {@code (0,0,0)} 的零面积 polygon。对未旋转的 cube 来说，
     * 该点等于<b>骨骼 pivot</b>，可能离几何很远（scope_1873_6x 的 division pivot 在模型原点，
     * 而十字线在 z=-111）——把它计入包围盒会把盒子撑到 32×10.97×111，
     * 从而误杀横线（当年「竖线在、横线没」的直接原因）。</p>
     */
    static boolean isThinMark(BedrockCube cube) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        boolean foundVertex = false;

        for (var polygon : cube.getPolygons()) {
            if (polygon == null || isDegenerateEmptyFace(polygon)) {
                continue;
            }
            for (var vertex : polygon.vertices) {
                if (vertex == null) {
                    continue;
                }
                foundVertex = true;
                minX = Math.min(minX, vertex.pos.x());
                minY = Math.min(minY, vertex.pos.y());
                minZ = Math.min(minZ, vertex.pos.z());
                maxX = Math.max(maxX, vertex.pos.x());
                maxY = Math.max(maxY, vertex.pos.y());
                maxZ = Math.max(maxZ, vertex.pos.z());
            }
        }
        if (!foundVertex) {
            return false;
        }

        float x = maxX - minX;
        float y = maxY - minY;
        float z = maxZ - minZ;
        float largest = Math.max(x, Math.max(y, z));
        float smallest = Math.min(x, Math.min(y, z));
        float middle = x + y + z - largest - smallest;
        return middle <= THIN_MARK_MAX_EXTENT;
    }

    /**
     * 真实面可能横跨数个单位，但未映射 UV 的占位面四个顶点全部钉在精确的
     * {@code (0,0,0)}。这种 polygon 面积为零、永远不会光栅化，因此不得参与包围盒测量。
     */
    private static boolean isDegenerateEmptyFace(com.tacz.guns.client.model.bedrock.BedrockPolygon polygon) {
        if (polygon.vertices == null || polygon.vertices.length == 0) {
            return true;
        }
        for (var vertex : polygon.vertices) {
            if (vertex == null) {
                continue;
            }
            if (vertex.pos.x() != 0.0F || vertex.pos.y() != 0.0F || vertex.pos.z() != 0.0F) {
                return false;
            }
        }
        return true;
    }
}
