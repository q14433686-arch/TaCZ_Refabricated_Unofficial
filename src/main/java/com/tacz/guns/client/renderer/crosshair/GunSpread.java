package com.tacz.guns.client.renderer.crosshair;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunFireModeAdjustData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

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
            spread = baseSpread(data.getInaccuracy(), stance, data.getFireModeAdjustData(gun.getFireMode(stack)));
        }
        var heat = data.getHeatData();
        if (heat != null) spread = withHeat(spread, heat, gun.getHeatAmount(stack));
        var camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (!camera.initialized) return 0;
        return pixels(spread, camera.projectionMatrix.m11(), mc.getWindow().getGuiScaledHeight(), scalePercent);
    }

    static float baseSpread(@Nullable Map<InaccuracyType, Float> base, InaccuracyType stance, @Nullable GunFireModeAdjustData adjust) {
        Float value = base == null ? null : base.get(stance);
        if (value == null) return 0;
        float add = adjust == null ? 0 : stance.isAim() ? adjust.getAimInaccuracy() : adjust.getOtherInaccuracy();
        return Math.max(0, value + add);
    }

    static float withHeat(float spread, GunHeatData heat, float amount) {
        if (!(heat.getHeatMax() > 0)) return spread;
        float fraction = Mth.clamp(amount / heat.getHeatMax(), 0f, 1f);
        return spread * Mth.lerp(fraction, heat.getMinInaccuracy(), heat.getMaxInaccuracy());
    }

    static int pixels(float spread, float projectionY, int height, int scalePercent) {
        // Vanilla Projectile's triangular uncertainty is expressed in direction-vector units. Project its
        // lateral scale through the world (not hand or scope) projection; this isn't a predicted hit point.
        double radius = spread * .0172275 * projectionY * height * .5 * scalePercent / 100;
        return Double.isFinite(radius) ? (int) Math.round(Math.clamp(radius, 0, height / 3d)) : 0;
    }
}
