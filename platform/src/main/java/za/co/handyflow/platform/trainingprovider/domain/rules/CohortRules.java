package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Rules for cohorts, venues, members and facilitators. Pure functions: each returns a plain-words problem or null. */
public final class CohortRules {

    private CohortRules() {
    }

    public static final int MIN_REASON = 10;
    public static final String PLANNED = "PLANNED";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    private static final Map<String, Set<String>> NEXT = Map.of(
            PLANNED, Set.of(RUNNING, CANCELLED),
            RUNNING, Set.of(COMPLETED, CANCELLED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of());

    public static Set<String> nextStatuses(String from) {
        return NEXT.getOrDefault(from, Set.of());
    }

    public static boolean longEnough(String s, int n) {
        return s != null && s.trim().length() >= n;
    }

    public static String reasonProblem(String reason) {
        return longEnough(reason, MIN_REASON) ? null : "Say why, in at least " + MIN_REASON + " characters";
    }

    public static boolean open(String status) {
        return PLANNED.equals(status) || RUNNING.equals(status);
    }

    public static String transitionProblem(String from, String to, String reason) {
        if (!nextStatuses(from).contains(to)) {
            return "A " + from.toLowerCase() + " cohort cannot move to " + to.toLowerCase();
        }
        if (CANCELLED.equals(to)) {
            return reasonProblem(reason);
        }
        return null;
    }

    public static String cohortProblem(String name, LocalDate start, LocalDate end, Integer capacity) {
        if (!longEnough(name, 2)) {
            return "Give the cohort a name";
        }
        if (start == null) {
            return "Enter the start date";
        }
        if (end != null && end.isBefore(start)) {
            return "The end date cannot be before the start date";
        }
        if (capacity != null && capacity < 1) {
            return "Capacity must be at least 1";
        }
        return null;
    }

    public static String venueProblem(String name, Integer capacity) {
        if (!longEnough(name, 2)) {
            return "Give the venue a name";
        }
        if (capacity != null && capacity < 1) {
            return "Capacity must be at least 1";
        }
        return null;
    }

    /**
     * Why an enrolment cannot join a cohort, or null. The cohort and the enrolment must be on the same programme version and site,
     * because each was frozen to what was approved on the day it was made.
     */
    public static String memberProblem(String cohortStatus, UUID cohortVersionId, UUID cohortSiteId, String enrolmentStatus,
                                       UUID enrolmentVersionId, UUID enrolmentSiteId, boolean inAnotherLiveCohort, long activeMembers, Integer capacity) {
        if (!open(cohortStatus)) {
            return "Learners can only be added to a planned or running cohort";
        }
        if (!"ENROLLED".equals(enrolmentStatus) && !"IN_PROGRESS".equals(enrolmentStatus)) {
            return "Only a learner who is enrolled or in progress can join a cohort";
        }
        if (!cohortVersionId.equals(enrolmentVersionId)) {
            return "This enrolment is on a different version of the programme than the cohort";
        }
        if (!cohortSiteId.equals(enrolmentSiteId)) {
            return "This enrolment is at a different site than the cohort";
        }
        if (inAnotherLiveCohort) {
            return "This enrolment is already in a cohort. Remove it from that cohort first";
        }
        if (capacity != null && activeMembers >= capacity) {
            return "The cohort is full (" + capacity + ")";
        }
        return null;
    }

    /** Why a person cannot be assigned to teach a cohort, or null. Standing comes from the practitioner rules, never from a typed number. */
    public static String facilitatorProblem(String cohortStatus, boolean personActive, PractitionerRules.Standing standing, boolean alreadyAssigned) {
        if (!open(cohortStatus)) {
            return "Facilitators can only be assigned to a planned or running cohort";
        }
        if (!personActive) {
            return "This person is inactive";
        }
        if (alreadyAssigned) {
            return "This person is already a facilitator on the cohort";
        }
        if (standing != PractitionerRules.Standing.VERIFIED_IN_FORCE) {
            return "This person has no checked, in-force facilitator appointment for the programme (" + standing.note() + ")";
        }
        return null;
    }
}
