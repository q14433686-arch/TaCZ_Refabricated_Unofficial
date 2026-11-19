package com.tacz.guns.client.renderer.crosshair;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.Map;

/** Read-only HUD cue. Never sample the projectile RNG, run pack shoot scripts or rebuild an attachment cache to draw it. */
final class GunSpread {
    private GunSpread() {}

    static int radius(Minecraft mc, int scalePercent) {
        var player = mc.player;
        if (player == null) return 0;
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun gun)) return 0;
        var data = TimelessAPI.getClientGunIndex(gun.getGunId(stack)).map(index -> index.getGunData()).orElse(null);
        if (data == null) return 0;

        var stance = InaccuracyType.getInaccuracyType(player, () -> Math.sqrt(player.distanceToSqr(player.xOld, player.yOld, player.zOld)));
        var cache = IGunOperator.fromLivingEntity(player).getCacheProperty();
        Float spread = cache == null ? null : cache.getCacheOrDefault(GunProperties.INACCURACY, Map.of()).get(stance);
        if (spread == null) { // A newly drawn gun may not have its modified values yet.
            spread = data.getInaccuracy() == null ? null : data.getInaccuracy().get(stance);
            if (spread == null) return 0;
            var adjust = data.getFireModeAdjustData(gun.getFireMode(stack));
            if (adjust != null) spread += stance.isAim() ? adjust.getAimInaccuracy() : adjust.getOtherInaccuracy();
        }
        var heat = data.getHeatData();
        if (heat != null && heat.getHeatMax() > 0) {
            float fraction = Mth.clamp(gun.getHeatAmount(stack) / heat.getHeatMax(), 0f, 1f);
            spread *= Mth.lerp(fraction, heat.getMinInaccuracy(), heat.getMaxInaccuracy());
        }
        var camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (!camera.initialized) return 0;
        // Vanilla Projectile's triangular uncertainty is expressed in direction-vector units. Project its
        // lateral scale through the world (not hand or scope) projection; this isn't a predicted hit point.
        int height = mc.getWindow().getGuiScaledHeight();
        double radius = spread * .0172275 * camera.projectionMatrix.m11() * height * .5 * scalePercent / 100;
        return Double.isFinite(radius) ? (int) Math.round(Math.clamp(radius, 0, height / 3d)) : 0;
    }
}
