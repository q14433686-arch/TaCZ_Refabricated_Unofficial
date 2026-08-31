package cn.sh1rocu.tacz.compat.meshloader.core;

import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.common.ForgeConfigSpec;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 一根骨骼上的一份 poly_mesh：解析为骨骼本地坐标数组（SoA 布局）。
 *
 * <p>顶点写入与移植版 {@code BedrockCubeBox#compile} 对齐：位置/法线手动乘矩阵后
 * 以裸坐标写入，{@code setNormal} <b>不再</b>传 Pose（26.2 的
 * {@code setNormal(Pose,FFF)} 会再乘一次法线矩阵）。</p>
 *
 * <p>三角形按上游 TML 语义展开为「第 4 顶点重复第 3 顶点」的退化 quad
 * （QUADS 拓扑下的标准三角形表达）。纯三角网格因此多付 ~33% 顶点；
 * 导入期三角形配对是后续性能方向（docs/TML_PERF_DIRECTIONS_2026_08_29.md 方向 2），
 * 本轮保持与上游一致的展开方式，先保正确性。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
@Environment(EnvType.CLIENT)
public class PolyMesh {

    private static final boolean FLIP_MODEL_X       = false;
    private static final boolean FLIP_MODEL_Y       = true;
    private static final boolean FLIP_UV_V          = true;

    /**
     * 位置翻转对法线的效果 = 逐分量乘上翻转符号（{@code D * n}，D=diag(sx,sy,sz)）。
     * 这是「镜像后该面的朝外法线」，与 {@code BedrockPolygon} 对 mirror 的处理同构
     * （那边是 {@code normal.mul(-1,1,1)} + 反转顶点顺序）。
     */
    private static final float NORMAL_SX = FLIP_MODEL_X ? -1f : 1f;
    private static final float NORMAL_SY = FLIP_MODEL_Y ? -1f : 1f;
    private static final float NORMAL_SZ = 1f;
    /** 翻转了奇数个轴 = 镜像 ⇒ 绕序与法线的一致性需要开关介入（见下面三个配置项）。 */
    private static final boolean MIRROR = (NORMAL_SX * NORMAL_SY * NORMAL_SZ) < 0f;

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
            if (poly.length >= 3) {
                totalVerts += (poly.length == 3) ? 4 : poly.length;
            }
        }
        this.vertexCount = totalVerts;
        this.bakedX  = new float[totalVerts]; this.bakedY  = new float[totalVerts]; this.bakedZ  = new float[totalVerts];
        this.bakedNX = new float[totalVerts]; this.bakedNY = new float[totalVerts]; this.bakedNZ = new float[totalVerts];
        this.bakedU  = new float[totalVerts]; this.bakedV  = new float[totalVerts];

        // 配置要到客户端 init 才被赋值；解析线程万一线程跑在它前面（或有人在服务端/数据生成里
        // 直接调 parseMeshMapFromJson），这些静态字段还是 null —— 宁可按声明的默认值烘，也别抛
        // NPE 把整个 mesh 丢掉（loader 那边 catch 掉之后的症状是「枪没有 poly 部件」+ 一行 WARN）。
        // 三个开关都在 PolyMesh 构造期读一次 ⇒ 改了要重载资源（F3+T）才生效，见 `docs/MESH_LOADER.md` §5.7。
        // 枪包给了 normals 数组才有得选，没有就只能平面着色（parse2DArray 对缺失返回空数组）；
        // 并且要求每个面引用的法线索引都在范围内 —— 少一个元素就整体退回平面着色，而不是在
        // 烘焙中途抛 AIOOBE（这个构造函数在模型解析线程上跑，抛出 = 整个 mesh 丢失）。
        boolean preferPackNormals = flag(MeshyConfig.POLY_PREFER_PACK_NORMALS, false)
                && normals != null && normals.length > 0;
        if (preferPackNormals) {
            for (int[][] poly : polys) {
                for (int[] vi : poly) {
                    if (vi[1] < 0 || vi[1] >= normals.length) {
                        preferPackNormals = false;
                        break;
                    }
                }
                if (!preferPackNormals) {
                    break;
                }
            }
        }
        // 整个模型用同一组开关，逐项读配置没有意义（这个循环是 O(面数)）。
        boolean reverseWinding = MIRROR && flag(MeshyConfig.POLY_MIRROR_REVERSE_WINDING, true);
        float nSign = flag(MeshyConfig.POLY_INVERT_NORMALS, false) ? -1f : 1f;
        // 面里最多几个顶点：不逐面 new（这个循环在每个模型的每个 mesh 上跑）。
        int maxFaceVerts = 4;
        for (int[][] poly : polys) {
            if (poly.length > maxFaceVerts) {
                maxFaceVerts = poly.length;
            }
        }
        int[] faceOrder = new int[maxFaceVerts];

        int vIdx = 0;
        for (int[][] poly : polys) {
            if (poly.length < 3) {
                continue;
            }
            // 发射顺序。三角形要按 QUADS 拓扑展开成「第 4 顶点重复第 3 顶点」；
            // 需要反绕序时（镜像 + MeshPolyMirrorReverseWinding）整体倒过来即可 ——
            // 三角形展开后 [0,1,2,2] 反过来是 [2,2,1,0]，退化三角形在前，
            // 有效三角形 (2,1,0) 的朝向正好相反 ✓。
            int drawCount = (poly.length == 3) ? 4 : poly.length;
            int[] order = faceOrder;
            if (poly.length == 3) {
                order[0] = 0; order[1] = 1; order[2] = 2; order[3] = 2;
            } else {
                for (int i = 0; i < drawCount; i++) {
                    order[i] = i;
                }
            }
            if (reverseWinding) {
                for (int a = 0, b = drawCount - 1; a < b; a++, b--) {
                    int t = order[a]; order[a] = order[b]; order[b] = t;
                }
            }

            // 平面法线：始终从**原始（未翻转）顺序**的前三个顶点求叉积，再乘翻转符号
            // （见 NORMAL_SX 的注释）。发射顺序反过来只影响「哪一面算正面」，不影响朝外方向，
            // 所以这里刻意**不**跟着 faceOrder 走 —— 跟着走等于把 D 乘两遍，法线会翻回错误的一侧。
            float faceNx = 0, faceNy = 0, faceNz = 0;
            boolean usePackNormals = preferPackNormals;
            if (!usePackNormals) {
                float[] v0 = positions[poly[0][0]];
                float[] v1 = positions[poly[1][0]];
                float[] v2 = positions[poly[2][0]];
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
                } else {
                    // 退化面（三点共线）：叉积给不出方向。留零向量在光影里 normalize() 会出 NaN
                    // （表现是这一面带随机高光），所以退回枪包的逐顶点法线，实在没有就写一个
                    // 确定方向 —— 面积为 0 的面画不出像素，方向不影响外观。
                    usePackNormals = normals != null && normals.length > 0;
                    if (!usePackNormals) {
                        faceNx = 0f; faceNy = NORMAL_SY; faceNz = 0f;
                    }
                }
            }
            for (int i = 0; i < drawCount; i++) {
                int[] vi = poly[order[i]];
                float[] pos = positions[vi[0]];
                float[] uv = uvs[vi[2]];
                bakedX[vIdx] = (FLIP_MODEL_X ? -(pos[0] - pivotX) : (pos[0] - pivotX)) / 16.0f;
                bakedY[vIdx] = (FLIP_MODEL_Y ? -(pos[1] - pivotY) : (pos[1] - pivotY)) / 16.0f;
                bakedZ[vIdx] = (pos[2] - pivotZ) / 16.0f;
                if (usePackNormals) {
                    float[] n = normals[vi[1]];
                    bakedNX[vIdx] = nSign * NORMAL_SX * n[0];
                    bakedNY[vIdx] = nSign * NORMAL_SY * n[1];
                    bakedNZ[vIdx] = nSign * NORMAL_SZ * n[2];
                } else {
                    bakedNX[vIdx] = nSign * NORMAL_SX * faceNx;
                    bakedNY[vIdx] = nSign * NORMAL_SY * faceNy;
                    bakedNZ[vIdx] = nSign * NORMAL_SZ * faceNz;
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

    /** 配置项可能还没被赋值（见构造函数里那段注释）—— 取不到就按声明的默认值。 */
    private static boolean flag(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        return value == null ? fallback : value.get();
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
