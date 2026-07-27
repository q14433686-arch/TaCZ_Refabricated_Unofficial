package cn.sh1rocu.simplebedrockmodel.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Camera;

/**
 * Stub for simplebedrockmodel ViewportEvent (library not yet available for 26.2)
 */
public class ViewportEvent {

    @FunctionalInterface
    public interface CameraCallback {
        void onComputeCameraAngles(ComputeCameraAngles event);
    }

    @FunctionalInterface
    public interface FovCallback {
        void onComputeFov(ComputeFov event);
    }

    public static final Event<CameraCallback> CAMERA = EventFactory.createArrayBacked(CameraCallback.class, callbacks -> event -> {
        for (CameraCallback callback : callbacks) {
            callback.onComputeCameraAngles(event);
        }
    });

    public static final Event<FovCallback> FOV = EventFactory.createArrayBacked(FovCallback.class, callbacks -> event -> {
        for (FovCallback callback : callbacks) {
            callback.onComputeFov(event);
        }
    });

    public static class ComputeCameraAngles {
        private final Camera camera;
        private final double partialTick;
        private float yaw;
        private float pitch;
        private float roll;

        public ComputeCameraAngles(Camera camera, double partialTick) {
            this(camera, partialTick, 0, 0, 0);
        }

        public ComputeCameraAngles(Camera camera, double partialTick, float yaw, float pitch, float roll) {
            this.camera = camera;
            this.partialTick = partialTick;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        public Camera getCamera() {
            return camera;
        }

        public double getPartialTick() {
            return partialTick;
        }

        public float getYaw() {
            return yaw;
        }

        public void setYaw(float yaw) {
            this.yaw = yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public void setPitch(float pitch) {
            this.pitch = pitch;
        }

        public float getRoll() {
            return roll;
        }

        public void setRoll(float roll) {
            this.roll = roll;
        }
    }

    public static class ComputeFov {
        private final Camera camera;
        private final double partialTick;
        private final boolean usedConfiguredFov;
        private double fov;

        public ComputeFov(Camera camera, double partialTick, boolean usedConfiguredFov, double fov) {
            this.camera = camera;
            this.partialTick = partialTick;
            this.usedConfiguredFov = usedConfiguredFov;
            this.fov = fov;
        }

        public Camera getCamera() {
            return camera;
        }

        public double getPartialTick() {
            return partialTick;
        }

        public boolean usedConfiguredFov() {
            return usedConfiguredFov;
        }

        public double getFOV() {
            return fov;
        }

        public void setFOV(double fov) {
            this.fov = fov;
        }
    }
}
