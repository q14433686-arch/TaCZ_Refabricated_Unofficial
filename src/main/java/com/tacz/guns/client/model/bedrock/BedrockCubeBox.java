package com.tacz.guns.client.model.bedrock;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class BedrockCubeBox implements BedrockCube {
    public final float minX;
    public final float minY;
    public final float minZ;
    public final float maxX;
    public final float maxY;
    public final float maxZ;
    private final BedrockPolygon[] polygons;

    public BedrockCubeBox(float texOffX, float texOffY, float x, float y, float z, float width, float height, float depth, float delta, boolean mirror, float texWidth, float texHeight) {
        this.minX = x;
        this.minY = y;
        this.minZ = z;
        this.maxX = x + width;
        this.maxY = y + height;
        this.maxZ = z + depth;
        this.polygons = new BedrockPolygon[6];

        float xEnd = x + width;
        float yEnd = y + height;
        float zEnd = z + depth;
        x = x - delta;
        y = y - delta;
        z = z - delta;
        xEnd = xEnd + delta;
        yEnd = yEnd + delta;
        zEnd = zEnd + delta;

        if (mirror) {
            float tmp = xEnd;
            xEnd = x;
            x = tmp;
        }

        BedrockVertex vertex1 = new BedrockVertex(x, y, z, 0.0F, 0.0F);
        BedrockVertex vertex2 = new BedrockVertex(xEnd, y, z, 0.0F, 8.0F);
        BedrockVertex vertex3 = new BedrockVertex(xEnd, yEnd, z, 8.0F, 8.0F);
        BedrockVertex vertex4 = new BedrockVertex(x, yEnd, z, 8.0F, 0.0F);
        BedrockVertex vertex5 = new BedrockVertex(x, y, zEnd, 0.0F, 0.0F);
        BedrockVertex vertex6 = new BedrockVertex(xEnd, y, zEnd, 0.0F, 8.0F);
        BedrockVertex vertex7 = new BedrockVertex(xEnd, yEnd, zEnd, 8.0F, 8.0F);
        BedrockVertex vertex8 = new BedrockVertex(x, yEnd, zEnd, 8.0F, 0.0F);

        int dx = (int) width;
        int dy = (int) height;
        int dz = (int) depth;

        float p1 = texOffX + dz;
        float p2 = texOffX + dz + dx;
        float p3 = texOffX + dz + dx + dx;
        float p4 = texOffX + dz + dx + dz;
        float p5 = texOffX + dz + dx + dz + dx;
        float p6 = texOffY + dz;
        float p7 = texOffY + dz + dy;
        float p8 = texOffY;
        float p9 = texOffX;

        this.polygons[2] = new BedrockPolygon(new BedrockVertex[]{vertex6, vertex5, vertex1, vertex2}, p1, p8, p2, p6, texWidth, texHeight, mirror, Direction.DOWN);
        this.polygons[3] = new BedrockPolygon(new BedrockVertex[]{vertex3, vertex4, vertex8, vertex7}, p2, p6, p3, p8, texWidth, texHeight, mirror, Direction.UP);
        this.polygons[1] = new BedrockPolygon(new BedrockVertex[]{vertex1, vertex5, vertex8, vertex4}, p9, p6, p1, p7, texWidth, texHeight, mirror, Direction.WEST);
        this.polygons[4] = new BedrockPolygon(new BedrockVertex[]{vertex2, vertex1, vertex4, vertex3}, p1, p6, p2, p7, texWidth, texHeight, mirror, Direction.NORTH);
        this.polygons[0] = new BedrockPolygon(new BedrockVertex[]{vertex6, vertex2, vertex3, vertex7}, p2, p6, p4, p7, texWidth, texHeight, mirror, Direction.EAST);
        this.polygons[5] = new BedrockPolygon(new BedrockVertex[]{vertex5, vertex6, vertex7, vertex8}, p4, p6, p5, p7, texWidth, texHeight, mirror, Direction.SOUTH);
    }

    /**
     * 只读访问六个面。
     *
     * <p>供瞄具掩码（{@code ScopeMaskRenderer}）复用同一份几何：
     * 掩码要把目镜写进离屏纹理，走的是<b>自建顶点缓冲</b>而非 VertexConsumer，
     * 因此不能用 {@link #compile}，但必须使用<b>完全相同</b>的顶点数据，
     * 否则掩码会与画面错位。</p>
     *
     * <p>返回内部数组本身而不是拷贝：这是渲染热路径，每帧都会走；
     * 调用方只读不改。</p>
     */
    @Override
    public BedrockPolygon[] getPolygons() {
        return this.polygons;
    }

    @Override
    public void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, float red, float green, float blue, float alpha) {
        Matrix4f matrix4f = pose.pose();
        Matrix3f matrix3f = pose.normal();

        for (BedrockPolygon polygon : this.polygons) {
            Vector3f vector3f = new Vector3f(polygon.normal);
            vector3f.mul(matrix3f);
            float nx = vector3f.x();
            float ny = vector3f.y();
            float nz = vector3f.z();

            for (BedrockVertex vertex : polygon.vertices) {
                float x = vertex.pos.x() / 16.0F;
                float y = vertex.pos.y() / 16.0F;
                float z = vertex.pos.z() / 16.0F;
                // 26.2 迁移: 旧 API consumer.vertex(x,y,z,r,g,b,a,u,v,overlay,light,nx,ny,nz) 已移除
                // 新 API 使用链式 addVertex + setColor + setUv + setOverlay + setLight + setNormal
                Vector4f vector4f = new Vector4f(x, y, z, 1.0F);
                vector4f.mul(matrix4f);
                // 【法线修复：枪械阴影方向错误 / 视平线高度看枪过暗】
                // 上面的 mul(matrix3f) 已经把法线变换了一次；
                // 26.2 的 setNormal(Pose,FFF) 内部还会再做一次 pose.transformNormal
                // （字节码确认：VertexConsumer#setNormal(Pose,FFF) → pose.transformNormal(x,y,z)）。
                // 两次应用法线矩阵的效果：ZP(180) 翻转被抵消（R·R=I）、动画骨骼旋转被二倍化，
                // 枪的"上表面"在光照计算里实际朝下 —— 水平视角下两个平行光(Light0/1，+y 分量)
                // 都照不到 → 枪身过暗，且阴影朝向随视角漂移。vanilla 26.2
                // ModelPart$Cube#compile 只做一次 transformNormal 后直接写裸值，对齐之。
                consumer.addVertex(vector4f.x(), vector4f.y(), vector4f.z())
                        .setColor(red, green, blue, alpha)
                        .setUv(vertex.u, vertex.v)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(nx, ny, nz);
            }
        }
    }
}
