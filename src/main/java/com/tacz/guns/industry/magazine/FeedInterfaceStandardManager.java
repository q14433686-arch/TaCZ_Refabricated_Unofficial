package com.tacz.guns.industry.magazine;

import com.google.gson.JsonElement;
import com.tacz.guns.GunMod;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.industry.ammo.CartridgeStandardDefinition;
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
 * Loads named removable-feed interface standards after cartridge standards.
 *
 * <p>The manager refuses two standard resources that claim the same mechanism
 * and family. A family string must consequently resolve to one audit-owned
 * physical interface, rather than becoming an accidental loose alias.</p>
 */
public final class FeedInterfaceStandardManager extends CommonDataManager<FeedInterfaceStandardDefinition> {
    private Map<Identifier, FeedInterfaceStandardDefinition> validStandards = Map.of();
    private Map<InterfaceKey, Identifier> standardByInterface = Map.of();

    public FeedInterfaceStandardManager() {
        super(DataType.FEED_STANDARD, FeedInterfaceStandardDefinition.class, CommonAssetsManager.GSON,
                "industry/feed_standards", "FeedInterfaceStandardLoader");
    }

    @Override
    public Collection<Identifier> getFabricDependencies() {
        return List.of(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "cartridgestandardloader"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            super.apply(Map.<Identifier, JsonElement>of(), resourceManager, profiler);
            validStandards = Map.of();
            standardByInterface = Map.of();
            return;
        }
        super.apply(objects, resourceManager, profiler);
        Map<Identifier, FeedInterfaceStandardDefinition> candidates = new LinkedHashMap<>();
        Map<InterfaceKey, Identifier> ownerByInterface = new LinkedHashMap<>();
        Map<Identifier, String> synchronizedStandards = new LinkedHashMap<>();

        for (Map.Entry<Identifier, FeedInterfaceStandardDefinition> entry : getAllData().entrySet()) {
            Identifier standardId = entry.getKey();
            FeedInterfaceStandardDefinition definition = entry.getValue();
            if (definition == null || !definition.isValid()) {
                GunMod.LOGGER.error("Ignoring invalid feed-interface standard {}.", standardId);
                continue;
            }
            CartridgeStandardDefinition cartridge = CommonAssetsManager.get()
                    .getCartridgeStandard(definition.getCartridgeStandard());
            if (cartridge == null) {
                GunMod.LOGGER.error("Ignoring feed-interface standard {}: cartridge standard {} is absent or invalid.",
                        standardId, definition.getCartridgeStandard());
                continue;
            }
            InterfaceKey key = new InterfaceKey(definition.getMechanism(), definition.getMagazineFamily());
            Identifier previous = ownerByInterface.putIfAbsent(key, standardId);
            if (previous != null) {
                GunMod.LOGGER.error(
                        "Ignoring feed-interface standard {}: {} / {} is already owned by {}; interfaces must be unambiguous.",
                        standardId, definition.getMechanism().serializedName(), definition.getMagazineFamily(), previous
                );
                continue;
            }
            candidates.put(standardId, definition);
        }

        Map<InterfaceKey, Integer> keyCounts = new LinkedHashMap<>();
        for (FeedInterfaceStandardDefinition definition : getAllData().values()) {
            if (definition != null && definition.isValid()
                    && CommonAssetsManager.get().getCartridgeStandard(definition.getCartridgeStandard()) != null) {
                keyCounts.merge(new InterfaceKey(definition.getMechanism(), definition.getMagazineFamily()), 1, Integer::sum);
            }
        }
        candidates.entrySet().removeIf(entry -> keyCounts.getOrDefault(
                new InterfaceKey(entry.getValue().getMechanism(), entry.getValue().getMagazineFamily()), 0
        ) != 1);

        Map<InterfaceKey, Identifier> resolvedOwners = new LinkedHashMap<>();
        for (Map.Entry<Identifier, FeedInterfaceStandardDefinition> entry : candidates.entrySet()) {
            Identifier standardId = entry.getKey();
            FeedInterfaceStandardDefinition definition = entry.getValue();
            resolvedOwners.put(new InterfaceKey(definition.getMechanism(), definition.getMagazineFamily()), standardId);
            JsonElement raw = objects.get(standardId);
            if (raw != null) {
                synchronizedStandards.put(standardId, raw.toString());
            }
        }
        validStandards = Map.copyOf(candidates);
        standardByInterface = Map.copyOf(resolvedOwners);
        networkCache = Map.copyOf(synchronizedStandards);
        GunMod.LOGGER.info("Loaded {} validated feed-interface standard(s).", validStandards.size());
    }

    @Nullable
    public FeedInterfaceStandardDefinition getStandard(Identifier standardId) {
        return standardId == null ? null : validStandards.get(standardId);
    }

    @Nullable
    public Identifier getStandardId(FeedMechanism mechanism, String magazineFamily) {
        return standardByInterface.get(new InterfaceKey(
                mechanism == null ? FeedMechanism.LEGACY : mechanism,
                magazineFamily == null ? "" : magazineFamily
        ));
    }

    public Map<Identifier, FeedInterfaceStandardDefinition> getValidStandards() {
        return validStandards;
    }

    private record InterfaceKey(FeedMechanism mechanism, String family) {
    }
}
