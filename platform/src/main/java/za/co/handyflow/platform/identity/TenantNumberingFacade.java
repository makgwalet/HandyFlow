package za.co.handyflow.platform.identity;

import za.co.handyflow.platform.shared.TenantId;

/**
 * Platform-wide document numbering. This is the tenant-identity-aware
 * successor to calling {@code TenantSequenceService.nextValue(...)} and
 * hand-formatting the result — which is what all ~22 existing
 * per-module NumberGenerator classes still do (InvoiceNumberGenerator,
 * BookingNumberGenerator, FeeNoteNumberGenerator, etc.).
 * <p>
 * WHY this exists alongside TenantSequenceService rather than replacing it:
 * TenantSequenceService owns the one thing that must never move — the
 * atomic, race-free (tenant_id, sequence_name) counter increment. This
 * facade adds tenant identity and human formatting on top of that same
 * counter; it does not touch how the counter itself is incremented, so
 * existing sequence values and existing generator classes keep working
 * unmodified until each is migrated over (see InvoiceNumberGenerator for the
 * reference migration).
 * <p>
 * WHY {@code documentType} is a plain String and not yet a closed enum:
 * the real codebase has ~35+ distinct document number types spread across
 * ~22 generator classes. Forcing a single exhaustive enum in this first
 * phase would mean every module's migration has to land in one change to
 * compile. Passing the same string already used as
 * {@code TenantSequenceService}'s {@code sequenceName} lets each module
 * migrate independently, verified on its own. A closed enum is reasonable
 * once most modules have migrated — tracked as platform backlog, not done
 * here to avoid a large, unverified, all-at-once rewrite.
 */
public interface TenantNumberingFacade {

    /**
     * Returns the next formatted document number for this tenant and
     * document type, e.g. {@code "FPS-INV-2026-000001"}.
     *
     * @param documentType   same string passed as {@code sequenceName} to
     *                       {@code TenantSequenceService.nextValue(...)}
     *                       (e.g. "INVOICE", "QUOTE", "CREDIT_NOTE")
     * @param defaultTypeCode fallback type code (e.g. "INV") used only when
     *                       no {@link #DEFAULT_TYPE_CODES} mapping and no
     *                       tenant override exist for this documentType —
     *                       callers migrating an existing generator should
     *                       pass their current prefix here so the visible
     *                       format is unchanged on first migration.
     */
    String next(TenantId tenantId, String documentType, String defaultTypeCode);
}
