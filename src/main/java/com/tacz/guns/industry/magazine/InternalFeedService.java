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
import com.tacz.guns.industry.ammo.RoundProfileService;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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
    /** Ordered bottom-to-top AmmoIds; the final entry is next to feed/fire. */
    public static final String INTERNAL_FEED_ROUNDS = "InternalFeedRounds";
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
        // En-bloc clips use InstalledEnBlocClip and EnBlocClipService, never
        // InternalFeedAmmoCount. Keep this guard alongside the definition
        // validator so a future data-validator broadening cannot make a gun
        // start two competing reload transactions again.
        return definition != null && !definition.getMechanism().usesEnBlocClip()
                && definition.isValidInternalDefinition() ? definition : null;
    }

    public static boolean usesInternalFeed(ItemStack gun) {
        return isFeatureEnabled() && getDefinition(gun) != null;
    }

    public static int getAmmoCount(ItemStack gun) {
        return getDefinition(gun) == null ? legacyAmmo(gun) : getRoundAmmoIds(gun).size();
    }

    /** Ordered internal rounds from bottom to top, with lossless legacy projection. */
    public static List<Identifier> getRoundAmmoIds(ItemStack gun) {
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return List.of();
        }
        CompoundTag tag = ItemNbtUtils.getTag(gun);
        int capacity = definition.getMagazineCapacity();
        if (!tag.contains(INTERNAL_FEED_ROUNDS)) {
            int legacyCount = tag.contains(INTERNAL_FEED_COUNT)
                    ? tag.getIntOr(INTERNAL_FEED_COUNT, 0) : legacyAmmo(gun);
            return repeated(definition.getAmmoId(), Math.clamp(legacyCount, 0, capacity));
        }
        List<Identifier> rounds = new ArrayList<>();
        for (Tag entry : tag.getListOrEmpty(INTERNAL_FEED_ROUNDS)) {
            if (entry instanceof StringTag stringTag) {
                Identifier ammoId = Identifier.tryParse(stringTag.getAsString());
                if (ammoId != null && !com.tacz.guns.api.DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                    rounds.add(ammoId);
                    if (rounds.size() >= capacity) {
                        break;
                    }
                }
            }
        }
        int legacyCount = Math.clamp(tag.getIntOr(INTERNAL_FEED_COUNT, 0), 0, capacity);
        while (rounds.size() < legacyCount) {
            rounds.add(definition.getAmmoId());
        }
        return List.copyOf(rounds);
    }

    public static Identifier getNextRoundAmmoId(ItemStack gun) {
        List<Identifier> rounds = getRoundAmmoIds(gun);
        return rounds.isEmpty() ? com.tacz.guns.api.DefaultAssets.EMPTY_AMMO_ID : rounds.getLast();
    }

    /** Compatibility bridge: grow with base rounds and shrink from the top. */
    public static void setAmmoCount(ItemStack gun, int count) {
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return;
        }
        int clamped = Math.clamp(count, 0, definition.getMagazineCapacity());
        List<Identifier> rounds = new ArrayList<>(getRoundAmmoIds(gun));
        while (rounds.size() > clamped) {
            rounds.removeLast();
        }
        while (rounds.size() < clamped) {
            rounds.add(definition.getAmmoId());
        }
        writeRounds(gun, definition, rounds);
    }

    /** Append source profiles in the supplied next-feed order. */
    public static int appendRoundsPreservingFeedOrder(ItemStack gun, List<Identifier> sourceNextFirst) {
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null || sourceNextFirst == null || sourceNextFirst.isEmpty()) {
            return 0;
        }
        List<Identifier> rounds = new ArrayList<>(getRoundAmmoIds(gun));
        int free = Math.max(0, definition.getMagazineCapacity() - rounds.size());
        int accepted = Math.min(free, sourceNextFirst.size());
        // The destination's final list element feeds first. Source order is
        // top-first, so append the accepted source range in reverse.
        for (int index = accepted - 1; index >= 0; index--) {
            Identifier ammoId = sourceNextFirst.get(index);
            if (ammoId != null && !com.tacz.guns.api.DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                rounds.add(ammoId);
            }
        }
        writeRounds(gun, definition, rounds);
        return accepted;
    }

    public static Identifier takeNextRound(ItemStack gun) {
        return takeRound(gun, true);
    }

    /** See MagazineItemDataAccessor#popOldestRound for closed-bolt order preservation. */
    public static Identifier takeOldestRound(ItemStack gun) {
        return takeRound(gun, false);
    }

    private static Identifier takeRound(ItemStack gun, boolean top) {
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return com.tacz.guns.api.DefaultAssets.EMPTY_AMMO_ID;
        }
        List<Identifier> rounds = new ArrayList<>(getRoundAmmoIds(gun));
        if (rounds.isEmpty()) {
            return com.tacz.guns.api.DefaultAssets.EMPTY_AMMO_ID;
        }
        Identifier round = top ? rounds.removeLast() : rounds.removeFirst();
        writeRounds(gun, definition, rounds);
        return round;
    }

    public static int removeRounds(ItemStack gun, int amount) {
        int removed = 0;
        for (int index = 0; index < Math.max(0, amount); index++) {
            if (takeNextRound(gun).equals(com.tacz.guns.api.DefaultAssets.EMPTY_AMMO_ID)) {
                break;
            }
            removed++;
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
        int missing = definition.getMagazineCapacity() - getAmmoCount(gun);
        boolean consumesSource = shouldConsumeAmmo(player) && !isInfiniteReload(gun);
        if (definition.hasReloadRoutes()) {
            return selectReloadRoute(player, gun, definition, missing, isTacticalReload(gun), consumesSource) != null;
        }
        if (!consumesSource) {
            // Creative/infinite reloads still need a real native reload action,
            // but do not need a physical source stack. In particular, a
            // stripper-clip profile such as the Type 56 may deliberately have
            // no loose-round route because its pack only supplies a batch
            // reload animation. The matching virtual source is materialised in
            // beginReload; requiring a creative player to own a clip here made
            // that valid animation path impossible to start.
            return true;
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
        RouteSelection explicitRoute = definition.hasReloadRoutes()
                ? selectReloadRoute(player, gun, definition, missing, tactical, consumesSource) : null;
        if (explicitRoute != null) {
            plan = createRouteReloadPlan(iGun.getGunId(gun), player, gun, definition, missing, tactical,
                    consumesSource, explicitRoute);
        } else if (definition.hasReloadRoutes()) {
            // A route-aware profile must not silently fall through to the old
            // generic device preference. That would let a partly-filled clip
            // choose a five-round bridge animation it cannot honestly supply.
            return null;
        } else if (definition.getMechanism().usesLoadingDevice()) {
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
            } else if (!consumesSource) {
                // No real source is decremented in Creative/infinite mode. Do
                // not invent an inventory clip, but reserve the same batch that
                // one physical device would transfer so a pack's native batch
                // reload timing remains authoritative. This is essential for
                // audited batch-only clip guns (for example rainforest:56),
                // whose honest profile intentionally has no loose-round path.
                int transfer = Math.min(missing, definition.getReloadBatch());
                if (transfer <= 0) {
                    return null;
                }
                plan = new InternalFeedReloadPlan(iGun.getGunId(gun), definition.getAmmoId(), transfer, transfer,
                        transfer, tactical);
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
        data.industryReloadRoute = plan.getReloadRouteId().isBlank() ? ""
                : plan.getGunId() + "|" + plan.getReloadRouteId();
        return plan;
    }

    /**
     * Client-side prediction uses the same data-only resolver before it
     * triggers the local reload animation. The server always resolves again
     * and owns the actual reservation/transaction.
     */
    public static ReloadRoutePreview previewReloadRoute(Player player, ItemStack gun) {
        if (!usesInternalFeed(gun)) {
            return ReloadRoutePreview.EMPTY;
        }
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null || !definition.hasReloadRoutes()) {
            return ReloadRoutePreview.EMPTY;
        }
        int missing = definition.getMagazineCapacity() - getAmmoCount(gun);
        if (missing <= 0) {
            return ReloadRoutePreview.EMPTY;
        }
        boolean consumesSource = shouldConsumeAmmo(player) && !isInfiniteReload(gun);
        RouteSelection selection = selectReloadRoute(player, gun, definition, missing, isTacticalReload(gun), consumesSource);
        return selection == null ? ReloadRoutePreview.EMPTY
                : new ReloadRoutePreview(selection.route().getId(),
                selection.route().getAnimationForceAttachmentPresent(),
                selection.route().getAnimationForceMagExtentLevel());
    }

    /** Route data needed by the client to mirror an audited animation selector. */
    public static ReloadRoutePreview getReloadRoutePreview(ItemStack gun, String routeId) {
        GunFeedDefinition definition = getDefinition(gun);
        GunReloadRoute route = definition == null ? null : definition.getReloadRoute(routeId);
        return route == null ? ReloadRoutePreview.EMPTY
                : new ReloadRoutePreview(route.getId(), route.getAnimationForceAttachmentPresent(),
                route.getAnimationForceMagExtentLevel());
    }

    /**
     * Client-safe source check for an active/predicted route. It is visual
     * gating only; the server plan still binds and validates the actual source.
     */
    @Nullable
    public static Boolean hasReloadRouteSource(Player player, ItemStack gun, String routeId) {
        GunFeedDefinition definition = getDefinition(gun);
        GunReloadRoute route = definition == null ? null : definition.getReloadRoute(routeId);
        if (route == null) {
            return null;
        }
        int missing = definition.getMagazineCapacity() - getAmmoCount(gun);
        boolean consumesSource = shouldConsumeAmmo(player) && !isInfiniteReload(gun);
        if (missing <= 0 || !route.matchesMissingRounds(missing)
                || !route.matchesTactical(isTacticalReload(gun)) || !matchesRouteAttachments(gun, route)) {
            return false;
        }
        if (route.getSource() == ReloadRouteSource.LOADING_DEVICE) {
            return findBestLoadingDevice(player, definition, missing,
                    route.getMaximumTransferRounds(definition.getReloadBatch()),
                    route.getMinimumSourceRounds()) != null;
        }
        return !consumesSource || countLooseAmmo(player, definition.getAmmoId()) > 0;
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
                sourceBudget, tactical, "", definition.usesScriptedLooseReloadLoop(), "",
                -1, ItemStack.EMPTY, true);
    }

    /**
     * Resolve ordered audited routes. Route order is semantic: a valid full
     * physical clip may win before loose rounds, while a partially filled clip
     * that cannot match its batch animation naturally falls through to the
     * loose-loop branch.
     */
    @Nullable
    private static RouteSelection selectReloadRoute(Player player, ItemStack gun, GunFeedDefinition definition,
                                                    int missing, boolean tactical, boolean consumesSource) {
        for (GunReloadRoute route : definition.getReloadRoutes()) {
            if (!route.matchesMissingRounds(missing) || !route.matchesTactical(tactical)
                    || !matchesRouteAttachments(gun, route)) {
                continue;
            }
            if (route.getSource() == ReloadRouteSource.LOADING_DEVICE) {
                if (!definition.isValidLoadingDeviceDefinition()) {
                    continue;
                }
                LoadingDeviceSelection device = findBestLoadingDevice(player, definition, missing,
                        route.getMaximumTransferRounds(definition.getReloadBatch()), route.getMinimumSourceRounds());
                if (device != null) {
                    return new RouteSelection(route, device);
                }
                continue;
            }
            int sourceLimit = route.isScriptDriven()
                    ? missing + emptyChamberSourceAllowance(gun, tactical) + route.getExtraSourceRounds()
                    : Math.min(missing, route.getMaximumTransferRounds(definition.getLooseReloadBatch()));
            if (sourceLimit <= 0) {
                continue;
            }
            if (!consumesSource || countLooseAmmo(player, definition.getAmmoId()) > 0) {
                return new RouteSelection(route, null);
            }
        }
        return null;
    }

    @Nullable
    private static InternalFeedReloadPlan createRouteReloadPlan(Identifier gunId, Player player, ItemStack gun,
                                                                 GunFeedDefinition definition, int missing,
                                                                 boolean tactical, boolean consumesSource,
                                                                 RouteSelection selection) {
        GunReloadRoute route = selection.route();
        if (route.getSource() == ReloadRouteSource.LOADING_DEVICE) {
            LoadingDeviceSelection device = selection.device();
            if (device == null) {
                return null;
            }
            int animationRounds = route.getForcedAnimationRounds() > 0
                    ? route.getForcedAnimationRounds()
                    : Math.min(missing, route.getMaximumTransferRounds(definition.getReloadBatch()));
            int sourceBudget = animationRounds + route.getExtraSourceRounds();
            ItemStack reserved = player.getInventory().getItem(device.slot());
            if (!(reserved.getItem() instanceof IMagazine magazine)
                    || magazine.getAmmoCount(reserved) < sourceBudget) {
                return null;
            }
            ensureLoadingDeviceInstanceId(reserved);
            player.getInventory().setChanged();
            return new InternalFeedReloadPlan(gunId, definition.getAmmoId(), animationRounds, animationRounds,
                    sourceBudget, tactical, route.getId(), route.isScriptDriven(),
                    route.getAnimationForceAttachmentPresent(), route.getAnimationForceMagExtentLevel(),
                    route.getScriptRemovalMode(), device.slot(), reserved.copy(),
                    definition.isFeedDeviceReusable());
        }

        int animationRounds = route.getForcedAnimationRounds() > 0
                ? route.getForcedAnimationRounds()
                : route.isScriptDriven() ? missing
                : Math.min(missing, route.getMaximumTransferRounds(definition.getLooseReloadBatch()));
        if (animationRounds <= 0) {
            return null;
        }
        int sourceLimit = animationRounds + route.getExtraSourceRounds();
        if (route.isScriptDriven()) {
            sourceLimit += emptyChamberSourceAllowance(gun, tactical);
        }
        int available = consumesSource ? countLooseAmmo(player, definition.getAmmoId()) : sourceLimit;
        if (available <= 0) {
            return null;
        }
        int sourceBudget = Math.min(sourceLimit, available);
        int fallbackRounds = Math.min(animationRounds, available);
        return new InternalFeedReloadPlan(gunId, definition.getAmmoId(), animationRounds, fallbackRounds,
                sourceBudget, tactical, route.getId(), route.isScriptDriven(),
                route.getAnimationForceAttachmentPresent(), route.getAnimationForceMagExtentLevel(),
                route.getScriptRemovalMode(), -1, ItemStack.EMPTY, true);
    }

    private static boolean matchesRouteAttachments(ItemStack gun, GunReloadRoute route) {
        String requiredEmpty = route.getRequiredAttachmentEmpty();
        if (!requiredEmpty.isBlank() && !isAttachmentEmpty(gun, requiredEmpty)) {
            return false;
        }
        String requiredPresent = route.getRequiredAttachmentPresent();
        return requiredPresent.isBlank() || !isAttachmentEmpty(gun, requiredPresent);
    }

    private static boolean isAttachmentEmpty(ItemStack gun, String attachmentName) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return true;
        }
        try {
            var type = com.tacz.guns.api.item.attachment.AttachmentType.valueOf(
                    attachmentName.toUpperCase(java.util.Locale.ROOT));
            return com.tacz.guns.api.DefaultAssets.EMPTY_ATTACHMENT_ID.equals(iGun.getAttachmentId(gun, type));
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    private static boolean isTacticalReload(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        Bolt bolt = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> index.getGunData().getBolt()).orElse(null);
        int available = iGun.getCurrentAmmoCount(gun)
                + (iGun.hasBulletInBarrel(gun) && bolt != Bolt.OPEN_BOLT ? 1 : 0);
        return available > 0;
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
        if (data == null) {
            return false;
        }
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
        data.industryReloadRoute = "";
    }

    /** True only while the active server plan asks a legacy script to treat one attachment slot as occupied. */
    public static boolean forcesScriptAttachmentPresent(ShooterDataHolder data, ItemStack gun, String attachmentType) {
        return data != null && isReloadManaged(data, gun)
                && data.internalFeedReload.forcesAttachmentPresent(attachmentType)
                && isAttachmentEmpty(gun, attachmentType);
    }

    /** Returns -1 when no audited route overrides a legacy extended-mag selector. */
    public static int getScriptForcedMagExtentLevel(ShooterDataHolder data, ItemStack gun) {
        return data != null && isReloadManaged(data, gun)
                ? data.internalFeedReload.getAnimationForceMagExtentLevel() : -1;
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
        if (plan.getScriptRemovalMode() == ReloadRouteScriptRemovalMode.DISCARD) {
            // Verified speedloader scripts first empty the cylinder. Preserve
            // that declared semantics explicitly instead of pretending every
            // remove call is a chamber transfer.
            plan.markScriptTouched();
            return removeRounds(gun, requested);
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
            Identifier chambered = takeNextRound(gun);
            if (chambered.equals(com.tacz.guns.api.DefaultAssets.EMPTY_AMMO_ID)) {
                return false;
            }
            RoundProfileService.setChamberAmmoId(gun, chambered);
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
        if (plan.wasScriptTouched() || plan.isScriptDriven()) {
            // An audited script-driven branch owns every real feed point. In
            // particular, an immediate interrupt before its first feed must
            // not be converted into a full end-of-animation batch grant.
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
        List<Identifier> profiles = plan.consumeExtractedSourceProfiles(moved);
        int appended = appendRoundsPreservingFeedOrder(gun, profiles);
        if (appended != moved) {
            // The capacity was checked before extraction; a mismatch here is a
            // fail-closed guard against concurrent state edits, never a reason
            // to silently discard a physical source profile.
            plan.markSourceFailed();
            return 0;
        }
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
        Identifier chambered = plan.consumeExtractedSourceProfiles(moved).getFirst();
        plan.recordSourceTransfer(moved);
        RoundProfileService.setChamberAmmoId(gun, chambered);
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
                && !iGun.hasBulletInBarrel(gun)) {
            Identifier chambered = bolt == Bolt.CLOSED_BOLT
                    ? takeOldestRound(gun) : takeNextRound(gun);
            if (!chambered.equals(com.tacz.guns.api.DefaultAssets.EMPTY_AMMO_ID)) {
                RoundProfileService.setChamberAmmoId(gun, chambered);
                iGun.setBulletInBarrel(gun, true);
            }
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
        return findBestLoadingDevice(player, definition, missing, definition.getReloadBatch(), 1);
    }

    @Nullable
    private static LoadingDeviceSelection findBestLoadingDevice(Player player, GunFeedDefinition definition,
                                                                int missing, int maximumTransfer,
                                                                int minimumSourceRounds) {
        int bestTransfer = 0;
        LoadingDeviceSelection best = null;
        var inventory = player.getInventory();
        int slots = inventory.getNonEquipmentItems().size();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!isCompatibleLoadingDevice(definition, candidate) || !(candidate.getItem() instanceof IMagazine device)) {
                continue;
            }
            int available = device.getAmmoCount(candidate);
            if (available < Math.max(1, minimumSourceRounds)) {
                continue;
            }
            int transferable = Math.min(Math.min(available, Math.max(0, missing)), Math.max(1, maximumTransfer));
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
        int requestedSafe = Math.min(Math.max(0, requested), device.getAmmoCount(current));
        if (requestedSafe <= 0) {
            plan.markSourceFailed();
            return 0;
        }
        List<Identifier> extractedProfiles = new ArrayList<>(requestedSafe);
        for (int index = 0; index < requestedSafe; index++) {
            Identifier profile = device.popNextRound(current);
            if (profile.equals(com.tacz.guns.api.DefaultAssets.EMPTY_AMMO_ID)) {
                plan.markSourceFailed();
                break;
            }
            extractedProfiles.add(profile);
        }
        if (extractedProfiles.isEmpty()) {
            return 0;
        }
        plan.recordExtractedSourceProfiles(extractedProfiles);
        // Bridge clips and speedloaders are reusable loading tools. Current
        // plans always keep the now-empty physical ItemStack; the conditional
        // remains only for old non-device plan compatibility.
        if (device.getAmmoCount(current) <= 0 && !plan.keepEmptyFeedDevice()) {
            inventory.setItem(plan.getFeedDeviceSlot(), ItemStack.EMPTY);
        }
        inventory.setChanged();
        return extractedProfiles.size();
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

    private static List<Identifier> repeated(Identifier ammoId, int count) {
        if (count <= 0 || ammoId == null || com.tacz.guns.api.DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
            return List.of();
        }
        List<Identifier> rounds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rounds.add(ammoId);
        }
        return List.copyOf(rounds);
    }

    private static void writeRounds(ItemStack gun, GunFeedDefinition definition, List<Identifier> requested) {
        int capacity = definition.getMagazineCapacity();
        List<Identifier> safe = new ArrayList<>();
        if (requested != null) {
            for (Identifier ammoId : requested) {
                if (ammoId != null && !com.tacz.guns.api.DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                    safe.add(ammoId);
                    if (safe.size() >= capacity) {
                        break;
                    }
                }
            }
        }
        ItemNbtUtils.updateTag(gun, tag -> {
            ListTag stored = new ListTag();
            for (Identifier ammoId : safe) {
                stored.add(StringTag.valueOf(ammoId.toString()));
            }
            tag.putString(INTERNAL_FEED_AMMO_ID, definition.getAmmoId().toString());
            tag.putInt(INTERNAL_FEED_CAPACITY, definition.getMagazineCapacity());
            tag.putInt(INTERNAL_FEED_COUNT, safe.size());
            tag.put(INTERNAL_FEED_ROUNDS, stored);
            tag.putString(INTERNAL_FEED_MECHANISM, definition.getMechanism().name());
        });
        syncLegacy(gun, safe.size());
    }

    private static int legacyAmmo(ItemStack gun) {
        return gun.getItem() instanceof GunItemDataAccessor data ? data.getLegacyAmmoCount(gun) : 0;
    }

    private static void syncLegacy(ItemStack gun, int count) {
        if (gun.getItem() instanceof GunItemDataAccessor data) {
            data.setLegacyAmmoCount(gun, count);
        }
    }

    /** Small client-safe projection; server reservation data never leaves the server. */
    public record ReloadRoutePreview(String routeId, String animationForceAttachmentPresent,
                                     int animationForceMagExtentLevel) {
        public static final ReloadRoutePreview EMPTY = new ReloadRoutePreview("", "", -1);

        public boolean isEmpty() {
            return routeId == null || routeId.isBlank();
        }
    }

    private record RouteSelection(GunReloadRoute route, @Nullable LoadingDeviceSelection device) {
    }

    private record LoadingDeviceSelection(int slot, ItemStack preview, int transferableRounds) {
    }
}
