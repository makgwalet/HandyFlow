package za.co.handyflow.platform.supplychain.domain.enums;

/**
 * Typed reasons a supplier invoice failed (or could not complete) matching.
 * Each type carries the MatchStatus it forces: DISPUTE = money/identity problem
 * needing a decision (override or cancel); PARTIAL_MATCH = on hold, usually
 * fixed by receiving the goods / adding detail and re-running the match.
 * Kept separate from MatchStatus on purpose: sc_supplier_invoices.match_status
 * may carry a CHECK constraint, so detail lives in sc_match_exceptions instead.
 */
public enum MatchExceptionType {
    SUPPLIER_MISMATCH(MatchStatus.DISPUTE),
    PO_NOT_INVOICEABLE(MatchStatus.DISPUTE),
    LINE_NOT_ON_PO(MatchStatus.DISPUTE),
    PRICE_VARIANCE(MatchStatus.DISPUTE),
    QUANTITY_VARIANCE(MatchStatus.DISPUTE),
    AMOUNT_VARIANCE(MatchStatus.DISPUTE),
    TAX_VARIANCE(MatchStatus.DISPUTE),
    TOTAL_MISMATCH(MatchStatus.DISPUTE),
    POSSIBLE_DUPLICATE(MatchStatus.DISPUTE),
    MISSING_GRN(MatchStatus.PARTIAL_MATCH),
    UNMATCHED_LINE(MatchStatus.PARTIAL_MATCH),
    NO_PURCHASE_ORDER(MatchStatus.PARTIAL_MATCH),
    ENGINE_ERROR(MatchStatus.PARTIAL_MATCH);

    private final MatchStatus resultingStatus;

    MatchExceptionType(MatchStatus resultingStatus) { this.resultingStatus = resultingStatus; }

    public MatchStatus resultingStatus() { return resultingStatus; }
}
