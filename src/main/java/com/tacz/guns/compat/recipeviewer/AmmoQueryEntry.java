package com.tacz.guns.compat.recipeviewer;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Viewer-neutral data for the built-in ammo-to-gun query.
 *
 * <p>Keeping the lookup here gives JEI and REI exactly the same gun-pack-aware result set. The
 * common indexes are the synchronized, server-authoritative indexes, so third-party ammunition and
 * guns are included after the normal recipe-viewer refresh without either viewer scanning recipes.
 */
public final class AmmoQueryEntry {
    /** Six full rows plus room for an overflow slot on the seventh row. */
    public static final int MAX_GUN_SHOW_COUNT = 60;

    private final ItemStack ammoStack;
    private final List<ItemStack> gunStacks;
    private final List<ItemStack> extraGunStacks;

    private AmmoQueryEntry(Identifier ammoId, List<ItemStack> compatibleGuns) {
        this.ammoStack = AmmoItemBuilder.create().setId(ammoId).build();
        int visibleCount = Math.min(compatibleGuns.size(), MAX_GUN_SHOW_COUNT);
        this.gunStacks = List.copyOf(compatibleGuns.subList(0, visibleCount));
        this.extraGunStacks = List.copyOf(compatibleGuns.subList(visibleCount, compatibleGuns.size()));
    }

    /** Builds one query display per ammunition that is used by at least one loaded gun. */
    public static List<AmmoQueryEntry> getAllAmmoQueryEntries() {
        List<Map.Entry<Identifier, CommonGunIndex>> gunIndexes =
                new ArrayList<>(TimelessAPI.getAllCommonGunIndex());
        gunIndexes.sort(Comparator
                .comparingInt((Map.Entry<Identifier, CommonGunIndex> entry) -> entry.getValue().getSort())
                .thenComparing(entry -> entry.getKey().toString()));

        Map<Identifier, List<ItemStack>> gunsByAmmo = new HashMap<>();
        for (Map.Entry<Identifier, CommonGunIndex> entry : gunIndexes) {
            ItemStack gunStack = GunItemBuilder.create().setId(entry.getKey()).build();
            if (gunStack.isEmpty()) {
                continue;
            }
            Identifier ammoId = entry.getValue().getGunData().getAmmoId();
            gunsByAmmo.computeIfAbsent(ammoId, ignored -> new ArrayList<>()).add(gunStack);
        }

        List<Map.Entry<Identifier, CommonAmmoIndex>> ammoIndexes =
                new ArrayList<>(TimelessAPI.getAllCommonAmmoIndex());
        ammoIndexes.sort(Comparator
                .comparingInt((Map.Entry<Identifier, CommonAmmoIndex> entry) -> entry.getValue().getSort())
                .thenComparing(entry -> entry.getKey().toString()));

        List<AmmoQueryEntry> queries = new ArrayList<>();
        for (Map.Entry<Identifier, CommonAmmoIndex> entry : ammoIndexes) {
            List<ItemStack> compatibleGuns = gunsByAmmo.get(entry.getKey());
            if (compatibleGuns != null && !compatibleGuns.isEmpty()) {
                queries.add(new AmmoQueryEntry(entry.getKey(), compatibleGuns));
            }
        }
        return List.copyOf(queries);
    }

    public ItemStack getAmmoStack() {
        return ammoStack;
    }

    public List<ItemStack> getGunStacks() {
        return gunStacks;
    }

    public List<ItemStack> getExtraGunStacks() {
        return extraGunStacks;
    }
}
