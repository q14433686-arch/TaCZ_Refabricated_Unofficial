package com.tacz.guns.client.industry.magazine;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.industry.magazine.InternalFeedService;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side prediction for an audited conditional reload route.
 *
 * <p>The local client needs the route a fraction of a second before the normal
 * synced reload state arrives so it can start the correct existing animation.
 * The server recomputes and validates the route independently; after that its
 * short-lived synced route becomes authoritative for the rest of the reload.</p>
 */
@Environment(EnvType.CLIENT)
public final class IndustryReloadRouteClientState {
    private static final long PREDICTION_GRACE_MS = 1_500L;

    private static Identifier predictedGunId;
    private static InternalFeedService.ReloadRoutePreview predicted = InternalFeedService.ReloadRoutePreview.EMPTY;
    private static long predictionExpiry = -1L;

    private IndustryReloadRouteClientState() {
    }

    /** Called immediately before the client triggers its normal reload input animation. */
    public static void preview(LocalPlayer player, ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            clear();
            return;
        }
        predictedGunId = iGun.getGunId(gun);
        predicted = InternalFeedService.previewReloadRoute(player, gun);
        predictionExpiry = System.currentTimeMillis() + PREDICTION_GRACE_MS;
    }

    /**
     * Returns a route-specific visual source check when an audited route is
     * active, or {@code null} for ordinary legacy reloads.
     */
    public static Boolean hasAmmoToConsume(LocalPlayer player, ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return null;
        }
        String routeId = activeRouteId(player, iGun.getGunId(gun));
        return routeId == null ? null : InternalFeedService.hasReloadRouteSource(player, gun, routeId);
    }

    /**
     * Returns true only for a route whose audited state machine uses attachment
     * presence as the selector between bridge-clip and loose-round animation.
     */
    public static boolean forcesAttachmentPresent(LocalPlayer player, ItemStack gun, String attachmentType) {
        if (!(gun.getItem() instanceof IGun iGun) || attachmentType == null || attachmentType.isBlank()) {
            return false;
        }
        String routeId = activeRouteId(player, iGun.getGunId(gun));
        if (routeId == null) {
            return false;
        }
        InternalFeedService.ReloadRoutePreview route = InternalFeedService.getReloadRoutePreview(gun, routeId);
        return !route.isEmpty()
                && attachmentType.equalsIgnoreCase(route.animationForceAttachmentPresent());
    }

    /** Returns -1 when no active route overrides a legacy extended-mag selector. */
    public static int forcedMagExtentLevel(LocalPlayer player, ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return -1;
        }
        String routeId = activeRouteId(player, iGun.getGunId(gun));
        if (routeId == null) {
            return -1;
        }
        return InternalFeedService.getReloadRoutePreview(gun, routeId).animationForceMagExtentLevel();
    }

    private static String activeRouteId(LocalPlayer player, Identifier gunId) {
        String syncedRoute = IGunOperator.fromLivingEntity(player).getSynIndustryReloadRoute();
        if (syncedRoute != null && !syncedRoute.isBlank()) {
            int separator = syncedRoute.indexOf('|');
            if (separator > 0 && gunId.toString().equals(syncedRoute.substring(0, separator))) {
                String routeId = syncedRoute.substring(separator + 1);
                return routeId.isBlank() ? null : routeId;
            }
        }
        if (gunId.equals(predictedGunId) && System.currentTimeMillis() <= predictionExpiry && !predicted.isEmpty()) {
            return predicted.routeId();
        }
        return null;
    }

    /** Clear a rejected/old local prediction once the server had time to answer. */
    public static void tick(LocalPlayer player) {
        if (predictedGunId == null) {
            return;
        }
        String syncedRoute = IGunOperator.fromLivingEntity(player).getSynIndustryReloadRoute();
        if ((syncedRoute == null || syncedRoute.isBlank()) && System.currentTimeMillis() > predictionExpiry) {
            clear();
        }
    }

    public static void clear() {
        predictedGunId = null;
        predicted = InternalFeedService.ReloadRoutePreview.EMPTY;
        predictionExpiry = -1L;
    }
}
