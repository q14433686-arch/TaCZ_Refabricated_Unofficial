package com.tacz.guns.industry;

import com.tacz.guns.GunMod;
import com.tacz.guns.config.sync.SyncConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * Central gate for features that only make sense in the industrial profile.
 *
 * <p>Do not test only {@code FabricLoader#isModLoaded("create")}.  A pack may
 * install Create Fly for unrelated automation while its server administrator
 * intentionally keeps TACZ in LEGACY mode.  Conversely, selecting CREATE_FLY
 * without the dependency must fail closed: ordinary TACZ recipes remain usable,
 * but physical-magazine and industrial-recipe code must not quietly create a
 * second, hand-crafted progression path.</p>
 */
public final class IndustryProfileManager {
    public static final String CREATE_MOD_ID = "create";

    private IndustryProfileManager() {
    }

    public static IndustryProfile getProfile() {
        if (SyncConfig.INDUSTRY_PROFILE == null) {
            // Configuration objects are not available during very early bootstrap.
            // Fail closed so item construction and resource discovery never depend on
            // Create Fly before ForgeConfigApiPort has loaded the server config.
            return IndustryProfile.LEGACY;
        }
        IndustryProfile profile = SyncConfig.INDUSTRY_PROFILE.get();
        return profile == null ? IndustryProfile.LEGACY : profile;
    }

    public static boolean isCreateFlyInstalled() {
        return FabricLoader.getInstance().isModLoaded(CREATE_MOD_ID);
    }

    /**
     * True only when the administrator selected the industrial ruleset and its
     * required Create Fly implementation is actually present.
     */
    public static boolean isCreateFlyProfileActive() {
        return getProfile() == IndustryProfile.CREATE_FLY && isCreateFlyInstalled();
    }

    /**
     * Log the configuration error once per server start.  We intentionally do
     * not crash the whole server: LEGACY remains a recovery route for old worlds.
     */
    public static void validateServerProfile(MinecraftServer server) {
        if (getProfile() == IndustryProfile.CREATE_FLY && !isCreateFlyInstalled()) {
            GunMod.LOGGER.error(
                    "TACZ industry profile is CREATE_FLY but Create Fly (mod id '{}') is not installed. "
                            + "Industrial recipes and physical magazines are disabled; set IndustryProfile=LEGACY "
                            + "to acknowledge the fallback or install a matching Create Fly build on both server and clients.",
                    CREATE_MOD_ID
            );
        } else if (getProfile() == IndustryProfile.CREATE_FLY) {
            GunMod.LOGGER.info("TACZ industrial CREATE_FLY profile is active.");
        }
    }
}
