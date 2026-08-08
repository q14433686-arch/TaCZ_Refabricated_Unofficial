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

    /** Named cartridge dimensional standards, separate from individual AmmoId ballistic profiles. */
    CARTRIDGE_STANDARD,

    /** Explicit alternate AmmoId → canonical-calibre / ballistic profile definitions. */
    AMMO_PROFILE,

    /** Named removable-magazine/belt interface standards. */
    FEED_STANDARD,

    /** Data-driven projection of TACZ Create Fly processes for client recipe viewers. */
    INDUSTRY_PROCESS,

    /** Dedicated four-slot cartridge assembly definitions, synchronized for GUI/REI display. */
    CARTRIDGE_ASSEMBLY,

    /** Curated factual action/feed/ammunition reference profiles keyed by loaded GunId. */
    INDUSTRY_REFERENCE,

    /** Per-gun condition/fouling baselines synchronized for maintenance HUD/Tooltip semantics. */
    INDUSTRY_MAINTENANCE,

    /** Explicit, guarded repairs for legacy table recipe result ids. */
    INDUSTRY_ID_ALIAS,
}
