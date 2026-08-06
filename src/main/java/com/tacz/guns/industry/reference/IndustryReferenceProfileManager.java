package com.tacz.guns.industry.reference;

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
 * Loads validated actual-structure/reference profiles for loaded gun ids.
 *
 * <p>Because the {@link net.minecraft.resources.FileToIdConverter} path is
 * {@code industry/reference/guns}, the data resource id is the target gun id:
 * {@code data/ww/industry/reference/guns/mg42.json} directly maps to
 * {@code ww:mg42}. A compatibility datapack may safely contribute resources in
 * another gun pack's data namespace without editing that pack's archive.</p>
 */
public final class IndustryReferenceProfileManager extends CommonDataManager<IndustryReferenceProfile> {
    private Map<Identifier, IndustryReferenceProfile> validProfiles = Map.of();
    private IndustryRuntimeAudit.Snapshot auditSnapshot = new IndustryRuntimeAudit.Snapshot(
            0, 0, 0, 0, 0, 0, 0, 0, java.util.List.of()
    );

    public IndustryReferenceProfileManager() {
        super(DataType.INDUSTRY_REFERENCE, IndustryReferenceProfile.class, CommonAssetsManager.GSON,
                "industry/reference/guns", "IndustryReferenceProfileLoader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        super.apply(objects, resourceManager, profiler);
        Map<Identifier, IndustryReferenceProfile> resolved = new LinkedHashMap<>();
        int candidates = 0;
        // Every successfully loaded gun gets a deliberately conservative table
        // row. It records actual GunId/ammo/index facts for future automation,
        // but leaves feed runtime behaviour legacy until evidence arrives.
        for (Map.Entry<Identifier, com.tacz.guns.resource.index.CommonGunIndex> entry
                : CommonAssetsManager.get().getAllGuns()) {
            IndustryReferenceProfile candidate = IndustryReferenceProfile.automaticCandidate(entry.getKey(), entry.getValue());
            IndustryReferenceProfile.Validation validation = candidate.validateAgainst(entry.getKey(), entry.getValue());
            if (validation.valid()) {
                resolved.put(entry.getKey(), candidate);
                candidates++;
            } else {
                GunMod.LOGGER.warn("Could not build surveyed industry reference candidate for {}: {}",
                        entry.getKey(), validation.reason());
            }
        }

        int curated = 0;
        for (Map.Entry<Identifier, IndustryReferenceProfile> entry : getAllData().entrySet()) {
            var loadedGun = CommonAssetsManager.get().getGunIndex(entry.getKey());
            if (loadedGun == null) {
                // A GPL compatibility layer may carry data/ww/... or
                // data/rainforest/... profiles while that optional pack is not
                // installed. Keep the row dormant rather than issuing a false
                // error on every ordinary default-pack reload.
                GunMod.LOGGER.debug("Leaving optional industry reference profile {} dormant: target gun is absent.",
                        entry.getKey());
                continue;
            }
            IndustryReferenceProfile profile = entry.getValue();
            IndustryReferenceProfile.Validation validation = profile == null
                    ? IndustryReferenceProfile.Validation.invalid("profile is null")
                    : profile.validateAgainst(entry.getKey(), loadedGun);
            if (!validation.valid()) {
                // Retain the safe automatic candidate, if any, rather than
                // deleting a loaded gun from the comparison table because a
                // compatibility overlay was written for another pack version.
                GunMod.LOGGER.error("Ignoring industry reference profile {}: {}", entry.getKey(), validation.reason());
                continue;
            }
            resolved.put(entry.getKey(), profile);
            curated++;
        }
        validProfiles = Map.copyOf(resolved);
        Map<Identifier, String> synchronizedProfiles = new LinkedHashMap<>();
        validProfiles.forEach((id, profile) -> synchronizedProfiles.put(id, CommonAssetsManager.GSON.toJson(profile)));
        networkCache = Map.copyOf(synchronizedProfiles);
        GunMod.LOGGER.info("Loaded {} curated TACZ industry reference profile(s) and {} conservative surveyed candidate(s).",
                curated, Math.max(0, candidates - curated));

        // TableRecipeManager is registered before this manager. Its immutable
        // raw snapshot therefore still contains the upstream id for audit,
        // while its effective recipe map may already use a validated alias.
        CommonAssetsManager manager = CommonAssetsManager.getInstance();
        if (IndustryProfileManager.isCreateFlyProfileActive() && manager != null
                && manager.getTableRecipeManager() != null) {
            auditSnapshot = IndustryRuntimeAudit.audit(
                    manager.getTableRecipeManager().getRawTableRecipeDefinitions(),
                    manager,
                    manager.getIndustryIdentityAliasManager(),
                    validProfiles
            );
        } else {
            auditSnapshot = new IndustryRuntimeAudit.Snapshot(0, 0, 0, 0, 0, 0, 0, 0, java.util.List.of());
        }
    }

    @Nullable
    public IndustryReferenceProfile getProfile(Identifier gunId) {
        return gunId == null ? null : validProfiles.get(gunId);
    }

    public Map<Identifier, IndustryReferenceProfile> getValidProfiles() {
        return validProfiles;
    }

    public IndustryRuntimeAudit.Snapshot getAuditSnapshot() {
        return auditSnapshot;
    }
}
