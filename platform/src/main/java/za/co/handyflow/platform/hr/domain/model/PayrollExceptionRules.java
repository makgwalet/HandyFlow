package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Things a checker should look at before approving a pay run. Pure, so it runs without a database.
 * An ERROR blocks approval. A WARNING is shown and the checker decides.
 */
public final class PayrollExceptionRules {

    public enum Severity { ERROR, WARNING }

    /** Net pay moving by more than this fraction of the previous run is flagged. */
    public static final BigDecimal VARIANCE_LIMIT = new BigDecimal("0.10");

    private PayrollExceptionRules() {}

    public record Line(UUID employeeId, String employee, String number, BigDecimal net,
                       boolean medicalAssumed, boolean bankDetailsMissing) {}

    public record Item(Severity severity, String code, String employee, String message) {}

    /**
     * @param previousNet net pay of each employee on the previous approved run, or null when there is no previous run
     * @param previousNames names of people on the previous run, keyed by employee, for those who are missing now
     */
    public static List<Item> check(List<Line> lines, Map<UUID, BigDecimal> previousNet, Map<UUID, String> previousNames) {
        List<Item> out = new ArrayList<>();
        if (lines.isEmpty()) {
            out.add(new Item(Severity.ERROR, "NO_PAYSLIPS", null, "The run has no payslips."));
            return out;
        }
        for (Line l : lines) {
            String who = l.employee() + " (" + l.number() + ")";
            if (l.net() == null || l.net().signum() <= 0)
                out.add(new Item(Severity.ERROR, "NET_NOT_POSITIVE", who, "Net pay is zero or negative. Check the deductions."));
            if (l.medicalAssumed())
                out.add(new Item(Severity.WARNING, "MEDICAL_ASSUMED", who, "No medical scheme membership is on record, so the medical credit assumes the employee only."));
            if (l.bankDetailsMissing())
                out.add(new Item(Severity.WARNING, "BANK_DETAILS_MISSING", who, "No bank account is on record, so this person cannot be paid by bank file."));
            if (previousNet != null) {
                BigDecimal prev = previousNet.get(l.employeeId());
                if (prev == null) {
                    out.add(new Item(Severity.WARNING, "NEW_ON_RUN", who, "Not on the previous run."));
                } else if (prev.signum() > 0 && l.net() != null) {
                    BigDecimal change = l.net().subtract(prev).abs().divide(prev, 8, RoundingMode.HALF_UP);
                    if (change.compareTo(VARIANCE_LIMIT) > 0)
                        out.add(new Item(Severity.WARNING, "NET_VARIANCE", who, "Net pay changed by " + change.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                                + "% from the previous run (" + prev.setScale(2, RoundingMode.HALF_UP) + " to " + l.net().setScale(2, RoundingMode.HALF_UP) + ")."));
                }
            }
        }
        if (previousNet != null) {
            for (UUID id : previousNet.keySet()) {
                boolean present = lines.stream().anyMatch(l -> l.employeeId().equals(id));
                if (!present)
                    out.add(new Item(Severity.WARNING, "MISSING_FROM_RUN", previousNames.getOrDefault(id, id.toString()), "Was on the previous run but is not on this one."));
            }
        }
        return out;
    }

    public static boolean hasBlocking(List<Item> items) {
        return items.stream().anyMatch(i -> i.severity() == Severity.ERROR);
    }
}
