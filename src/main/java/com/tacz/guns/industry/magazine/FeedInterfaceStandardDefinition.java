package com.tacz.guns.industry.magazine;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A named physical feed-interface standard.
 *
 * <p>This is the explicit parent contract for a removable magazine or belt
 * box: receiver geometry, latch/feed-lip interface, mechanism, cartridge
 * dimensional standard and audited capacities. It is not inferred from a gun
 * class, model, native AmmoId or matching capacity.</p>
 */
public final class FeedInterfaceStandardDefinition {
    public static final int SCHEMA_VERSION = 1;

    @SerializedName("schema_version")
    private int schemaVersion = SCHEMA_VERSION;
    private FeedMechanism mechanism = FeedMechanism.LEGACY;
    @SerializedName("magazine_family")
    private String magazineFamily = "";
    @SerializedName("cartridge_standard")
    private Identifier cartridgeStandard;
    @SerializedName("accepted_capacities")
    private List<Integer> acceptedCapacities = List.of();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public FeedMechanism getMechanism() {
        return mechanism == null ? FeedMechanism.LEGACY : mechanism;
    }

    public String getMagazineFamily() {
        return magazineFamily == null ? "" : magazineFamily;
    }

    public Identifier getCartridgeStandard() {
        return cartridgeStandard;
    }

    public List<Integer> getAcceptedCapacities() {
        if (acceptedCapacities == null || acceptedCapacities.isEmpty()) {
            return List.of();
        }
        Set<Integer> unique = new LinkedHashSet<>();
        for (Integer capacity : acceptedCapacities) {
            if (capacity != null && capacity > 0 && capacity <= MagazineItemDataAccessor.MAX_MAGAZINE_CAPACITY) {
                unique.add(capacity);
            }
        }
        List<Integer> result = new ArrayList<>(unique);
        result.sort(Integer::compareTo);
        return List.copyOf(result);
    }

    public boolean acceptsCapacity(int capacity) {
        return getAcceptedCapacities().contains(Math.max(0, capacity));
    }

    public boolean isValid() {
        return schemaVersion == SCHEMA_VERSION
                && (getMechanism().usesDetachableMagazine() || getMechanism() == FeedMechanism.BELT)
                && !getMagazineFamily().isBlank()
                && cartridgeStandard != null
                && !getAcceptedCapacities().isEmpty()
                && acceptedCapacities != null
                && getAcceptedCapacities().size() == acceptedCapacities.size();
    }
}
