package com.tacz.guns.industry.magazine;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent data carried by one physical magazine/clip stack.
 *
 * <p>{@code MagazineAmmoId} remains the carrier's canonical chamber/calibre
 * identity. The actual physical contents are an ordered per-round list in
 * {@code MagazineRounds}; its final entry is the top/next round. This supports
 * real same-calibre AP/HP/slug mixtures while preserving old-world carriers
 * that only stored {@code MagazineAmmoCount}.</p>
 */
public interface MagazineItemDataAccessor extends IMagazine {
    String MAGAZINE_FAMILY_TAG = "MagazineFamily";
    /** Canonical carrier/chamber ammo identity, not a claim that all rounds have this AmmoId. */
    String MAGAZINE_AMMO_ID_TAG = "MagazineAmmoId";
    String MAGAZINE_CAPACITY_TAG = "MagazineCapacity";
    /** Legacy HUD/Lua mirror. The ordered list is authoritative when present. */
    String MAGAZINE_AMMO_COUNT_TAG = "MagazineAmmoCount";
    /** Ordered bottom-to-top AmmoId strings; the last entry is next to feed/eject. */
    String MAGAZINE_ROUNDS_TAG = "MagazineRounds";
    /** Stable server-only transaction identity for a timed inventory operation. */
    String ROUND_HANDLING_INSTANCE_ID_TAG = "RoundHandlingInstanceId";
    String MAGAZINE_DISPLAY_NAME_TAG = "MagazineDisplayName";
    /** detachable_magazine/belt carriers; stripper_clip/speedloader loaders; en_bloc_clip is installed in gun NBT. */
    String FEED_DEVICE_KIND_TAG = "FeedDeviceKind";
    /**
     * Stable per-stack identity used only while a bridge clip/speedloader is
     * reserved during a reload animation. AmmoCount is intentionally mutable,
     * so comparing a copied ItemStack byte-for-byte would reject the second
     * round of a genuine scripted loop.
     */
    String FEED_DEVICE_INSTANCE_ID_TAG = "FeedDeviceInstanceId";

    int MAX_MAGAZINE_CAPACITY = 512;

    @Override
    default String getMagazineFamily(ItemStack magazine) {
        return ItemNbtUtils.getTag(magazine).getStringOr(MAGAZINE_FAMILY_TAG, "");
    }

    default void setMagazineFamily(ItemStack magazine, String family) {
        ItemNbtUtils.updateTag(magazine, tag -> tag.putString(MAGAZINE_FAMILY_TAG, family == null ? "" : family));
    }

    @Override
    default Identifier getAmmoId(ItemStack magazine) {
        CompoundTag tag = ItemNbtUtils.getTag(magazine);
        Identifier id = Identifier.tryParse(tag.getStringOr(MAGAZINE_AMMO_ID_TAG, ""));
        return id == null ? DefaultAssets.EMPTY_AMMO_ID : id;
    }

    default void setAmmoId(ItemStack magazine, @Nullable Identifier ammoId) {
        ItemNbtUtils.updateTag(magazine, tag -> tag.putString(
                MAGAZINE_AMMO_ID_TAG,
                ammoId == null ? DefaultAssets.EMPTY_AMMO_ID.toString() : ammoId.toString()
        ));
    }

    @Override
    default int getCapacity(ItemStack magazine) {
        return Math.clamp(
                ItemNbtUtils.getTag(magazine).getIntOr(MAGAZINE_CAPACITY_TAG, 0),
                0,
                MAX_MAGAZINE_CAPACITY
        );
    }

    default void setCapacity(ItemStack magazine, int capacity) {
        int safeCapacity = Math.clamp(capacity, 1, MAX_MAGAZINE_CAPACITY);
        boolean hasOrderedRounds = ItemNbtUtils.getTag(magazine).contains(MAGAZINE_ROUNDS_TAG);
        ItemNbtUtils.updateTag(magazine, tag -> {
            tag.putInt(MAGAZINE_CAPACITY_TAG, safeCapacity);
            int oldCount = tag.getIntOr(MAGAZINE_AMMO_COUNT_TAG, 0);
            tag.putInt(MAGAZINE_AMMO_COUNT_TAG, Math.clamp(oldCount, 0, safeCapacity));
        });
        if (hasOrderedRounds) {
            // Capacity reductions discard only the current top rounds, exactly
            // matching the old integer-count truncation semantics.
            setAmmoCount(magazine, Math.min(getAmmoCount(magazine), safeCapacity));
        }
    }

