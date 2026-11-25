package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.Set;

/**
 * Rules for attendance. Attendance records who was in the room. It never says a learner is competent, and no threshold is applied here:
 * what counts as enough attendance for a programme is a decision for the provider, made elsewhere.
 */
public final class AttendanceRules {

    private AttendanceRules() {
    }

    public static final Set<String> STATUSES = Set.of("PRESENT", "LATE", "ABSENT", "EXCUSED");

    public static boolean attended(String status) {
        return "PRESENT".equals(status) || "LATE".equals(status);
    }

    /** Why attendance cannot be recorded for this learner on this slot, or null. */
    public static String recordProblem(String slotStatus, LocalDate slotDate, LocalDate today, String memberStatus, String status, String note) {
        if (!"SCHEDULED".equals(slotStatus)) {
            return "Attendance cannot be recorded for a cancelled slot";
        }
        if (slotDate.isAfter(today)) {
            return "This slot is on " + slotDate + ". Attendance can only be recorded on or after the day";
        }
        if (!"ACTIVE".equals(memberStatus)) {
            return "This learner has been removed from the cohort";
        }
        if (status == null || !STATUSES.contains(status)) {
            return "Attendance must be present, late, absent or excused";
        }
        if ("EXCUSED".equals(status) && !CohortRules.longEnough(note, CohortRules.MIN_REASON)) {
            return "Say why the learner is excused, in at least " + CohortRules.MIN_REASON + " characters";
        }
        return null;
    }

    /** Why a recorded entry cannot be corrected this way, or null. A correction always carries a written reason. */
    public static String correctionProblem(String from, String to, String oldNote, String newNote, String reason) {
        if (to == null || !STATUSES.contains(to)) {
            return "Attendance must be present, late, absent or excused";
        }
        if (from.equals(to) && java.util.Objects.equals(blankToNull(oldNote), blankToNull(newNote))) {
            return "Nothing has changed";
        }
        if ("EXCUSED".equals(to) && !CohortRules.longEnough(newNote, CohortRules.MIN_REASON)) {
            return "Say why the learner is excused, in at least " + CohortRules.MIN_REASON + " characters";
        }
        return CohortRules.reasonProblem(reason);
    }

    /**
     * Counts for one learner. The rate is attended over attended plus absent, so slots that are excused or not yet recorded are left out
     * of it rather than counted against the learner. It is null until there is something to measure. It is information, not a result.
     */
    public record Summary(int expected, int attended, int absent, int excused, int unrecorded, Integer ratePercent) {
    }

    public static Summary summary(int expected, int attended, int absent, int excused) {
        int unrecorded = Math.max(0, expected - attended - absent - excused);
        int measured = attended + absent;
        Integer rate = measured == 0 ? null : (int) Math.round(attended * 100.0 / measured);
        return new Summary(expected, attended, absent, excused, unrecorded, rate);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
