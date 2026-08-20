package za.co.handyflow.platform.controls.application;

import za.co.handyflow.platform.controls.dto.ControlExceptionResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * The public contract other modules depend on — same shape as
 * EvidenceFacade (Stage 0's own proven pattern): a narrow interface in
 * application/, implementation in application/internal/, so a calling
 * module never depends on this module's internal service class
 * directly.
 * <p>
 * Deliberately does NOT own or replace any module's own existing
 * exception state (e.g. SCM's ScSupplierInvoice.matchStatus). A caller
 * raises a parallel record here for the shared cross-module view; its
 * own domain-specific status field remains its own real source of
 * truth for its own screens.
 */
public interface ControlExceptionFacade {

    ControlExceptionResponse raise(TenantId tenantId, String sourceModule, String controlType,
                                   String relatedEntityType, UUID relatedEntityId,
                                   String severity, String description);

    List<ControlExceptionResponse> listOpen(TenantId tenantId);

    /**
     * NEW: every exception for a tenant, open AND resolved/dismissed —
     * added for Stage 3. An auditor's real question is "what got
     * flagged, and how was it handled", not just "what's currently
     * outstanding" — listOpen() alone can't answer that.
     */
    List<ControlExceptionResponse> listAll(TenantId tenantId);

    ControlExceptionResponse resolve(TenantId tenantId, UUID exceptionId,
                                     UUID resolvedBy, String resolvedByName, String resolutionNotes);

    /**
     * Resolves whichever open exception(s) are tied to a specific
     * record, without the caller needing to know this table's own
     * exceptionId. Used by SCM's override/cancel paths, which only ever
     * have the invoice's own ID on hand. Silently does nothing if no
     * open exception exists for that entity — resolving something that
     * was never flagged (or already resolved) isn't an error case a
     * caller needs to handle specially.
     */
    void resolveForEntity(TenantId tenantId, String relatedEntityType, UUID relatedEntityId,
                          UUID resolvedBy, String resolvedByName, String resolutionNotes);
}