package za.co.handyflow.platform.businessreadiness;

/** What the evidence says about one requirement. Factual, not a score. */
public enum ReadinessResult {
    /** The evidence the requirement's rule asks for exists, is valid on the date that matters, and is verified where verification is asked for. */
    MET,
    /** The rule asks for a registration or document and none is recorded. */
    MISSING,
    /** It exists but is expired, lapsed, or expires before the date that matters. */
    EXPIRED,
    /** It exists but is not yet usable: a registration still pending, or a document not yet verified. */
    PENDING,
    /** There is nothing to evaluate against: no rule is set on the requirement, or it is not linked to a tracked requirement. */
    NOT_EVALUATED,
    /** The user marked the requirement not applicable, so it is not evaluated. */
    NOT_APPLICABLE;

    /** Higher is worse; used to combine a registration check and a document check into one result. */
    int severity() {
        return switch (this) {
            case MISSING -> 3;
            case EXPIRED -> 2;
            case PENDING -> 1;
            default -> 0;
        };
    }
}
