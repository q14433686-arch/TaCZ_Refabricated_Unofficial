package com.tacz.guns.industry.reference;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.resource.ICommonResourceProvider;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Conservative reload-time audit of third-party table recipe identities.
 *
 * <p>This is intentionally an audit, not a filename-based repair engine. It
 * identifies the exact recipes safe for a future measured/generic industrial
 * line, reports broken upstream identities, and accepts only an explicit
 * {@link IndustryIdentityAlias} as a repair. Unknown feeds remain legacy until
 * a reference profile declares their real device.</p>
 */
public final class IndustryRuntimeAudit {
    private static final int MAX_LOGGED_FINDINGS = 24;
    private static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 0, 0, 0, List.of());

    private IndustryRuntimeAudit() {
    }

    public static Snapshot audit(Map<Identifier, JsonElement> rawRecipes, ICommonResourceProvider assets,
                                 IndustryIdentityAliasManager aliases,
                                 Map<Identifier, IndustryReferenceProfile> profiles) {
        if (!IndustryProfileManager.isCreateFlyProfileActive() || rawRecipes == null || rawRecipes.isEmpty()) {
            return EMPTY;
        }
        int guns = 0;
        int ammo = 0;
        int attachments = 0;
        int direct = 0;
        int alias = 0;
        int unresolved = 0;
        int profiledGuns = 0;
        int surveyedGunCandidates = 0;
        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<Identifier, JsonElement> entry : rawRecipes.entrySet()) {
            if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject recipe = entry.getValue().getAsJsonObject();
            JsonObject result = object(recipe, "result");
            if (result == null) {
                continue;
            }
            String kind = string(result, "type");
            if (!"gun".equals(kind) && !"ammo".equals(kind) && !"attachment".equals(kind)) {
                continue;
            }
            if ("gun".equals(kind)) {
                guns++;
            } else if ("ammo".equals(kind)) {
                ammo++;
            } else {
                attachments++;
            }

            Identifier declared = Identifier.tryParse(string(result, "id"));
            IndustryIdentityAlias configuredAlias = aliases == null ? null : aliases.getAlias(entry.getKey());
            Identifier resolved = configuredAlias == null ? declared : configuredAlias.getTarget();
            Resolution resolution;
            String reason = "";
            if (resolved != null && resolves(kind, resolved, assets)) {
                resolution = configuredAlias == null ? Resolution.DIRECT : Resolution.ALIAS;
                if (resolution == Resolution.DIRECT) {
                    direct++;
                } else {
                    alias++;
                }
                if ("gun".equals(kind)) {
                    IndustryReferenceProfile profile = profiles == null ? null : profiles.get(resolved);
                    if (profile != null && "automatic_candidate".equals(profile.getConfidence())) {
                        surveyedGunCandidates++;
                    } else if (profile != null) {
                        profiledGuns++;
                    }
                }
            } else {
                resolution = Resolution.UNRESOLVED;
                unresolved++;
                if (declared == null) {
                    reason = "result.id is absent or invalid";
                } else if (configuredAlias != null) {
                    reason = "configured alias target is no longer loaded";
                } else {
                    reason = "no loaded " + kind + " identity and no validated alias";
                }
                findings.add(new Finding(entry.getKey(), kind, declared, null, resolution, reason));
                continue;
            }
            if (resolution == Resolution.ALIAS) {
                findings.add(new Finding(entry.getKey(), kind, declared, resolved, resolution,
                        configuredAlias.getReason()));
            }
        }

        Snapshot snapshot = new Snapshot(guns, ammo, attachments, direct, alias, unresolved,
                profiledGuns, surveyedGunCandidates, List.copyOf(findings));
        GunMod.LOGGER.info(
                "TACZ industry runtime audit: {} gun / {} ammo / {} attachment table result(s); "
                        + "{} direct, {} explicit-alias, {} unresolved; {} gun profile(s), {} surveyed gun candidate(s).",
                guns, ammo, attachments, direct, alias, unresolved, profiledGuns, surveyedGunCandidates
        );
        int logged = 0;
        for (Finding finding : findings) {
            if (finding.resolution() != Resolution.UNRESOLVED || logged++ >= MAX_LOGGED_FINDINGS) {
                continue;
            }
            GunMod.LOGGER.warn("TACZ industry audit unresolved {} recipe {} -> {}: {}",
                    finding.kind(), finding.recipeId(), finding.declaredId(), finding.reason());
        }
        if (unresolved > MAX_LOGGED_FINDINGS) {
            GunMod.LOGGER.warn("TACZ industry audit omitted {} additional unresolved table-result identity finding(s).",
                    unresolved - MAX_LOGGED_FINDINGS);
        }
        return snapshot;
    }

    private static boolean resolves(String kind, Identifier id, ICommonResourceProvider assets) {
        return switch (kind) {
            case "gun" -> assets.getGunIndex(id) != null;
            case "ammo" -> assets.getAmmoIndex(id) != null;
            case "attachment" -> assets.getAttachmentIndex(id) != null;
            default -> false;
        };
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    public enum Resolution {
        DIRECT,
        ALIAS,
        UNRESOLVED
    }

    public record Finding(Identifier recipeId, String kind, Identifier declaredId, Identifier resolvedId,
                          Resolution resolution, String reason) {
    }

    public record Snapshot(int guns, int ammo, int attachments, int direct, int aliases, int unresolved,
                           int profiledGuns, int surveyedGunCandidates, List<Finding> findings) {
    }
}
