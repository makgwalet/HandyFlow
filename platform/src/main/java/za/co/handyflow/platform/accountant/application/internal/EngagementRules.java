package za.co.handyflow.platform.accountant.application.internal;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Review and sign-off of a client's financial year (finance patch F15). Pure, so it is tested without Spring.
 * <p>
 * A financial year is named by the calendar year it ends in. A client with a February year end has FY 2026 running from
 * 1 March 2025 to 28 February 2026; December year end is the calendar year. The engagement moves OPEN, PREPARED (by the
 * preparer), REVIEWED (by someone else), SIGNED_OFF (by a partner who did not prepare it). A signed-off year takes no more
 * journals until it is reopened with a reason.
 */
public final class EngagementRules {

    private EngagementRules() {}

    public static final String OPEN = "OPEN", PREPARED = "PREPARED", REVIEWED = "REVIEWED", SIGNED_OFF = "SIGNED_OFF";
    public static final int MIN_REASON = 10;

    /** The calendar year in which the financial year containing this month ends. */
    public static int financialYearOf(int yearEndMonth, int periodYear, int periodMonth) {
        return periodMonth <= yearEndMonth ? periodYear : periodYear + 1;
    }

    public static LocalDate start(int yearEndMonth, int financialYear) {
        return yearEndMonth == 12 ? LocalDate.of(financialYear, 1, 1)
                : LocalDate.of(financialYear - 1, yearEndMonth + 1, 1);
    }

    public static LocalDate end(int yearEndMonth, int financialYear) {
        return YearMonth.of(financialYear, yearEndMonth).atEndOfMonth();
    }

    /** Null when the year can be marked prepared, otherwise why not. */
    public static String prepareProblem(String status, long unpostedJournals) {
        if (!OPEN.equals(status)) return "Only an open year can be marked prepared (it is " + status + ")";
        if (unpostedJournals > 0)
            return unpostedJournals + (unpostedJournals == 1 ? " journal in this year is" : " journals in this year are")
                    + " not posted yet. Post or reverse them first";
        return null;
    }

    /** Null when {@code reviewer} may mark the prepared year reviewed, otherwise why not. */
    public static String reviewProblem(String status, UUID preparedBy, UUID reviewer) {
        if (!PREPARED.equals(status)) return "Only a prepared year can be reviewed (it is " + status + ")";
        if (preparedBy != null && preparedBy.equals(reviewer))
            return "You prepared this year. Someone else must review it";
        return null;
    }

    /**
     * Null when {@code signer} may sign off, otherwise why not. The preparer cannot sign off their own work unless nobody
     * else holds the sign-off permission (a one-person practice), in which case it is allowed and logged.
     */
    public static String signOffProblem(String status, UUID preparedBy, UUID signer, boolean otherSignersExist) {
        if (!REVIEWED.equals(status)) return "Only a reviewed year can be signed off (it is " + status + ")";
        if (preparedBy != null && preparedBy.equals(signer) && otherSignersExist)
            return "You prepared this year. A different partner must sign it off";
        return null;
    }

    /** True when the preparer is signing off their own work because nobody else can, so the log says so. */
    public static boolean soleSigner(UUID preparedBy, UUID signer, boolean otherSignersExist) {
        return preparedBy != null && preparedBy.equals(signer) && !otherSignersExist;
    }

    /** Null when the year may be sent back (prepared or reviewed) or reopened (signed off), otherwise why not. */
    public static String reopenProblem(String status, String reason) {
        if (OPEN.equals(status)) return "The year is already open";
        if (reason == null || reason.trim().length() < MIN_REASON)
            return "Say why the year is being reopened (at least " + MIN_REASON + " characters)";
        if (reason.trim().length() > 500) return "Keep the reason to 500 characters";
        return null;
    }

    /** True when no journal may be added for a month in a year with this status. */
    public static boolean locksJournals(String status) {
        return SIGNED_OFF.equals(status);
    }

    /** Things to look at before signing off that do not stop it. */
    public static List<String> warnings(long overdueDeadlines, boolean ficaComplete, long openDocumentRequests) {
        List<String> out = new ArrayList<>();
        if (overdueDeadlines > 0)
            out.add(overdueDeadlines + (overdueDeadlines == 1 ? " tax deadline is" : " tax deadlines are") + " overdue");
        if (!ficaComplete) out.add("FICA is not complete for this client");
        if (openDocumentRequests > 0)
            out.add(openDocumentRequests + (openDocumentRequests == 1 ? " document request is" : " document requests are") + " still open");
        return out;
    }
}
