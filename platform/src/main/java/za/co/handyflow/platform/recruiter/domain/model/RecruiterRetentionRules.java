package za.co.handyflow.platform.recruiter.domain.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * How long an unsuccessful applicant's personal details are kept. POPIA asks that personal information is not kept longer than needed for the
 * purpose it was collected for; the law sets no number of months for applicants, so the company chooses (3 to 60) and this decides who is past it.
 * No database or framework in here.
 */
public final class RecruiterRetentionRules {

    private RecruiterRetentionRules() {}

    public static final int DEFAULT_MONTHS = 12, MIN_MONTHS = 3, MAX_MONTHS = 60;
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    /**
     * One of an applicant's applications, as far as retention is concerned. {@code reused} is an application opened from the talent pool on the strength of
     * an earlier agreement: while it is open it keeps their details, but once it is closed it does not start the retention period again, because the person
     * never agreed to be kept longer than the period that agreement gave.
     */
    public record App(String stage, boolean linkedToEmployee, Instant lastActivity, boolean reused) {
        public App(String stage, boolean linkedToEmployee, Instant lastActivity) { this(stage, linkedToEmployee, lastActivity, false); }
    }

    public static String monthsProblem(Integer months) {
        if (months == null || months < MIN_MONTHS || months > MAX_MONTHS)
            return "Keep applicant details for between " + MIN_MONTHS + " and " + MAX_MONTHS + " months.";
        return null;
    }

    /** Applications that can still lead to a hire keep the applicant's details; so does anyone who was hired. */
    private static boolean live(App a) {
        return a.linkedToEmployee() || !("REJECTED".equals(a.stage()) || "WITHDRAWN".equals(a.stage()));
    }

    /**
     * Null when the applicant's details may be removed: nothing live or hired, already not removed, and nothing has happened for longer than
     * the retention period. The period runs from the most recent of the applicant's last update and any application's last activity.
     */
    public static String removalProblem(boolean alreadyRemoved, Instant applicantUpdated, List<App> apps, int months, Instant now) {
        if (alreadyRemoved) return "This applicant's details were already removed.";
        for (App a : apps) if (live(a)) return "This applicant has an application that is still open, or was hired. Their details are needed.";
        Instant last = applicantUpdated;
        for (App a : apps) if (!a.reused() && a.lastActivity() != null && (last == null || a.lastActivity().isAfter(last))) last = a.lastActivity();
        if (last == null) return null;
        if (!dueOn(last, months).isBefore(now)) return "The retention period of " + months + " months has not ended yet (it ends " + dueOn(last, months) + ").";
        return null;
    }

    /** The moment the retention period ends. */
    public static Instant dueOn(Instant lastActivity, int months) {
        ZonedDateTime t = lastActivity.atZone(ZONE).plusMonths(months);
        return t.toInstant();
    }
}
