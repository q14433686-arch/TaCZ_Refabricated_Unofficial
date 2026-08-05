package com.tacz.guns.industry.magazine;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Physical internal-feed ownership for tube, revolver, internal-box and
 * single-shot weapons. These mechanisms intentionally do not pretend an
 * internal tube/cylinder is a detachable magazine item.
 */
public final class InternalFeedService {
    public static final String INTERNAL_FEED_AMMO_ID = "InternalFeedAmmoId";
    public static final String INTERNAL_FEED_CAPACITY = "InternalFeedCapacity";
    public static final String INTERNAL_FEED_COUNT = "InternalFeedAmmoCount";
    public static final String INTERNAL_FEED_MECHANISM = "InternalFeedMechanism";

    private InternalFeedService() {
    }

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
        GunFeedDefinition definition = CommonAssetsManager.get().getGunFeedDefinition(iGun.getGunId(gun));
        return definition != null && definition.isValidInternalDefinition() ? definition : null;
    }

    public static boolean usesInternalFeed(ItemStack gun) {
        return isFeatureEnabled() && getDefinition(gun) != null;
    }

    public static int getAmmoCount(ItemStack gun) {
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return legacyAmmo(gun);
        }
        CompoundTag tag = ItemNbtUtils.getTag(gun);
        if (!tag.contains(INTERNAL_FEED_COUNT)) {
            return Math.min(definition.getMagazineCapacity(), legacyAmmo(gun));
        }
        return Math.clamp(tag.getIntOr(INTERNAL_FEED_COUNT, 0), 0, definition.getMagazineCapacity());
    }

    public static void setAmmoCount(ItemStack gun, int count) {
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return;
        }
        int clamped = Math.clamp(count, 0, definition.getMagazineCapacity());
        ItemNbtUtils.updateTag(gun, tag -> {
            tag.putString(INTERNAL_FEED_AMMO_ID, definition.getAmmoId().toString());
            tag.putInt(INTERNAL_FEED_CAPACITY, definition.getMagazineCapacity());
            tag.putInt(INTERNAL_FEED_COUNT, clamped);
            tag.putString(INTERNAL_FEED_MECHANISM, definition.getMechanism().name());
        });
        syncLegacy(gun, clamped);
    }

    public static int removeRounds(ItemStack gun, int amount) {
        int current = getAmmoCount(gun);
        int removed = Math.min(Math.max(0, amount), current);
        if (removed > 0) {
            setAmmoCount(gun, current - removed);
        }
        return removed;
    }

    public static boolean canReload(LivingEntity shooter, ItemStack gun) {
        if (!usesInternalFeed(gun) || !(shooter instanceof Player player)) {
            return false;
        }
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null || getAmmoCount(gun) >= definition.getMagazineCapacity()) {
            return false;
        }
        if (!shouldConsumeAmmo(player) || isInfiniteReload(gun)) {
            return true;
        }
        return countLooseAmmo(player, definition.getAmmoId()) > 0;
    }

    @Nullable
    public static InternalFeedReloadPlan beginReload(ShooterDataHolder data, LivingEntity shooter,
                                                      ItemStack gun, boolean tactical) {
        if (!usesInternalFeed(gun) || !(shooter instanceof Player player) || !(gun.getItem() instanceof IGun iGun)) {
            return null;
        }
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return null;
        }
        int missing = definition.getMagazineCapacity() - getAmmoCount(gun);
        if (missing <= 0) {
            return null;
        }
        int planned = Math.min(missing, definition.getReloadBatch());
        if (shouldConsumeAmmo(player) && !isInfiniteReload(gun)) {
            planned = Math.min(planned, countLooseAmmo(player, definition.getAmmoId()));
        }
        if (planned <= 0) {
            return null;
        }
        InternalFeedReloadPlan plan = new InternalFeedReloadPlan(iGun.getGunId(gun), definition.getAmmoId(), planned, tactical);
        data.internalFeedReload = plan;
        return plan;
    }

    public static boolean isReloadManaged(ShooterDataHolder data, ItemStack gun) {
        InternalFeedReloadPlan plan = data.internalFeedReload;
        return plan != null && gun.getItem() instanceof IGun iGun && plan.getGunId().equals(iGun.getGunId(gun));
    }

    /** Number of real loose rounds reserved for the current scripted reload cycle. */
    public static int getPlannedReloadRounds(ShooterDataHolder data, ItemStack gun) {
        if (!isReloadManaged(data, gun)) {
            return -1;
        }
        return Math.max(0, data.internalFeedReload.getRounds());
    }

    public static void clearReloadPlan(ShooterDataHolder data) {
        data.internalFeedReload = null;
    }

    public static void onReloadStateTransition(ShooterDataHolder data, LivingEntity shooter, ItemStack gun,
                                               ReloadState.StateType previous, ReloadState.StateType next) {
        InternalFeedReloadPlan plan = data.internalFeedReload;
        if (plan == null) {
            return;
        }
        if (!isReloadManaged(data, gun)) {
            clearReloadPlan(data);
            return;
        }
        boolean enteringFinishing = !previous.isReloadFinishing() && next.isReloadFinishing();
        if (!plan.isFeedHandled() && enteringFinishing) {
            plan.markFeedHandled();
            finishReload(shooter, gun, plan);
        }
        if (!next.isReloading()) {
            clearReloadPlan(data);
        }
    }

    private static boolean finishReload(LivingEntity shooter, ItemStack gun, InternalFeedReloadPlan plan) {
        if (!(shooter instanceof Player player)) {
            return false;
        }
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null || !definition.getAmmoId().equals(plan.getAmmoId())) {
            return false;
        }
        int inserted = plan.getRounds();
        if (shouldConsumeAmmo(player) && !isInfiniteReload(gun)) {
            inserted = extractLooseAmmo(player, definition.getAmmoId(), inserted);
        }
        if (inserted <= 0) {
            return false;
        }
        int before = getAmmoCount(gun);
        setAmmoCount(gun, before + inserted);
        if (!plan.isTactical()) {
            chamberRoundAfterEmptyReload(gun);
        }
        player.inventoryMenu.broadcastFullState();
        return true;
    }

    private static void chamberRoundAfterEmptyReload(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return;
        }
        Bolt bolt = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> index.getGunData().getBolt())
                .orElse(null);
        if ((bolt == Bolt.MANUAL_ACTION || bolt == Bolt.CLOSED_BOLT)
                && !iGun.hasBulletInBarrel(gun)
                && removeRounds(gun, 1) == 1) {
            iGun.setBulletInBarrel(gun, true);
        }
    }

    private static int countLooseAmmo(Player player, Identifier ammoId) {
        int count = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof IAmmo ammo && ammoId.equals(ammo.getAmmoId(stack))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int extractLooseAmmo(Player player, Identifier ammoId, int amount) {
        int remaining = amount;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof IAmmo ammo) || !ammoId.equals(ammo.getAmmoId(stack))) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            remaining -= take;
        }
        inventory.setChanged();
        return amount - remaining;
    }

    private static boolean shouldConsumeAmmo(Player player) {
        return IGunOperator.fromLivingEntity(player).needCheckAmmo();
    }

    private static boolean isInfiniteReload(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        return TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> index.getGunData().getReloadData().isInfinite())
                .orElse(false);
    }

    private static int legacyAmmo(ItemStack gun) {
        return gun.getItem() instanceof GunItemDataAccessor data ? data.getLegacyAmmoCount(gun) : 0;
    }

    private static void syncLegacy(ItemStack gun, int count) {
        if (gun.getItem() instanceof GunItemDataAccessor data) {
            data.setLegacyAmmoCount(gun, count);
        }
    }
}
