// security/application/internal/GuardScoreCalculator.java
package za.co.handyflow.platform.security.application.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * The operational score out of 100, with the working shown. Pure: no framework, no clock, so it can be tested directly.
 *
 * Seven components carry fixed weights (total 100). A component with too little evidence is left out and the score
 * is taken over the components that have evidence, so a new guard is not marked down for data that does not exist yet.
 * If less than MIN_COVERAGE points of evidence exist there is no score at all, only "not enough data".
 * The score describes the last window of records; it never decides anything about employment.
 */
public final class GuardScoreCalculator {

    private GuardScoreCalculator() {}

    public static final int MIN_SHIFTS = 3;
    public static final int MIN_COVERAGE = 40;

    public record Facts(
            int completedShifts, int pulledShifts, int missedShifts, int lateShifts,
            int patrolShiftsRequired, int patrolShiftsMet,
            int incidentsLow, int incidentsMedium, int incidentsHigh, int incidentsCritical,
            int substantiatedComplaints, Integer readinessPercent,
            int ratingCount, double ratingAverage) {}

    public record Component(String key, String label, int weight, boolean hasData, double fraction, double points, String detail) {}

    public record Result(Integer score, String band, int coverage, List<Component> components) {}

    public static Result calculate(Facts f) {
        int attended = f.completedShifts() + f.pulledShifts();
        int scheduledPast = attended + f.missedShifts();
        boolean enoughShifts = scheduledPast >= MIN_SHIFTS;
        List<Component> c = new ArrayList<>();

        c.add(enoughShifts
                ? comp("ATTENDANCE", "Attendance", 20, (double) attended / scheduledPast, attended + " of " + scheduledPast + " shifts worked")
                : none("ATTENDANCE", "Attendance", 20, "fewer than " + MIN_SHIFTS + " shifts in the period"));

        c.add(attended >= MIN_SHIFTS
                ? comp("PUNCTUALITY", "Punctuality", 10, 1.0 - (double) Math.min(f.lateShifts(), attended) / attended,
                        f.lateShifts() == 0 ? "no late arrivals" : f.lateShifts() + " late of " + attended + " shifts")
                : none("PUNCTUALITY", "Punctuality", 10, "fewer than " + MIN_SHIFTS + " shifts worked"));

        c.add(f.patrolShiftsRequired() >= MIN_SHIFTS
                ? comp("PATROL", "Checkpoint compliance", 15, (double) f.patrolShiftsMet() / f.patrolShiftsRequired(),
                        f.patrolShiftsMet() + " of " + f.patrolShiftsRequired() + " shifts met the required scans")
                : none("PATROL", "Checkpoint compliance", 15, "fewer than " + MIN_SHIFTS + " shifts with a scan requirement"));

        double incidentPenalty = 0.10 * f.incidentsLow() + 0.25 * f.incidentsMedium() + 0.50 * f.incidentsHigh() + 1.0 * f.incidentsCritical();
        int incidents = f.incidentsLow() + f.incidentsMedium() + f.incidentsHigh() + f.incidentsCritical();
        c.add(enoughShifts
                ? comp("INCIDENTS", "Incidents", 10, Math.max(0, 1 - incidentPenalty), incidents == 0 ? "no incidents" : incidents + " incident" + (incidents == 1 ? "" : "s") + " involving this guard")
                : none("INCIDENTS", "Incidents", 10, "fewer than " + MIN_SHIFTS + " shifts in the period"));

        c.add(enoughShifts
                ? comp("COMPLAINTS", "Complaints", 15, Math.max(0, 1 - 0.30 * f.substantiatedComplaints()),
                        f.substantiatedComplaints() == 0 ? "no substantiated complaints" : f.substantiatedComplaints() + " substantiated")
                : none("COMPLAINTS", "Complaints", 15, "fewer than " + MIN_SHIFTS + " shifts in the period"));

        c.add(f.readinessPercent() != null
                ? comp("READINESS", "Training and certifications", 15, f.readinessPercent() / 100.0, "deployment readiness " + f.readinessPercent() + "%")
                : none("READINESS", "Training and certifications", 15, "no readiness figure"));

        c.add(f.ratingCount() > 0
                ? comp("RATINGS", "Client and supervisor ratings", 15, Math.min(1.0, f.ratingAverage() / 5.0),
                        String.format("%.1f out of 5 across %d rating%s", f.ratingAverage(), f.ratingCount(), f.ratingCount() == 1 ? "" : "s"))
                : none("RATINGS", "Client and supervisor ratings", 15, "no ratings in the period"));

        int coverage = c.stream().filter(Component::hasData).mapToInt(Component::weight).sum();
        if (coverage < MIN_COVERAGE) return new Result(null, "NOT_ENOUGH_DATA", coverage, c);
        double earned = c.stream().filter(Component::hasData).mapToDouble(Component::points).sum();
        int score = (int) Math.round(earned / coverage * 100);
        return new Result(score, band(score), coverage, c);
    }

    public static String band(int score) {
        return score >= 85 ? "EXCELLENT" : score >= 70 ? "GOOD" : score >= 50 ? "NEEDS_ATTENTION" : "AT_RISK";
    }

    private static Component comp(String key, String label, int weight, double fraction, String detail) {
        double fr = Math.max(0, Math.min(1, fraction));
        return new Component(key, label, weight, true, fr, fr * weight, detail);
    }

    private static Component none(String key, String label, int weight, String why) {
        return new Component(key, label, weight, false, 0, 0, "Not enough data: " + why);
    }
}
