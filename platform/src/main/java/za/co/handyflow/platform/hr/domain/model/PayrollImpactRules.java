package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * How a recorded payslip compares with a recalculation under the approved rules. Pure.
 * <p>
 * The verdict says how sure we are, not what to do: nothing here changes a payslip. Any correction goes through an
 * approved adjustment or reversal, not through this report.
 */
public final class PayrollImpactRules {

    public enum Verdict { NO_DIFFERENCE, POTENTIAL_DIFFERENCE, CONFIRMED_DIFFERENCE, REVIEW_REQUIRED }

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private PayrollImpactRules() {}

    /**
     * @param rulesMissing      no approved rules exist for the correct tax year, so nothing could be recalculated
     * @param employeeMissing   the employee record is gone, so the date of birth is unknown
     * @param ignoredInputs     the payslip carries overtime or a bonus that the old calculation never taxed
     * @param assumedMembership the credit depended on a guess because no medical scheme membership is on record
     * @param payeDifference    recalculated minus recorded
     */
    public record Facts(boolean rulesMissing, boolean employeeMissing, boolean ignoredInputs, boolean assumedMembership,
                        BigDecimal payeDifference, BigDecimal uifDifference, BigDecimal sdlDifference) {}

    public static Verdict classify(Facts f) {
        if (f.rulesMissing() || f.employeeMissing() || f.ignoredInputs()) return Verdict.REVIEW_REQUIRED;
        boolean differs = differs(f.payeDifference()) || differs(f.uifDifference()) || differs(f.sdlDifference());
        if (!differs) return Verdict.NO_DIFFERENCE;
        return f.assumedMembership() ? Verdict.POTENTIAL_DIFFERENCE : Verdict.CONFIRMED_DIFFERENCE;
    }

    /** Plain reasons to show next to the verdict. */
    public static List<String> reasons(Facts f, Integer recordedTaxYear, int correctTaxYear) {
        List<String> out = new ArrayList<>();
        if (f.rulesMissing()) out.add("No approved statutory rules exist for tax year " + correctTaxYear + ".");
        if (f.employeeMissing()) out.add("The employee record is no longer available.");
        if (f.ignoredInputs()) out.add("The payslip has overtime or a bonus, which this calculation did not tax. Check it by hand.");
        if (recordedTaxYear != null && recordedTaxYear != correctTaxYear)
            out.add("The run was labelled tax year " + recordedTaxYear + " but its pay date falls in " + correctTaxYear + ".");
        if (f.assumedMembership()) out.add("No medical scheme membership is on record. The credit assumes the employee only.");
        if (!f.rulesMissing() && !f.employeeMissing()) {
            if (differs(f.payeDifference())) out.add("PAYE differs by " + f.payeDifference().toPlainString() + ".");
            if (differs(f.uifDifference())) out.add("UIF differs by " + f.uifDifference().toPlainString() + ".");
            if (differs(f.sdlDifference())) out.add("SDL differs by " + f.sdlDifference().toPlainString() + ".");
        }
        return out;
    }

    /** One verdict for a whole run: a confirmed difference wins, then review, then potential. */
    public static Verdict runVerdict(Collection<Verdict> lines) {
        if (lines.contains(Verdict.CONFIRMED_DIFFERENCE)) return Verdict.CONFIRMED_DIFFERENCE;
        if (lines.contains(Verdict.REVIEW_REQUIRED)) return Verdict.REVIEW_REQUIRED;
        if (lines.contains(Verdict.POTENTIAL_DIFFERENCE)) return Verdict.POTENTIAL_DIFFERENCE;
        return Verdict.NO_DIFFERENCE;
    }

    private static boolean differs(BigDecimal d) { return d != null && d.abs().compareTo(TOLERANCE) >= 0; }
}
