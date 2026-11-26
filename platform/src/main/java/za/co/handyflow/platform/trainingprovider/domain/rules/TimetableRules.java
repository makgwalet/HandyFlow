package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Rules for timetable slots. Pure functions: each returns a plain-words problem or null. */
public final class TimetableRules {

    private TimetableRules() {
    }

    public static final String DEFAULT_ZONE = "Africa/Johannesburg";
    public static final int MIN_REASON = 10;
    public static final String VENUE = "VENUE";
    public static final String PRACTITIONER = "PRACTITIONER";
    public static final String COHORT = "COHORT";
    public static final String LEARNER = "LEARNER";

    /** One booking that would be double-booked. Equipment and rooms-as-resources use the same shape when they exist. */
    public record Clash(String resourceType, UUID resourceId, String resourceLabel, UUID otherSlotId, UUID otherCohortId, String message) {
    }

    /** Why a time zone id cannot be used, or null. */
    public static String zoneProblem(String zone) {
        if (zone == null || zone.isBlank()) {
            return "Give a time zone, for example " + DEFAULT_ZONE;
        }
        try {
            ZoneId.of(zone.trim());
            return null;
        } catch (DateTimeException e) {
            return "\"" + zone.trim() + "\" is not a time zone the system knows. Use a name such as " + DEFAULT_ZONE;
        }
    }

    /** The exact moment a wall-clock date and time is, in the given zone. A time that does not exist on a clock-change day moves forward. */
    public static Instant instant(LocalDate date, LocalTime time, String zone) {
        return date.atTime(time).atZone(ZoneId.of(zone)).toInstant();
    }

    /** Two moments-in-time ranges clash when each starts before the other ends. Back-to-back bookings do not clash. */
    public static boolean overlaps(Instant start1, Instant end1, Instant start2, Instant end2) {
        return start1.isBefore(end2) && start2.isBefore(end1);
    }

    public static boolean reasonOk(String reason) {
        return CohortRules.longEnough(reason, MIN_REASON);
    }

    /**
     * Why clashes cannot be accepted as they are, or null when there are none or they are properly overridden. A clash is accepted only with a
     * written reason from someone who may approve it. Nothing is ever double-booked silently.
     */
    public static String overrideProblem(List<Clash> clashes, String reason, boolean mayApprove) {
        if (clashes == null || clashes.isEmpty()) {
            return null;
        }
        String listed = String.join("; ", clashes.stream().map(Clash::message).toList());
        if (!reasonOk(reason)) {
            return listed + ". To book it anyway an administrator must approve it with a written reason of at least " + MIN_REASON + " characters";
        }
        if (!mayApprove) {
            return listed + ". Only an administrator can approve a clash";
        }
        return null;
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
