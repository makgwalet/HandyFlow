package za.co.handyflow.platform.shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The statutory figures of one tax year, as loaded from the approved tax-year row, the brackets and the rebates.
 * A tax year is named by the year it ends, the way SARS names it: 2027 = 1 March 2026 to 28 February 2027.
 */
public record StatutoryYearRules(
        int taxYear,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<Bracket> brackets,
        BigDecimal primaryRebate,
        BigDecimal secondaryRebate,
        BigDecimal tertiaryRebate,
        BigDecimal mtcTaxpayer,
        BigDecimal mtcFirstDependant,
        BigDecimal mtcAdditional,
        BigDecimal uifMonthlyCeiling,
        BigDecimal uifRateEmployee,
        BigDecimal uifRateEmployer,
        BigDecimal sdlRate,
        BigDecimal sdlThreshold,
        BigDecimal retirementCapAmount,
        BigDecimal retirementCapPct,
        BigDecimal travelTaxablePct) {

    /** One line of the tax table. {@code from} is the top of the line below; tax = baseTax + rate% of income above {@code from}. */
    public record Bracket(BigDecimal from, BigDecimal to, BigDecimal baseTax, BigDecimal ratePercent) {}

    /** The tax year a date falls in, named by the year it ends. 15 Jan 2026 is 2026; 1 March 2026 is 2027. */
    public static int taxYearOf(LocalDate date) {
        return date.getMonthValue() >= 3 ? date.getYear() + 1 : date.getYear();
    }

    /**
     * Monthly medical scheme fees tax credit for the people covered (the employee counts as one):
     * the taxpayer, then the first dependant, then each additional dependant. No members means no credit.
     */
    public BigDecimal monthlyMedicalCredit(int members) {
        if (members <= 0) return BigDecimal.ZERO;
        BigDecimal total = mtcTaxpayer;
        if (members >= 2) total = total.add(mtcFirstDependant);
        if (members > 2) total = total.add(mtcAdditional.multiply(BigDecimal.valueOf(members - 2L)));
        return total;
    }
}
