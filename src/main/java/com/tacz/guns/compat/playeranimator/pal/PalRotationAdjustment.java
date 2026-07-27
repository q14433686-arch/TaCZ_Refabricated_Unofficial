package com.tacz.guns.compat.playeranimator.pal;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier;
import com.zigythebird.playeranimcore.math.Vec3f;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Pose;

import java.util.Optional;
import java.util.function.Function;

/** PAL-axis version of TACZ's legacy head/body pitch-yaw adjustment. */
final class PalRotationAdjustment implements Function<String, Optional<AdjustmentModifier.PartModifier>> {
    private final Avatar avatar;

    PalRotationAdjustment(Avatar avatar) {
        this.avatar = avatar;
    }

    @Override
    public Optional<AdjustmentModifier.PartModifier> apply(String partName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (avatar == minecraft.player && minecraft.gui.screen() != null) {
            return Optional.empty();
        }
        if (avatar.getVehicle() != null && "body".equals(partName)) {
            return Optional.empty();
        }

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float bodyYaw = Mth.rotLerp(partialTick, avatar.yBodyRotO, avatar.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, avatar.yHeadRotO, avatar.yHeadRot);
        float pitchDegrees = Mth.wrapDegrees(Mth.lerp(partialTick, avatar.xRotO, avatar.getXRot()));
        float yawDegrees = Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -85.0F, 85.0F);
        float pitch = pitchDegrees * Mth.DEG_TO_RAD;
        float yaw = yawDegrees * Mth.DEG_TO_RAD;

        return switch (partName) {
            case "body" -> {
                // PAL negates body X/Y rotation axes compared with PlayerAnimator. The sign here
                // intentionally differs from the legacy modifier to preserve the visual result.
                if (!avatar.isSwimming() && avatar.getPose() == Pose.SWIMMING) {
                    yield Optional.of(part(0, 0, -yaw));
                }
                yield Optional.of(part(0, yaw, 0));
            }
            case "head" -> Optional.of(part(pitch, 0, 0));
            case "left_arm", "right_arm" -> {
                if (TimelessAPI.getGunDisplay(avatar.getMainHandItem())
                        .map(GunDisplayInstance::is3rdFixedHand).orElse(false)) {
                    yield Optional.empty();
                }
                yield Optional.of(part(pitch, 0, 0));
            }
            default -> Optional.empty();
        };
    }

    private static AdjustmentModifier.PartModifier part(float x, float y, float z) {
        return new AdjustmentModifier.PartModifier(new Vec3f(x, y, z), Vec3f.ZERO);
    }
}
