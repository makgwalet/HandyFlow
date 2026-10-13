package za.co.handyflow.platform.expenses.application.internal;

import java.util.Locale;
import java.util.UUID;

/**
 * Who may do what with an expense claim (finance patch F1, 2026-10-13). Pure, so it can be tested without Spring.
 * Before this, anyone with USER_UPDATE could approve any claim, including their own, and anyone with USER_READ saw every claim.
 */
public final class ExpenseRules {

    private ExpenseRules() {}

    /**
     * Null when the approver may approve the claim, otherwise why not. A claim cannot be approved by the person who submitted it,
     * nor by the employee it pays (matched by email, because an HR employee record carries an email rather than a user id).
     */
    public static String selfApprovalProblem(UUID submittedBy, UUID approver, String employeeEmail, String approverEmail) {
        if (approver == null) return "The approver is not known, so the claim cannot be approved";
        if (approver.equals(submittedBy)) return "You submitted this claim. Someone else must approve it";
        String e = norm(employeeEmail), a = norm(approverEmail);
        if (e != null && e.equals(a)) return "This claim pays you. Someone else must approve it";
        return null;
    }

    /** Someone without EXPENSES_READ sees only the claims they submitted. */
    public static boolean maySee(boolean seesAll, UUID submittedBy, UUID viewer) {
        return seesAll || (viewer != null && viewer.equals(submittedBy));
    }

    /** A receipt may be added by the claimant, or by someone who manages claims. */
    public static boolean mayAttach(boolean manages, UUID submittedBy, UUID user) {
        return manages || (user != null && user.equals(submittedBy));
    }

    private static String norm(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT);
    }
}
