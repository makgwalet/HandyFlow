package za.co.handyflow.platform.ap.application.internal;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Separation of duties in Accounts Payable (finance patch F6, 2026-10-14). Pure, so it is tested without Spring.
 * <p>
 * The person who captured a bill does not approve it; the second approver is not the first; the person who captured a
 * bill does not pay it; the person who made a payment batch does not release it; and changed supplier bank details are
 * checked by a second person before money goes to them.
 * <p>
 * A business where nobody else holds the permission for a step cannot have a second person. Then the step is allowed,
 * because blocking it would stop the business paying its bills, and it is logged as an exception.
 */
public final class ApDutyRules {

    public enum Duty {
        APPROVE_OWN_BILL("You captured this bill, so someone else must approve it"),
        SECOND_APPROVAL("You gave the first approval, so someone else must give the second"),
        PAY_OWN_BILL("You captured this bill, so someone else must pay it"),
        RELEASE_OWN_BATCH("You made this payment batch, so someone else must release it"),
        VERIFY_OWN_BANKING("You entered these bank details, so someone else must check them");

        final String message;
        Duty(String message) { this.message = message; }
    }

    private ApDutyRules() {}

    /**
     * Null when {@code actor} may do the step, otherwise why not. {@code earlier} is whoever did the step this one must
     * be separate from; {@code otherHolders} counts the other active people who hold the permission for this step.
     */
    public static String problem(Duty duty, UUID earlier, UUID actor, long otherHolders) {
        if (earlier == null || actor == null || !earlier.equals(actor)) return null;
        if (otherHolders <= 0) return null;
        return duty.message + (otherHolders == 1 ? " (one other person can)" : " (" + otherHolders + " other people can)");
    }

    /** True when the step is allowed only because nobody else can do it; log it. */
    public static boolean soleException(UUID earlier, UUID actor, long otherHolders) {
        return earlier != null && earlier.equals(actor) && otherHolders <= 0;
    }

    /** True when the details money is paid to have changed (bank, holder, account, branch). */
    public static boolean bankingChanged(String bank, String holder, String account, String branch,
                                         String newBank, String newHolder, String newAccount, String newBranch) {
        return !same(bank, newBank) || !same(holder, newHolder) || !same(account, newAccount) || !same(branch, newBranch);
    }

    /** Null when every supplier in a payment has checked bank details, otherwise which are waiting. */
    public static String unverifiedProblem(List<String> unverifiedSuppliers) {
        if (unverifiedSuppliers == null || unverifiedSuppliers.isEmpty()) return null;
        List<String> names = unverifiedSuppliers.stream().filter(Objects::nonNull).distinct().sorted().toList();
        return "Bank details waiting for a second person to check: " + String.join(", ", names)
                + ". Check them under Supplier banking before paying";
    }

    private static boolean same(String a, String b) {
        String x = a == null ? "" : a.replaceAll("\\s+", "").toLowerCase();
        String y = b == null ? "" : b.replaceAll("\\s+", "").toLowerCase();
        return x.equals(y);
    }
}
