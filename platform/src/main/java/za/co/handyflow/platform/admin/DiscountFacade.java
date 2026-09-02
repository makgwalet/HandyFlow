// admin/DiscountFacade.java

package za.co.handyflow.platform.admin;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DiscountFacade — the sole cross-module entry point into admin's
 * discount engine (AdminDiscountService), for the Discount Engine Fix
 * session's Piece A/B wiring.
 * <p>
 * WHY THIS EXISTS: billing's own package-info declares
 * allowedDependencies = {shared, identity, notifications} — admin isn't
 * in that list. Confirmed directly against the real file before writing
 * this. A facade alone doesn't bypass that — Spring Modulith enforces
 * allowedDependencies at the IMPORTING module's own declaration, so
 * billing's package-info still needs "admin" added regardless of how
 * clean this interface is. What this buys instead: billing only ever
 * sees this one narrow interface, never admin's raw internal
 * JDBC-heavy services (AdminInvoiceService, AdminLookupService, etc.) —
 * same reasoning as every other XxxFacade in this codebase
 * (AccountingFacade, CrmFacade, EvidenceFacade, ApprovalFacade).
 * <p>
 * resolveDiscount() and applyAndRecord() are deliberately combined into
 * one call rather than exposed as two separate facade methods — this
 * means billing never needs to reconstruct AdminDiscountService's own
 * internal DiscountResult type (which isn't safely importable across
 * the boundary either); the implementation receives it from
 * resolveDiscount()'s own return and passes it straight through to
 * applyAndRecord() without the caller ever touching it.
 * <p>
 * Does NOT touch AdminDiscountService.resolveDiscount()'s resolution
 * logic itself — that stays exactly as it is, per this session's own
 * explicit instruction. This is a pure adapter.
 */
public interface DiscountFacade {

    record DiscountOutcome(BigDecimal pct, String source) {}

    /**
     * Resolves the best applicable discount for a tenant/module (per
     * AdminDiscountService's own priority order: Partnership > Volume >
     * Code, non-stacking), and — if the result is non-zero — records the
     * redemption in the same call. Returns zero-pct/"NONE" if nothing
     * applies.
     */
    DiscountOutcome resolveAndRecordDiscount(UUID tenantId, String moduleKey,
                                             String discountCode, BigDecimal originalPrice,
                                             UUID activatedBy);
}