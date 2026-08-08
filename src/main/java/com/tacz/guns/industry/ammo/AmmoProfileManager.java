package com.tacz.guns.industry.ammo;

import com.google.gson.JsonElement;
import com.tacz.guns.GunMod;
import com.tacz.guns.industry.IndustryProfileManager;
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
 * Loads only explicit alternate-ammunition profiles. Base AmmoIds deliberately
 * use {@link AmmoProfileDefinition#standard(Identifier)} when no profile file
 * exists, so adding this system cannot silently change legacy ballistics.
 */
public final class AmmoProfileManager extends CommonDataManager<AmmoProfileDefinition> {
    private Map<Identifier, AmmoProfileDefinition> validProfiles = Map.of();

    public AmmoProfileManager() {
        super(DataType.AMMO_PROFILE, AmmoProfileDefinition.class, CommonAssetsManager.GSON,
                "industry/ammo_profiles", "AmmoProfileLoader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            super.apply(Map.<Identifier, JsonElement>of(), resourceManager, profiler);
            validProfiles = Map.of();
            return;
        }
        super.apply(objects, resourceManager, profiler);
        Map<Identifier, AmmoProfileDefinition> resolved = new LinkedHashMap<>();
        Map<Identifier, String> synchronizedProfiles = new LinkedHashMap<>();
        for (Map.Entry<Identifier, AmmoProfileDefinition> entry : getAllData().entrySet()) {
            Identifier profileId = entry.getKey();
            AmmoProfileDefinition definition = entry.getValue();
            if (definition == null || !definition.isValid()) {
                GunMod.LOGGER.error("Ignoring invalid ammunition profile {}.", profileId);
                continue;
            }
            if (!profileId.equals(definition.getAmmoId())) {
                GunMod.LOGGER.error("Ignoring ammunition profile {}: JSON ammo {} must equal the resource id.",
                        profileId, definition.getAmmoId());
                continue;
            }
            if (CommonAssetsManager.get().getAmmoIndex(definition.getAmmoId()) == null
                    || CommonAssetsManager.get().getAmmoIndex(definition.getCaliberAmmoId()) == null) {
                GunMod.LOGGER.error("Ignoring ammunition profile {}: ammo {} or canonical calibre {} has no loaded AmmoIndex.",
                        profileId, definition.getAmmoId(), definition.getCaliberAmmoId());
                continue;
            }
            resolved.put(profileId, definition);
            JsonElement raw = objects.get(profileId);
            if (raw != null) {
                synchronizedProfiles.put(profileId, raw.toString());
            }
        }
        validProfiles = Map.copyOf(resolved);
        networkCache = Map.copyOf(synchronizedProfiles);
        GunMod.LOGGER.info("Loaded {} validated TACZ ammunition profile(s).", validProfiles.size());
    }

    @Nullable
    public AmmoProfileDefinition getProfile(Identifier ammoId) {
        return ammoId == null ? null : validProfiles.get(ammoId);
    }

    public Map<Identifier, AmmoProfileDefinition> getValidProfiles() {
        return validProfiles;
    }
}
