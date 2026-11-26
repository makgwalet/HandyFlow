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

    /** Whether the provider is covered on one day. Comes from the accreditation rules; "covered" is never true for pending or lapsed evidence. */
    public record Cover(boolean covered, String outcome, String message) {
    }

    /** What is known about delivering a programme at a site, for a cohort. coverageAtStart and coverageAtEnd are null unless the programme is accredited. */
    public record DeliveryFacts(boolean programmeActive, boolean siteActive, boolean offeredAtSite, LocalDate offeredFrom,
                                boolean accredited, Cover coverageAtStart, Cover coverageAtEnd) {
    }

    /**
     * Why a cohort cannot be run for this programme at this site on these dates, or null. The programme must be offered at the site
     * (a live offering), the site and programme must be active, and an accredited programme must be covered by an approval in force
     * at the site on the first and last day. A draft, submitted, conditional-only-on-paper or lapsed approval is not cover.
     */
    public static String deliveryProblem(DeliveryFacts f, LocalDate start, LocalDate end) {
        if (!f.programmeActive()) {
            return "The programme is not active";
        }
        if (!f.siteActive()) {
            return "That site is not active";
        }
        if (!f.offeredAtSite()) {
            return "This programme is not offered at that site. Add an offering for it at the site first";
        }
        if (f.offeredFrom() != null && start != null && start.isBefore(f.offeredFrom())) {
            return "The programme is offered at this site from " + f.offeredFrom() + ", which is after the cohort starts on " + start;
        }
        if (f.accredited()) {
            if (f.coverageAtStart() == null || !f.coverageAtStart().covered()) {
                return "The cohort cannot start on " + start + ": " + (f.coverageAtStart() == null ? "no approval was checked" : f.coverageAtStart().message());
            }
            if (end != null && (f.coverageAtEnd() == null || !f.coverageAtEnd().covered())) {
                return "The cohort cannot run to " + end + ": " + (f.coverageAtEnd() == null ? "no approval was checked" : f.coverageAtEnd().message());
            }
        }
        return null;
    }

    /** A plain-words caveat when the cohort is allowed but rests on a conditional or renewal-pending approval, otherwise null. */
    public static String deliveryCaveat(DeliveryFacts f) {
        if (!f.accredited()) {
            return null;
        }
        for (Cover c : new Cover[]{f.coverageAtStart(), f.coverageAtEnd()}) {
            if (c != null && c.covered() && ("COVERED_WITH_CONDITIONS".equals(c.outcome()) || "COVERED_RENEWAL_PENDING".equals(c.outcome()))) {
                return c.message();
            }
        }
        return null;
    }
}
