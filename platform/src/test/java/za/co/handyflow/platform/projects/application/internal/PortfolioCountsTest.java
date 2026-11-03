package za.co.handyflow.platform.projects.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.dto.PortfolioResponse.Counts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The dashboard's counting rules. */
class PortfolioCountsTest {

    LocalDate today = LocalDate.of(2026, 11, 3);

    @Test
    void overdueMeansLivePastItsEndDate() {
        assertTrue(ProjectPortfolioService.overdue(ProjectStatus.ACTIVE, today.minusDays(1), today));
        assertTrue(ProjectPortfolioService.overdue(ProjectStatus.ON_HOLD, today.minusDays(1), today));
        assertFalse(ProjectPortfolioService.overdue(ProjectStatus.ACTIVE, today, today), "due today is not late yet");
        assertFalse(ProjectPortfolioService.overdue(ProjectStatus.COMPLETED, today.minusDays(9), today));
        assertFalse(ProjectPortfolioService.overdue(ProjectStatus.PLANNING, today.minusDays(9), today));
        assertFalse(ProjectPortfolioService.overdue(ProjectStatus.ACTIVE, null, today));
    }

    @Test
    void countsSplitLiveProjectsAndOnlySumCostTheCallerMaySee() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID(), e = UUID.randomUUID();
        var rows = List.of(
                new ProjectPortfolioService.Row(a, "ACTIVE", "GREEN", false, new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("10")),
                new ProjectPortfolioService.Row(b, "ACTIVE", "RED", false, new BigDecimal("200"), new BigDecimal("50"), BigDecimal.ZERO),
                new ProjectPortfolioService.Row(c, "ACTIVE", "GREEN", true, new BigDecimal("300"), new BigDecimal("60"), BigDecimal.ZERO),
                new ProjectPortfolioService.Row(d, "CANCELLED", "GREEN", false, new BigDecimal("999"), BigDecimal.ZERO, BigDecimal.ZERO),
                new ProjectPortfolioService.Row(e, "PLANNING", "GREEN", false, new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO));
        Counts n = ProjectPortfolioService.count(rows, Set.of(a, b, d, e));
        assertEquals(4, n.total(), "cancelled is left out");
        assertEquals(3, n.active());
        assertEquals(1, n.onTrack());
        assertEquals(1, n.atRisk(), "red but not late");
        assertEquals(1, n.overdue(), "late counts as overdue, not at risk");
        assertEquals(new BigDecimal("350"), n.budget(), "c's cost is hidden, d is cancelled");
        assertEquals(new BigDecimal("90"), n.spent());
    }
}
