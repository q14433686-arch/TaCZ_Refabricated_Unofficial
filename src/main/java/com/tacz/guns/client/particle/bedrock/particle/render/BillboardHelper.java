// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.render;

import com.tacz.guns.client.particle.bedrock.particle.data.component.ParticleAppearanceBillboard;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;


/**
 * Billboard quad 构建工具。
 */
public final class BillboardHelper {

    // 可复用的临时变量，避免每个粒子渲染时分配新对象
    private static final Matrix4f TEMP_POSE = new Matrix4f();
    private static final Vector4f TEMP_VIEW_POS = new Vector4f();
    private static final Vector3f TEMP_AXIS_X = new Vector3f();
    private static final Vector3f TEMP_AXIS_Y = new Vector3f();
    private static final Vector3f TEMP_DIR = new Vector3f();

    private static final Matrix3f IDENTITY_NORMAL = new Matrix3f();

    private BillboardHelper() {}

    /**
     * 渲染一个局部空间粒子的 billboard quad。
     * <p>
     * 仅处理局部空间粒子（{@code worldSpace=false}）。
     * 世界空间粒子由 the host world renderer 渲染。
     *
     * @param cameraRotation 摄像机旋转矩阵（世界对齐空间 → 视图空间），冻结在提交当帧。
     */
    public static void renderBillboard(ParticleInstance particle, PoseStack poseStack,
                                        VertexConsumer consumer, int light,
                                        ParticleAppearanceBillboard.FaceCameraMode mode,
                                        Matrix4f emitterTransform,
                                        boolean localPos, boolean localRot,
                                        Matrix4f cameraRotation) {
        // 1.21.1 将视图变换拆在 ModelView 和 PoseStack 中；billboard 轴必须在完整视图空间计算。
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewStack());
        Matrix4f pose = BillboardTransforms.viewPose(modelView, poseStack.last().pose());

        // 选择变换矩阵：
        // - localPos 且非 fpDetached：用 pose × emitterTransform（跟随定位器）
        // - fpDetached 或非 localPos：用 pose（在模型空间中自由运动）
        Matrix4f effectivePose;
        if (localPos && !particle.fpDetached) {
            effectivePose = TEMP_POSE.set(pose).mul(emitterTransform);
        } else {
            effectivePose = pose;
        }

        // 用 effectivePose 把粒子坐标变换到视图空间
        TEMP_VIEW_POS.set(particle.x, particle.y, particle.z, 1);
        effectivePose.transform(TEMP_VIEW_POS);

        // 使用发射时的骨骼缩放快照计算粒子大小
        float scale = particle.spawnScale;

        float hw = particle.width * scale;
        float hh = particle.height * scale;

        // 确定速度方向使用的变换矩阵
        float velX = particle.vx, velY = particle.vy, velZ = particle.vz;
        Matrix4f velPose;
        if (localRot && !particle.fpDetached) {
            velPose = effectivePose;
        } else {
            velPose = pose;
        }

        // 计算 billboard 的两个轴向量（视图空间中）
        Vector3f axisX = TEMP_AXIS_X.set(1, 0, 0);
        Vector3f axisY = TEMP_AXIS_Y.set(0, 1, 0);
        applyBillboardAxes(axisX, axisY, mode, velX, velY, velZ, velPose, pose, cameraRotation);

        // 应用粒子自旋旋转
        if (particle.rotation != 0) {
            float rad = (float) Math.toRadians(particle.rotation);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            float ax = axisX.x, ay = axisX.y, az = axisX.z;
            float bx = axisY.x, by = axisY.y, bz = axisY.z;
            axisX.set(ax * cos + bx * sin, ay * cos + by * sin, az * cos + bz * sin);
            axisY.set(-ax * sin + bx * cos, -ay * sin + by * cos, -az * sin + bz * cos);
        }

        // 直接在视图空间中构建 4 个顶点
        float cx = TEMP_VIEW_POS.x, cy = TEMP_VIEW_POS.y, cz = TEMP_VIEW_POS.z;

        // 顶点已在视图空间；提交前撤销 ModelView，避免着色器再次变换 billboard 的轴。
        Matrix4f submissionPose = BillboardTransforms.viewToRender(modelView);
        IDENTITY_NORMAL.identity();

        vertex(consumer, submissionPose, IDENTITY_NORMAL,
                cx - axisX.x * hw - axisY.x * hh, cy - axisX.y * hw - axisY.y * hh, cz - axisX.z * hw - axisY.z * hh,
                particle.u0, particle.v1, particle, light);
        vertex(consumer, submissionPose, IDENTITY_NORMAL,
                cx - axisX.x * hw + axisY.x * hh, cy - axisX.y * hw + axisY.y * hh, cz - axisX.z * hw + axisY.z * hh,
                particle.u0, particle.v0, particle, light);
        vertex(consumer, submissionPose, IDENTITY_NORMAL,
                cx + axisX.x * hw + axisY.x * hh, cy + axisX.y * hw + axisY.y * hh, cz + axisX.z * hw + axisY.z * hh,
                particle.u1, particle.v0, particle, light);
        vertex(consumer, submissionPose, IDENTITY_NORMAL,
                cx + axisX.x * hw - axisY.x * hh, cy + axisX.y * hw - axisY.y * hh, cz + axisX.z * hw - axisY.z * hh,
                particle.u1, particle.v1, particle, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                                float x, float y, float z,
                                float u, float v,
                                ParticleInstance particle, int light) {
        consumer.addVertex(pose, x, y, z)
                .setColor(particle.r, particle.g, particle.b, particle.a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0, 1, 0);
    }

