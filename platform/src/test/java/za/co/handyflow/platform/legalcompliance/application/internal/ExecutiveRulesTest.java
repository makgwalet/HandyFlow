package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.dto.ExecDtos.AttentionItem;
import za.co.handyflow.platform.legalcompliance.dto.ExecDtos.Indicator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutiveRulesTest {

    private static Indicator find(List<Indicator> l, String key) { return l.stream().filter(i -> i.key().equals(key)).findFirst().orElse(null); }

    @Test @DisplayName("Percent is null when there is nothing to measure, rounded otherwise, and clamped to 0..100")
    void percent() {
        assertNull(ExecutiveRules.percent(0, 0));
        assertNull(ExecutiveRules.percent(5, -1));
        assertEquals(Integer.valueOf(0), ExecutiveRules.percent(0, 4));
        assertEquals(Integer.valueOf(33), ExecutiveRules.percent(1, 3));
        assertEquals(Integer.valueOf(67), ExecutiveRules.percent(2, 3));
        assertEquals(Integer.valueOf(100), ExecutiveRules.percent(9, 3));
        assertEquals(Integer.valueOf(0), ExecutiveRules.percent(-2, 3));
    }

    @Test @DisplayName("Indicators are computed from counts; zero denominators give a null percent rather than 0% or 100%")
    void indicators() {
        Map<String, Long> m = new HashMap<>();
        m.put("obligations.total", 10L); m.put("obligations.compliant", 7L);
        m.put("findings.open", 4L); m.put("findings.overdue", 1L);
        m.put("capa.proposed", 0L); m.put("capa.approved", 0L); m.put("capa.inProgress", 0L); m.put("capa.awaitingVerification", 0L); m.put("capa.overdue", 0L);
        List<Indicator> out = ExecutiveRules.indicators(m);
        assertEquals(Integer.valueOf(70), find(out, "obligationsCompliant").percent());
        assertEquals(Integer.valueOf(75), find(out, "findingsOnTime").percent());
        assertNull(find(out, "capaOnTime").percent());
    }

    @Test @DisplayName("Coverage indicators combine untested and effective cover, and use their own denominators")
    void coverage() {
        Map<String, Long> m = Map.of("coverage.obligations", 20L, "coverage.coveredUntested", 6L, "coverage.coveredEffective", 4L);
        List<Indicator> out = ExecutiveRules.indicators(m);
        assertEquals(Integer.valueOf(50), find(out, "controlCoverage").percent());
        assertEquals(Integer.valueOf(20), find(out, "effectiveCoverage").percent());
    }

    @Test @DisplayName("An older snapshot that lacks newer keys simply omits those indicators")
    void oldSnapshot() {
        List<Indicator> out = ExecutiveRules.indicators(Map.of("obligations.total", 5L, "obligations.compliant", 5L));
        assertEquals(1, out.size());
        assertNull(find(out, "licencesValid"));
        assertTrue(ExecutiveRules.indicators(Map.of()).isEmpty());
    }

    @Test @DisplayName("Attention list: only counts above zero, critical before warning, each with a tab to open")
    void attention() {
        Map<String, Long> m = new HashMap<>();
        m.put("obligations.overdue", 2L);
        m.put("licences.expired", 1L);
        m.put("findings.critical", 0L);
        m.put("incidents.awaitingAssessment", 3L);
        List<AttentionItem> out = ExecutiveRules.attention(m);
        assertEquals(3, out.size());
        assertEquals("CRITICAL", out.get(0).severity());
        assertEquals("CRITICAL", out.get(1).severity());
        assertEquals("WARNING", out.get(2).severity());
        assertEquals("obligations", out.get(2).tab());
        assertTrue(out.stream().noneMatch(a -> a.key().equals("findings.critical")));
        assertTrue(ExecutiveRules.attention(Map.of()).isEmpty());
    }

    @Test @DisplayName("Every attention rule key and tab id is distinct and non-empty")
    void rulesWellFormed() {
        Map<String, Long> all = new HashMap<>();
        for (String k : List.of("obligations.nonCompliant", "findings.critical", "incidents.awaitingAssessment", "risks.outsideTolerance", "licences.expired", "licences.conditionsBreached",
                "privacy.paiaOverdue", "thirdParties.expired", "changes.overdue", "obligations.overdue", "findings.overdue", "capa.overdue", "audits.overdue", "testing.overdue",
                "risks.overdueReview", "policies.reviewOverdue", "incidents.awaitingNotification", "privacy.filingsOverdue", "privacy.piaReviewOverdue", "thirdParties.reviewOverdue",
                "licences.conditionsOverdue", "licences.expiring", "coverage.uncovered", "risks.unassessed", "governance.withoutQuorumCapacity", "governance.meetingsOverdue", "governance.charterReviewOverdue", "governance.actionsOverdue", "governance.minutesPending", "ethics.awaitingReview", "ethics.reviewOverdue", "ethics.giftsPending")) all.put(k, 1L);
        List<AttentionItem> out = ExecutiveRules.attention(all);
        assertEquals(all.size(), out.size());
        assertEquals(out.size(), out.stream().map(AttentionItem::key).distinct().count());
        assertTrue(out.stream().allMatch(a -> !a.tab().isBlank() && a.text().startsWith("1 ")));
    }

    @Test @DisplayName("Committee-action and declaration indicators use open / in-force counts and omit themselves when absent")
    void governanceIndicators() {
        Map<String, Long> m = Map.of("governance.actionsOpen", 8L, "governance.actionsOverdue", 2L, "ethics.inForce", 0L, "ethics.reviewOverdue", 0L);
        List<Indicator> out = ExecutiveRules.indicators(m);
        assertEquals(Integer.valueOf(75), find(out, "committeeActionsOnTime").percent());
        assertNull(find(out, "declarationsInDate").percent(), "nothing in force is not 100%");
        assertNull(find(ExecutiveRules.indicators(Map.of("obligations.total", 1L, "obligations.compliant", 1L)), "committeeActionsOnTime"));
    }
}
