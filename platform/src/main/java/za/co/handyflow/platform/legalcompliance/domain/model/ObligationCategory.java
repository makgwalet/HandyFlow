package za.co.handyflow.platform.legalcompliance.domain.model;

/**
 * Regulatory area a RegulatoryObligation belongs to. Deliberately a fixed
 * enum, not a free-text field — the strategic plan names these
 * specifically (POPIA, BCEA, OHS Act, industry-specific regulations) as
 * the concrete SA regulatory areas this tracker exists to cover.
 * INDUSTRY_SPECIFIC/OTHER exist because no fixed list can be exhaustive
 * across every tenant's industry (e.g. PSIRA for Security tenants, NHBRC
 * for Contracting tenants) — those stay as free text on the obligation's
 * own `regulationReference` field rather than growing this enum forever.
 * <p>
 * Phase 1 (regulatory library) added FICA, LABOUR, BBBEE, PUBLIC_FINANCE and CONSUMER so
 * the library's catalogue maps onto real areas instead of collapsing into OTHER. The column
 * is VARCHAR(30), so adding values is non-destructive; existing rows are unaffected.
 */
public enum ObligationCategory {
    POPIA,
    COMPANIES_ACT,
    BCEA,
    OHS_ACT,
    TAX,
    FICA,
    LABOUR,
    BBBEE,
    PUBLIC_FINANCE,
    CONSUMER,
    INDUSTRY_SPECIFIC,
    OTHER
}
