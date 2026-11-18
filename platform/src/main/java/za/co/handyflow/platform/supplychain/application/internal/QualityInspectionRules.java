package za.co.handyflow.platform.supplychain.application.internal;

import java.math.BigDecimal;
import java.util.List;

/** Pure rules for quality inspection of received goods. */
public final class QualityInspectionRules {

    private QualityInspectionRules() {}

    public record Result(BigDecimal inspected, BigDecimal passed, BigDecimal failed) {}

    /** Null when the counts are acceptable. A line can only inspect what the receipt accepted into stock. */
    public static String lineProblem(BigDecimal acceptedOnReceipt, BigDecimal passed, BigDecimal failed) {
        if (passed == null || failed == null) return "Passed and failed quantities are required";
        if (passed.signum() < 0 || failed.signum() < 0) return "Quantities cannot be negative";
        if (passed.add(failed).compareTo(acceptedOnReceipt) > 0)
            return "Passed plus failed (" + passed.add(failed).stripTrailingZeros().toPlainString() + ") is more than the "
                    + acceptedOnReceipt.stripTrailingZeros().toPlainString() + " the receipt accepted";
        return null;
    }

    /** PASSED, PARTIAL (some failed) or FAILED (nothing passed); null while nothing has been inspected. */
    public static String result(List<Result> lines) {
        BigDecimal inspected = BigDecimal.ZERO, passed = BigDecimal.ZERO, failed = BigDecimal.ZERO;
        for (Result l : lines) { inspected = inspected.add(l.inspected()); passed = passed.add(l.passed()); failed = failed.add(l.failed()); }
        if (inspected.signum() == 0) return null;
        if (failed.signum() == 0) return "PASSED";
        return passed.signum() == 0 ? "FAILED" : "PARTIAL";
    }

    /** Share of inspected quantity that passed, as a percentage; null with nothing inspected. */
    public static Double passRatePct(double passed, double inspected) {
        return inspected <= 0 ? null : Math.max(0, Math.min(100, passed / inspected * 100.0));
    }
}
