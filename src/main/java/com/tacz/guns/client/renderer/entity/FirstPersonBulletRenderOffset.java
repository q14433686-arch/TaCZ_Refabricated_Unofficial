package com.tacz.guns.client.renderer.entity;

import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Computes the client-only visual offset that makes a locally fired projectile start at the
 * first-person muzzle while its authoritative entity and hit trajectory continue to start at the eye.
 *
 * <p>The offset is captured once in world space. Reusing that launch-frame vector keeps persistent
 * projectile smoke from bending when the player turns after firing. It then fades to zero over
 * 50 blocks, so model, smoke and tracer converge onto the authoritative trajectory.</p>
 */
public final class FirstPersonBulletRenderOffset {
    private static final double CONVERGENCE_DISTANCE = 50.0;

    private FirstPersonBulletRenderOffset() {
    }

    public static @Nullable Vector3f atRenderPosition(EntityKineticBullet bullet, float partialTicks) {
        Entity owner = bullet.getOwner();
        if (!(owner instanceof LocalPlayer localPlayer) || !isFirstPerson()) {
            return null;
        }
        return compute(bullet, bullet.getPosition(partialTicks), localPlayer.getEyePosition(partialTicks));
    }

    public static @Nullable Vector3f atTickPosition(EntityKineticBullet bullet) {
        Entity owner = bullet.getOwner();
        if (!(owner instanceof LocalPlayer localPlayer) || !isFirstPerson()) {
            return null;
        }
        return compute(bullet, bullet.position(), localPlayer.getEyePosition());
    }

    private static Vector3f compute(EntityKineticBullet bullet, Vec3 bulletPosition, Vec3 eyePosition) {
        Vector3f launchWorldOffset = bullet.getFirstPersonRenderOffset();
        if (launchWorldOffset == null) {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            launchWorldOffset = new Vector3f(GunItemRendererWrapper.muzzleRenderOffset)
                    .rotate(camera.rotation());
            bullet.setFirstPersonRenderOffset(new Vector3f(launchWorldOffset));
            bullet.setCameraXRot(camera.xRot());
            bullet.setCameraYRot(camera.yRot());
        }

        double distanceToEye = bulletPosition.distanceTo(eyePosition);
        float reducer = (float) (Math.max(0.0, CONVERGENCE_DISTANCE - distanceToEye)
                / CONVERGENCE_DISTANCE);
        return new Vector3f(launchWorldOffset).mul(reducer);
    }

    private static boolean isFirstPerson() {
        return Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }
}
