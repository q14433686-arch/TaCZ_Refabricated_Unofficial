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

import java.util.UUID;

/**
 * Physical internal-feed ownership for tube, revolver, internal-box,
 * single-shot and bridge-device weapons. Stripper clips/speedloaders supply
 * rounds into this state but are never installed as replacement magazines.
 *
 * <p>For a gun pack that declares {@code loose_reload_mode = script_loop},
 * this service also bridges the pack's existing Lua feed calls to real source
 * mutations. One press of R can therefore run its native per-round animation,
 * and every visible feed point moves exactly the matching round on the server.
 * No end-of-animation bulk grant is used for that path.</p>
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
            return !definition.getMechanism().usesLoadingDevice()
                    || findBestLoadingDevice(player, definition,
                    definition.getMagazineCapacity() - getAmmoCount(gun)) != null
                    || definition.allowsLooseReload();
        }
        if (definition.getMechanism().usesLoadingDevice()
                && findBestLoadingDevice(player, definition,
                definition.getMagazineCapacity() - getAmmoCount(gun)) != null) {
            return true;
        }
        return definition.allowsLooseReload() && countLooseAmmo(player, definition.getAmmoId()) > 0;
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

        boolean consumesSource = shouldConsumeAmmo(player) && !isInfiniteReload(gun);
        InternalFeedReloadPlan plan;
        if (definition.getMechanism().usesLoadingDevice()) {
            LoadingDeviceSelection selection = findBestLoadingDevice(player, definition, missing);
            if (selection != null) {
                int transfer = Math.min(Math.min(missing, definition.getReloadBatch()), selection.transferableRounds());
                if (transfer <= 0) {
                    return null;
                }
                ItemStack reserved = player.getInventory().getItem(selection.slot());
                ensureLoadingDeviceInstanceId(reserved);
                player.getInventory().setChanged();
                // A bridge clip/speedloader remains a single explicitly
                // selected source. Its normal native animation is a batch
                // transfer; loose per-round behaviour must be separately
                // declared and is never inferred from reload.type.
                plan = new InternalFeedReloadPlan(iGun.getGunId(gun), definition.getAmmoId(), transfer, transfer,
                        transfer, tactical, selection.slot(), reserved.copy(), definition.isFeedDeviceReusable());
            } else {
                if (!definition.allowsLooseReload()) {
                    return null;
                }
                plan = createLooseReloadPlan(iGun.getGunId(gun), player, gun, definition, missing, tactical,
                        consumesSource);
            }
        } else {
            if (!definition.allowsLooseReload()) {
                return null;
            }
            plan = createLooseReloadPlan(iGun.getGunId(gun), player, gun, definition, missing, tactical,
                    consumesSource);
        }
        if (plan == null) {
            return null;
        }
        data.internalFeedReload = plan;
        return plan;
    }

    @Nullable
    private static InternalFeedReloadPlan createLooseReloadPlan(Identifier gunId, Player player, ItemStack gun,
                                                                  GunFeedDefinition definition, int missing,
                                                                  boolean tactical, boolean consumesSource) {
        int animationRounds = definition.usesScriptedLooseReloadLoop()
                ? missing
                : Math.min(missing, definition.getLooseReloadBatch());
        if (animationRounds <= 0) {
            return null;
        }

        int normalSourceLimit = animationRounds;
        // Tube/cylinder scripts normally load a chamber round from the same
        // loose source before their repeated magazine feed calls. The script's
        // target remains "missing magazine rounds", but its real source budget
        // must include that one genuine chambered round.
        if (definition.usesScriptedLooseReloadLoop()) {
            normalSourceLimit += emptyChamberSourceAllowance(gun, tactical);
        }
        int available = consumesSource ? countLooseAmmo(player, definition.getAmmoId()) : normalSourceLimit;
        if (available <= 0) {
            return null;
        }
        int sourceBudget = Math.min(normalSourceLimit, available);
        int fallbackRounds = Math.min(animationRounds, available);
        return new InternalFeedReloadPlan(gunId, definition.getAmmoId(), animationRounds, fallbackRounds,
                sourceBudget, tactical);
    }

    private static int emptyChamberSourceAllowance(ItemStack gun, boolean tactical) {
        if (tactical || !(gun.getItem() instanceof IGun iGun) || iGun.hasBulletInBarrel(gun)) {
            return 0;
        }
        Bolt bolt = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> index.getGunData().getBolt())
                .orElse(null);
        return bolt == Bolt.MANUAL_ACTION || bolt == Bolt.CLOSED_BOLT ? 1 : 0;
    }

    public static boolean isReloadManaged(ShooterDataHolder data, ItemStack gun) {
        InternalFeedReloadPlan plan = data.internalFeedReload;
        return plan != null && gun.getItem() instanceof IGun iGun && plan.getGunId().equals(iGun.getGunId(gun));
    }

    /** Number exposed to an existing Lua script as its normal feed-loop target. */
    public static int getPlannedReloadRounds(ShooterDataHolder data, ItemStack gun) {
        if (!isReloadManaged(data, gun)) {
            return -1;
        }
        return data.internalFeedReload.getAnimationRounds();
    }

    public static void clearReloadPlan(ShooterDataHolder data) {
        data.internalFeedReload = null;
    }

    /**
     * Called from {@link com.tacz.guns.item.ModernKineticGunScriptAPI} when a
     * managed internal-feed Lua script reaches consumeAmmoFromPlayer. The
     * result is a reservation only; the actual source mutation happens at the
     * immediately following put-in-magazine or set-in-barrel feed point.
     *
     * @return {@code -1} when this gun has no active internal-feed plan;
     * otherwise the number safely reserved for the script.
     */
    public static int reserveScriptAmmo(ShooterDataHolder data, LivingEntity shooter, ItemStack gun, int requested) {
        InternalFeedReloadPlan plan = activePlan(data, shooter, gun);
        if (plan == null) {
            return -1;
        }
        if (!shouldConsumeReloadSource(shooter, gun)) {
            // Creative/infinite scripts may still call consumeAmmoFromPlayer
            // before a placement call. Return their available virtual amount,
            // but let that placement claim the budget directly so no stale
            // survival-style credit is left behind.
            plan.markScriptTouched();
            return Math.min(Math.max(0, requested), plan.getAvailableScriptSourceRounds());
        }
        return plan.reserveScriptRounds(requested);
    }

    /**
     * Handles a Lua putAmmoInMagazine call at its real animation feed point.
     *
     * @return {@code -1} when unhandled; otherwise the legacy API's normal
     * overflow value (requested minus actual inserted rounds).
     */
    public static int putScriptAmmoInMagazine(ShooterDataHolder data, LivingEntity shooter, ItemStack gun,
                                               int requested, boolean consumesSource) {
        InternalFeedReloadPlan plan = activePlan(data, shooter, gun);
        if (plan == null) {
            return -1;
        }
        int requestedSafe = Math.max(0, requested);
        boolean actualConsumesSource = consumesSource && !isInfiniteReload(gun);
        int authorized = actualConsumesSource
                ? plan.claimReservedScriptRounds(requestedSafe)
                : plan.claimDirectScriptRounds(requestedSafe);
        int inserted = transferSourceIntoInternalFeed(shooter, gun, plan, authorized, actualConsumesSource);
        return requestedSafe - inserted;
    }

    /**
     * Handles a Lua removeAmmoFromMagazine call that is immediately followed by
     * setAmmoInBarrel(true). The internal round is reserved, not removed until
     * that follow-up call, so a cancelled script cannot silently lose it.
     *
     * @return {@code -1} when unhandled; otherwise the number available for
     * the subsequent chamber operation.
     */
    public static int reserveScriptMagazineRoundsForChamber(ShooterDataHolder data, LivingEntity shooter,
                                                              ItemStack gun, int requested) {
        InternalFeedReloadPlan plan = activePlan(data, shooter, gun);
        if (plan == null) {
            return -1;
        }
        return plan.reserveMagazineRoundsForChamber(requested, getAmmoCount(gun));
    }

    /**
     * Handles a Lua setAmmoInBarrel(true) call. It first consumes a round that
     * a script reserved from the internal feed; otherwise it transfers one
     * directly from its already-reserved loose/clip source.
     *
     * @return {@code true} when the plan handled the request successfully,
     * {@code false} when it handled it but no physical round could be moved,
     * or {@code null} when there is no active internal-feed plan.
     */
    @Nullable
    public static Boolean placeScriptRoundInChamber(ShooterDataHolder data, LivingEntity shooter, ItemStack gun,
                                                     boolean consumesSource) {
        InternalFeedReloadPlan plan = activePlan(data, shooter, gun);
        if (plan == null) {
            return null;
        }
        if (!(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        plan.markScriptTouched();
        if (iGun.hasBulletInBarrel(gun)) {
            return true;
        }
        if (plan.claimMagazineRoundForChamber()) {
            if (removeRounds(gun, 1) != 1) {
                return false;
            }
            iGun.setBulletInBarrel(gun, true);
            broadcastInventory(shooter);
            return true;
        }

        boolean actualConsumesSource = consumesSource && !isInfiniteReload(gun);
        int authorized = actualConsumesSource
                ? plan.claimReservedScriptRounds(1)
                : plan.claimDirectScriptRounds(1);
        int moved = transferSourceIntoChamber(shooter, gun, plan, authorized, actualConsumesSource);
        return moved == 1;
    }

    /**
     * Lets existing loop scripts stop naturally at an empty source or a moved
     * reserved device instead of continuing to animate unbacked cartridges.
     *
     * @return {@code null} when unhandled, otherwise whether one more script
     * feed operation can still be sourced.
     */
    @Nullable
    public static Boolean hasScriptAmmoToConsume(ShooterDataHolder data, LivingEntity shooter, ItemStack gun,
                                                  boolean consumesSource) {
        InternalFeedReloadPlan plan = activePlan(data, shooter, gun);
        if (plan == null) {
            return null;
        }
        if (plan.isSourceFailed() || plan.getAvailableScriptSourceRounds() <= 0) {
            return false;
        }
        boolean actualConsumesSource = consumesSource && !isInfiniteReload(gun);
        if (!actualConsumesSource) {
            return true;
        }
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null || !(shooter instanceof Player player)) {
            return false;
        }
        if (plan.usesFeedDevice()) {
            ItemStack current = player.getInventory().getItem(plan.getFeedDeviceSlot());
            return isReservedLoadingDevice(definition, plan, current)
                    && current.getItem() instanceof MagazineItemDataAccessor device
                    && device.getAmmoCount(current) > 0;
        }
        return countLooseAmmo(player, definition.getAmmoId()) > 0;
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

    /**
     * Default Java reloads do not expose script-side feed calls, so they retain
     * one central transaction at FEEDING -> FINISHING. A Lua script which
     * already touched the plan has transferred its real rounds incrementally;
     * granting another final batch would duplicate ammunition on interruption.
     */
    private static boolean finishReload(LivingEntity shooter, ItemStack gun, InternalFeedReloadPlan plan) {
        if (!(shooter instanceof Player player)) {
            return false;
        }
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null || !definition.getAmmoId().equals(plan.getAmmoId())) {
            return false;
        }
        if (plan.wasScriptTouched() || (definition.usesScriptedLooseReloadLoop() && !plan.usesFeedDevice())) {
            // A declared loose-round loop owns every actual feed point. In
            // particular, an immediate interrupt before its first feed must
            // not be converted into a full end-of-animation batch grant.
            // Device-fed batch animations remain central unless their script
            // actually calls a feed API and marks the plan itself.
            broadcastInventory(shooter);
            return plan.getTransferredSourceRounds() > 0;
        }

        boolean consumesSource = shouldConsumeAmmo(player) && !isInfiniteReload(gun);
        int inserted = transferSourceIntoInternalFeed(shooter, gun, plan, plan.getFallbackRounds(), consumesSource);
        if (inserted <= 0) {
            return false;
        }
        if (!plan.isTactical()) {
            chamberRoundAfterEmptyReload(gun);
        }
        broadcastInventory(shooter);
        return true;
    }

    private static int transferSourceIntoInternalFeed(LivingEntity shooter, ItemStack gun,
                                                       InternalFeedReloadPlan plan, int requested,
                                                       boolean consumesSource) {
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null || requested <= 0) {
            return 0;
        }
        int free = Math.max(0, definition.getMagazineCapacity() - getAmmoCount(gun));
        int wanted = Math.min(requested, free);
        if (wanted <= 0) {
            return 0;
        }
        int moved = drawFromPlanSource(shooter, gun, definition, plan, wanted, consumesSource);
        if (moved <= 0) {
            return 0;
        }
        setAmmoCount(gun, getAmmoCount(gun) + moved);
        plan.recordSourceTransfer(moved);
        broadcastInventory(shooter);
        return moved;
    }

    private static int transferSourceIntoChamber(LivingEntity shooter, ItemStack gun,
                                                  InternalFeedReloadPlan plan, int requested,
                                                  boolean consumesSource) {
        if (!(gun.getItem() instanceof IGun iGun) || requested <= 0 || iGun.hasBulletInBarrel(gun)) {
            return 0;
        }
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return 0;
        }
        int moved = drawFromPlanSource(shooter, gun, definition, plan, 1, consumesSource);
        if (moved <= 0) {
            return 0;
        }
        plan.recordSourceTransfer(moved);
        iGun.setBulletInBarrel(gun, true);
        broadcastInventory(shooter);
        return moved;
    }

    private static int drawFromPlanSource(LivingEntity shooter, ItemStack gun, GunFeedDefinition definition,
                                          InternalFeedReloadPlan plan, int requested, boolean consumesSource) {
        if (requested <= 0) {
            return 0;
        }
        if (!consumesSource) {
            // Creative/infinite reloads never decrement the selected source,
            // but a bridge device must still remain in its reserved slot. A
            // moved clip cannot become a free virtual source mid-animation.
            if (plan.usesFeedDevice()) {
                if (!(shooter instanceof Player player)
                        || !isReservedLoadingDevice(definition, plan,
                        player.getInventory().getItem(plan.getFeedDeviceSlot()))) {
                    plan.markSourceFailed();
                    return 0;
                }
            }
            return requested;
        }
        if (!(shooter instanceof Player player)) {
            plan.markSourceFailed();
            return 0;
        }
        if (plan.usesFeedDevice()) {
            return extractLoadingDeviceRounds(player, definition, plan, requested);
        }
        int extracted = extractLooseAmmo(player, definition.getAmmoId(), requested);
        if (extracted < requested) {
            plan.markSourceFailed();
        }
        return extracted;
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

    /**
     * Choose the clip/speedloader by actual useful transfer, not by comparing
     * its remaining rounds against the gun's current internal count. A 5-round
     * bridge clip is valuable to a 7/10 fixed magazine because it can still
     * transfer three rounds; the detachable-magazine "only replace with a
     * better mag" rule is intentionally not used here.
     */
    @Nullable
    private static LoadingDeviceSelection findBestLoadingDevice(Player player, GunFeedDefinition definition, int missing) {
        int bestTransfer = 0;
        LoadingDeviceSelection best = null;
        var inventory = player.getInventory();
        int slots = inventory.getNonEquipmentItems().size();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!isCompatibleLoadingDevice(definition, candidate) || !(candidate.getItem() instanceof IMagazine device)) {
                continue;
            }
            int transferable = Math.min(Math.min(device.getAmmoCount(candidate), Math.max(0, missing)),
                    definition.getReloadBatch());
            if (transferable > bestTransfer) {
                bestTransfer = transferable;
                best = new LoadingDeviceSelection(slot, candidate.copy(), transferable);
            }
        }
        return best;
    }

    private static boolean isCompatibleLoadingDevice(GunFeedDefinition definition, ItemStack stack) {
        if (!definition.isValidLoadingDeviceDefinition() || !(stack.getItem() instanceof MagazineItemDataAccessor device)
                || !device.isConfigured(stack)) {
            return false;
        }
        return definition.getMechanism().serializedName().equals(device.getFeedDeviceKind(stack))
                && definition.getMagazineFamily().equals(device.getMagazineFamily(stack))
                && definition.getAmmoId().equals(device.getAmmoId(stack))
                && definition.getFeedDeviceCapacity() == device.getCapacity(stack)
                && device.getAmmoCount(stack) > 0;
    }

    /**
     * Checks immutable device configuration plus its stable per-stack id. The
     * mutable AmmoCount is intentionally excluded so a script loop may use the
     * same partially drained clip on its next real feed event.
     */
    private static boolean isReservedLoadingDevice(GunFeedDefinition definition, InternalFeedReloadPlan plan,
                                                   ItemStack current) {
        ItemStack expectedStack = plan.getExpectedFeedDevice();
        if (!isCompatibleLoadingDevice(definition, current)
                || !(current.getItem() instanceof MagazineItemDataAccessor actual)
                || !(expectedStack.getItem() instanceof MagazineItemDataAccessor expected)) {
            return false;
        }
        return current.getItem() == expectedStack.getItem()
                && actual.getFeedDeviceInstanceId(current).equals(expected.getFeedDeviceInstanceId(expectedStack))
                && actual.getMagazineFamily(current).equals(expected.getMagazineFamily(expectedStack))
                && actual.getAmmoId(current).equals(expected.getAmmoId(expectedStack))
                && actual.getCapacity(current) == expected.getCapacity(expectedStack)
                && actual.getFeedDeviceKind(current).equals(expected.getFeedDeviceKind(expectedStack))
                && actual.getDisplayNameKey(current).equals(expected.getDisplayNameKey(expectedStack));
    }

    private static void ensureLoadingDeviceInstanceId(ItemStack stack) {
        if (stack.getItem() instanceof MagazineItemDataAccessor device) {
            // Refresh at reservation start. Recipe outputs can legitimately be
            // copies of one static result stack, so an id created only while
            // crafting is not a sufficient per-transaction identity.
            device.setFeedDeviceInstanceId(stack, UUID.randomUUID().toString());
        }
    }

    private static int extractLoadingDeviceRounds(Player player, GunFeedDefinition definition,
                                                  InternalFeedReloadPlan plan, int requested) {
        if (!plan.usesFeedDevice() || plan.getFeedDeviceSlot() < 0) {
            plan.markSourceFailed();
            return 0;
        }
        var inventory = player.getInventory();
        ItemStack current = inventory.getItem(plan.getFeedDeviceSlot());
        if (!isReservedLoadingDevice(definition, plan, current)
                || !(current.getItem() instanceof MagazineItemDataAccessor device)) {
            // A different stack must never be substituted during the reload
            // animation: bridge clips are physical partly-filled items.
            plan.markSourceFailed();
            return 0;
        }
        int transferred = Math.min(Math.max(0, requested), device.getAmmoCount(current));
        if (transferred <= 0) {
            plan.markSourceFailed();
            return 0;
        }
        device.setAmmoCount(current, device.getAmmoCount(current) - transferred);
        if (device.getAmmoCount(current) <= 0 && !plan.keepEmptyFeedDevice()) {
            inventory.setItem(plan.getFeedDeviceSlot(), ItemStack.EMPTY);
        }
        inventory.setChanged();
        return transferred;
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

    @Nullable
    private static InternalFeedReloadPlan activePlan(ShooterDataHolder data, LivingEntity shooter, ItemStack gun) {
        return data != null && shooter instanceof Player && isReloadManaged(data, gun) ? data.internalFeedReload : null;
    }

    private static void broadcastInventory(LivingEntity shooter) {
        if (shooter instanceof Player player) {
            player.inventoryMenu.broadcastFullState();
        }
    }

    private static boolean shouldConsumeAmmo(Player player) {
        return IGunOperator.fromLivingEntity(player).needCheckAmmo();
    }

    private static boolean shouldConsumeReloadSource(LivingEntity shooter, ItemStack gun) {
        return shooter instanceof Player player && shouldConsumeAmmo(player) && !isInfiniteReload(gun);
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

    private record LoadingDeviceSelection(int slot, ItemStack preview, int transferableRounds) {
    }
}
