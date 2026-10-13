package za.co.handyflow.platform.debtcollection.application.internal;

import za.co.handyflow.platform.debtcollection.domain.model.FeeCalcMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Computes a fee from an approved rule's terms and enforces its limits. Pure.
 * maxChargesPerMonth: no more than N charges of this rule on the case in the calendar month.
 * maxPerCase: cumulative gross charges for the rule on the case never exceed the cap — the amount is trimmed to the
 * remaining headroom (capped=true) and an exhausted cap is refused. Waivers do NOT free up cap: a waived fee was
 * still charged once.
 */
final class FeeCalculator {

    record Terms(FeeCalcMethod method, BigDecimal fixed, BigDecimal percent, BigDecimal maxPerCase, Integer maxChargesPerMonth) {}

    record Result(BigDecimal amount, boolean capped) {}

    private FeeCalculator() {}

    static Result compute(Terms t, BigDecimal outstanding, BigDecimal alreadyChargedForRule, long chargesThisMonth) {
        if (t.maxChargesPerMonth() != null && chargesThisMonth >= t.maxChargesPerMonth()) {
            throw new IllegalStateException("Monthly limit of " + t.maxChargesPerMonth() + " charges for this fee has been reached on this case");
        }
        BigDecimal base = t.method() == FeeCalcMethod.FIXED
                ? t.fixed()
                : (outstanding == null ? BigDecimal.ZERO : outstanding).multiply(t.percent())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        base = base.setScale(2, RoundingMode.HALF_UP);
        boolean capped = false;
        if (t.maxPerCase() != null) {
            BigDecimal headroom = t.maxPerCase().subtract(alreadyChargedForRule == null ? BigDecimal.ZERO : alreadyChargedForRule);
            if (headroom.signum() <= 0) {
                throw new IllegalStateException("The per-case cap of R" + t.maxPerCase() + " for this fee has been reached");
            }
            if (base.compareTo(headroom) > 0) {
                base = headroom;
                capped = true;
            }
        }
        if (base.signum() <= 0) throw new IllegalStateException("The calculated fee is zero — nothing to charge");
        return new Result(base, capped);
    }
}
