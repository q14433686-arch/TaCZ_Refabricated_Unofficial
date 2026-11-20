package com.tacz.guns.client.particle;

import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import org.jetbrains.annotations.Nullable;

/** Opt-in exposure only: never creates a particle definition or changes a legacy quad. */
final class LegacyShotLight {
    static int level(@Nullable MuzzleFlash flash, boolean omitted, String type, boolean explosive,
                     MuzzleAttachment muzzle, boolean first) {
        if (muzzle.silenced()) return 0; // Match the legacy flash gate, not renew's silencer policy.
        float scale = 1;
        if (flash == null) {
            // Stock RPG7/M320 have neither a flash definition nor a locator. Do not light an
            // arbitrary silent pack, an explicit JSON null, or a rejected textureless definition.
            if (!omitted || !"rpg".equals(type) || !explosive) return 0;
        } else {
            if (flash.isRenew() || flash.getTexture() == null || (first ? flash.getFirstPersonFlashId() : flash.getThirdPersonFlashId()) == null) return 0;
            scale = flash.getScale();
            if (!Float.isFinite(scale) || scale <= 0) return 0;
        }
        float base = switch (type) {
            case "pistol", "smg" -> 6;
            case "shotgun", "sniper", "mg" -> 10;
            case "rpg" -> 12;
            default -> 8; // Includes unknown custom types with a valid legacy flash.
        };
        float desired = flash != null && flash.hasFlashLightLevel() ? flash.getFlashLightLevel()
                : base * Math.min(1.25f, (float)Math.sqrt(scale));
        float hider = muzzle.data() == null ? 1 : muzzle.data().getFlashHider();
        return Math.clamp((int)Math.ceil(desired * hider), 0, 15);
    }
    private LegacyShotLight() {}
}
