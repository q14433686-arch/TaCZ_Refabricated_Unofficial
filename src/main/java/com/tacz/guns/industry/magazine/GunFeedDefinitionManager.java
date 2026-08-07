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
 * Validates opt-in add-on feed declarations only after the real GunIndex has
 * loaded.  Generic JSON loading alone cannot prove that a declared magazine
 * family, ammo id, and receiver capacity still match the version of an addon
 * pack currently installed on the server.
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
        for (Map.Entry<Identifier, GunFeedDefinition> entry : getAllData().entrySet()) {
            Identifier gunId = entry.getKey();
            var index = CommonAssetsManager.get().getGunIndex(gunId);
            if (index == null) {
                // Compatibility data packs commonly ship entries for optional
                // addon packs. Keep those dormant instead of turning a missing
                // optional mod into an error or an unconfigured creative item.
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
        }
        validDefinitions = Map.copyOf(resolved);
        networkCache = Map.copyOf(synchronizedDefinitions);
        audit = new Audit(validDefinitions.size(), dormant, rejected);
        GunMod.LOGGER.info("Loaded {} validated gun-feed declaration(s); {} dormant optional declaration(s), {} rejected declaration(s).",
                validDefinitions.size(), dormant, rejected);
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

    /** Runtime-facing addon adapter diagnostic counts; no guessed feed is included. */
    public record Audit(int accepted, int dormant, int rejected) {
    }
}
