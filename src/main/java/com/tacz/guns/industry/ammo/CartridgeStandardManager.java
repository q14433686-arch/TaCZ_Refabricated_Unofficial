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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads named cartridge standards before profile aliases and feed standards.
 *
 * <p>A canonical ammo identity may have exactly one standard. This makes a
 * duplicated or ambiguous "universal calibre" declaration fail closed instead
 * of allowing datapack load order to select a gauge geometry.</p>
 */
public final class CartridgeStandardManager extends CommonDataManager<CartridgeStandardDefinition> {
    private Map<Identifier, CartridgeStandardDefinition> validStandards = Map.of();
    private Map<Identifier, Identifier> standardByCanonicalAmmo = Map.of();

    public CartridgeStandardManager() {
        super(DataType.CARTRIDGE_STANDARD, CartridgeStandardDefinition.class, CommonAssetsManager.GSON,
                "industry/cartridge_standards", "CartridgeStandardLoader");
    }

    @Override
    public Collection<Identifier> getFabricDependencies() {
        return List.of(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ammoindexloader"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            super.apply(Map.<Identifier, JsonElement>of(), resourceManager, profiler);
            validStandards = Map.of();
            standardByCanonicalAmmo = Map.of();
            return;
        }
        super.apply(objects, resourceManager, profiler);
        Map<Identifier, CartridgeStandardDefinition> candidates = new LinkedHashMap<>();
        Map<Identifier, Identifier> canonicalOwners = new LinkedHashMap<>();
        Map<Identifier, String> synchronizedStandards = new LinkedHashMap<>();

        for (Map.Entry<Identifier, CartridgeStandardDefinition> entry : getAllData().entrySet()) {
            Identifier standardId = entry.getKey();
            CartridgeStandardDefinition definition = entry.getValue();
            if (definition == null || !definition.isValid()) {
                GunMod.LOGGER.error("Ignoring invalid cartridge standard {}.", standardId);
                continue;
            }
            Identifier canonicalAmmo = definition.getCanonicalAmmo();
            if (CommonAssetsManager.get().getAmmoIndex(canonicalAmmo) == null) {
                GunMod.LOGGER.error("Ignoring cartridge standard {}: canonical ammo {} has no loaded AmmoIndex.",
                        standardId, canonicalAmmo);
                continue;
            }
            Identifier previous = canonicalOwners.putIfAbsent(canonicalAmmo, standardId);
            if (previous != null) {
                GunMod.LOGGER.error(
                        "Ignoring cartridge standard {}: canonical ammo {} is already owned by {}; standards must be unambiguous.",
                        standardId, canonicalAmmo, previous
                );
                continue;
            }
            candidates.put(standardId, definition);
        }

        // A duplicate was rejected above, but its original owner must also be
        // removed. Otherwise a partially loaded standard set could depend on
        // resource traversal order.
        Map<Identifier, Integer> canonicalCounts = new LinkedHashMap<>();
        for (CartridgeStandardDefinition definition : getAllData().values()) {
            if (definition != null && definition.isValid()
                    && CommonAssetsManager.get().getAmmoIndex(definition.getCanonicalAmmo()) != null) {
                canonicalCounts.merge(definition.getCanonicalAmmo(), 1, Integer::sum);
            }
        }
        candidates.entrySet().removeIf(entry -> canonicalCounts.getOrDefault(entry.getValue().getCanonicalAmmo(), 0) != 1);

        Map<Identifier, Identifier> resolvedCanonicalOwners = new LinkedHashMap<>();
        for (Map.Entry<Identifier, CartridgeStandardDefinition> entry : candidates.entrySet()) {
            Identifier standardId = entry.getKey();
            resolvedCanonicalOwners.put(entry.getValue().getCanonicalAmmo(), standardId);
            JsonElement raw = objects.get(standardId);
            if (raw != null) {
                synchronizedStandards.put(standardId, raw.toString());
            }
        }
        validStandards = Map.copyOf(candidates);
        standardByCanonicalAmmo = Map.copyOf(resolvedCanonicalOwners);
        networkCache = Map.copyOf(synchronizedStandards);
        GunMod.LOGGER.info("Loaded {} validated cartridge standard(s).", validStandards.size());
    }

    @Nullable
    public CartridgeStandardDefinition getStandard(Identifier standardId) {
        return standardId == null ? null : validStandards.get(standardId);
    }

    @Nullable
    public Identifier getStandardIdForCanonicalAmmo(Identifier canonicalAmmo) {
        return canonicalAmmo == null ? null : standardByCanonicalAmmo.get(canonicalAmmo);
    }

    public Map<Identifier, CartridgeStandardDefinition> getValidStandards() {
        return validStandards;
    }
}
