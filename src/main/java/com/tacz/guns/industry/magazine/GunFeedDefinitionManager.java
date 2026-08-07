package com.tacz.guns.industry.magazine;

import com.google.gson.JsonElement;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.manager.CommonDataManager;
import com.tacz.guns.resource.network.DataType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validates opt-in physical-feed declarations only after the real GunIndex has
 * loaded.
 *
 * <p>There are two explicit sources: an optional pack-author declaration in
 * {@code GunData.tacz_industry.feed}, and the established independent sidecar
 * at {@code data/<namespace>/industry/gun_feed/<gun>.json}. A sidecar owns its
 * target when present and deliberately takes precedence, even if stale or
 * invalid, so an administrator can fail closed rather than silently reactivate
 * an older inline declaration after a pack update.</p>
 */
public final class GunFeedDefinitionManager extends CommonDataManager<GunFeedDefinition> {
    private Map<Identifier, GunFeedDefinition> validDefinitions = Map.of();
    private Audit audit = new Audit(0, 0, 0);

    public GunFeedDefinitionManager() {
        super(DataType.GUN_FEED, GunFeedDefinition.class, CommonAssetsManager.GSON,
                "industry/gun_feed", "GunFeedLoader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        super.apply(objects, resourceManager, profiler);
        Map<Identifier, GunFeedDefinition> resolved = new LinkedHashMap<>();
        Map<Identifier, String> synchronizedDefinitions = new LinkedHashMap<>();
        int dormant = 0;
        int rejected = 0;
        int sidecarAccepted = 0;
        int inlineAccepted = 0;

        // Pack authors can opt in beside their actual GunData. It is never an
        // inference: unknown packs without tacz_industry remain legacy. A raw
        // sidecar resource wins ownership even if Gson rejected that sidecar,
        // avoiding a hidden fallback to older inline semantics.
        for (Map.Entry<Identifier, com.tacz.guns.resource.index.CommonGunIndex> entry
                : CommonAssetsManager.get().getAllGuns()) {
            Identifier gunId = entry.getKey();
            if (objects.containsKey(gunId)) {
                continue;
            }
            var index = entry.getValue();
            if (index == null || index.getGunData() == null) {
                continue;
            }
            IndustryGunDataExtension extension = index.getGunData().getIndustryExtension();
            if (extension == null) {
                continue;
            }
            if (extension.getSchemaVersion() != IndustryGunDataExtension.SCHEMA_VERSION) {
                rejected++;
                GunMod.LOGGER.error("Ignoring inline tacz_industry declaration {}: unsupported schema_version {}.",
                        gunId, extension.getSchemaVersion());
                continue;
            }
            GunFeedDefinition definition = extension.getFeed();
            GunFeedDefinition.Validation validation = definition == null
                    ? GunFeedDefinition.Validation.invalid("tacz_industry.feed is absent")
                    : definition.validateAgainst(gunId, index);
            if (!validation.valid()) {
                rejected++;
                GunMod.LOGGER.error("Ignoring inline tacz_industry declaration {}: {}", gunId, validation.reason());
                continue;
            }
            resolved.put(gunId, definition);
            synchronizedDefinitions.put(gunId, CommonAssetsManager.GSON.toJson(definition));
            inlineAccepted++;
        }

        // Compatibility data packs remain the preferred correction route for
        // old, unmaintained, or differently licensed gun packs. Only validated
        // sidecars reach runtime services or clients.
        for (Map.Entry<Identifier, GunFeedDefinition> entry : getAllData().entrySet()) {
            Identifier gunId = entry.getKey();
            var index = CommonAssetsManager.get().getGunIndex(gunId);
            if (index == null) {
                dormant++;
                GunMod.LOGGER.debug("Leaving optional gun-feed declaration {} dormant: target gun is absent.", gunId);
                continue;
            }
            GunFeedDefinition definition = entry.getValue();
            GunFeedDefinition.Validation validation = definition == null
                    ? GunFeedDefinition.Validation.invalid("definition is null")
                    : definition.validateAgainst(gunId, index);
            if (!validation.valid()) {
                rejected++;
                GunMod.LOGGER.error("Ignoring gun-feed declaration {}: {}", gunId, validation.reason());
                continue;
            }
            resolved.put(gunId, definition);
            JsonElement raw = objects.get(gunId);
            if (raw != null) {
                synchronizedDefinitions.put(gunId, raw.toString());
            }
            sidecarAccepted++;
        }
        validDefinitions = Map.copyOf(resolved);
        networkCache = Map.copyOf(synchronizedDefinitions);
        audit = new Audit(validDefinitions.size(), dormant, rejected, sidecarAccepted, inlineAccepted);
        GunMod.LOGGER.info(
                "Loaded {} validated gun-feed declaration(s): {} sidecar, {} inline; {} dormant optional declaration(s), {} rejected declaration(s).",
                validDefinitions.size(), sidecarAccepted, inlineAccepted, dormant, rejected
        );
    }

    @Nullable
    public GunFeedDefinition getDefinition(Identifier gunId) {
        return gunId == null ? null : validDefinitions.get(gunId);
    }

    public Map<Identifier, GunFeedDefinition> getValidDefinitions() {
        return validDefinitions;
    }

    public Audit getAudit() {
        return audit;
    }

    /** Runtime-facing adapter diagnostic counts; heuristic survey candidates are deliberately excluded. */
    public record Audit(int accepted, int dormant, int rejected, int sidecarAccepted, int inlineAccepted) {
        public Audit(int accepted, int dormant, int rejected) {
            this(accepted, dormant, rejected, 0, 0);
        }
    }
}
