package com.tacz.guns.client.compat;

import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

/**
 * Triggers a single client resource reload after a <b>remote</b> gun-pack sync
 * has rebuilt the client indexes, so that any GUI item-model atlas slots cached
 * before the sync arrived are repainted.
 *
 * <h2>Why this exists</h2>
 * TaCZ guns/ammo/attachments/workbenches use a custom {@code tacz:dynamic_item}
 * model whose special renderer resolves a gun display from {@code ClientIndexManager}.
 * That manager is populated by the {@code ServerMessageSyncGunPack} packet.
 *
 * <p>On singleplayer/LAN the integrated server shares the in-JVM indexes, which
 * are already loaded before the client builds its item models, so icons render
 * correctly. On a <b>dedicated server</b>, however, the sync packet arrives
 * <i>after</i> the client has already entered the world and after recipe viewers
 * (REI/JEI) have registered and rendered their item icons into the GUI item
 * atlas. With the indexes still empty at that moment, the dynamic renderer draws
 * nothing (empty display), and the atlas caches that empty result keyed by the
 * item identity. When the sync later populates the indexes — and even after REI
 * reloads its plugins — the cached atlas slot is not invalidated, so the icons
 * stay purple/black/missing until the player presses F3+T (a full resource
 * reload), which repaints every slot.
 *
 * <h2>What this does</h2>
 * Exactly the same action as F3+T: {@code Minecraft.reloadResourcePacks()}. It is
 * requested once per connection, only for a remote (non-memory) connection, and
 * is serviced on the next client tick after the world/player exists, so it never
 * runs on the network thread or during early login. A resource reload does <b>not</b>
 * re-send the gun-pack sync packet (that is driven by the server's data-pack sync),
 * so there is no reload loop.
 */
@Environment(EnvType.CLIENT)
public final class ClientResourceRefreshBridge {
    private static boolean reloadRequested;
    private static boolean reloadInProgress;
    /** Guards against a second request for the same connection while one is in flight. */
    private static boolean reloadDoneThisConnection;

    private ClientResourceRefreshBridge() {
    }

    /** Called when a remote gun-pack sync has finished rebuilding client indexes. */
    public static void requestReloadAfterRemoteSync() {
        if (reloadDoneThisConnection || reloadRequested || reloadInProgress) {
            return;
        }
        reloadRequested = true;
        GunMod.LOGGER.info("[GunPackSync] Scheduling one client resource reload to repaint item icons (F3+T equivalent).");
    }

    /** Serviced from {@code ClientTickEvents.END_CLIENT_TICK}. */
    public static void tick(Minecraft client) {
        if (!reloadRequested || reloadInProgress) {
            return;
        }
        // Wait until the world is fully loaded; reloading too early can race with
        // the level/resource lifecycle that is still settling after join.
        if (client.level == null || client.player == null) {
            return;
        }
        reloadRequested = false;
        reloadInProgress = true;
        reloadDoneThisConnection = true;
        try {
            client.reloadResourcePacks().whenComplete((unused, throwable) -> client.execute(() -> {
                reloadInProgress = false;
                if (throwable == null) {
                    GunMod.LOGGER.info("[GunPackSync] Client resource reload completed after gun-pack sync.");
                } else {
                    GunMod.LOGGER.warn("[GunPackSync] Client resource reload after gun-pack sync failed.", throwable);
                }
            }));
        } catch (RuntimeException ex) {
            reloadInProgress = false;
            GunMod.LOGGER.warn("[GunPackSync] Could not start client resource reload after gun-pack sync.", ex);
        }
    }

    /** Reset on disconnect so the next connection gets its own reload. */
    public static void reset() {
        reloadRequested = false;
        reloadInProgress = false;
        reloadDoneThisConnection = false;
    }
}
