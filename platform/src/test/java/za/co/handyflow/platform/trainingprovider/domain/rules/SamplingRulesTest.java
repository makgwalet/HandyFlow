package za.co.handyflow.platform.trainingprovider.domain.rules;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.trainingprovider.domain.rules.SamplingRules.*;

class SamplingRulesTest {
    private static Settings pct(String p, Integer min, Integer max, String method) {
        return new Settings("PERCENTAGE", new BigDecimal(p), min, max, method, null, null, null);
    }
    private static List<Candidate> pop(int n) {
        List<Candidate> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new Candidate(new UUID(0, i + 1), new UUID(1, i % 3), i % 5));
        return l;
    }
    @Test void requiredRoundsUpAndAppliesMinMax() {
        assertEquals(3, SamplingRules.requiredCount(pct("10", null, null, "RANDOM"), 25));
        assertEquals(5, SamplingRules.requiredCount(pct("10", 5, null, "RANDOM"), 25));
        assertEquals(2, SamplingRules.requiredCount(pct("50", null, 2, "RANDOM"), 25));
        assertEquals(4, SamplingRules.requiredCount(pct("10", 5, null, "RANDOM"), 4));
        assertEquals(0, SamplingRules.requiredCount(pct("10", 5, null, "RANDOM"), 0));
    }
    @Test void allBasisTakesEverything() {
        Settings s = new Settings("ALL", null, null, null, "RANDOM", null, null, null);
        assertEquals(7, SamplingRules.requiredCount(s, 7));
        assertEquals(7, SamplingRules.select(s, pop(7), null, false, 1L).picks().size());
    }
    @Test void sameSeedSameResultAnyListOrder() {
        Settings s = pct("40", null, null, "RANDOM");
        List<Candidate> a = pop(20); List<Candidate> b = new ArrayList<>(a); Collections.reverse(b);
        assertEquals(SamplingRules.select(s, a, null, false, 42L).picks(), SamplingRules.select(s, b, null, false, 42L).picks());
    }
    @Test void riskBasedTakesHighestScores() {
        Settings s = pct("20", null, null, "RISK_BASED");
        List<Candidate> p = pop(10);
        Outcome o = SamplingRules.select(s, p, null, false, 9L);
        assertEquals(2, o.picks().size());
        for (Pick k : o.picks()) { assertEquals("RISK", k.reason()); assertEquals(4, p.stream().filter(c -> c.attemptId().equals(k.attemptId())).findFirst().get().riskScore()); }
    }
    @Test void targetedAlwaysInAndCountedAgainstRequired() {
        Settings s = pct("20", null, null, "RANDOM");
        List<Candidate> p = pop(10);
        UUID t = p.get(3).attemptId();
        Outcome o = SamplingRules.select(s, p, Map.of(t, "Assessor under review"), false, 5L);
        assertEquals(2, o.picks().size());
        assertEquals("TARGETED", o.picks().get(0).reason());
        assertEquals(t, o.picks().get(0).attemptId());
    }
    @Test void extraTriggersOnRecentQueriesAndAddsPicks() {
        Settings s = new Settings("PERCENTAGE", new BigDecimal("10"), null, null, "RANDOM", 2, 4, new BigDecimal("20"));
        assertTrue(SamplingRules.extraTriggered(s, List.of(true, false, true, false, true)));
        assertFalse(SamplingRules.extraTriggered(s, List.of(true, false, false, false, true)));
        Outcome o = SamplingRules.select(s, pop(20), null, true, 3L);
        assertEquals(2, o.required());
        assertEquals(4, o.extraRequired());
        assertEquals(6, o.picks().size());
        assertEquals(4, o.picks().stream().filter(k -> k.reason().equals("EXTRA_AFTER_POOR")).count());
    }
    @Test void settingsProblems() {
        assertNull(SamplingRules.settingsProblem(pct("10", 1, 5, "RANDOM")));
        assertNotNull(SamplingRules.settingsProblem(pct("0", null, null, "RANDOM")));
        assertNotNull(SamplingRules.settingsProblem(pct("101", null, null, "RANDOM")));
        assertNotNull(SamplingRules.settingsProblem(pct("10", 6, 5, "RANDOM")));
        assertNotNull(SamplingRules.settingsProblem(new Settings("ALL", new BigDecimal("5"), null, null, "RANDOM", null, null, null)));
        assertNotNull(SamplingRules.settingsProblem(new Settings("PERCENTAGE", new BigDecimal("5"), null, null, "RANDOM", 3, null, null)));
        assertNotNull(SamplingRules.settingsProblem(new Settings("PERCENTAGE", new BigDecimal("5"), null, null, "RANDOM", 3, 2, new BigDecimal("10"))));
    }
    @Test void closeNeedsEveryPickModeratedAndANote() {
        assertNotNull(SamplingRules.closeProblem(3, 2, "All reviewed fine"));
        assertNotNull(SamplingRules.closeProblem(3, 3, "short"));
        assertNull(SamplingRules.closeProblem(3, 3, "Results accepted as recorded"));
    }
}
