package za.co.handyflow.platform.expenses.application.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * The life of an expense claim (finance patch F7, 2026-10-14). Pure, so it is tested without Spring.
 * <pre>
 *   DRAFT ──submit──▶ PENDING ──approve (all steps)──▶ APPROVED ──reimburse──▶ REIMBURSED
 *     ▲                  │  └──reject──▶ REJECTED ──edit, submit again──▶ PENDING
 *     └──edit            └──withdraw──▶ CANCELLED      (DRAFT can be withdrawn too)
 * </pre>
 * Only the claimant edits, submits and withdraws. Approving posts the claim to the ledger (Dr staff expenses, Cr accrued
 * expenses); reimbursing posts the payment (Dr accrued expenses, Cr the bank account it was paid from).
 */
public final class ExpenseLifecycle {

    public enum Action { EDIT, SUBMIT, WITHDRAW, APPROVE, REJECT, REIMBURSE }

    private static final Set<String> EDITABLE = Set.of("DRAFT", "REJECTED");

    private ExpenseLifecycle() {}

    /** Null when {@code action} is allowed on a claim in {@code status}, otherwise why not. */
    public static String problem(Action action, String status) {
        String s = status == null ? "" : status;
        boolean ok = switch (action) {
            case EDIT, SUBMIT -> EDITABLE.contains(s);
            case WITHDRAW -> "DRAFT".equals(s) || "PENDING".equals(s);
            case APPROVE, REJECT -> "PENDING".equals(s);
            case REIMBURSE -> "APPROVED".equals(s);
        };
        if (ok) return null;
        return switch (action) {
            case EDIT -> "Only a draft or a rejected claim can be changed; this one is " + label(s);
            case SUBMIT -> "Only a draft or a rejected claim can be submitted; this one is " + label(s);
            case WITHDRAW -> "Only a draft or a claim waiting for approval can be withdrawn; this one is " + label(s);
            case APPROVE -> "Only a claim waiting for approval can be approved; this one is " + label(s);
            case REJECT -> "Only a claim waiting for approval can be rejected; this one is " + label(s);
            case REIMBURSE -> "Only an approved claim can be paid out; this one is " + label(s);
        };
    }

    /** Null when the claimant may change or submit a claim, otherwise why not. */
    public static String claimantProblem(java.util.UUID submittedBy, java.util.UUID user) {
        if (user != null && user.equals(submittedBy)) return null;
        return "Only the person who made this claim can change, submit or withdraw it";
    }

    /** Null when the claim's details are complete enough to submit, otherwise what is missing. */
    public static String submitProblem(String employeeName, LocalDate claimDate, String category, String description,
                                       BigDecimal amount, LocalDate today) {
        if (employeeName == null || employeeName.isBlank()) return "Say who is claiming";
        if (claimDate == null) return "Give the date of the expense";
        if (claimDate.isAfter(today)) return "The expense date cannot be in the future";
        if (category == null || category.isBlank()) return "Choose a category";
        if (description == null || description.isBlank()) return "Describe the expense";
        if (amount == null || amount.signum() <= 0) return "Enter an amount above zero";
        return null;
    }

    public static String reasonProblem(String reason) {
        if (reason == null || reason.trim().length() < 5) return "Say why the claim is rejected (at least 5 characters), so it can be fixed";
        return null;
    }

    static String label(String status) {
        return switch (status) {
            case "DRAFT" -> "a draft";
            case "PENDING" -> "waiting for approval";
            case "APPROVED" -> "approved";
            case "REJECTED" -> "rejected";
            case "REIMBURSED" -> "already paid out";
            case "CANCELLED" -> "withdrawn";
            default -> status.isEmpty() ? "unknown" : status.toLowerCase();
        };
    }
}