    /**
     * Ordered contents from bottom to top. Legacy stacks are projected as their
     * declared canonical AmmoId without mutation; the first write materialises
     * that projection in NBT so no existing rounds are lost.
     */
    default List<Identifier> getRoundAmmoIds(ItemStack magazine) {
        CompoundTag tag = ItemNbtUtils.getTag(magazine);
        int capacity = getCapacity(magazine);
        if (capacity <= 0) {
            return List.of();
        }
        if (!tag.contains(MAGAZINE_ROUNDS_TAG)) {
            return repeated(getAmmoId(magazine), Math.clamp(tag.getIntOr(MAGAZINE_AMMO_COUNT_TAG, 0), 0, capacity));
        }
        List<Identifier> rounds = new ArrayList<>();
        for (Tag entry : tag.getListOrEmpty(MAGAZINE_ROUNDS_TAG)) {
            if (!(entry instanceof StringTag stringTag)) {
                continue;
            }
            Identifier ammoId = Identifier.tryParse(stringTag.value());
            if (ammoId != null && !DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                rounds.add(ammoId);
                if (rounds.size() >= capacity) {
                    break;
                }
            }
        }
        // A malformed/pre-release list must not silently destroy the old count
        // mirror. Restore only missing entries as canonical legacy rounds;
        // valid explicit entries retain their exact order.
        int legacyCount = Math.clamp(tag.getIntOr(MAGAZINE_AMMO_COUNT_TAG, 0), 0, capacity);
        while (rounds.size() < legacyCount) {
            rounds.add(getAmmoId(magazine));
        }
        return List.copyOf(rounds);
    }

    /** The top/next physical round, or EMPTY when the carrier is empty. */
    default Identifier getNextRoundAmmoId(ItemStack magazine) {
        List<Identifier> rounds = getRoundAmmoIds(magazine);
        return rounds.isEmpty() ? DefaultAssets.EMPTY_AMMO_ID : rounds.getLast();
    }

