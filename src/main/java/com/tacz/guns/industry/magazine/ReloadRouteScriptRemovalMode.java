package com.tacz.guns.industry.magazine;

import com.google.gson.annotations.SerializedName;

/**
 * Meaning of an audited legacy Lua {@code removeAmmoFromMagazine} call while a
 * route is active. Most scripts move that round to the chamber; a verified
 * speedloader script may first discard the cylinder's old rounds.
 */
public enum ReloadRouteScriptRemovalMode {
    @SerializedName("chamber")
    CHAMBER,

    @SerializedName("discard")
    DISCARD
}
