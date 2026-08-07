package com.tacz.guns.industry.magazine;

import cn.sh1rocu.tacz.util.itemhandler.ItemHandlerHelper;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Physical installed en-bloc clips.
 *
 * <p>An en-bloc clip is deliberately neither an InstalledMagazine nor a
 * bridge clip inventory source. The whole configured ItemStack is stored in
 * gun NBT while firing; its count falls with the gun's internal rounds and the
 * empty clip is physically ejected as a real world ItemEntity after the final
 * chambered round has actually left the gun.</p>
 */
public final class EnBlocClipService {
    public static final String INSTALLED_EN_BLOC_CLIP_TAG = "InstalledEnBlocClip";

    private EnBlocClipService() {
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
        return definition != null && definition.isValidEnBlocClipDefinition() ? definition : null;
    }

    public static boolean usesEnBlocClip(ItemStack gun) {
        return isFeatureEnabled() && getDefinition(gun) != null;
    }

    public static ItemStack getInstalledClip(ItemStack gun) {
        CompoundTag tag = ItemNbtUtils.getTag(gun);
        return tag.contains(INSTALLED_EN_BLOC_CLIP_TAG)
                ? ItemNbtUtils.loadItemStack(tag.getCompoundOrEmpty(INSTALLED_EN_BLOC_CLIP_TAG))
                : ItemStack.EMPTY;
    }

    public static void setInstalledClip(ItemStack gun, ItemStack clip) {
        ItemStack safe = clip == null ? ItemStack.EMPTY : clip.copy();
        ItemNbtUtils.updateTag(gun, tag -> {
            if (safe.isEmpty()) {
                tag.remove(INSTALLED_EN_BLOC_CLIP_TAG);
            } else {
                tag.put(INSTALLED_EN_BLOC_CLIP_TAG, ItemNbtUtils.saveItemStack(safe));
            }
        });
        syncLegacy(gun, getInstalledAmmoCount(gun));
    }

    public static boolean hasActiveInstalledClip(ItemStack gun) {
        GunFeedDefinition definition = getDefinition(gun);
        return definition != null && isCompatible(definition, getInstalledClip(gun));
    }

    public static int getInstalledAmmoCount(ItemStack gun) {
        ItemStack clip = getInstalledClip(gun);
        return clip.getItem() instanceof IMagazine magazine ? magazine.getAmmoCount(clip) : 0;
    }

    public static void setInstalledAmmoCount(ItemStack gun, int count) {
        ItemStack clip = getInstalledClip(gun);
        if (!(clip.getItem() instanceof IMagazine magazine)) {
            return;
        }
        magazine.setAmmoCount(clip, count);
        setInstalledClip(gun, clip);
    }

    public static int removeInstalledRounds(ItemStack gun, int amount) {
        int removed = Math.min(Math.max(0, amount), getInstalledAmmoCount(gun));
        if (removed > 0) {
            setInstalledAmmoCount(gun, getInstalledAmmoCount(gun) - removed);
        }
        return removed;
    }

