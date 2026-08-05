package com.tacz.guns.resource.network;

public enum DataType {
    /**
     * 需要同步到客户端的数据类型
     */
    GUN_DATA,
    ATTACHMENT_DATA,
    AMMO_INDEX,
    GUN_INDEX,
    ATTACHMENT_INDEX,
    RECIPES,
    RECIPE_FILTER,
    ATTACHMENT_TAGS,
    ALLOW_ATTACHMENT_TAGS,
    BLOCK_DATA,
    BLOCK_INDEX,

    /**
     * Per-gun physical-feed declarations.  Kept separate from GunData so a
     * gun pack can opt in without changing its original data file or licence.
     */
    GUN_FEED,

    /** Data-driven projection of TACZ Create Fly processes for client recipe viewers. */
    INDUSTRY_PROCESS,

    /** Dedicated four-slot cartridge assembly definitions, synchronized for GUI/REI display. */
    CARTRIDGE_ASSEMBLY,
}
