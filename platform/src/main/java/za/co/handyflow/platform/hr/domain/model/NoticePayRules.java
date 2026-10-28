package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;

/**
 * Pay in place of notice: when it may be entered and how much. It is owed only when the employer ends employment with notice owed and did
 * not give all of it (BCEA section 38). The amount comes from {@link NoticeRules}; a person may be paid more than the days short, up to the
 * pay for the whole notice period, and never more. Pure rules, no database.
 */
public final class NoticePayRules {

    private NoticePayRules() {}

    public static final String DESCRIPTION = "Pay in place of notice";

    /**
     * Null when notice pay may be entered.
     *
     * @param owed the pay for the notice days not given (what the employer owes at least)
     * @param cap  the pay for the whole notice period (the most that may be entered)
     */
    public static String problem(String employeeStatus, String endType, BigDecimal amount, BigDecimal owed, BigDecimal cap, boolean alreadyEntered) {
        if (!"TERMINATED".equals(employeeStatus)) return "Notice pay is entered once employment has ended.";
        if (endType == null) return "Record how employment ended first. Notice pay is owed only when the employer ends it with notice owed.";
        if (!NoticeRules.EMPLOYER_NOTICE.equals(endType)) return "Employment ended by " + NoticeRules.typeLabel(endType) + ", which owes no pay in place of notice.";
        if (alreadyEntered) return "Notice pay is already entered for this employee.";
        if (owed == null || owed.signum() <= 0) return "The whole notice period was given, so no pay in place of it is owed.";
        if (amount == null || amount.signum() <= 0) return "Enter the amount.";
        if (cap != null && amount.compareTo(cap) > 0) return "That is more than the pay for the whole notice period (" + cap + ").";
        return null;
    }
}
