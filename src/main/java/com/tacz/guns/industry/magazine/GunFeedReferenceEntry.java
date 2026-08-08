package com.tacz.guns.industry.magazine;

import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only, data-backed relationship shown by the optional recipe viewers.
 *
 * <p>This is deliberately a reference entry rather than a crafting recipe:
 * the gun's actual {@link GunData#getAmmoId()} is always read from its loaded
 * {@code GunIndex}, and a removable carrier is shown only when the gun has an
 * accepted {@link GunFeedDefinition}. In particular, no gun class, reload
 * type, name, model, capacity or guessed magazine well can create a magazine
 * relationship here.</p>
 *
 * <p>The same object backs JEI and REI. That keeps both viewers honest about
 * the runtime contract: an ammo slot contains exactly the profiles accepted by
 * {@link IAmmo#isAmmoOfGun(ItemStack, ItemStack)}, while an external-carrier
 * slot contains only real configured magazine stacks accepted by
 * {@link PhysicalMagazineService#isCompatible(GunFeedDefinition, ItemStack)}.
 * It is navigation/documentation only and never crafts, transfers, or mutates
 * an inventory.</p>
 */
public final class GunFeedReferenceEntry {
    private final Identifier gunId;
    private final ItemStack gunStack;
    private final List<ItemStack> ammoStacks;
    private final @Nullable GunFeedDefinition feedDefinition;
    private final List<ItemStack> carrierStacks;

    private GunFeedReferenceEntry(Identifier gunId, ItemStack gunStack, List<ItemStack> ammoStacks,
                                  @Nullable GunFeedDefinition feedDefinition, List<ItemStack> carrierStacks) {
        this.gunId = gunId;
        this.gunStack = gunStack;
        this.ammoStacks = List.copyOf(ammoStacks);
        this.feedDefinition = feedDefinition;
        this.carrierStacks = List.copyOf(carrierStacks);
    }

    public Identifier getGunId() {
        return gunId;
    }

    public ItemStack getGunStack() {
        return gunStack;
    }

    /** Native ammo plus only the alternate profiles that this exact sample gun currently accepts. */
    public List<ItemStack> getAmmoStacks() {
        return ammoStacks;
    }

    /** Null means that the loaded gun has no explicit physical-feed declaration. */
    @Nullable
    public GunFeedDefinition getFeedDefinition() {
        return feedDefinition;
    }

    /**
     * Real configured carrier/device examples. An empty list does not mean
     * that a guessed generic magazine should be used: it means the definition
     * is legacy or its feed remains fixed inside the receiver.
     */
    public List<ItemStack> getCarrierStacks() {
        return carrierStacks;
    }

    public boolean hasDeclaredPhysicalCarrier() {
        return !carrierStacks.isEmpty();
    }

    public boolean hasFixedInternalFeed() {
        return feedDefinition != null
                && !feedDefinition.isValidExternalCarrierDefinition()
                && !feedDefinition.getMechanism().usesPhysicalFeedDevice();
    }

    /** Build a stable snapshot from the currently synchronized common data. */
    public static List<GunFeedReferenceEntry> getAll() {
        // Build each configured external carrier only once for this snapshot.
        // REI asks dynamic generators at query time, so repeatedly rebuilding
        // every NBT carrier for every receiver would turn a catalogue lookup
        // into an avoidable quadratic allocation spike.
        List<ItemStack> externalCarrierSamples = createExternalCarrierSamples();
        List<GunFeedReferenceEntry> entries = new ArrayList<>();
        for (Map.Entry<Identifier, CommonGunIndex> entry : CommonAssetsManager.get().getAllGuns()) {
            Identifier gunId = entry.getKey();
            CommonGunIndex index = entry.getValue();
            if (gunId == null || index == null || index.getGunData() == null) {
                continue;
            }
            GunData data = index.getGunData();
            Identifier nativeAmmo = data.getAmmoId();
            if (nativeAmmo == null) {
                continue;
            }

            ItemStack gun = createGunStack(gunId);
            if (gun.isEmpty()) {
                continue;
            }
            List<ItemStack> acceptedAmmo = createAcceptedAmmoStacks(gun, nativeAmmo);
            if (acceptedAmmo.isEmpty()) {
                // A valid GunIndex must have a native ammo identity. Retain a
                // visible, exact native fallback if a transient client cache
                // has not yet populated its AmmoIndex/profile companion.
                acceptedAmmo = List.of(AmmoItemBuilder.create().setId(nativeAmmo).build());
            }

            GunFeedDefinition feed = CommonAssetsManager.get().getGunFeedDefinition(gunId);
            entries.add(new GunFeedReferenceEntry(
                    gunId,
                    gun,
                    acceptedAmmo,
                    feed,
                    createCompatibleCarrierStacks(feed, externalCarrierSamples)
            ));
        }
        entries.sort(Comparator.comparing(entry -> entry.getGunId().toString()));
        return List.copyOf(entries);
    }

    private static ItemStack createGunStack(Identifier gunId) {
        ItemStack gun = GunItemBuilder.create().setId(gunId).build();
        // A third-party index can use an item type whose client registry has
        // not been installed yet. It is still useful to expose the exact GunId
        // in the reference, so fall back to TACZ's neutral sample item rather
        // than dropping the relationship or guessing a different gun.
        return gun.isEmpty() ? GunItemBuilder.create().setId(gunId).forceBuild() : gun;
    }

    private static List<ItemStack> createAcceptedAmmoStacks(ItemStack gun, Identifier nativeAmmo) {
        List<Identifier> candidateIds = new ArrayList<>();
        candidateIds.add(nativeAmmo);
        CommonAssetsManager.get().getAllAmmos().stream()
                .map(Map.Entry::getKey)
                .filter(id -> id != null && !id.equals(nativeAmmo))
                .sorted(Comparator.comparing(Identifier::toString))
                .forEach(candidateIds::add);

        List<ItemStack> accepted = new ArrayList<>();
        for (Identifier ammoId : candidateIds) {
            ItemStack ammo = AmmoItemBuilder.create().setId(ammoId).build();
            if (ammo.getItem() instanceof IAmmo item && item.isAmmoOfGun(gun, ammo)) {
                accepted.add(ammo);
            }
        }
        return List.copyOf(accepted);
    }

    /** Every explicitly manufactured external carrier once, in stable catalogue order. */
    private static List<ItemStack> createExternalCarrierSamples() {
        Map<String, ItemStack> samples = new LinkedHashMap<>();
        CommonAssetsManager.get().getAllGunFeedDefinitions().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> {
                    GunFeedDefinition candidate = entry.getValue();
                    if (!candidate.isValidExternalCarrierDefinition()) {
                        return;
                    }
                    for (ExternalCarrierVariant variant : candidate.getExternalCarrierVariants()) {
                        ItemStack stack = MagazineItemBuilder.create().fromExternalCarrier(candidate, variant).build();
                        samples.putIfAbsent(carrierIdentity(stack), stack);
                    }
                });
        return List.copyOf(samples.values());
    }

    /** Stable physical identity, deliberately excluding only cosmetic display text. */
    private static String carrierIdentity(ItemStack stack) {
        if (stack.getItem() instanceof MagazineItemDataAccessor accessor) {
            return accessor.getMagazineFamily(stack)
                    + "|" + accessor.getFeedStandardId(stack)
                    + "|" + accessor.getAmmoId(stack)
                    + "|" + accessor.getCapacity(stack)
                    + "|" + accessor.getFeedDeviceKind(stack);
        }
        return String.valueOf(stack.getItem());
    }

    private static List<ItemStack> createCompatibleCarrierStacks(@Nullable GunFeedDefinition receiver,
                                                                   List<ItemStack> externalCarrierSamples) {
        if (receiver == null) {
            return List.of();
        }
        if (receiver.isValidExternalCarrierDefinition()) {
            // The same runtime predicate handles named-standard conflicts as
            // well as legacy family + canonical-calibre compatibility. Do not
            // duplicate that contract in the viewer with a looser heuristic.
            // Put this receiver's own named variants first. A shared
            // standard can contribute additional valid capacities afterwards,
            // but the first stack a player sees should be the one explicitly
            // declared for the selected gun rather than another pack's skin.
            Map<String, ItemStack> compatible = new LinkedHashMap<>();
            for (ExternalCarrierVariant variant : receiver.getExternalCarrierVariants()) {
                ItemStack own = MagazineItemBuilder.create().fromExternalCarrier(receiver, variant).build();
                if (PhysicalMagazineService.isCompatible(receiver, own)) {
                    compatible.putIfAbsent(carrierIdentity(own), own);
                }
            }
            for (ItemStack sample : externalCarrierSamples) {
                if (PhysicalMagazineService.isCompatible(receiver, sample)) {
                    compatible.putIfAbsent(carrierIdentity(sample), sample);
                }
            }
            return List.copyOf(compatible.values());
        }
        if (receiver.getMechanism().usesPhysicalFeedDevice()) {
            return List.of(MagazineItemBuilder.create().fromDefinition(receiver).build());
        }
        return List.of();
    }

}
