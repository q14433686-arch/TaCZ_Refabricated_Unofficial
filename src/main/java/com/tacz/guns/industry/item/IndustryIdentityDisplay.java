package com.tacz.guns.industry.item;

import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Player-facing names for otherwise stable industrial NBT identities.
 *
 * <p>Strings such as {@code surveyed/ww/77a}, {@code ww:77a}, and
 * {@code suffuse:trapper50cal} are deliberately retained in item data: they
 * are the exact server-side identity used by recipes, standards and save
 * migration. They are not suitable ordinary tooltip text, however. This
 * resolver reads the already-synchronised GunIndex / AmmoIndex and renders the
 * source pack's own display-name key instead. A missing index fails honestly
 * with a localised "unresolved" label; raw identity remains available only in
 * an advanced/debug tooltip.</p>
 */
public final class IndustryIdentityDisplay {
    private static final Set<String> ACTION_PROFILES = Set.of(
            "anti_material_bolt", "belt_fed", "blowback_smg", "bolt_action", "break_action",
            "bullpup_rifle", "gas_operated_shotgun", "gas_rifle", "hybrid_shotgun", "launcher",
            "lever_action", "locked_breech_pistol", "pump_action", "revolver",
            "roller_delayed_rifle", "rotary", "surveyed"
    );

    private IndustryIdentityDisplay() {
    }

    /** Resolve an exact GunId to the gun pack's own player-facing name. */
    @Nullable
    public static Component findGunName(String rawGunId) {
        if (rawGunId == null || rawGunId.isBlank()) {
            return null;
        }
        Identifier gunId = Identifier.tryParse(rawGunId);
        if (gunId == null) {
            return null;
        }
        var index = CommonAssetsManager.get().getGunIndex(gunId);
        String name = index == null || index.getPojo() == null ? "" : index.getPojo().getName();
        return name == null || name.isBlank() ? null : Component.translatable(name);
    }

    /** Resolve an exact AmmoId to the ammunition pack's own player-facing name. */
    @Nullable
    public static Component findAmmoName(String rawAmmoId) {
        if (rawAmmoId == null || rawAmmoId.isBlank()) {
            return null;
        }
        Identifier ammoId = Identifier.tryParse(rawAmmoId);
        if (ammoId == null) {
            return null;
        }
        var index = CommonAssetsManager.get().getAmmoIndex(ammoId);
        String name = index == null || index.getPojo() == null ? "" : index.getPojo().getName();
        return name == null || name.isBlank() ? null : Component.translatable(name);
    }

    /**
     * Resolve the human name of a surveyed gun or ammunition target. Gun takes
     * precedence because platform dossiers/structural kits are fundamentally
     * tied to a receiver, even if their survey metadata also records its ammo.
     */
    public static Component surveyedTarget(String surveyedGunId, String surveyedAmmoId, String surveyedAmmoNameKey) {
        Component gun = findGunName(surveyedGunId);
        if (gun != null) {
            return gun;
        }
        Component ammo = findAmmoName(surveyedAmmoId);
        if (ammo != null) {
            return ammo;
        }
        if (surveyedAmmoNameKey != null && !surveyedAmmoNameKey.isBlank()) {
            return Component.translatable(surveyedAmmoNameKey);
        }
        return Component.translatable("tooltip.tacz.industry.surveyed_target.unresolved");
    }

    /**
     * Resolve a physical cartridge calibre through its exact loose AmmoId first,
     * then its survey provenance, then an explicit loaded cartridge standard.
     */
    public static Component cartridgeCaliber(String cartridgeCaliber, String cartridgeAmmoId,
                                             String surveyedAmmoId, String surveyedAmmoNameKey) {
        Component direct = findAmmoName(cartridgeAmmoId);
        if (direct != null) {
            return direct;
        }
        Component surveyed = findAmmoName(surveyedAmmoId);
        if (surveyed != null) {
            return surveyed;
        }
        if (surveyedAmmoNameKey != null && !surveyedAmmoNameKey.isBlank()) {
            return Component.translatable(surveyedAmmoNameKey);
        }
        if (cartridgeCaliber != null && !cartridgeCaliber.isBlank()) {
            for (var entry : CommonAssetsManager.get().getAllCartridgeStandards()) {
                var standard = entry.getValue();
                if (standard == null || !cartridgeCaliber.equals(standard.getCartridgeCaliber())) {
                    continue;
                }
                Identifier canonicalAmmo = standard.getCanonicalAmmo();
                Component standardAmmo = canonicalAmmo == null ? null : findAmmoName(canonicalAmmo.toString());
                if (standardAmmo != null) {
                    return standardAmmo;
                }
            }
        }
        return Component.translatable("tooltip.tacz.industry.caliber.unresolved");
    }

    /** Human readable projectile construction type; unknown values stay non-technical in normal tooltips. */
    public static Component projectileType(String type) {
        return switch (type == null ? "" : type) {
            case "fmj" -> Component.translatable("tooltip.tacz.industry.projectile_type.fmj");
            case "ap" -> Component.translatable("tooltip.tacz.industry.projectile_type.ap");
            case "hp" -> Component.translatable("tooltip.tacz.industry.projectile_type.hp");
            case "slug" -> Component.translatable("tooltip.tacz.industry.projectile_type.slug");
            case "shot" -> Component.translatable("tooltip.tacz.industry.projectile_type.shot");
            case "he" -> Component.translatable("tooltip.tacz.industry.projectile_type.he");
            case "heat" -> Component.translatable("tooltip.tacz.industry.projectile_type.heat");
            case "surveyed" -> Component.translatable("tooltip.tacz.industry.projectile_type.surveyed");
            default -> Component.translatable("tooltip.tacz.industry.projectile_type.unresolved");
        };
    }

    /**
     * Show an intelligible forming target without leaking a carrier slug or a
     * generated survey path into the ordinary tooltip. The exact raw target is
     * retained in the advanced identity section for authors/debugging.
     */
    public static Component dieTarget(String target) {
        String value = target == null ? "" : target;
        if (ACTION_PROFILES.contains(value)) {
            return Component.translatable("tooltip.tacz.industry.action_profile." + value);
        }
        return switch (value) {
            case "case" -> Component.translatable("tooltip.tacz.industry.die_target.case");
            case "projectile" -> Component.translatable("tooltip.tacz.industry.die_target.projectile");
            case "carrier" -> Component.translatable("tooltip.tacz.industry.die_target.carrier");
            case "receiver" -> Component.translatable("tooltip.tacz.industry.die_target.receiver");
            case "bolt" -> Component.translatable("tooltip.tacz.industry.die_target.bolt");
            case "barrel" -> Component.translatable("tooltip.tacz.industry.die_target.barrel");
            case "frame" -> Component.translatable("tooltip.tacz.industry.die_target.frame");
            case "slide" -> Component.translatable("tooltip.tacz.industry.die_target.slide");
            case "trigger" -> Component.translatable("tooltip.tacz.industry.die_target.trigger");
            case "recoil" -> Component.translatable("tooltip.tacz.industry.die_target.recoil");
            case "surveyed", "surveyed_cartridge" -> Component.translatable("tooltip.tacz.industry.die_target.surveyed");
            default -> Component.translatable("tooltip.tacz.industry.die_target.specialized");
        };
    }
}
