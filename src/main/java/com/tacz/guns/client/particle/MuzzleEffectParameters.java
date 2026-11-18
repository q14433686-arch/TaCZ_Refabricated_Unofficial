package com.tacz.guns.client.particle;

import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import com.tacz.guns.resource.pojo.data.SmokeDirectionData;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import net.minecraft.resources.Identifier;
import javax.annotation.Nullable;

/** Immutable per-shot parameters shared by future first/third-person native emitters.
 * This resolves pack semantics only; it neither spawns particles nor changes the legacy renderer. */
public record MuzzleEffectParameters(
        @Nullable Identifier firstFlash, @Nullable Identifier firstSmoke,
        @Nullable Identifier thirdFlash, @Nullable Identifier thirdSmoke,
        float flashScale, int flashLight, float smokeSize, float smokeSpeed, float smokeCount,
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
                scaledDefault(flash.getSmokeScale(), 1, sizeMultiplier),
                flash.getInitialSpeedScale() * speedMultiplier,
                scaledDefault(flash.getSmokeParticleCount(), 15, countMultiplier), direction);
    }

    private static float scaledDefault(float value, float fallback, float multiplier) {
        return multiplier == 1 ? value : (value > 0 ? value : fallback) * multiplier;
    }
}
