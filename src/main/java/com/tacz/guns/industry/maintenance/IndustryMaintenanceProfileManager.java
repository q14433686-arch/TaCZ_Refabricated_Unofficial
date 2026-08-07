package com.tacz.guns.industry.maintenance;

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
 * Loads validated maintenance profiles from
 * {@code data/<namespace>/industry/maintenance/guns/<gun-path>.json}.
 *
 * <p>The resource id is the target GunId. Optional compatibility profiles stay
 * dormant if that gun pack is absent; malformed profiles are omitted from both
 * server selection and client synchronisation rather than enabling an unsafe
 * partial maintenance path.</p>
 */
public final class IndustryMaintenanceProfileManager extends CommonDataManager<IndustryMaintenanceProfile> {
    private Map<Identifier, IndustryMaintenanceProfile> validProfiles = Map.of();

    public IndustryMaintenanceProfileManager() {
        super(DataType.INDUSTRY_MAINTENANCE, IndustryMaintenanceProfile.class, CommonAssetsManager.GSON,
                "industry/maintenance/guns", "IndustryMaintenanceProfileLoader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        super.apply(objects, resourceManager, profiler);
        Map<Identifier, IndustryMaintenanceProfile> resolved = new LinkedHashMap<>();
        Map<Identifier, String> synchronizedProfiles = new LinkedHashMap<>();
        for (Map.Entry<Identifier, IndustryMaintenanceProfile> entry : getAllData().entrySet()) {
            Identifier gunId = entry.getKey();
            IndustryMaintenanceProfile profile = entry.getValue();
            if (CommonAssetsManager.get().getGunIndex(gunId) == null) {
                GunMod.LOGGER.debug("Leaving optional maintenance profile {} dormant: target gun is absent.", gunId);
                continue;
            }
            if (profile == null || !profile.isValid()) {
                GunMod.LOGGER.error("Ignoring invalid industry maintenance profile {}.", gunId);
                continue;
            }
            resolved.put(gunId, profile);
            JsonElement raw = objects.get(gunId);
            if (raw != null) {
                synchronizedProfiles.put(gunId, raw.toString());
            }
        }
        validProfiles = Map.copyOf(resolved);
        networkCache = Map.copyOf(synchronizedProfiles);
        GunMod.LOGGER.info("Loaded {} validated TACZ industry maintenance profile(s).", validProfiles.size());
    }

    @Nullable
    public IndustryMaintenanceProfile getProfile(Identifier gunId) {
        return gunId == null ? null : validProfiles.get(gunId);
    }

    public Map<Identifier, IndustryMaintenanceProfile> getValidProfiles() {
        return validProfiles;
    }
}
