package com.tacz.guns.industry;

/**
 * Server-selected manufacturing ruleset.
 *
 * <p>{@link #CREATE_FLY} is deliberately a profile rather than a global Fabric
 * dependency: a server can still open an old world or a third-party gun pack in
 * {@link #LEGACY} without installing Create Fly.  When CREATE_FLY is selected,
 * {@link IndustryProfileManager} validates the {@code create} mod before any
 * industrial-only behaviour is enabled.</p>
 */
public enum IndustryProfile {
    /** Preserve legacy TACZ table recipes and the old integer-only ammunition store. */
    LEGACY,

    /**
     * Industrial TACZ rules backed by Create Fly's mechanical processing recipes.
     * This is the default profile for new server configs.
     */
    CREATE_FLY
}
