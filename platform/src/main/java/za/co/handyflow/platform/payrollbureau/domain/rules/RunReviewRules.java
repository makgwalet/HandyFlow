package za.co.handyflow.platform.payrollbureau.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Checks on a pay run (phase 2). Before calculating: things that must stop the run (BLOCK) and things a person should look at (WARN).
 * After calculating: how the run compares with the client's previous run.
 */
public final class RunReviewRules {

    public static final String BLOCK = "BLOCK";
    public static final String WARN = "WARN";

    /** A change in an employee's net pay of at least this share of last run's net is flagged. A starting point, not a SARS rule. */
    public static final BigDecimal VARIANCE_THRESHOLD = new BigDecimal("0.10");

    public record Finding(String severity, String code, UUID employeeId, String employeeName, String message) {}

    /** What the checks need to know about an employee, with sensitive values reduced to what the checks use. */
    public record Facts(UUID id, String name, BigDecimal grossSalary, String idNumber, String taxNumber,
                        String bankAccountNumber, String email) {}

    private RunReviewRules() {}

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    /** Checks before a run is calculated. {@code overlappingRuns} are the numbers of other runs of the same group covering any of the same days. */
    public static List<Finding> preflight(List<Facts> employees, List<String> overlappingRuns) {
        List<Finding> out = new ArrayList<>();
        if (!overlappingRuns.isEmpty()) {
            out.add(new Finding(BLOCK, "OVERLAPPING_RUN", null, null,
                    "Another pay run for this group covers some of the same days (" + String.join(", ", overlappingRuns)
                            + "). Reverse it first, or change this run's period, so no one is paid twice."));
        }
        if (employees.isEmpty()) {
            out.add(new Finding(BLOCK, "NO_EMPLOYEES", null, null, "There are no active employees to pay in this run."));
        }
        Map<String, List<Facts>> byId = new HashMap<>(), byBank = new HashMap<>();
        for (Facts e : employees) {
            if (e.grossSalary() == null || e.grossSalary().signum() <= 0) {
                out.add(new Finding(BLOCK, "ZERO_PAY", e.id(), e.name(), e.name() + " has no pay set."));
            }
            if (blank(e.bankAccountNumber())) {
                out.add(new Finding(WARN, "NO_BANK_ACCOUNT", e.id(), e.name(), e.name() + " has no bank account, so cannot be paid by bank file."));
            }
            if (blank(e.taxNumber())) {
                out.add(new Finding(WARN, "NO_TAX_NUMBER", e.id(), e.name(), e.name() + " has no tax number; their tax certificate will be incomplete."));
            }
            if (blank(e.email())) {
                out.add(new Finding(WARN, "NO_EMAIL", e.id(), e.name(), e.name() + " has no email address, so their payslip cannot be emailed."));
            }
            if (!blank(e.idNumber())) byId.computeIfAbsent(e.idNumber().trim(), k -> new ArrayList<>()).add(e);
            if (!blank(e.bankAccountNumber())) byBank.computeIfAbsent(e.bankAccountNumber().trim(), k -> new ArrayList<>()).add(e);
        }
        duplicates(byId, "DUPLICATE_ID_NUMBER", "share an ID number", out);
        duplicates(byBank, "DUPLICATE_BANK_ACCOUNT", "are paid into the same bank account", out);
        return out;
    }

    private static void duplicates(Map<String, List<Facts>> groups, String code, String what, List<Finding> out) {
        for (List<Facts> g : groups.values()) {
            if (g.size() < 2) continue;
            String names = String.join(" and ", g.stream().map(Facts::name).toList());
            for (Facts e : g) out.add(new Finding(WARN, code, e.id(), e.name(), names + " " + what + "."));
        }
    }

    public static boolean blocked(List<Finding> findings) {
        return findings.stream().anyMatch(f -> BLOCK.equals(f.severity()));
    }

    /** One employee's pay in a run, for comparing runs. */
    public record Pay(String name, BigDecimal net) {}

    /** How this run's payslips compare with the previous run's. Employees are matched by id. */
    public static List<Finding> variance(Map<UUID, Pay> previous, Map<UUID, Pay> current) {
        List<Finding> out = new ArrayList<>();
        for (Map.Entry<UUID, Pay> e : current.entrySet()) {
            Pay now = e.getValue();
            Pay before = previous.get(e.getKey());
            if (before == null) {
                out.add(new Finding(WARN, "NEW_IN_RUN", e.getKey(), now.name(), now.name() + " was not in the previous run."));
                continue;
            }
            if (before.net().signum() > 0) {
                BigDecimal change = now.net().subtract(before.net()).abs().divide(before.net(), 4, RoundingMode.HALF_UP);
                if (change.compareTo(VARIANCE_THRESHOLD) >= 0) {
                    out.add(new Finding(WARN, "NET_PAY_CHANGED", e.getKey(), now.name(), now.name() + "'s net pay went from "
                            + before.net().setScale(2, RoundingMode.HALF_UP) + " to " + now.net().setScale(2, RoundingMode.HALF_UP)
                            + " (" + change.movePointRight(2).setScale(1, RoundingMode.HALF_UP) + "%)."));
                }
            }
        }
        for (Map.Entry<UUID, Pay> e : previous.entrySet()) {
            if (!current.containsKey(e.getKey())) {
                out.add(new Finding(WARN, "NOT_IN_THIS_RUN", e.getKey(), e.getValue().name(),
                        e.getValue().name() + " was in the previous run but is not in this one."));
            }
        }
        return out;
    }
}
