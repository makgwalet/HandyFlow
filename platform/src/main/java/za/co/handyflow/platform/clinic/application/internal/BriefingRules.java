package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.dto.PatientBriefing.Alert;
import za.co.handyflow.platform.clinic.dto.PatientBriefing.Recall;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure rules behind the patient briefing. Dates are the clinic's own (South African) calendar days. */
final class BriefingRules {

    static final ZoneId CLINIC_ZONE = RecallRules.CLINIC_ZONE;
    /** A visit that is over, as opposed to a draft or one still moving between nurse and doctor. */
    static final Set<String> FINISHED = Set.of("SIGNED", "LOCKED", "DOCTOR_COMPLETED");
    /** Appointments that are still ahead of the patient. */
    static final Set<String> UPCOMING = Set.of("SCHEDULED", "CONFIRMED");
    static final Set<String> SEVERE = Set.of("SEVERE", "LIFE_THREATENING");

    private BriefingRules() {}

    record Visit(UUID id, Instant at, String status, Integer followUpDays, UUID appointmentId) {}
    record Appt(UUID id, Instant at, String status) {}

    static boolean finished(String status) { return status != null && FINISHED.contains(status); }

    /** Finished visits only, newest first. */
    static List<Visit> finishedNewestFirst(List<Visit> all) {
        return all.stream().filter(v -> finished(v.status()))
                .sorted(Comparator.comparing(Visit::at).reversed()).toList();
    }

    /** Whole clinic-local calendar days between the two moments; never negative. */
    static long daysSince(Instant then, Instant now, ZoneId zone) {
        return Math.max(0, ChronoUnit.DAYS.between(then.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate()));
    }

    /** The earliest appointment still ahead (scheduled or confirmed) at or after now. */
    static Optional<Appt> nextAppointment(List<Appt> appts, Instant now) {
        return appts.stream()
                .filter(a -> a.at() != null && !a.at().isBefore(now) && UPCOMING.contains(a.status()))
                .min(Comparator.comparing(Appt::at));
    }

    /**
     * The follow-up promised at the last finished visit, if nobody has booked it yet. Same rule as the recall list:
     * a live appointment after that visit (not cancelled, not a no-show, not the visit's own) clears it.
     * Returns null when there is none. Not yet due is still returned (due = false) so the doctor sees it coming.
     */
    static Recall recall(Visit lastFinished, List<Appt> appts, LocalDate today, ZoneId zone) {
        if (lastFinished == null || lastFinished.followUpDays() == null) return null;
        boolean booked = appts.stream().anyMatch(a -> a.at() != null && a.at().isAfter(lastFinished.at())
                && !"CANCELLED".equals(a.status()) && !"NO_SHOW".equals(a.status())
                && (lastFinished.appointmentId() == null || !a.id().equals(lastFinished.appointmentId())));
        if (booked) return null;
        LocalDate due = RecallRules.dueDate(lastFinished.at(), lastFinished.followUpDays(), zone);
        return new Recall(due, RecallRules.overdueDays(due, today), RecallRules.isDue(due, today));
    }

    /** What the doctor must not miss, most serious first. */
    static List<Alert> alerts(List<String> severeAllergies, int unreviewedCritical, int unreviewedAbnormal,
                              Recall recall, boolean openDraft, int visitCount) {
        List<Alert> out = new ArrayList<>();
        if (!severeAllergies.isEmpty())
            out.add(new Alert("SEVERE_ALLERGY", "DANGER", "Severe allergy: " + String.join(", ", severeAllergies)));
        if (unreviewedCritical > 0)
            out.add(new Alert("CRITICAL_LAB", "DANGER",
                    unreviewedCritical + (unreviewedCritical == 1 ? " critical lab result" : " critical lab results") + " not yet reviewed"));
        if (unreviewedAbnormal > 0)
            out.add(new Alert("ABNORMAL_LAB", "WARNING",
                    unreviewedAbnormal + (unreviewedAbnormal == 1 ? " abnormal lab result" : " abnormal lab results") + " not yet reviewed"));
        if (recall != null && recall.overdueDays() > 0)
            out.add(new Alert("RECALL_OVERDUE", "WARNING", "Follow-up is " + recall.overdueDays()
                    + (recall.overdueDays() == 1 ? " day" : " days") + " overdue"));
        if (openDraft)
            out.add(new Alert("OPEN_DRAFT", "INFO", "A consultation for this patient is already in progress"));
        if (visitCount == 0)
            out.add(new Alert("FIRST_VISIT", "INFO", "No finished visits on record"));
        return out;
    }
}
