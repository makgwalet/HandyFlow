package za.co.handyflow.platform.hr.domain.model;

import za.co.handyflow.platform.shared.StatutoryYearRules;

import java.time.LocalDate;

/**
 * Decides whether a pay run's tax-year label agrees with its pay date. The label is the year the tax year ends,
 * so a May 2026 pay date belongs to 2027 (shown as 2026/27). This only ever concerns the label.
 */
public final class PayRunTaxYearRules {

    public enum Verdict { CORRECT, WRONG_LABEL, NEEDS_REVIEW }

    public record Check(Verdict verdict, int proposedYear, String reason) {}

    private PayRunTaxYearRules() {}

    public static Check check(LocalDate payDate, LocalDate periodStart, LocalDate periodEnd, int storedYear) {
        if (payDate == null) return new Check(Verdict.NEEDS_REVIEW, storedYear, "The run has no pay date, so its tax year cannot be verified.");
        int byPayDate = StatutoryYearRules.taxYearOf(payDate);
        if (periodEnd != null && StatutoryYearRules.taxYearOf(periodEnd) != byPayDate)
            return new Check(Verdict.NEEDS_REVIEW, storedYear, "The pay period ends in tax year " + label(StatutoryYearRules.taxYearOf(periodEnd))
                    + " but the pay date falls in " + label(byPayDate) + ". Decide which applies.");
        if (periodStart != null && periodEnd != null && periodEnd.isBefore(periodStart))
            return new Check(Verdict.NEEDS_REVIEW, storedYear, "The pay period ends before it starts.");
        if (storedYear == byPayDate) return new Check(Verdict.CORRECT, byPayDate, "The label agrees with the pay date.");
        return new Check(Verdict.WRONG_LABEL, byPayDate, "The pay date " + payDate + " falls in tax year " + label(byPayDate) + ".");
    }

    /** 2027 reads as "2026/27". */
    public static String label(int taxYear) {
        return (taxYear - 1) + "/" + String.format("%02d", taxYear % 100);
    }
}
