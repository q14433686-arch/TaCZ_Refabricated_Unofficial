package com.tacz.guns.industry.maintenance;

/**
 * Server-side eligibility boundary for the maintenance foundation.
 *
 * <p>{@link #INDUSTRIAL_ASSEMBLY} is intentionally the safe default: only a
 * gun carrying a real industrial provenance marker is migrated and worn. An
 * administrator may opt into {@link #ALL_GUNS}, but legacy stacks are still
 * first migrated at full condition rather than being punished for their age.</p>
 */
public enum IndustryMaintenanceScope {
    /** Only guns produced through an industrial assembly/surveyed final operation participate. */
    INDUSTRIAL_ASSEMBLY,

    /** Administrator opt-in for every loaded gun identity; first migration is still full condition. */
    ALL_GUNS
}
