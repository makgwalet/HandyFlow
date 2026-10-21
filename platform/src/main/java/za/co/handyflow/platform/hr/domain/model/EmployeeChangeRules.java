package za.co.handyflow.platform.hr.domain.model;

import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Changing what an employee is paid, or where the money goes, is done by asking and then approving, never in one edit.
 * Pure, so it runs without a database.
 */
public final class EmployeeChangeRules {

    public static final String COMPENSATION = "COMPENSATION", BANK_DETAILS = "BANK_DETAILS";
    public static final String PENDING = "PENDING", APPROVED = "APPROVED", APPLIED = "APPLIED", REJECTED = "REJECTED", CANCELLED = "CANCELLED";

    public static final String GROSS = "grossSalary", TRAVEL = "travelAllowance", MEDICAL = "medicalAidContribution",
            PENSION = "pensionContribution", FREQUENCY = "payFrequency",
            BANK_NAME = "bankName", ACCOUNT = "bankAccountNumber", BRANCH = "bankBranchCode";

    public static final BigDecimal MAX_GROSS = new BigDecimal("5000000");

    private EmployeeChangeRules() {}

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put(GROSS, "Gross salary"); LABELS.put(TRAVEL, "Travel allowance"); LABELS.put(MEDICAL, "Medical aid contribution");
        LABELS.put(PENSION, "Pension contribution"); LABELS.put(FREQUENCY, "Pay frequency");
        LABELS.put(BANK_NAME, "Bank"); LABELS.put(ACCOUNT, "Account number"); LABELS.put(BRANCH, "Branch code");
    }

    public static String label(String field) { return LABELS.getOrDefault(field, field); }

    public static String compensationProblem(BigDecimal gross, BigDecimal travel, BigDecimal medical, BigDecimal pension, String frequency) {
        if (gross == null || gross.signum() <= 0) return "Gross salary must be more than zero.";
        if (gross.compareTo(MAX_GROSS) > 0) return "Gross salary looks too high. Check the amount.";
        for (BigDecimal v : new BigDecimal[] {travel, medical, pension})
            if (v != null && v.signum() < 0) return "Allowances and contributions cannot be negative.";
        BigDecimal deductions = nz(medical).add(nz(pension));
        if (deductions.compareTo(gross) > 0) return "Medical aid and pension together are more than the gross salary.";
        if (nz(travel).compareTo(gross) > 0) return "The travel allowance is more than the gross salary.";
        if (frequency != null && !List.of("MONTHLY", "WEEKLY", "FORTNIGHTLY").contains(frequency))
            return "Pay frequency must be monthly, weekly or fortnightly.";
        return null;
    }

    /** A change can start up to a month back (it is only a record, nothing is recalculated) and up to a year ahead. */
    public static String effectiveProblem(LocalDate effective, LocalDate employeeStart, LocalDate today) {
        if (effective == null) return "Choose the date the change takes effect.";
        if (employeeStart != null && effective.isBefore(employeeStart)) return "The change cannot take effect before the employee started.";
        if (effective.isBefore(today.minusDays(31))) return "The change cannot take effect more than a month ago.";
        if (effective.isAfter(today.plusDays(366))) return "The change cannot take effect more than a year from now.";
        return null;
    }

    public static String reasonProblem(String reason) {
        if (reason == null || reason.isBlank()) return "Give a reason for the change.";
        return reason.trim().length() > 500 ? "The reason can be at most 500 characters." : null;
    }

    /** The fields whose value differs, in a stable order. Values are compared as numbers when both are numbers. */
    public static List<String> changedFields(Map<String, String> before, Map<String, String> after) {
        List<String> out = new ArrayList<>();
        for (String f : after.keySet()) if (!same(before.get(f), after.get(f))) out.add(f);
        return out;
    }

    private static boolean same(String a, String b) {
        if (a == null || a.isBlank()) return b == null || b.isBlank();
        if (b == null || b.isBlank()) return false;
        try { return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0; }
        catch (NumberFormatException e) { return a.trim().equals(b.trim()); }
    }

    /** @return true when the approver is the person who asked, which the business must have allowed */
    public static boolean checkCanDecide(String status, UUID requestedBy, UUID actor, boolean allowSelf) {
        if (!PENDING.equals(status)) throw new IllegalStateException("Only a change that is waiting for a decision can be approved or rejected.");
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        boolean same = requestedBy != null && requestedBy.equals(actor);
        if (same && !allowSelf) throw new AccessDeniedException("The person who asked for a change cannot approve it. Ask another approver.");
        return same;
    }

    public static void checkCanReject(String status, String note) {
        if (!PENDING.equals(status)) throw new IllegalStateException("Only a change that is waiting for a decision can be approved or rejected.");
        if (note == null || note.isBlank()) throw new IllegalArgumentException("Give a reason for rejecting the change.");
    }

    public static void checkCanCancel(String status, UUID requestedBy, UUID actor) {
        if (!PENDING.equals(status)) throw new IllegalStateException("Only a change that is waiting for a decision can be cancelled.");
        if (requestedBy == null || !requestedBy.equals(actor)) throw new AccessDeniedException("Only the person who asked for a change can cancel it.");
    }

    /** Approved and its date has come. */
    public static boolean isDue(String status, LocalDate effective, LocalDate today) {
        return APPROVED.equals(status) && !effective.isAfter(today);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
