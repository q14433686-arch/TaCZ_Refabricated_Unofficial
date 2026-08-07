package com.tacz.guns.industry.magazine;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

/**
 * Optional pack-author declaration embedded in a TACZ GunData JSON file.
 *
 * <p>This is an alternative to a compatibility sidecar at
 * {@code data/<namespace>/industry/gun_feed/<gun>.json}. It is deliberately
 * opt-in: the presence of ordinary {@code reload.type = magazine}, a gun class,
 * or a model bone never creates this declaration automatically. A server-side
 * sidecar takes precedence, allowing a data pack to correct an older pack
 * without editing its archive.</p>
 */
public final class IndustryGunDataExtension {
    public static final int SCHEMA_VERSION = 1;

    @SerializedName("schema_version")
    private int schemaVersion = SCHEMA_VERSION;

    @SerializedName("feed")
    @Nullable
    private GunFeedDefinition feed;

    public int getSchemaVersion() {
        return schemaVersion;
    }

    @Nullable
    public GunFeedDefinition getFeed() {
        return feed;
    }
}
