package za.co.handyflow.platform.documents.application;

import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Implemented by a module whose records the retention engine tracks. One bean per module code.
 */
public interface RetentionProvider {

    /** The module code the module tracks its records under (e.g. PROJECTS, DOCUMENTS). */
    String moduleCode();

    /**
     * Disposal approved by a second person: delete the record's files (through the document store, which keeps its own tombstone)
     * and mark the module's record as disposed, keeping the record itself. Runs in the engine's transaction for this one record;
     * throw to refuse (the record goes back to the due list and the reason is shown on the request).
     */
    Disposed dispose(TenantId tenantId, String sourceType, UUID sourceId, UUID batchId, UUID approvedBy);

    /** A hold was placed or lifted on the retention screen. A module that keeps its own hold flag mirrors it here. */
    default void legalHoldChanged(TenantId tenantId, String sourceType, UUID sourceId, boolean hold, String reason, UUID by, String byName) {}

    /** What was removed: shown on the request and kept on the tombstone (e.g. "1 file removed, sha256 3f2a…"). */
    record Disposed(int filesRemoved, String detail) {}
}
