package com.tacz.guns.client.particle;

import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import com.tacz.guns.resource.pojo.data.SmokeDirectionData;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import net.minecraft.resources.Identifier;
import javax.annotation.Nullable;

/** Per-shot inputs shared by the hand and tracked-world renderers (not animation keyframes). */
public record MuzzleEffectParameters(
        @Nullable Identifier firstFlash, @Nullable Identifier firstSmoke,
        @Nullable Identifier thirdFlash, @Nullable Identifier thirdSmoke,
        float flashScale, int flashLight, boolean smokeEnabled, float smokeSize, float smokeSpeed, float smokeCount,
        @Nullable SmokeDirectionData.Config smokeDirection) {

    @Nullable
    public static MuzzleEffectParameters resolve(MuzzleFlash flash, @Nullable AttachmentData muzzle, boolean silenced) {
        if (flash == null || !flash.isRenew()) return null;
        float flashMultiplier = muzzle == null ? 1 : muzzle.getFlashHider();
        float flashScale = flash.getScale() * flashMultiplier;
        if (silenced && (muzzle == null || !muzzle.hasFlashHider())) flashScale = 0;
        float sizeMultiplier = muzzle == null ? 1 : muzzle.getSmokeHider();
        float countMultiplier = muzzle == null ? 1 : muzzle.getSmokeCountScale();
        float speedMultiplier = muzzle == null ? 1 : muzzle.getSmokeSpeedScale();
        var direction = muzzle == null ? null : muzzle.getSmokeDirectionConfig();
        if (direction == null) direction = flash.getSmokeDirectionConfig();
        // Local muzzle matrices are flipped by 180 degrees about Z. Convert author-space
        // right/up into that space once, not independently in each emitter.
        if (direction != null) {
            float[][] original = direction.directions();
            float[][] corrected = new float[original.length][];
            for (int i = 0; i < original.length; i++)
                corrected[i] = new float[]{-original[i][0], -original[i][1], original[i][2]};
            direction = new SmokeDirectionData.Config(corrected, direction.spread(), direction.angleMode());
        }
        return new MuzzleEffectParameters(flash.getFirstPersonFlashId(), flash.getFirstPersonSmokeId(),
                flash.getThirdPersonFlashId(), flash.getThirdPersonSmokeId(), flashScale,
                Math.max(0, Math.min(15, (int) Math.ceil(flash.getFlashLightLevel() * flashMultiplier))),
                sizeMultiplier > 0 && countMultiplier > 0 && speedMultiplier > 0,
                scaledDefault(flash.getSmokeScale(), 1, sizeMultiplier),
                flash.getInitialSpeedScale() * speedMultiplier,
                scaledDefault(flash.getSmokeParticleCount(), 15, countMultiplier), direction);
    }

    public boolean hasFlash() {
        return Float.isFinite(flashScale) && flashScale > 0;
    }

    void bindFlash(ParticleMolangEnvironment env) {
        env.setVariable("muzzle_scale", flashScale);
        env.setVariable("muzzle_speed", smokeSpeed);
    }

    void bindSmoke(ParticleMolangEnvironment env) {
        env.setVariable("smoke_size", smokeSize);
        env.setVariable("smoke_vel", smokeSpeed);
        env.setVariable("smoke_count", smokeCount);
        int n = smokeDirection == null ? 0 : smokeDirection.directions().length;
        env.setVariable("smoke_dir_n", n);
        env.setVariable("smoke_spread", n == 0 ? 0 : smokeDirection.spread());
        env.setVariable("smoke_angle_mode", n > 0 && smokeDirection.angleMode() ? 1 : 0);
        // Unused slots read as zero: each emitter owns a fresh environment. For a cone
        // provide an orthonormal basis, including directions parallel to the local Z axis.
        for (int i = 0; i < n; i++) {
            float[] d = smokeDirection.directions()[i];
            float x = d[0], y = d[1], z = d[2];
            float lateral = (float)Math.hypot(x, y);
            float ux = lateral > 1e-6f ? -y / lateral : 1, uy = lateral > 1e-6f ? x / lateral : 0;
            vector(env, "smoke_dir" + i, x, y, z);
            vector(env, "smoke_u" + i, ux, uy, 0);
            vector(env, "smoke_v" + i, -z * uy, z * ux, x * uy - y * ux);
        }
    }

    private static void vector(ParticleMolangEnvironment env, String name, float x, float y, float z) {
        env.setVariable(name + "_x", x);
        env.setVariable(name + "_y", y);
        env.setVariable(name + "_z", z);
    }

    private static float scaledDefault(float value, float fallback, float multiplier) {
        return multiplier == 1 ? value : (value > 0 ? value : fallback) * multiplier;
    }
}