    /**
     * Materialise the legacy integer count as an ordered list, then append one
     * physical round to the top of the carrier.
     */
    default boolean pushRound(ItemStack magazine, @Nullable Identifier ammoId) {
        if (ammoId == null || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId) || getAmmoCount(magazine) >= getCapacity(magazine)) {
            return false;
        }
        List<Identifier> rounds = new ArrayList<>(getRoundAmmoIds(magazine));
        rounds.add(ammoId);
        writeRounds(magazine, rounds);
        return true;
    }

    /** Remove and return the top/next physical round. */
    default Identifier popNextRound(ItemStack magazine) {
        List<Identifier> rounds = new ArrayList<>(getRoundAmmoIds(magazine));
        if (rounds.isEmpty()) {
            return DefaultAssets.EMPTY_AMMO_ID;
        }
        Identifier result = rounds.removeLast();
        writeRounds(magazine, rounds);
        return result;
    }

    /**
     * Remove the bottom/oldest round. TACZ's legacy closed-bolt count model
     * fires stored rounds before its separately flagged chamber round, so a
     * full reload uses this method for that chamber reservation to preserve the
     * player-visible last-loaded-first order across the whole firing sequence.
     */
    default Identifier popOldestRound(ItemStack magazine) {
        List<Identifier> rounds = new ArrayList<>(getRoundAmmoIds(magazine));
        if (rounds.isEmpty()) {
            return DefaultAssets.EMPTY_AMMO_ID;
        }
        Identifier result = rounds.removeFirst();
        writeRounds(magazine, rounds);
        return result;
    }

    /** Replace ordered contents only after a service has already validated every AmmoId/calibre. */
    default void setRoundAmmoIds(ItemStack magazine, List<Identifier> rounds) {
        writeRounds(magazine, rounds == null ? List.of() : rounds);
    }

    @Override
    default int getAmmoCount(ItemStack magazine) {
        CompoundTag tag = ItemNbtUtils.getTag(magazine);
        if (tag.contains(MAGAZINE_ROUNDS_TAG)) {
            return getRoundAmmoIds(magazine).size();
        }
        return Math.clamp(tag.getIntOr(MAGAZINE_AMMO_COUNT_TAG, 0), 0, getCapacity(magazine));
    }

    /**
     * Compatibility bridge for old scripts/HUDs. Shrinking removes top rounds;
     * growing adds canonical base rounds because no caller supplied a profile.
     * Profile-aware paths must use {@link #pushRound(ItemStack, Identifier)}.
     */
    @Override
    default void setAmmoCount(ItemStack magazine, int count) {
        int desired = Math.clamp(count, 0, getCapacity(magazine));
        List<Identifier> rounds = new ArrayList<>(getRoundAmmoIds(magazine));
        while (rounds.size() > desired) {
            rounds.removeLast();
        }
        while (rounds.size() < desired) {
            rounds.add(getAmmoId(magazine));
        }
        writeRounds(magazine, rounds);
    }

    @Override
    default String getDisplayNameKey(ItemStack magazine) {
        return ItemNbtUtils.getTag(magazine).getStringOr(MAGAZINE_DISPLAY_NAME_TAG, "");
    }

    default void setDisplayNameKey(ItemStack magazine, String displayNameKey) {
        ItemNbtUtils.updateTag(magazine, tag -> tag.putString(
                MAGAZINE_DISPLAY_NAME_TAG, displayNameKey == null ? "" : displayNameKey
        ));
    }

    /**
     * Existing magazine stacks predate this tag. Their behaviour is still
     * resolved by the gun's FeedMechanism, so the neutral default here is only
     * a display/storage convention and never changes an old belt box into an
     * AR magazine by itself.
     */
    default String getFeedDeviceKind(ItemStack magazine) {
        return ItemNbtUtils.getTag(magazine).getStringOr(FEED_DEVICE_KIND_TAG, "");
    }

    default void setFeedDeviceKind(ItemStack magazine, String kind) {
        ItemNbtUtils.updateTag(magazine, tag -> tag.putString(
                FEED_DEVICE_KIND_TAG, kind == null ? "" : kind
        ));
    }

    default String getFeedDeviceInstanceId(ItemStack magazine) {
        return ItemNbtUtils.getTag(magazine).getStringOr(FEED_DEVICE_INSTANCE_ID_TAG, "");
    }

    default void setFeedDeviceInstanceId(ItemStack magazine, String instanceId) {
        ItemNbtUtils.updateTag(magazine, tag -> tag.putString(
                FEED_DEVICE_INSTANCE_ID_TAG, instanceId == null ? "" : instanceId
        ));
    }

    default String getRoundHandlingInstanceId(ItemStack magazine) {
        return ItemNbtUtils.getTag(magazine).getStringOr(ROUND_HANDLING_INSTANCE_ID_TAG, "");
    }

    default void setRoundHandlingInstanceId(ItemStack magazine, String instanceId) {
        ItemNbtUtils.updateTag(magazine, tag -> tag.putString(
                ROUND_HANDLING_INSTANCE_ID_TAG, instanceId == null ? "" : instanceId
        ));
    }

    @Override
    default boolean isConfigured(ItemStack magazine) {
        return !getMagazineFamily(magazine).isBlank()
                && !getAmmoId(magazine).equals(DefaultAssets.EMPTY_AMMO_ID)
                && getCapacity(magazine) > 0;
    }

    private static List<Identifier> repeated(Identifier ammoId, int count) {
        if (count <= 0 || ammoId == null || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
            return List.of();
        }
        List<Identifier> rounds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rounds.add(ammoId);
        }
        return List.copyOf(rounds);
    }

    private static void writeRounds(ItemStack magazine, List<Identifier> requested) {
        int capacity = getStaticCapacity(magazine);
        List<Identifier> safe = new ArrayList<>();
        if (requested != null) {
            for (Identifier ammoId : requested) {
                if (ammoId != null && !DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                    safe.add(ammoId);
                    if (safe.size() >= capacity) {
                        break;
                    }
                }
            }
        }
        ItemNbtUtils.updateTag(magazine, tag -> {
            ListTag stored = new ListTag();
            for (Identifier ammoId : safe) {
                stored.add(StringTag.valueOf(ammoId.toString()));
            }
            tag.put(MAGAZINE_ROUNDS_TAG, stored);
            tag.putInt(MAGAZINE_AMMO_COUNT_TAG, safe.size());
        });
    }

    private static int getStaticCapacity(ItemStack magazine) {
        return Math.clamp(ItemNbtUtils.getTag(magazine).getIntOr(MAGAZINE_CAPACITY_TAG, 0), 0, MAX_MAGAZINE_CAPACITY);
    }
}