    public static boolean canReload(LivingEntity shooter, ItemStack gun) {
        if (!usesEnBlocClip(gun) || !(shooter instanceof Player player)) {
            return false;
        }
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return false;
        }
        if (!shouldConsumeClip(player) || isInfiniteReload(gun)) {
            return true;
        }
        ClipSelection selection = findBestClip(player, gun, definition);
        return selection != null && selection.preview().getItem() instanceof IMagazine magazine
                && magazine.getAmmoCount(selection.preview()) > getInstalledAmmoCount(gun);
    }

    @Nullable
    public static EnBlocClipReloadPlan beginReload(ShooterDataHolder data, LivingEntity shooter,
                                                    ItemStack gun, boolean tactical) {
        if (!usesEnBlocClip(gun) || !(shooter instanceof Player player) || !(gun.getItem() instanceof IGun iGun)) {
            return null;
        }
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return null;
        }
        boolean consumesClip = shouldConsumeClip(player) && !isInfiniteReload(gun);
        if (!consumesClip) {
            EnBlocClipReloadPlan plan = new EnBlocClipReloadPlan(iGun.getGunId(gun), tactical, false, -1, ItemStack.EMPTY);
            data.enBlocClipReload = plan;
            return plan;
        }
        ClipSelection selection = findBestClip(player, gun, definition);
        if (selection == null || !(selection.preview().getItem() instanceof IMagazine magazine)
                || magazine.getAmmoCount(selection.preview()) <= getInstalledAmmoCount(gun)) {
            return null;
        }
        EnBlocClipReloadPlan plan = new EnBlocClipReloadPlan(
                iGun.getGunId(gun), tactical, true, selection.slot(), selection.preview()
        );
        data.enBlocClipReload = plan;
        return plan;
    }

    public static boolean isReloadManaged(ShooterDataHolder data, ItemStack gun) {
        if (data == null) {
            return false;
        }
        EnBlocClipReloadPlan plan = data.enBlocClipReload;
        return plan != null && gun.getItem() instanceof IGun iGun && plan.getGunId().equals(iGun.getGunId(gun));
    }

    public static void clearReloadPlan(ShooterDataHolder data) {
        data.enBlocClipReload = null;
    }

    public static void onReloadStateTransition(ShooterDataHolder data, LivingEntity shooter, ItemStack gun,
                                               ReloadState.StateType previous, ReloadState.StateType next) {
        EnBlocClipReloadPlan plan = data.enBlocClipReload;
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
            finishReservedReload(shooter, gun, plan);
        }
        if (!next.isReloading()) {
            clearReloadPlan(data);
        }
    }

    /**
     * Explicit manual unload for an installed en-bloc clip. Automatic empty
     * ejection remains separate and is performed after the final real shot.
     */
    public static boolean ejectClip(LivingEntity shooter, ItemStack gun) {
        if (!usesEnBlocClip(gun) || !(shooter instanceof Player player)) {
            return false;
        }
        ItemStack clip = getInstalledClip(gun);
        if (clip.isEmpty()) {
            return false;
        }
        setInstalledClip(gun, ItemStack.EMPTY);
        ItemHandlerHelper.giveItemToPlayer(player, clip);
        player.inventoryMenu.broadcastFullState();
        return true;
    }

    /**
     * Eject the empty physical clip only once the gun has no remaining clip
     * rounds and no chambered round still waiting to be fired. This is a real
     * server ItemEntity ejection, not a delayed inventory grant or a client
     * animation substitute; manual/tactical removal remains inventory-native.
     */
    public static boolean ejectIfEmpty(LivingEntity shooter, ItemStack gun) {
        if (!usesEnBlocClip(gun) || !(shooter instanceof Player player) || !(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        ItemStack clip = getInstalledClip(gun);
        if (clip.isEmpty() || getInstalledAmmoCount(gun) > 0 || hasUnfiredChamberRound(iGun, gun)) {
            return false;
        }
        setInstalledClip(gun, ItemStack.EMPTY);
        ejectClipIntoWorld(shooter, clip);
        player.inventoryMenu.broadcastFullState();
        return true;
    }

    /**
     * Native item-entity ejection for an automatically emptied clip. It uses a
     * server-side trajectory so remote clients, hoppers and the firing player
     * all observe the same physical result. The short pickup delay keeps the
     * clip visibly clear of the receiver before normal vanilla pickup begins.
     */
    private static void ejectClipIntoWorld(LivingEntity shooter, ItemStack clip) {
        if (clip.isEmpty() || shooter.level().isClientSide()) {
            return;
        }
        Vec3 forward = shooter.getLookAngle();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        if (right.lengthSqr() < 1.0E-6) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3 spawn = shooter.position()
                .add(right.scale(0.14))
                .add(forward.scale(0.08))
                .add(0.0, shooter.getEyeHeight() * 0.56, 0.0);
        Vec3 velocity = right.scale(0.16 + shooter.getRandom().nextDouble() * 0.05)
                .add(forward.scale((shooter.getRandom().nextDouble() - 0.5) * 0.04))
                .add(0.0, 0.13 + shooter.getRandom().nextDouble() * 0.05, 0.0);
        ItemEntity entity = new ItemEntity(shooter.level(), spawn.x, spawn.y, spawn.z, clip.copy());
        entity.setPickUpDelay(10);
        entity.setDeltaMovement(velocity);
        shooter.level().addFreshEntity(entity);
    }

    private static boolean finishReservedReload(LivingEntity shooter, ItemStack gun, EnBlocClipReloadPlan plan) {
        if (!(shooter instanceof Player player) || !(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        GunFeedDefinition definition = getDefinition(gun);
        if (definition == null) {
            return false;
        }
        ItemStack incoming = plan.consumesClip()
                ? extractReservedClip(player, definition, plan)
                : createClip(definition, definition.getFeedDeviceCapacity());
        if (incoming.isEmpty()) {
            return false;
        }
        ItemStack outgoing = getInstalledClip(gun);
        setInstalledClip(gun, incoming);
        if (plan.consumesClip() && !outgoing.isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, outgoing);
        }
        if (!plan.isTactical()) {
            chamberRoundAfterEmptyReload(gun);
        }
        player.inventoryMenu.broadcastFullState();
        return true;
    }

    private static ItemStack extractReservedClip(Player player, GunFeedDefinition definition, EnBlocClipReloadPlan plan) {
        if (!plan.consumesClip() || plan.getClipSlot() < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack current = player.getInventory().getItem(plan.getClipSlot());
        ItemStack expected = plan.getExpectedClip();
        if (!ItemStack.isSameItemSameComponents(current, expected) || !isCompatible(definition, current)) {
            return ItemStack.EMPTY;
        }
        player.getInventory().setItem(plan.getClipSlot(), ItemStack.EMPTY);
        player.getInventory().setChanged();
        return current.copy();
    }

    @Nullable
    private static ClipSelection findBestClip(Player player, ItemStack gun, GunFeedDefinition definition) {
        int current = isCompatible(definition, getInstalledClip(gun)) ? getInstalledAmmoCount(gun) : 0;
        ClipSelection best = null;
        int bestRounds = current;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!(candidate.getItem() instanceof IMagazine magazine) || !isCompatible(definition, candidate)) {
                continue;
            }
            int rounds = magazine.getAmmoCount(candidate);
            if (rounds > bestRounds) {
                bestRounds = rounds;
                best = new ClipSelection(slot, candidate.copy());
            }
        }
        return best;
    }


    private static boolean isCompatible(GunFeedDefinition definition, ItemStack clip) {
        if (!definition.isValidEnBlocClipDefinition() || !(clip.getItem() instanceof MagazineItemDataAccessor data)
                || !data.isConfigured(clip)) {
            return false;
        }
        return definition.getMechanism().serializedName().equals(data.getFeedDeviceKind(clip))
                && definition.getMagazineFamily().equals(data.getMagazineFamily(clip))
                && definition.getAmmoId().equals(data.getAmmoId(clip))
                && definition.getFeedDeviceCapacity() == data.getCapacity(clip);
    }

    private static ItemStack createClip(GunFeedDefinition definition, int rounds) {
        return MagazineItemBuilder.create().fromDefinition(definition).setAmmoCount(rounds).build();
    }

    /**
     * Mirrors TACZ's actual firing semantics: an OPEN_BOLT gun never has a
     * meaningful chambered-round state, even if an old ItemStack still carries
     * a stale HasBulletInBarrel flag. Without this distinction an empty M1
     * en-bloc clip can remain installed forever after its final shot.
     */
    private static boolean hasUnfiredChamberRound(IGun iGun, ItemStack gun) {
        Bolt bolt = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> index.getGunData().getBolt()).orElse(null);
        return bolt != Bolt.OPEN_BOLT && iGun.hasBulletInBarrel(gun);
    }

    private static void chamberRoundAfterEmptyReload(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return;
        }
        Bolt bolt = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> index.getGunData().getBolt()).orElse(null);
        if ((bolt == Bolt.MANUAL_ACTION || bolt == Bolt.CLOSED_BOLT)
                && !iGun.hasBulletInBarrel(gun) && removeInstalledRounds(gun, 1) == 1) {
            iGun.setBulletInBarrel(gun, true);
        }
    }

    private static boolean shouldConsumeClip(Player player) {
        return IGunOperator.fromLivingEntity(player).needCheckAmmo();
    }

    private static boolean isInfiniteReload(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        return TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> index.getGunData().getReloadData().isInfinite()).orElse(false);
    }

    private static void syncLegacy(ItemStack gun, int count) {
        if (gun.getItem() instanceof GunItemDataAccessor accessor) {
            accessor.setLegacyAmmoCount(gun, count);
        }
    }

    private record ClipSelection(int slot, ItemStack preview) {
    }
}
