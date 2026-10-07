// security/application/internal/DashboardAttention.java
package za.co.handyflow.platform.security.application.internal;

import za.co.handyflow.platform.security.dto.SecurityDashboardDtos.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns the dashboard's figures into the ranked "needs attention" list. Pure: no database. Dangers come first,
 * then warnings, then notices; within a level the larger count first. Nothing with a count of zero is listed.
 */
public final class DashboardAttention {

    private DashboardAttention() {}

    public static List<AttentionItem> build(int openAlarms, int newAlarms, Shifts shifts, Workforce workforce,
                                            Incidents incidents, Complaints complaints, Gate gate) {
        List<AttentionItem> out = new ArrayList<>();
        add(out, "CRITICAL_INCIDENTS", "DANGER", plural(incidents.criticalOpen(), "critical incident") + " open",
                "Not yet resolved. Open the incident to act on it.", incidents.criticalOpen(), "incidents");
        add(out, "ALARMS", newAlarms > 0 ? "DANGER" : "WARNING", plural(openAlarms, "alarm") + " in the control room queue",
                newAlarms > 0 ? newAlarms + " not yet triaged." : "Triaged or dispatched, not yet resolved.", openAlarms, "control-room");
        add(out, "PSIRA_EXPIRED", "DANGER", plural(workforce.psiraExpired(), "guard") + " with an expired PSiRA registration",
                "An expired registration means the guard may not work.", workforce.psiraExpired(), "guards");
        add(out, "URGENT_COMPLAINTS", "DANGER", plural(complaints.urgent(), "urgent complaint") + " open",
                "Critical, or in an urgent category such as theft or excessive force.", complaints.urgent(), "complaints");
        add(out, "NOT_STARTED", "WARNING", plural(shifts.notStarted(), "shift") + " past the start time with no clock-in",
                "More than " + ShiftPunctuality.GRACE_MINUTES + " minutes after the scheduled start.", shifts.notStarted(), "shifts");
        add(out, "MISSED", "WARNING", plural(shifts.missedToday(), "shift") + " missed today", "Marked as missed.", shifts.missedToday(), "shifts");
        add(out, "UNACKNOWLEDGED", "WARNING", plural(incidents.unacknowledged(), "incident") + " not yet acknowledged",
                "Reported, and nobody has taken it on.", incidents.unacknowledged(), "incidents");
        add(out, "OVERSTAYED", "WARNING", plural(gate.overstayed(), "visitor") + " overstayed on site",
                "Past the expected departure time.", gate.overstayed(), "gate-dashboard");
        add(out, "PSIRA_EXPIRING", "WARNING", plural(workforce.psiraExpiring(), "PSiRA registration") + " expiring within 30 days",
                "Renew before they lapse.", workforce.psiraExpiring(), "guards");
        add(out, "COMPETENCIES_EXPIRED", "WARNING", plural(workforce.competenciesExpired(), "required competency", "required competencies") + " expired",
                "Firearm competency, first aid and similar certificates marked as required.", workforce.competenciesExpired(), "guards");
        add(out, "COMPETENCIES_EXPIRING", "INFO", plural(workforce.competenciesExpiring(), "required competency", "required competencies") + " expiring within 30 days",
                "Book the renewals.", workforce.competenciesExpiring(), "guards");
        // Stable: the order above is the tie-break within a level and count.
        out.sort(Comparator.comparingInt((AttentionItem i) -> rank(i.level())).thenComparing(Comparator.comparingInt(AttentionItem::count).reversed()));
        return List.copyOf(out);
    }

    private static void add(List<AttentionItem> out, String code, String level, String title, String detail, int count, String section) {
        if (count > 0) out.add(new AttentionItem(code, level, title, detail, count, section));
    }

    private static int rank(String level) {
        return switch (level) { case "DANGER" -> 0; case "WARNING" -> 1; default -> 2; };
    }

    private static String plural(int n, String noun) { return plural(n, noun, noun + "s"); }

    private static String plural(int n, String one, String many) { return n + " " + (n == 1 ? one : many); }
}
