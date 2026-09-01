package cn.sh1rocu.tacz.compat.meshloader.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 一根骨骼上的一份 poly_mesh：解析为骨骼本地坐标数组（SoA 布局）。
 *
 * <p>顶点写入与移植版 {@code BedrockCubeBox#compile} 对齐：位置/法线手动乘矩阵后
 * 以裸坐标写入，{@code setNormal} <b>不再</b>传 Pose（26.2 的
 * {@code setNormal(Pose,FFF)} 会再乘一次法线矩阵）。</p>
 *
 * <h2>镜像绕序（下游 1.21.11 分支审查 A10，2026-08-31 采纳）</h2>
 * <p>位置烘焙做了 Y 轴镜像（{@code FLIP_MODEL_Y}），单轴镜像 det&lt;0 ⇒ 每个面的
 * 正反面互换。上游 TML 原始代码<b>不</b>反转发射绕序，于是「烘焙法线朝外」与
 * 「gl_FrontFacing 判为背面」同时成立 —— 原版管线不受影响（GPU 路径
 * NO_CARDINAL_LIGHTING 不读法线；两条路径的 RenderType 均不剔除背面），但光影包
 * 常见的 {@code normal *= gl_FrontFacing ? 1 : -1} 双面自洽写法会把朝外法线取反，
 * 高光/反射跑到错误一侧。修复对照物是本仓 {@code BedrockPolygon}：mirror 时
 * <b>反转顶点顺序</b> + 翻转被镜像轴的法线分量 —— poly_mesh 此前只做了后半截。
 * 绕序反转后：变换后绕序叉积 = det(D)·D·n 再取反 = +D·n = 烘焙法线，两者一致。
 * 无光影视觉零变化（无剔除 + 法线值不变），因此默认开启；
 * {@code MeshPolyMirrorReverseWinding=false} 可回退。</p>
 *
 * <p>三角形按上游 TML 语义展开为「第 4 顶点重复第 3 顶点」的退化 quad
 * （QUADS 拓扑下的标准三角形表达）。纯三角网格因此多付 ~33% 顶点；
 * 导入期三角形配对是后续性能方向（docs/investigations/TML_PERF_DIRECTIONS_2026_08_29.md 方向 2），
 * 本轮保持与上游一致的展开方式，先保正确性。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
@Environment(EnvType.CLIENT)
public class PolyMesh {

    private static final boolean FLIP_MODEL_X       = false;
    private static final boolean FLIP_MODEL_Y       = true;
    private static final boolean FLIP_UV_V          = true;

    private final float[] bakedX, bakedY, bakedZ;
    private final float[] bakedNX, bakedNY, bakedNZ;
    private final float[] bakedU, bakedV;
    private final int vertexCount;

    public PolyMesh(JsonObject meshObj, float texWidth, float texHeight, float[] absPivot) {
        float pivotX = absPivot[0], pivotY = absPivot[1], pivotZ = absPivot[2];

        // 构造期读一次配置（PolyMeshSupport 的解析缓存以 geo 为键，改配置需资源重载生效）。
        final boolean reverseWinding = readToggle(
                cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig.POLY_MIRROR_REVERSE_WINDING, true);
        final boolean invertNormals = readToggle(
                cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig.POLY_INVERT_NORMALS, false);
        final boolean preferPackNormals = readToggle(
                cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig.POLY_PREFER_PACK_NORMALS, false);
        // 位置做了奇数次轴镜像才需要反转绕序（当前 Y 一次 = 奇数）。
        final boolean mirrored = FLIP_MODEL_X ^ FLIP_MODEL_Y;
        final boolean reverse = mirrored && reverseWinding;

        boolean normalizedUvs = meshObj.has("normalized_uvs") && meshObj.get("normalized_uvs").getAsBoolean();
        float[][] positions = parse2DArray(meshObj.getAsJsonArray("positions"), 3);
        float[][] normals   = parse2DArray(meshObj.getAsJsonArray("normals"), 3);
        float[][] uvs       = parse2DArray(meshObj.getAsJsonArray("uvs"), 2);
        int[][][] polys     = parse3DArray(meshObj.getAsJsonArray("polys"));

        int totalVerts = 0;
        for (int[][] poly : polys) {
            if (poly.length >= 3) {
                totalVerts += (poly.length == 3) ? 4 : poly.length;
            }
        }
        this.vertexCount = totalVerts;
        this.bakedX  = new float[totalVerts]; this.bakedY  = new float[totalVerts]; this.bakedZ  = new float[totalVerts];
        this.bakedNX = new float[totalVerts]; this.bakedNY = new float[totalVerts]; this.bakedNZ = new float[totalVerts];
        this.bakedU  = new float[totalVerts]; this.bakedV  = new float[totalVerts];

        int vIdx = 0;
        for (int[][] poly : polys) {
            if (poly.length < 3) {
                continue;
            }
            // 平坦法线：从【原始顶点顺序】求叉积（绕序反转不影响它 ——
            // 详见类注释的数学推导，反转后两者恰好自洽）。
            float faceNx = 0, faceNy = 0, faceNz = 0;
            boolean faceDegenerate = true;
            {
                float[] v0 = positions[poly[0][0]], v1 = positions[poly[1][0]], v2 = positions[poly[2][0]];
                float ux = v1[0] - v0[0], uy = v1[1] - v0[1], uz = v1[2] - v0[2];
                float vx = v2[0] - v0[0], vy = v2[1] - v0[1], vz = v2[2] - v0[2];
                faceNx = uy * vz - uz * vy;
                faceNy = uz * vx - ux * vz;
                faceNz = ux * vy - uy * vx;
                float len = (float) Math.sqrt(faceNx * faceNx + faceNy * faceNy + faceNz * faceNz);
                if (len > 1e-6f) {
                    faceNx /= len;
                    faceNy /= len;
                    faceNz /= len;
                    faceDegenerate = false;
                }
            }
            int drawCount = (poly.length == 3) ? 4 : poly.length;
            for (int i = 0; i < drawCount; i++) {
                // 绕序反转 = 发射序整体倒过来（quad 的逆循环仍是同一个 quad）。
                int emitIdx = reverse ? (drawCount - 1 - i) : i;
                int srcIdx = (poly.length == 3 && emitIdx == 3) ? 2 : emitIdx;
                int[] vi = poly[srcIdx];
                float[] pos = positions[vi[0]];
                float[] uv = uvs[vi[2]];
                bakedX[vIdx] = (FLIP_MODEL_X ? -(pos[0] - pivotX) : (pos[0] - pivotX)) / 16.0f;
                bakedY[vIdx] = (FLIP_MODEL_Y ? -(pos[1] - pivotY) : (pos[1] - pivotY)) / 16.0f;
                bakedZ[vIdx] = (pos[2] - pivotZ) / 16.0f;

                float nx, ny, nz;
                float[] packNormal = (vi[1] >= 0 && vi[1] < normals.length && normals[vi[1]].length >= 3)
                        ? normals[vi[1]] : null;
                if (preferPackNormals && packNormal != null) {
                    // 枪包自带的（可平滑）法线。上游 FORCE_FLAT_SHADING 恒 true，
                    // normals 数组解析后从不消费 —— 曲面在光影下呈棱角状高光（审查 A10 第二条）。
                    nx = packNormal[0]; ny = packNormal[1]; nz = packNormal[2];
                } else if (!faceDegenerate) {
                    nx = faceNx; ny = faceNy; nz = faceNz;
                } else if (packNormal != null) {
                    // 三点共线的退化面：叉积为零向量，光影里 normalize() 出 NaN
                    // （表现为随机高光）。退回枪包法线。
                    nx = packNormal[0]; ny = packNormal[1]; nz = packNormal[2];
                } else {
                    // 连枪包法线都没有：写确定方向，绝不写零向量。
                    nx = 0f; ny = 1f; nz = 0f;
                }
                float outNx = FLIP_MODEL_X ? -nx : nx;
                float outNy = FLIP_MODEL_Y ? -ny : ny;
                float outNz = nz;
                if (invertNormals) {
                    outNx = -outNx; outNy = -outNy; outNz = -outNz;
                }
                bakedNX[vIdx] = outNx;
                bakedNY[vIdx] = outNy;
                bakedNZ[vIdx] = outNz;

                bakedU[vIdx] = normalizedUvs ? uv[0] : (uv[0] / texWidth);
                float v = normalizedUvs ? uv[1] : (uv[1] / texHeight);
                bakedV[vIdx] = FLIP_UV_V ? 1.0f - v : v;
                vIdx++;
            }
        }
    }

    /** 配置尚未加载时（理论上不发生：资源加载晚于 mod init）退回默认值。 */
    private static boolean readToggle(net.minecraftforge.common.ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (Throwable t) {
            return fallback;
        }
    }

    public int getVertexCount() {
        return vertexCount;
    }

    /**
     * Collector 路径：热循环把矩阵系数提到局部变量，零分配。
     * 法线写入与 {@code BedrockCubeBox} 一样用裸值，避免二次变换。
     */
    public void compile(PoseStack.Pose pose, VertexConsumer consumer,
                        int light, int overlay, float red, float green, float blue, float alpha) {
        if (vertexCount == 0) {
            return;
        }
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        final float a00 = m.m00(), a01 = m.m01(), a02 = m.m02();
        final float a10 = m.m10(), a11 = m.m11(), a12 = m.m12();
        final float a20 = m.m20(), a21 = m.m21(), a22 = m.m22();
        final float a30 = m.m30(), a31 = m.m31(), a32 = m.m32();
        final float n00 = n.m00(), n01 = n.m01(), n02 = n.m02();
        final float n10 = n.m10(), n11 = n.m11(), n12 = n.m12();
        final float n20 = n.m20(), n21 = n.m21(), n22 = n.m22();
        for (int i = 0; i < vertexCount; i++) {
            float x = bakedX[i], y = bakedY[i], z = bakedZ[i];
            float px = a00 * x + a10 * y + a20 * z + a30;
            float py = a01 * x + a11 * y + a21 * z + a31;
            float pz = a02 * x + a12 * y + a22 * z + a32;
            float nx = bakedNX[i], ny = bakedNY[i], nz = bakedNZ[i];
            float tnx = n00 * nx + n10 * ny + n20 * nz;
            float tny = n01 * nx + n11 * ny + n21 * nz;
            float tnz = n02 * nx + n12 * ny + n22 * nz;
            consumer.addVertex(px, py, pz)
                    .setColor(red, green, blue, alpha)
                    .setUv(bakedU[i], bakedV[i])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(tnx, tny, tnz);
        }
    }

    /**
     * GPU 烘焙：顶点保持<b>骨骼本地</b>坐标写入，变换交给绘制时的
     * DynamicTransforms.ModelViewMat（{@code PolyMeshGpuRenderer} 每骨骼写一次）。
     * light 直接烘进 UV2 —— GPU 路径靠 {@code quantizeLight} + 重烘节流吸收光照变化。
     */
    public void writeRaw(BufferBuilder builder, int light) {
        if (vertexCount == 0) {
            return;
        }
        for (int i = 0; i < vertexCount; i++) {
            builder.addVertex(bakedX[i], bakedY[i], bakedZ[i])
                    .setColor(1f, 1f, 1f, 1f)
                    .setUv(bakedU[i], bakedV[i])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(bakedNX[i], bakedNY[i], bakedNZ[i]);
        }
    }

    private float[][] parse2DArray(JsonArray array, int dim) {
        if (array == null) {
            return new float[0][0];
        }
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
        if (array == null) {
            return new int[0][0][0];
        }
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
