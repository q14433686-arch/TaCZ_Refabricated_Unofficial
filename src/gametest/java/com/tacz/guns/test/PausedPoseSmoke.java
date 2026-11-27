package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.client.animation.third.InnerThirdPersonManager;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.model.geom.ModelPart;

import java.util.List;
import java.util.Map;

/** Real integrated pause and F5 rendering, plus a direct pose check on the real local gun operator. */
final class PausedPoseSmoke {
    static void run(ClientGameTestContext ctx) {
        var camera = ctx.computeOnClient(mc -> mc.options.getCameraType());
        try {
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.setScreen(() -> new PauseScreen(true));
            ctx.waitFor(mc -> mc.isPaused());
            ctx.waitTicks(5); // let actual paused F5 model extraction run as well
            ctx.runOnClient(mc -> {
                var player = mc.player;
                var display = TimelessAPI.getGunDisplay(player.getMainHandItem()).orElseThrow();
                ModelPart right = part(), left = part(), body = part(), head = part();
                head.xRot = .2f;
                head.yRot = .1f;
                InnerThirdPersonManager.setRotationAnglesHead(player, right, left, body, head, 0);
                if (PlayerAnimatorCompat.isInstalled()) {
                    // PAL updates are deliberately not re-entered from the paused model stage.
                    if (right.xRot != 0 || left.xRot != 0 || body.yRot != 0)
                        throw new AssertionError("Paused PAL model-stage guard was bypassed");
                } else {
                    ModelPart expectedRight = part(), expectedLeft = part(), expectedBody = part();
                    var animation = ThirdPersonManager.getAnimation(display.getThirdPersonAnimation());
                    float aiming = IGunOperator.fromLivingEntity(player).getSynAimingProgress();
                    if (aiming <= 0) animation.animateGunHold(player, expectedRight, expectedLeft, expectedBody, head);
                    else animation.animateGunAim(player, expectedRight, expectedLeft, expectedBody, head, aiming);
                    if (expectedRight.xRot == 0 || !right.storePose().equals(expectedRight.storePose())
                            || !left.storePose().equals(expectedLeft.storePose()))
                        throw new AssertionError("Paused vanilla gun-hold pose was not applied");
                }
                GunMod.LOGGER.info("PORT_PAUSED_POSE: PAL={} paused F5 frames and model-stage pose check passed",
                        PlayerAnimatorCompat.isInstalled());
            });
        } finally {
            ctx.setScreen(() -> null);
            ctx.runOnClient(mc -> mc.options.setCameraType(camera));
        }
        ctx.waitFor(mc -> !mc.isPaused());
    }

    private static ModelPart part() {
        return new ModelPart(List.of(), Map.of());
    }
}
