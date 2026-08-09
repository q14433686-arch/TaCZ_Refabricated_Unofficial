package cn.sh1rocu.tacz.compat.meshloader.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * poly_mesh 的一个绘制单元：解析 + 顶点写入。
 *
 * <p>数据来自 Blockbench Meshy 插件导出的基岩版 {@code geo.json} 中骨骼的
 * {@code poly_mesh} 段（positions / normals / uvs / polys，三角形或四边形面）。</p>
 *
 * <p>26.2 移植版：删除了上游的 VBO 缓存与即时模式绘制路径
 * （26.2 已移除 {@code Tesselator}/{@code BufferUploader}），只保留解析与
 * 向 {@link VertexConsumer} 写入顶点的能力。顶点每帧由快照回调写入，
 * 引擎按 RenderType 自行分批上传。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
@Environment(EnvType.CLIENT)
public class PolyMesh {

    // =========================================================
    // ▼ 坐标/UV/法线适配开关（与上游保持一致，实机如出现
    //   光照反向/模型翻转，优先调整这里）▼
    // =========================================================
    private static final boolean FLIP_MODEL_X       = false;
    private static final boolean FLIP_MODEL_Y       = true;
    private static final boolean FLIP_UV_V          = true;
    private static final boolean FORCE_FLAT_SHADING = true;
    private static final boolean INVERT_FLAT_NORMAL = false;
    // =========================================================

    private final float[] bakedX, bakedY, bakedZ;
    private final float[] bakedNX, bakedNY, bakedNZ;
    private final float[] bakedU, bakedV;
    private final int vertexCount;

    public PolyMesh(JsonObject meshObj, float texWidth, float texHeight, float[] absPivot) {
        float pivotX = absPivot[0], pivotY = absPivot[1], pivotZ = absPivot[2];

        boolean normalizedUvs = meshObj.has("normalized_uvs") && meshObj.get("normalized_uvs").getAsBoolean();
        float[][] positions = parse2DArray(meshObj.getAsJsonArray("positions"), 3);
        float[][] normals   = parse2DArray(meshObj.getAsJsonArray("normals"), 3);
        float[][] uvs       = parse2DArray(meshObj.getAsJsonArray("uvs"), 2);
        int[][][] polys     = parse3DArray(meshObj.getAsJsonArray("polys"));

        int totalVerts = 0;
        for (int[][] poly : polys) {
            if (poly.length >= 3) totalVerts += (poly.length == 3) ? 4 : poly.length;
        }
        this.vertexCount = totalVerts;
        this.bakedX  = new float[totalVerts]; this.bakedY  = new float[totalVerts]; this.bakedZ  = new float[totalVerts];
        this.bakedNX = new float[totalVerts]; this.bakedNY = new float[totalVerts]; this.bakedNZ = new float[totalVerts];
        this.bakedU  = new float[totalVerts]; this.bakedV  = new float[totalVerts];

        int vIdx = 0;
        for (int[][] poly : polys) {
            if (poly.length < 3) continue;
            float faceNx = 0, faceNy = 0, faceNz = 0;
            if (FORCE_FLAT_SHADING) {
                float[] v0 = positions[poly[0][0]], v1 = positions[poly[1][0]], v2 = positions[poly[2][0]];
                float ux = v1[0] - v0[0], uy = v1[1] - v0[1], uz = v1[2] - v0[2];
                float vx = v2[0] - v0[0], vy = v2[1] - v0[1], vz = v2[2] - v0[2];
                faceNx = INVERT_FLAT_NORMAL ? vy * uz - vz * uy : uy * vz - uz * vy;
                faceNy = INVERT_FLAT_NORMAL ? vz * ux - vx * uz : uz * vx - ux * vz;
                faceNz = INVERT_FLAT_NORMAL ? vx * uy - vy * ux : ux * vy - uy * vx;
                float len = (float) Math.sqrt(faceNx * faceNx + faceNy * faceNy + faceNz * faceNz);
                if (len > 1e-6f) {
                    faceNx /= len;
                    faceNy /= len;
                    faceNz /= len;
                }
            }
            int drawCount = (poly.length == 3) ? 4 : poly.length;
            for (int i = 0; i < drawCount; i++) {
                int srcIdx = (poly.length == 3 && i == 3) ? 2 : i;
                int[] vi = poly[srcIdx];
                float[] pos = positions[vi[0]];
                float[] uv = uvs[vi[2]];
                bakedX[vIdx] = (FLIP_MODEL_X ? -(pos[0] - pivotX) : (pos[0] - pivotX)) / 16.0f;
                bakedY[vIdx] = (FLIP_MODEL_Y ? -(pos[1] - pivotY) : (pos[1] - pivotY)) / 16.0f;
                bakedZ[vIdx] = (pos[2] - pivotZ) / 16.0f;
                if (FORCE_FLAT_SHADING) {
                    bakedNX[vIdx] = FLIP_MODEL_X ? -faceNx : faceNx;
                    bakedNY[vIdx] = FLIP_MODEL_Y ? -faceNy : faceNy;
                    bakedNZ[vIdx] = faceNz;
                } else {
                    float[] n = normals[vi[1]];
                    bakedNX[vIdx] = FLIP_MODEL_X ? -n[0] : n[0];
                    bakedNY[vIdx] = FLIP_MODEL_Y ? -n[1] : n[1];
                    bakedNZ[vIdx] = n[2];
                }
                bakedU[vIdx] = normalizedUvs ? uv[0] : (uv[0] / texWidth);
                float v = normalizedUvs ? uv[1] : (uv[1] / texHeight);
                bakedV[vIdx] = FLIP_UV_V ? 1.0f - v : v;
                vIdx++;
            }
        }
    }

