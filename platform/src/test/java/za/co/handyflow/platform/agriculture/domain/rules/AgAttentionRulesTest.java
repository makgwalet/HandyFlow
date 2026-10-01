package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.agriculture.dto.AttentionItemResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgAttentionRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    private static AttentionItemResponse item(String severity, LocalDate due, String title) {
        return new AttentionItemResponse("HEALTH_EVENT_DUE", severity, title, null, due, UUID.randomUUID(), UUID.randomUUID(), "Farm");
    }

    @Test
    @DisplayName("a due date is classified as overdue, due today or upcoming")
    void dueSeverity() {
        assertEquals("OVERDUE", AgAttentionRules.dueSeverity(TODAY.minusDays(1), TODAY));
        assertEquals("DUE_TODAY", AgAttentionRules.dueSeverity(TODAY, TODAY));
        assertEquals("UPCOMING", AgAttentionRules.dueSeverity(TODAY.plusDays(1), TODAY));
        assertEquals("UPCOMING", AgAttentionRules.dueSeverity(AgAttentionRules.horizon(TODAY), TODAY));
    }

    @Test
    @DisplayName("the look-ahead window is seven days")
    void horizon() {
        assertEquals(LocalDate.of(2026, 10, 8), AgAttentionRules.horizon(TODAY));
    }

    @Test
    @DisplayName("out of stock is critical; below reorder level but in stock is medium")
    void stockSeverity() {
        assertEquals("CRITICAL", AgAttentionRules.stockSeverity(BigDecimal.ZERO));
        assertEquals("CRITICAL", AgAttentionRules.stockSeverity(new BigDecimal("-2")));
        assertEquals("MEDIUM", AgAttentionRules.stockSeverity(new BigDecimal("0.5")));
        assertEquals("MEDIUM", AgAttentionRules.stockSeverity(null));
    }

    @Test
    @DisplayName("severity ladder: critical, overdue, due today, upcoming, medium; unknown last")
    void rank() {
        assertTrue(AgAttentionRules.rank("CRITICAL") < AgAttentionRules.rank("OVERDUE"));
        assertTrue(AgAttentionRules.rank("OVERDUE") < AgAttentionRules.rank("DUE_TODAY"));
        assertTrue(AgAttentionRules.rank("DUE_TODAY") < AgAttentionRules.rank("UPCOMING"));
        assertTrue(AgAttentionRules.rank("UPCOMING") < AgAttentionRules.rank("MEDIUM"));
        assertTrue(AgAttentionRules.rank("MEDIUM") < AgAttentionRules.rank("SOMETHING_NEW"));
        assertEquals(AgAttentionRules.rank("SOMETHING_NEW"), AgAttentionRules.rank(null));
    }

    @Test
    @DisplayName("ranking: by severity, then earliest due date, undated last, then title")
    void ranking() {
        AttentionItemResponse medium = item("MEDIUM", null, "Lick is low");
        AttentionItemResponse overdueLate = item("OVERDUE", TODAY.minusDays(1), "B overdue");
        AttentionItemResponse overdueEarly = item("OVERDUE", TODAY.minusDays(9), "A overdue");
        AttentionItemResponse critical = item("CRITICAL", null, "Seed is out");
        AttentionItemResponse upcoming = item("UPCOMING", TODAY.plusDays(3), "Vaccination");
        AttentionItemResponse today = item("DUE_TODAY", TODAY, "Dosing");
        AttentionItemResponse tieB = item("MEDIUM", null, "Zinc is low");

        List<AttentionItemResponse> ranked = AgAttentionRules.ranked(List.of(medium, overdueLate, tieB, upcoming, critical, today, overdueEarly));

        assertEquals(List.of(critical, overdueEarly, overdueLate, today, upcoming, medium, tieB), ranked);
    }

    @Test
    @DisplayName("ranked() returns a new list and leaves its input alone")
    void rankedDoesNotMutateInput() {
        AttentionItemResponse a = item("MEDIUM", null, "x");
        AttentionItemResponse b = item("CRITICAL", null, "y");
        List<AttentionItemResponse> input = new java.util.ArrayList<>(List.of(a, b));
        AgAttentionRules.ranked(input);
        assertEquals(List.of(a, b), input);
    }

    @Test
    @DisplayName("counts per severity in ladder order, omitting empty severities")
    void countBySeverity() {
        Map<String, Integer> counts = AgAttentionRules.countBySeverity(List.of(
                item("MEDIUM", null, "a"), item("OVERDUE", TODAY, "b"), item("OVERDUE", TODAY, "c"), item("CRITICAL", null, "d")));
        assertEquals(List.of("CRITICAL", "OVERDUE", "MEDIUM"), List.copyOf(counts.keySet()));
        assertEquals(2, counts.get("OVERDUE").intValue());
        assertFalse(counts.containsKey("UPCOMING"));
        assertTrue(AgAttentionRules.countBySeverity(List.of()).isEmpty());
    }

    @Test
    @DisplayName("urgent means critical, overdue or due today")
    void urgent() {
        assertTrue(AgAttentionRules.isUrgent(item("CRITICAL", null, "a")));
        assertTrue(AgAttentionRules.isUrgent(item("OVERDUE", TODAY, "a")));
        assertTrue(AgAttentionRules.isUrgent(item("DUE_TODAY", TODAY, "a")));
        assertFalse(AgAttentionRules.isUrgent(item("UPCOMING", TODAY, "a")));
        assertFalse(AgAttentionRules.isUrgent(item("MEDIUM", null, "a")));
    }
}
