package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle and entry rules for applications and learner enrolments.
 * A person records the entry check; nothing here decides eligibility. Enrolling into an accredited programme
 * needs current cover at the site, or an administrator's written override. Attendance, completion and
 * certification are not decided here: they come with assessment.
 */
public final class EnrolmentRules {

    public static final int MIN_REASON = 10;

    // applications
    public static final String RECEIVED = "RECEIVED";
    public static final String UNDER_REVIEW = "UNDER_REVIEW";
    public static final String DOCUMENTS_REQUESTED = "DOCUMENTS_REQUESTED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String ENROLLED = "ENROLLED";
    // enrolments
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String ON_HOLD = "ON_HOLD";
    public static final String CANCELLED = "CANCELLED";

    private static final Map<String, Set<String>> APP_NEXT = Map.of(
            RECEIVED, Set.of(UNDER_REVIEW, DECLINED, WITHDRAWN),
            UNDER_REVIEW, Set.of(DOCUMENTS_REQUESTED, ACCEPTED, DECLINED, WITHDRAWN),
            DOCUMENTS_REQUESTED, Set.of(UNDER_REVIEW, DECLINED, WITHDRAWN),
            ACCEPTED, Set.of(WITHDRAWN),
            DECLINED, Set.of(),
            WITHDRAWN, Set.of(),
            ENROLLED, Set.of());
    private static final Set<String> APP_NEEDS_REASON = Set.of(DECLINED, WITHDRAWN, DOCUMENTS_REQUESTED);

    private static final Map<String, Set<String>> ENROL_NEXT = Map.of(
            ENROLLED, Set.of(IN_PROGRESS, ON_HOLD, WITHDRAWN, CANCELLED),
            IN_PROGRESS, Set.of(ON_HOLD, WITHDRAWN),
            ON_HOLD, Set.of(IN_PROGRESS, WITHDRAWN),
            WITHDRAWN, Set.of(),
            CANCELLED, Set.of());
    private static final Set<String> ENROL_NEEDS_REASON = Set.of(ON_HOLD, WITHDRAWN, CANCELLED);
    private static final Set<String> LIVE = Set.of(ENROLLED, IN_PROGRESS, ON_HOLD);
    private static final Set<String> COVERED = Set.of("COVERED", "COVERED_WITH_CONDITIONS", "COVERED_RENEWAL_PENDING");

    private EnrolmentRules() {
    }

    public static Set<String> applicationNext(String from) {
        return APP_NEXT.getOrDefault(from, Set.of());
    }

    public static Set<String> enrolmentNext(String from) {
        return ENROL_NEXT.getOrDefault(from, Set.of());
    }

    public static boolean isLive(String enrolmentStatus) {
        return LIVE.contains(enrolmentStatus);
    }

    public static boolean isCovered(String outcome) {
        return outcome != null && COVERED.contains(outcome);
    }

    private static boolean longEnough(String s) {
        return s != null && s.trim().length() >= MIN_REASON;
    }

    /**
     * Why an application cannot move on, or null. ACCEPTED needs a person's recorded entry check; if the learner
     * does not meet the entry requirements as checked, acceptance needs a written reason. ENROLLED is only ever set
     * by enrolling, never by hand.
     */
    public static String applicationProblem(String from, String to, String reason, Boolean entryMet, String entryNote, String overrideReason) {
        if (to == null || !APP_NEXT.containsKey(to)) {
            return "Unknown status " + to;
        }
        if (ENROLLED.equals(to)) {
            return "An application becomes enrolled when the learner is enrolled";
        }
        if (!applicationNext(from).contains(to)) {
            return "An application cannot move from " + from + " to " + to;
        }
        if (APP_NEEDS_REASON.contains(to) && !longEnough(reason)) {
            return "Say why, in at least " + MIN_REASON + " characters";
        }
        if (ACCEPTED.equals(to)) {
            if (entryMet == null) {
                return "Record whether the learner meets the entry requirements, as checked by a person";
            }
            if (!longEnough(entryNote)) {
                return "Say what was checked for entry, in at least " + MIN_REASON + " characters";
            }
            if (!entryMet && !longEnough(overrideReason)) {
                return "The entry requirements are not met. To accept anyway, say why in at least " + MIN_REASON + " characters";
            }
        }
        return null;
    }

    public static String enrolmentTransitionProblem(String from, String to, String reason) {
        if (to == null || !ENROL_NEXT.containsKey(to)) {
            return "Unknown status " + to;
        }
        if (!enrolmentNext(from).contains(to)) {
            return "An enrolment cannot move from " + from + " to " + to;
        }
        if (ENROL_NEEDS_REASON.contains(to) && !longEnough(reason)) {
            return "Say why, in at least " + MIN_REASON + " characters";
        }
        return null;
    }

    /**
     * Why a learner cannot be enrolled, or null. Accredited programmes need cover at the site on the start date,
     * unless an administrator records why not.
     */
    public static String enrolProblem(boolean learnerActive, String programmeStatus, boolean hasApprovedVersion, boolean offeredAtSite,
                                      String activity, String coverOutcome, boolean admin, String overrideReason,
                                      LocalDate start, LocalDate expectedEnd) {
        if (!learnerActive) {
            return "This learner is archived";
        }
        if (!"ACTIVE".equals(programmeStatus)) {
            return "Only an active programme can take enrolments";
        }
        if (!hasApprovedVersion) {
            return "This programme has no approved version yet";
        }
        if (!offeredAtSite) {
            return "This programme is not offered at that site";
        }
        if (start == null) {
            return "Enter the start date";
        }
        if (expectedEnd != null && expectedEnd.isBefore(start)) {
            return "The expected end date cannot be before the start date";
        }
        if ("ACCREDITED".equals(activity) && !isCovered(coverOutcome)) {
            if (!longEnough(overrideReason)) {
                return "No current accreditation covers this programme at that site. Only an administrator can enrol anyway, with a written reason";
            }
            if (!admin) {
                return "Only an administrator can enrol into an accredited programme that is not covered";
            }
        }
        return null;
    }
}
