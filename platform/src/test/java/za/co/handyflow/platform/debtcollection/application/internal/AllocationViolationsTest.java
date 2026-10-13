package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationEngine.CaseFacts;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationEngine.Collector;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CollectorTier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationViolationsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private final CaseFacts facts = new CaseFacts(UUID.randomUUID(), "DC-00001", "ABC", new BigDecimal("6000"), CaseStatus.OPEN, 5, null, null);

    private Collector c(boolean active, LocalDate away, Integer maxCases, BigDecimal maxCase, BigDecimal maxPort, int open, String port) {
        return new Collector(UUID.randomUUID(), "Sarah", null, CollectorTier.STANDARD, Set.of(), maxCases, maxCase, maxPort, away,
                active, open, new BigDecimal(port), 0L);
    }

    @Test
    void noViolationsWhenEverythingFits() {
        assertTrue(AllocationEngine.violations(c(true, null, 10, new BigDecimal("10000"), new BigDecimal("100000"), 3, "20000"), facts, TODAY).isEmpty());
    }

    @Test
    void reportsEveryReasonInPlainLanguage() {
        List<String> v = AllocationEngine.violations(c(false, null, 2, new BigDecimal("5000"), new BigDecimal("10000"), 2, "8000"), facts, TODAY);
        assertEquals(4, v.size());
        assertTrue(v.get(0).contains("inactive"));
        assertTrue(v.stream().anyMatch(s -> s.contains("2 of a maximum 2")));
        assertTrue(v.stream().anyMatch(s -> s.contains("single-case limit")));
        assertTrue(v.stream().anyMatch(s -> s.contains("portfolio limit")));
    }

    @Test
    void awayThroughTodayInclusive() {
        assertEquals(1, AllocationEngine.violations(c(true, TODAY, null, null, null, 0, "0"), facts, TODAY).size());
        assertTrue(AllocationEngine.violations(c(true, TODAY.minusDays(1), null, null, null, 0, "0"), facts, TODAY).isEmpty());
    }
}
