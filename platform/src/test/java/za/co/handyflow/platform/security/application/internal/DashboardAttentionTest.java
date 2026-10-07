package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.dto.SecurityDashboardDtos.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardAttentionTest {

    private static final Shifts NO_SHIFTS = new Shifts(0, 0, 0, 0, 0);
    private static final Workforce NO_WORKFORCE = new Workforce(10, 9, 0, 0, 0, 0);
    private static final Incidents NO_INCIDENTS = new Incidents(0, 0, 0, 0);
    private static final Complaints NO_COMPLAINTS = new Complaints(0, 0);
    private static final Gate NO_GATE = new Gate(0, 0, 0);

    private static List<AttentionItem> build(int alarms, int newAlarms, Shifts s, Workforce w, Incidents i, Complaints c, Gate g) {
        return DashboardAttention.build(alarms, newAlarms, s, w, i, c, g);
    }

    @Test @DisplayName("Nothing is listed when every count is zero")
    void quiet() {
        assertThat(build(0, 0, NO_SHIFTS, NO_WORKFORCE, NO_INCIDENTS, NO_COMPLAINTS, NO_GATE)).isEmpty();
    }

    @Test @DisplayName("Dangers come before warnings before notices, the larger count first within a level")
    void ranking() {
        var out = build(2, 0, new Shifts(5, 8, 3, 1, 0), new Workforce(10, 9, 1, 4, 2, 5), new Incidents(4, 2, 1, 6), new Complaints(3, 1), new Gate(7, 2, 9));
        List<Integer> ranks = out.stream().map(i -> switch (i.level()) { case "DANGER" -> 0; case "WARNING" -> 1; default -> 2; }).toList();
        assertThat(ranks).isSorted();
        assertThat(out.get(0).level()).isEqualTo("DANGER");
        assertThat(out.get(out.size() - 1).code()).isEqualTo("COMPETENCIES_EXPIRING");
        var warnings = out.stream().filter(i -> i.level().equals("WARNING")).toList();
        assertThat(warnings).extracting(AttentionItem::count).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test @DisplayName("An alarm nobody has triaged is a danger; a queue of triaged alarms is a warning")
    void alarmLevel() {
        assertThat(build(2, 1, NO_SHIFTS, NO_WORKFORCE, NO_INCIDENTS, NO_COMPLAINTS, NO_GATE).get(0).level()).isEqualTo("DANGER");
        assertThat(build(2, 0, NO_SHIFTS, NO_WORKFORCE, NO_INCIDENTS, NO_COMPLAINTS, NO_GATE).get(0).level()).isEqualTo("WARNING");
    }

    @Test @DisplayName("Wording agrees with the count and each item points at a section")
    void wording() {
        var one = build(0, 0, NO_SHIFTS, new Workforce(10, 9, 1, 0, 0, 0), NO_INCIDENTS, NO_COMPLAINTS, NO_GATE).get(0);
        assertThat(one.title()).isEqualTo("1 guard with an expired PSiRA registration");
        assertThat(one.section()).isEqualTo("guards");
        var many = build(0, 0, NO_SHIFTS, new Workforce(10, 9, 3, 0, 0, 0), NO_INCIDENTS, NO_COMPLAINTS, NO_GATE).get(0);
        assertThat(many.title()).startsWith("3 guards");
        var comps = build(0, 0, NO_SHIFTS, new Workforce(10, 9, 0, 0, 2, 0), NO_INCIDENTS, NO_COMPLAINTS, NO_GATE).get(0);
        assertThat(comps.title()).startsWith("2 required competencies");
    }

    @Test @DisplayName("Urgent complaints follow the complaint rules; an unknown category is not urgent")
    void urgentComplaints() {
        assertThat(SecurityDashboardService.urgentComplaint("THEFT", "LOW")).isTrue();
        assertThat(SecurityDashboardService.urgentComplaint("LATENESS", "CRITICAL")).isTrue();
        assertThat(SecurityDashboardService.urgentComplaint("LATENESS", "HIGH")).isFalse();
        assertThat(SecurityDashboardService.urgentComplaint("MADE_UP", "HIGH")).isFalse();
        assertThat(SecurityDashboardService.urgentComplaint("null", "null")).isFalse();
    }
}
