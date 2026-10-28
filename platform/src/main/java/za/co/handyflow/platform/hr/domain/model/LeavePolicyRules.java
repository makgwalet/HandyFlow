package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The company policy on carrying annual leave into a new year. Leave may only be forfeited by agreement (BCEA section 20(7)), so a cap
 * cannot be set without naming the agreement, and it only applies to rollovers that have not happened yet. Pure: no loading, no saving.
 */
public final class LeavePolicyRules {

    private LeavePolicyRules() {}

    public static final BigDecimal MAX_CAP = new BigDecimal("100");

    /** Carried leave can be made to expire no sooner than the six months the BCEA gives after a leave cycle ends (section 20(2)). */
    public static final int MIN_EXPIRY_MONTHS = 6, MAX_EXPIRY_MONTHS = 24;

    /** Null when the policy may be saved. A null cap and a null expiry remove the policy. */
    public static String policyProblem(BigDecimal cap, Integer effectiveYear, String agreementRef, int thisYear) {
        return policyProblem(cap, null, effectiveYear, agreementRef, thisYear);
    }

    /** As above, for a policy that may also make leave carried into a year expire after a number of months. */
    public static String policyProblem(BigDecimal cap, Integer expiryMonths, Integer effectiveYear, String agreementRef, int thisYear) {
        if (cap == null && expiryMonths == null) return null;
        if (cap != null) {
            if (cap.signum() < 0) return "The most that can be carried over cannot be negative.";
            if (cap.compareTo(MAX_CAP) > 0) return "The most that can be carried over cannot be more than " + MAX_CAP.toBigInteger() + " days.";
            if (cap.scale() > 2) return "Use at most two decimals for the days.";
        }
        if (expiryMonths != null && (expiryMonths < MIN_EXPIRY_MONTHS || expiryMonths > MAX_EXPIRY_MONTHS))
            return "Carried leave can expire after " + MIN_EXPIRY_MONTHS + " to " + MAX_EXPIRY_MONTHS + " months. Employees have six months after a leave cycle to take their leave.";
        if (effectiveYear == null) return "Say from which year the limit applies.";
        if (effectiveYear <= thisYear)
            return "The limit can only start from next year (" + (thisYear + 1) + ") or later; leave already carried into " + thisYear + " cannot be changed.";
        if (effectiveYear > thisYear + 5) return "That is too far ahead.";
        if (agreementRef == null || agreementRef.isBlank())
            return "Leave can only be forfeited by written agreement. Say which agreement or policy document allows it.";
        if (agreementRef.trim().length() > 200) return "The agreement reference is too long (200 characters at most).";
        return null;
    }

    /** The cap that applies to the rollover into the given year, or null when there is none. */
    public static BigDecimal capFor(BigDecimal cap, Integer effectiveYear, int rollIntoYear) {
        if (cap == null || effectiveYear == null || rollIntoYear < effectiveYear) return null;
        return cap;
    }

    /** Days that go when the year closes with this balance under the cap; zero when nothing is lost or there is no cap. */
    public static BigDecimal forfeited(BigDecimal closing, BigDecimal cap) {
        if (cap == null || closing == null || closing.compareTo(cap) <= 0) return BigDecimal.ZERO;
        return closing.subtract(cap).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Days an employee would lose at the coming rollover if no more leave were taken: today's balance plus what is still to be earned
     * this year, less the cap.
     */
    public static BigDecimal atRisk(BigDecimal availableNow, BigDecimal stillToEarn, BigDecimal cap) {
        if (cap == null) return BigDecimal.ZERO;
        BigDecimal projected = (availableNow == null ? BigDecimal.ZERO : availableNow).add(stillToEarn == null ? BigDecimal.ZERO : stillToEarn);
        return forfeited(projected, cap);
    }

    public static String forfeitNote(BigDecimal cap, String agreementRef, int fromYear) {
        return "Over the " + cap.stripTrailingZeros().toPlainString() + "-day limit for carrying leave into " + (fromYear + 1)
                + (agreementRef == null || agreementRef.isBlank() ? "" : " (" + agreementRef.trim() + ")");
    }

    /** The number of months carried leave lasts in the given year, or null when it does not expire. */
    public static Integer expiryFor(Integer expiryMonths, Integer effectiveYear, int carriedIntoYear) {
        if (expiryMonths == null || effectiveYear == null || carriedIntoYear < effectiveYear) return null;
        return expiryMonths;
    }

    /** The last day carried leave can be used: the end of the Nth month of the year it was carried into. */
    public static java.time.LocalDate expiryDate(int carriedIntoYear, int months) {
        return java.time.LocalDate.of(carriedIntoYear, 1, 1).plusMonths(months).minusDays(1);
    }

    /**
     * Carried days still unused on the expiry date. Leave taken in the new year uses the carried days first, so only what is left of them
     * goes. {@code takenInWindow} is a positive number of days taken from 1 January to the expiry date, reversals netted off.
     */
    public static BigDecimal unusedCarried(BigDecimal carried, BigDecimal takenInWindow) {
        if (carried == null || carried.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal left = carried.subtract(takenInWindow == null ? BigDecimal.ZERO : takenInWindow.max(BigDecimal.ZERO));
        return left.signum() <= 0 ? BigDecimal.ZERO : left.setScale(2, RoundingMode.HALF_UP);
    }

    public static String expireNote(int months, String agreementRef, int carriedIntoYear) {
        return "Leave carried into " + carriedIntoYear + " not used within " + months + " months"
                + (agreementRef == null || agreementRef.isBlank() ? "" : " (" + agreementRef.trim() + ")");
    }
}
