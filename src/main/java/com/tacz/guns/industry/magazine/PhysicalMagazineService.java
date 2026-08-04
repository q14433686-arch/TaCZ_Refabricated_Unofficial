package com.tacz.guns.industry.magazine;

import cn.sh1rocu.tacz.util.itemhandler.ItemHandlerHelper;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative operations for ItemStack-backed detachable magazines.
 *
 * <p>The service deliberately has no direct Create Fly Java dependency.  The
 * industrial profile gates this behaviour, while Create Fly supplies the
 * manufacturing recipes.  That keeps the reload transaction compatible with
 * both the normal TACZ client/server networking path and a future replacement
 * of the technology integration.</p>
 */
public final class PhysicalMagazineService {
    private PhysicalMagazineService() {
    }

    /** Physical magazines are an industrial-profile rule, not a global item-NBT interpretation. */
    public static boolean isFeatureEnabled() {
        return IndustryProfileManager.isCreateFlyProfileActive()
                && SyncConfig.PHYSICAL_MAGAZINES != null
                && SyncConfig.PHYSICAL_MAGAZINES.get();
    }

    @Nullable
    public static GunFeedDefinition getDefinition(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return null;
        }
        Identifier gunId = iGun.getGunId(gun);
        GunFeedDefinition definition = CommonAssetsManager.get().getGunFeedDefinition(gunId);
        return definition != null && definition.isValidDetachableDefinition() ? definition : null;
    }

    public static boolean usesPhysicalMagazine(ItemStack gun) {
        return isFeatureEnabled() && getDefinition(gun) != null;
    }

    /**
     * True only for an inserted magazine which is structurally compatible with
     * the current gun.  An absent/unmigrated old gun continues to read its
     * legacy integer count until the next reload/eject transaction.
     */
    public static boolean hasActiveInstalledMagazine(ItemStack gun) {
        if (!usesPhysicalMagazine(gun) || !(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        GunFeedDefinition definition = getDefinition(gun);
        return definition != null && isCompatible(definition, iGun.getInstalledMagazine(gun));
    }

    /**
     * Checks the serialized magazine without consulting the current profile.
     * This is used to keep legacy and physical state mirrored when an
     * administrator temporarily switches the profile off and later re-enables
     * it.
     */
    public static boolean hasStoredInstalledMagazine(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        ItemStack magazine = iGun.getInstalledMagazine(gun);
        return magazine.getItem() instanceof IMagazine item && item.isConfigured(magazine);
    }

    public static int getInstalledAmmoCount(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return 0;
        }
        ItemStack magazine = iGun.getInstalledMagazine(gun);
        return magazine.getItem() instanceof IMagazine item ? item.getAmmoCount(magazine) : 0;
    }

    /** Mutates the stored magazine then updates the old integer compatibility mirror. */
    public static void setInstalledAmmoCount(ItemStack gun, int count) {
        setStoredInstalledAmmoCount(gun, count);
    }

    /**
     * Profile-independent variant used solely to preserve a lossless mirror
     * while LEGACY is temporarily selected on a world that already has physical
     * magazines in item data.
     */
    public static void setStoredInstalledAmmoCount(ItemStack gun, int count) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return;
        }
        ItemStack magazine = iGun.getInstalledMagazine(gun);
        if (!(magazine.getItem() instanceof IMagazine item)) {
            return;
        }
        item.setAmmoCount(magazine, count);
        iGun.setInstalledMagazine(gun, magazine);
        syncLegacyAmmoCount(gun, item.getAmmoCount(magazine));
    }

    public static int removeInstalledRounds(ItemStack gun, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int current = getInstalledAmmoCount(gun);
        int removed = Math.min(current, amount);
        if (removed > 0) {
            setInstalledAmmoCount(gun, current - removed);
        }
        return removed;
    }

    /**
     * Whether a player can begin a physical-magazine reload.  A candidate must
     * contain more rounds than the currently installed/legacy magazine; this
     * prevents an animation and a needless swap for an equal or worse mag.
     */
    public static boolean canReload(LivingEntity shooter, ItemStack gun) {
        if (!usesPhysicalMagazine(gun)) {
            return false;
        }
        if (!(shooter instanceof Player player)) {
            // NPCs keep the legacy path until their inventory semantics and
            // reload scripts have an explicit physical-magazine implementation.
            return false;
        }
        if (!shouldConsumeMagazine(player)) {
            return true;
        }
        MagazineSelection selection = findBestMagazine(player, gun);
        return selection != null && selection.preview().getItem() instanceof IMagazine magazine
                && magazine.getAmmoCount(selection.preview()) > getEffectiveAmmoCount(gun);
    }

    /**
     * Perform the feed-stage transaction of a reload animation.
     *
     * <p>Extraction happens before the old magazine is returned, so a full
     * inventory can never overwrite a candidate.  Returning the old magazine
     * uses {@link ItemHandlerHelper#giveItemToPlayer}; if no slot is free the
     * exact stack is dropped at the player rather than silently deleted.</p>
     *
     * @param tactical true when the old chamber state is retained
     * @param consumeMagazine true for survival / configurations that consume
     *                        reload resources; false creates a creative full mag
     */
    public static boolean finishReload(LivingEntity shooter, ItemStack gun, boolean tactical, boolean consumeMagazine) {
        if (!usesPhysicalMagazine(gun) || !(shooter instanceof Player player)
                || !(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return false;
        }

        ItemStack incoming;
        if (!consumeMagazine) {
            // Creative/non-consuming reloads get a full, valid magazine without
            // cloning an arbitrary player-owned ItemStack.
            incoming = createMagazine(definition, definition.getMagazineCapacity());
        } else {
            MagazineSelection selection = findBestMagazine(player, gun);
            if (selection == null) {
                return false;
            }
            incoming = extractSelectedMagazine(player, selection, definition);
            if (incoming.isEmpty()) {
                return false;
            }
        }

        ItemStack outgoing = takeInstalledOrMigrateLegacy(gun, definition);
        iGun.setInstalledMagazine(gun, incoming);
        syncLegacyAmmoCount(gun, getMagazineAmmoCount(incoming));

        // In creative mode the old stack is intentionally not copied back into
        // inventory; creative players can create a new magazine at will and the
        // rule avoids a reload-key duplication path.
        if (consumeMagazine && !outgoing.isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, outgoing);
        }

        if (!tactical) {
            chamberRoundAfterEmptyReload(gun);
        }
        player.inventoryMenu.broadcastFullState();
        return true;
    }

    /**
     * Materialise a compatible physical magazine from an old integer ammo count.
     * Used for creative/sample guns; normal survival guns receive their first
     * magazine through the manufacturing chain instead.
     */
    public static boolean migrateLegacyIntoInstalledMagazine(ItemStack gun) {
        if (!usesPhysicalMagazine(gun) || !(gun.getItem() instanceof IGun iGun)
                || hasActiveInstalledMagazine(gun)) {
            return false;
        }
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return false;
        }
        int legacyAmmo = getLegacyAmmoCount(gun);
        if (legacyAmmo <= 0) {
            return false;
        }
        ItemStack magazine = createMagazine(definition, legacyAmmo);
        iGun.setInstalledMagazine(gun, magazine);
        syncLegacyAmmoCount(gun, getMagazineAmmoCount(magazine));
        return true;
    }

    /** Safely eject the inserted magazine while preserving any chambered round. */
    public static boolean ejectMagazine(LivingEntity shooter, ItemStack gun) {
        if (!usesPhysicalMagazine(gun) || !(shooter instanceof Player player)
                || !(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return false;
        }
        ItemStack outgoing = takeInstalledOrMigrateLegacy(gun, definition);
        if (outgoing.isEmpty()) {
            return false;
        }
        iGun.setInstalledMagazine(gun, ItemStack.EMPTY);
        syncLegacyAmmoCount(gun, 0);
        ItemHandlerHelper.giveItemToPlayer(player, outgoing);
        player.inventoryMenu.broadcastFullState();
        return true;
    }

    public static boolean isCompatible(GunFeedDefinition definition, ItemStack magazine) {
        if (!definition.isValidDetachableDefinition() || !(magazine.getItem() instanceof IMagazine item)
                || !item.isConfigured(magazine)) {
            return false;
        }
        return definition.getMagazineFamily().equals(item.getMagazineFamily(magazine))
                && definition.getAmmoId().equals(item.getAmmoId(magazine))
                && item.getCapacity(magazine) <= definition.getMagazineCapacity();
    }

    private static boolean shouldConsumeMagazine(Player player) {
        // Keep the client-side precheck in lockstep with TACZ's existing
        // CreativePlayerConsumeAmmo rule rather than assuming all creative
        // players have free reloads.
        return IGunOperator.fromLivingEntity(player).needCheckAmmo();
    }

    @Nullable
    private static MagazineSelection findBestMagazine(Player player, ItemStack gun) {
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return null;
        }

        /*
         * Do not use LivingEntity#tacz$getItemHandler here.  That capability is
         * server-owned in this port and its LazyOptional has no client value;
         * querying it from LocalPlayerReload produced Optional.of(null) and
         * aborted the client before it could send the reload packet.  The
         * regular player inventory is available and authoritative enough for a
         * client-side precheck, and the same slot order is used by the server
         * transaction below.
         */
        var inventory = player.getInventory();
        MagazineSelection best = null;
        int bestRounds = -1;
        int mainInventorySlots = inventory.getNonEquipmentItems().size();
        for (int slot = 0; slot < mainInventorySlots; slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!isCompatible(definition, candidate) || !(candidate.getItem() instanceof IMagazine magazine)) {
                continue;
            }
            int rounds = magazine.getAmmoCount(candidate);
            if (rounds <= 0) {
                continue;
            }
            // Strictly greater preserves the first inventory slot as the tie
            // breaker. Player main inventory exposes hotbar slots first.
            if (rounds > bestRounds) {
                best = new MagazineSelection(slot, candidate.copy());
                bestRounds = rounds;
            }
        }
        return best;
    }

    private static ItemStack extractSelectedMagazine(Player player, MagazineSelection selection,
                                                      GunFeedDefinition definition) {
        var inventory = player.getInventory();
        // The slot was selected from getNonEquipmentItems(), which is the same
        // index space used by Inventory#getItem/removeItem in 26.2.
        ItemStack extracted = inventory.removeItem(selection.slot(), 1);
        inventory.setChanged();

        // Revalidate after extraction. This is normally redundant on the
        // server thread, but prevents item loss if another inventory operation
        // altered the slot between selection and the feed animation boundary.
        if (isCompatible(definition, extracted)) {
            return extracted;
        }
        if (!extracted.isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, extracted);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Return the actual inserted magazine, or materialise the old integer count
     * as one compatible magazine during first physical interaction.  This is the
     * no-loss migration path for existing worlds.
     */
    private static ItemStack takeInstalledOrMigrateLegacy(ItemStack gun, GunFeedDefinition definition) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return ItemStack.EMPTY;
        }
        ItemStack installed = iGun.getInstalledMagazine(gun);
        if (!installed.isEmpty()) {
            return installed;
        }
        int legacyAmmo = getLegacyAmmoCount(gun);
        return legacyAmmo <= 0 ? ItemStack.EMPTY : createMagazine(definition, legacyAmmo);
    }

    private static void chamberRoundAfterEmptyReload(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return;
        }
        Bolt bolt = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> index.getGunData().getBolt())
                .orElse(null);
        if ((bolt == Bolt.MANUAL_ACTION || bolt == Bolt.CLOSED_BOLT)
                && !iGun.hasBulletInBarrel(gun)
                && removeInstalledRounds(gun, 1) == 1) {
            iGun.setBulletInBarrel(gun, true);
        }
    }

    private static ItemStack createMagazine(GunFeedDefinition definition, int rounds) {
        return MagazineItemBuilder.create()
                .fromDefinition(definition)
                .setAmmoCount(rounds)
                .build();
    }

    private static int getMagazineAmmoCount(ItemStack magazine) {
        return magazine.getItem() instanceof IMagazine item ? item.getAmmoCount(magazine) : 0;
    }

    private static int getEffectiveAmmoCount(ItemStack gun) {
        if (hasActiveInstalledMagazine(gun)) {
            return getInstalledAmmoCount(gun);
        }
        return getLegacyAmmoCount(gun);
    }

    private static int getLegacyAmmoCount(ItemStack gun) {
        return gun.getItem() instanceof GunItemDataAccessor data
                ? data.getLegacyAmmoCount(gun)
                : 0;
    }

    private static void syncLegacyAmmoCount(ItemStack gun, int amount) {
        if (gun.getItem() instanceof GunItemDataAccessor data) {
            data.setLegacyAmmoCount(gun, amount);
        }
    }

    private record MagazineSelection(int slot, ItemStack preview) {
    }
}