    /**
     * 根据朝向模式计算 billboard 的 X/Y 轴方向（视图空间中的单位向量）。
     * axisX 对应 quad 的宽度方向，axisY 对应高度方向。
     *
     * @param velX/velY/velZ 粒子速度（在粒子自身的坐标空间中）
     * @param velPose 用于将速度变换到视图空间的矩阵
     * @param viewPose 视图矩阵（poseStack 的 pose），用于将世界坐标轴变换到视图空间
     */
    private static void applyBillboardAxes(Vector3f axisX, Vector3f axisY,
                                            ParticleAppearanceBillboard.FaceCameraMode mode,
                                            float velX, float velY, float velZ,
                                            Matrix4f velPose, Matrix4f viewPose,
                                            Matrix4f cameraRotation) {
        switch (mode) {
            case ROTATE_XYZ, LOOKAT_XYZ -> {
                axisX.set(1, 0, 0);
                axisY.set(0, 1, 0);
            }
            case ROTATE_Y, LOOKAT_Y -> {
                // Upright in world space. Use the horizontal camera-right axis (yaw only) and
                // world up, transformed to view coordinates; do not pitch the sprite toward the eye.
                cameraRotation.transformDirection(axisY.set(0, 1, 0)).normalize();
                // Roll tilts the view projection of world up. Derive right without the unstable
                // look-at cross product at a vertical pitch.
                float h = (float) Math.hypot(axisY.x, axisY.y);
                if (h > 1e-5f) axisX.set(axisY.y / h, -axisY.x / h, 0);
                else axisX.set(1, 0, 0);
            }
            case LOOKAT_DIRECTION -> {
                transformDirection(TEMP_DIR, velPose, velX, velY, velZ);
                float velLen = TEMP_DIR.length();
                if (velLen > 0.0001f) {
                    TEMP_DIR.normalize();
                    // forward = (0,0,1), up = forward × dir
                    float upX = -TEMP_DIR.y, upY = TEMP_DIR.x, upZ = 0; // (0,0,1) × dir
                    // 修正：(0,0,1) × (dx,dy,dz) = (-dy, dx, 0)
                    float upLen = (float) Math.sqrt(upX * upX + upY * upY);
                    if (upLen > 0.0001f) {
                        axisX.set(TEMP_DIR);
                        axisY.set(upX / upLen, upY / upLen, upZ / upLen);
                    }
                }
            }
            case DIRECTION_X -> {
                // 面片法线朝世界 X 轴，面片在 YZ 平面上
                // 用视图矩阵将世界 Z 轴变换到视图空间 → axisX
                transformDirection(axisX, viewPose, 0, 0, 1);
                axisX.normalize();
                // 用视图矩阵将世界 Y 轴变换到视图空间 → axisY
                transformDirection(axisY, viewPose, 0, 1, 0);
                axisY.normalize();
            }
            case DIRECTION_Y -> {
                // 面片法线朝世界 Y 轴，面片在 XZ 平面上
                transformDirection(axisX, viewPose, 1, 0, 0);
                axisX.normalize();
                transformDirection(axisY, viewPose, 0, 0, 1);
                axisY.normalize();
            }
            case DIRECTION_Z -> {
                // 面片法线朝世界 Z 轴，面片在 XY 平面上
                transformDirection(axisX, viewPose, 1, 0, 0);
                axisX.normalize();
                transformDirection(axisY, viewPose, 0, 1, 0);
                axisY.normalize();
            }
            case EMITTER_TRANSFORM_XY -> {
                transformDirection(axisX,velPose,1,0,0);transformDirection(axisY,velPose,0,1,0);
                axisX.normalize();axisY.normalize();
            }
            case EMITTER_TRANSFORM_XZ -> {
                transformDirection(axisX,velPose,1,0,0);transformDirection(axisY,velPose,0,0,1);
                axisX.normalize();axisY.normalize();
            }
            case EMITTER_TRANSFORM_YZ -> {
                transformDirection(axisX,velPose,0,1,0);transformDirection(axisY,velPose,0,0,1);
                axisX.normalize();axisY.normalize();
            }
        }
    }

    /**
     * 用矩阵的 3x3 部分（含缩放+旋转）变换一个方向向量，结果写入 dest。
     */
    private static void transformDirection(Vector3f dest, Matrix4f pose, float x, float y, float z) {
        dest.set(
                pose.m00() * x + pose.m10() * y + pose.m20() * z,
                pose.m01() * x + pose.m11() * y + pose.m21() * z,
                pose.m02() * x + pose.m12() * y + pose.m22() * z
        );
    }
}
