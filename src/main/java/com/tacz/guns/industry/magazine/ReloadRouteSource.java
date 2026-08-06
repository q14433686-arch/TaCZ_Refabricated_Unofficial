package com.tacz.guns.industry.magazine;

import com.google.gson.annotations.SerializedName;

/** Source selected for one explicitly audited internal-feed reload route. */
public enum ReloadRouteSource {
    /** A reserved physical bridge clip or speedloader supplies the route. */
    @SerializedName("loading_device")
    LOADING_DEVICE,

    /** Matching loose cartridges supply the route at its native feed points. */
    @SerializedName("loose_ammo")
    LOOSE_AMMO
}
