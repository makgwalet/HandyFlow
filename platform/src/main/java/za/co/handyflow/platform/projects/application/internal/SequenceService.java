package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

import java.util.UUID;

/**
 * Formats PM entity numbers (project numbers, task numbers, CO numbers,
 * etc.) — the naming/formatting conventions this module owns.
 * <p>
 * WHY THIS CLASS IS NOW A THIN WRAPPER, NOT ITS OWN SEQUENCE ENGINE: this
 * class used to run its own copy of the exact same atomic
 * "INSERT ... ON CONFLICT DO UPDATE ... RETURNING" pattern against a
 * separate pm_counters table, duplicating what invoicing's
 * TenantSequenceService already did against tenant_number_sequences —
 * same reasoning (concurrent HTTP requests can't both read the same
 * MAX+1), same fix, two independent implementations. TenantSequenceService
 * has since moved to `shared` (see HandyFlow BOS Discovery doc, Section
 * 27/28, Q18) specifically so modules like this one can depend on it
 * instead of re-deriving the same logic.
 * <p>
 * MIGRATION NOTE: switching this class over required migrating existing
 * pm_counters values into tenant_number_sequences first — see
 * V_migrate_pm_counters_to_tenant_sequences.sql. Without that migration,
 * every tenant's project/task/CO/snag numbering would have silently
 * restarted at 1 on deploy, colliding with numbers already in use. Do not
 * deploy this class ahead of that migration.
 * <p>
 * PUBLIC API UNCHANGED: every existing caller (ProjectService,
 * ChangeOrderService, RfiService, FieldService, etc.) keeps calling
 * next(tenantId, counterType) or the nextXxxNumber() convenience methods
 * exactly as before — only what happens inside next() changed.
 * <p>
 * COUNTER TYPE CONVENTION (unchanged from the original class):
 *  - Projects:      "PROJECT"
 *  - Tasks:          "TASK:&lt;projectId&gt;"
 *  - Change orders:  "CO:&lt;projectId&gt;"
 *  - Snags:          "SNAG:&lt;projectId&gt;"
 *  - Phases:         "PHASE:&lt;projectId&gt;"
 *  - Budget lines:   "BUDGET:&lt;projectId&gt;"
 */
@Service
@RequiredArgsConstructor
public class SequenceService {

    private final TenantSequenceService tenantSequenceService;

    /**
     * Returns the next sequence value for the given tenant and counter
     * type. Delegates entirely to TenantSequenceService.nextValue() —
     * same atomicity and same REQUIRES_NEW-commits-independently
     * guarantee as before, just one shared implementation instead of two.
     * <p>
     * Narrowed from TenantSequenceService's `long` return type to `int`
     * to keep every existing caller's `String.format("%03d"/"%04d", ...)`
     * call compiling unchanged. Safe in practice — no tenant is realistically
     * going to generate more than ~2 billion of the same entity type — but
     * flagging the narrowing explicitly rather than leaving it implicit.
     *
     * @param tenantId    the tenant whose counter is being incremented
     * @param counterType a namespaced key (see class Javadoc for conventions)
     * @return            the next integer in the sequence (starts at 1)
     */
    public int next(UUID tenantId, String counterType) {
        // TenantId.of(UUID) — confirm this exact overload exists before merging;
        // if TenantId only exposes a String-based factory, use
        // TenantId.of(tenantId.toString()) instead (that overload is confirmed
        // in use elsewhere, e.g. DeskService.createPublicTicketBySlug).
        long next = tenantSequenceService.nextValue(TenantId.of(tenantId), counterType);
        return (int) next;
    }

    // ── Convenience factory methods (unchanged) ──────────────────────────────

    /** Formats "PRJ0042" — global per tenant */
    public String nextProjectNumber(UUID tenantId) {
        return "PRJ" + String.format("%04d", next(tenantId, "PROJECT"));
    }

    /** Formats "T042" — scoped per project */
    public String nextTaskNumber(UUID tenantId, UUID projectId) {
        return "T" + String.format("%03d", next(tenantId, "TASK:" + projectId));
    }

    /** Formats "CO-007" — scoped per project */
    public String nextChangeOrderNumber(UUID tenantId, UUID projectId) {
        return "CO-" + String.format("%03d", next(tenantId, "CO:" + projectId));
    }

    /** Formats "SN0021" — scoped per project */
    public String nextSnagNumber(UUID tenantId, UUID projectId) {
        return "SN" + String.format("%04d", next(tenantId, "SNAG:" + projectId));
    }

    /** Returns next sort-order integer for phases or budget lines */
    public int nextSortOrder(UUID tenantId, UUID projectId, String entityType) {
        return next(tenantId, entityType + ":" + projectId);
    }
}