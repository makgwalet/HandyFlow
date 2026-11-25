package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.time.LocalTime;

/** Rules for timetable slots. Pure functions: each returns a plain-words problem or null. */
public final class TimetableRules {

    private TimetableRules() {
    }

    /** Two blocks on the same day clash when each starts before the other ends. Back-to-back blocks do not clash. */
    public static boolean overlaps(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && start2.isBefore(end1);
    }

    /** Why a slot's date and times are wrong for its cohort, or null. */
    public static String slotProblem(String title, LocalDate date, LocalTime start, LocalTime end, String cohortStatus, LocalDate cohortStart, LocalDate cohortEnd) {
        if (!CohortRules.open(cohortStatus)) {
            return "The timetable of a " + cohortStatus.toLowerCase() + " cohort cannot be changed";
        }
        if (!CohortRules.longEnough(title, 2)) {
            return "Give the slot a title";
        }
        if (date == null || start == null || end == null) {
            return "Enter the date, start time and end time";
        }
        if (!end.isAfter(start)) {
            return "The end time must be after the start time";
        }
        if (date.isBefore(cohortStart)) {
            return "The slot is before the cohort starts on " + cohortStart;
        }
        if (cohortEnd != null && date.isAfter(cohortEnd)) {
            return "The slot is after the cohort ends on " + cohortEnd;
        }
        return null;
    }

    /** Why a venue cannot be used for the slot, or null. A venue at another site is never silently accepted. */
    public static String venueProblem(boolean venueActive, java.util.UUID venueSiteId, java.util.UUID cohortSiteId, Integer venueCapacity, long activeLearners) {
        if (!venueActive) {
            return "That venue is inactive";
        }
        if (!venueSiteId.equals(cohortSiteId)) {
            return "That venue is at a different site than the cohort";
        }
        if (venueCapacity != null && activeLearners > venueCapacity) {
            return "The venue holds " + venueCapacity + " but the cohort has " + activeLearners + " learners";
        }
        return null;
    }

    /** Why the facilitator cannot teach on the slot's date, or null. Standing is worked out for that date, not for today. */
    public static String facilitatorProblem(boolean assignedToCohort, PractitionerRules.Standing standingOnDate, LocalDate date) {
        if (!assignedToCohort) {
            return "That person is not a facilitator on this cohort. Assign them to the cohort first";
        }
        if (standingOnDate != PractitionerRules.Standing.VERIFIED_IN_FORCE) {
            return "That facilitator's appointment is not checked and in force on " + date + " (" + standingOnDate.note() + ")";
        }
        return null;
    }
}
