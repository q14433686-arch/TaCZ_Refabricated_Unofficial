package com.tacz.guns.client.model.listener.camera;

import com.tacz.guns.api.client.animation.AnimationListener;
import com.tacz.guns.api.client.animation.ObjectAnimationChannel;
import com.tacz.guns.util.math.MathUtil;
import org.joml.Quaternionf;

public class CameraRotateListener implements AnimationListener {
    private final CameraAnimationObject camera;

    public CameraRotateListener(CameraAnimationObject camera) {
        this.camera = camera;
    }

    @Override
    public void update(float[] values, boolean blend) {
        if (values.length == 4) {
            values = MathUtil.toEulerAngles(values);
        }
        float xRot = values[0];
        float yRot = values[1];
        float zRot = -values[2];
        // 在关键帧中储存的旋转数值并不是摄像头的旋转数值，是世界箱体的旋转数值
        // 最终需要存入rotationQuaternion的是摄像机的旋转（即世界箱体旋转的反相）
        if (blend) {
            float[] q = MathUtil.toQuaternion(xRot, yRot, zRot);
            Quaternionf quaternion = MathUtil.toQuaternion(q);
            MathUtil.blendQuaternion(camera.rotationQuaternion, quaternion);
        } else {
            MathUtil.toQuaternion(xRot, yRot, zRot, camera.rotationQuaternion);
        }
        // 【RecoilDebug 探针】第 27 轮定位：rotationQuaternion 在 vanilla 管线被观测到
        // 含 |sin(玩家yaw)| 比例的 x 分量，而 AK shoot 动画的 camera 轨道是纯 z 欧拉键
        // （x,y 恒 0），即偏离必然来自写入侧——本探针记录每次写入的**输入数组与 blend 标志**，
        // 并附 listener/容器对象身份码：若同帧出现多个 self/cam 身份，即为并发动画通道叠加。
        if (com.tacz.guns.config.client.RenderConfig.RECOIL_DEBUG.get()) {
            debugLogWrite(blend, values);
        }
    }

    private void debugLogWrite(boolean blend, float[] values) {
        try {
            net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            float fy = player == null ? Float.NaN : net.minecraft.util.Mth.wrapDegrees(player.getYRot());
            float fx = player == null ? Float.NaN : player.getXRot();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(String.format(java.util.Locale.ROOT, "%+.5f", values[i]));
            }
            Quaternionf w = camera.rotationQuaternion;
            com.tacz.guns.GunMod.LOGGER.info(
                    "[TACZ RecoilDebug] camWrite self={} cam={} blend={} inLen={} in=[{}] out=({},{},{},{}) facing=({},{}) shader={} irisHand={}",
                    Integer.toHexString(System.identityHashCode(this)),
                    Integer.toHexString(System.identityHashCode(camera)),
                    blend, values.length, sb,
                    String.format(java.util.Locale.ROOT, "%+.5f", w.x()),
                    String.format(java.util.Locale.ROOT, "%+.5f", w.y()),
                    String.format(java.util.Locale.ROOT, "%+.5f", w.z()),
                    String.format(java.util.Locale.ROOT, "%+.5f", w.w()),
                    String.format(java.util.Locale.ROOT, "%+.3f", fx),
                    String.format(java.util.Locale.ROOT, "%+.3f", fy),
                    com.tacz.guns.compat.iris.IrisCompat.isUsingRenderPack(),
                    com.tacz.guns.compat.iris.IrisCompat.isHandRendererActive());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public float[] initialValue() {
        float[] ret = MathUtil.toQuaternion(camera.cameraRenderer.getRotateAngleX(), camera.cameraRenderer.getRotateAngleY(), camera.cameraRenderer.getRotateAngleZ());
        // 【RecoilDebug 探针】initialValue 会把相机节点**当前欧拉角**烧录进后续动画的相对量，
        // 若捕获瞬间节点姿态已被前一状态污染，污染将被写进新动画的逆初值。
        if (com.tacz.guns.config.client.RenderConfig.RECOIL_DEBUG.get()) {
            debugLogInitial(ret);
        }
        return ret;
    }

    private void debugLogInitial(float[] ret) {
        try {
            com.tacz.guns.GunMod.LOGGER.info(
                    "[TACZ RecoilDebug] camInit self={} cam={} nodeEuler=({},{},{}) q0=({},{},{},{})",
                    Integer.toHexString(System.identityHashCode(this)),
                    Integer.toHexString(System.identityHashCode(camera)),
                    String.format(java.util.Locale.ROOT, "%+.5f", camera.cameraRenderer.getRotateAngleX()),
                    String.format(java.util.Locale.ROOT, "%+.5f", camera.cameraRenderer.getRotateAngleY()),
                    String.format(java.util.Locale.ROOT, "%+.5f", camera.cameraRenderer.getRotateAngleZ()),
                    String.format(java.util.Locale.ROOT, "%+.5f", ret[0]),
                    String.format(java.util.Locale.ROOT, "%+.5f", ret[1]),
                    String.format(java.util.Locale.ROOT, "%+.5f", ret[2]),
                    String.format(java.util.Locale.ROOT, "%+.5f", ret[3]));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public ObjectAnimationChannel.ChannelType getType() {
        return ObjectAnimationChannel.ChannelType.ROTATION;
    }
}
