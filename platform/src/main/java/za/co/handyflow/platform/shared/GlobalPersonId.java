package za.co.handyflow.platform.shared;

import java.util.UUID;

/**
 * Opaque reference to a resolved person identity — the "shared
 * identifier" half of the pattern described in HandyFlow BOS Discovery
 * doc Section 22.3. Modules store this as a plain UUID/foreign-key-style
 * column on their own entities (e.g. a future HrEmployee.globalPersonId)
 * to link records without depending on each other's entity types —
 * exactly the same relationship TenantId already has to every
 * tenant-scoped entity in this codebase, just for a person instead of a
 * tenant.
 */
public record GlobalPersonId(UUID value) {

    public static GlobalPersonId of(UUID value) {
        return new GlobalPersonId(value);
    }
}