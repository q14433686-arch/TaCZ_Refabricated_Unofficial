package me.xjqsh.lrtactical.client.camera;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only explosion camera shake.
 *
 * <p>The original LRTactical packet sent time/radius/amplitude but its NeoForge camera callback
 * decayed a raw value once per rendered frame, making the result frame-rate dependent and allowing
 * data values such as {@code amplitude=55} to rotate the camera by tens of degrees in one frame.
 * This Fabric implementation interprets time as game ticks, decays only from the client tick, and
 * applies a bounded sinusoidal offset at camera extraction time. No player rotation is persisted;
 * the shake is strictly a visual camera layer placed after TACZ recoil.</p>
 */
@Environment(EnvType.CLIENT)
public final class ScreenShakeState {
    /** Data amplitudes were authored around 50; this maps them to a strong but non-nauseating angle. */
    private static final float AMPLITUDE_TO_DEGREES = 0.04F;
    private static final float MAX_ANGLE_DEGREES = 12.0F;

    private static int remainingTicks;
    private static int totalTicks;
    private static float amplitudeDegrees;
    private static double radius;
    private static Vec3 origin = Vec3.ZERO;
    private static long phaseSeed;

    private ScreenShakeState() {
    }

    /** Starts or strengthens a shake received from the authoritative server explosion. */
    public static void start(double durationTicks, double shakeRadius, double amplitude, Vec3 position) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSpectator() || durationTicks <= 0.0 || shakeRadius <= 0.0 || amplitude <= 0.0) {
            return;
        }

        int ticks = Math.max(1, (int) Math.ceil(durationTicks));
        float degrees = Mth.clamp((float) amplitude * AMPLITUDE_TO_DEGREES, 0.0F, MAX_ANGLE_DEGREES);
        // A later, stronger explosion wins. Equal-strength nearby explosions refresh duration.
        if (remainingTicks <= 0 || degrees >= amplitudeDegrees) {
            remainingTicks = ticks;
            totalTicks = ticks;
            amplitudeDegrees = degrees;
            radius = shakeRadius;
            origin = position;
            phaseSeed = minecraft.level == null ? System.nanoTime() : minecraft.level.getGameTime();
        }
    }

    /** Advances strictly once per client tick, independent of rendering FPS. */
    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.player.isSpectator()) {
            clear();
            return;
        }
        if (remainingTicks > 0) {
            remainingTicks--;
            if (remainingTicks == 0) {
                clear();
            }
        }
    }

    /** Applies the visual-only offset after the normal TACZ camera/recoil handlers. */
    public static void apply(ViewportEvent.ComputeCameraAngles event) {
        if (remainingTicks <= 0 || totalTicks <= 0) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            return;
        }

        double distance = player.position().distanceTo(origin);
        float distanceFactor = Mth.clamp((float) (1.0 - distance / radius), 0.0F, 1.0F);
        if (distanceFactor <= 0.0F) {
            return;
        }

        float age = (totalTicks - remainingTicks) + (float) event.getPartialTick();
        float normalized = Mth.clamp(1.0F - age / totalTicks, 0.0F, 1.0F);
        // Smooth attack/release; no camera jump at either endpoint.
        float envelope = Mth.sin((float) Math.PI * normalized);
        float oscillation = Mth.sin((float) (age * 2.73 + phaseSeed * 0.173));
        float rollOscillation = Mth.cos((float) (age * 3.91 + phaseSeed * 0.117));
        float angle = amplitudeDegrees * distanceFactor * envelope;
        if (player.getVehicle() != null) {
            angle *= 0.1F;
        }

        event.setYaw(event.getYaw() + angle * oscillation);
        event.setPitch(event.getPitch() - angle * oscillation * 0.8F);
        event.setRoll(event.getRoll() + angle * rollOscillation * 0.35F);
    }

    private static void clear() {
        remainingTicks = 0;
        totalTicks = 0;
        amplitudeDegrees = 0.0F;
        radius = 0.0;
        origin = Vec3.ZERO;
    }
}