    public int getVertexCount() {
        return vertexCount;
    }

    /**
     * 把烘焙好的顶点写入 consumer。
     *
     * <p>变换方式与移植版 {@code BedrockCubeBox}/{@code BedrockCubePerFace}
     * 完全一致：位置手动乘 model 矩阵、法线手动乘 normal 矩阵后以原始坐标
     * 写入（不依赖 addVertex(Matrix4f,...) 等重载的变换语义）。</p>
     *
     * <p>注意：上游 1.21.1 的 consumer 回退路径在写入时对法线取了负号
     * （当时是为配合旧管线的手工修正）。26.2 版按上游主路径（VBO 烘焙）的
     * 语义直接写烘焙法线；若实机出现光照反向，把下面的 {@code bakedNX/NY/NZ}
     * 取负即可。</p>
     */
    public void compile(PoseStack.Pose pose, VertexConsumer consumer,
                        int light, int overlay, float red, float green, float blue, float alpha) {
        if (vertexCount == 0) return;
        Matrix4f matrix4f = pose.pose();
        Matrix3f matrix3f = pose.normal();
        Vector4f pos = new Vector4f();
        Vector3f normal = new Vector3f();
        for (int i = 0; i < vertexCount; i++) {
            pos.set(bakedX[i], bakedY[i], bakedZ[i], 1.0F);
            pos.mul(matrix4f);
            normal.set(bakedNX[i], bakedNY[i], bakedNZ[i]);
            normal.mul(matrix3f);
            consumer.addVertex(pos.x(), pos.y(), pos.z())
                    .setColor(red, green, blue, alpha)
                    .setUv(bakedU[i], bakedV[i])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, normal.x(), normal.y(), normal.z());
        }
    }

    // =========================================================================
    // 解析工具
    // =========================================================================

    private float[][] parse2DArray(JsonArray array, int dim) {
        if (array == null) return new float[0][0];
        float[][] result = new float[array.size()][dim];
        for (int i = 0; i < array.size(); i++) {
            JsonArray sub = array.get(i).getAsJsonArray();
            for (int j = 0; j < Math.min(dim, sub.size()); j++) {
                result[i][j] = sub.get(j).getAsFloat();
            }
        }
        return result;
    }

    private int[][][] parse3DArray(JsonArray array) {
        if (array == null) return new int[0][0][0];
        int[][][] result = new int[array.size()][][];
        for (int i = 0; i < array.size(); i++) {
            JsonArray face = array.get(i).getAsJsonArray();
            result[i] = new int[face.size()][3];
            for (int j = 0; j < face.size(); j++) {
                JsonArray vd = face.get(j).getAsJsonArray();
                result[i][j][0] = vd.get(0).getAsInt();
                result[i][j][1] = vd.get(1).getAsInt();
                result[i][j][2] = vd.get(2).getAsInt();
            }
        }
        return result;
    }
}
