package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 30);

    @Test
    void bucketsFollowDaysOverdue() {
        assertEquals("CURRENT", ReportRules.bucket(0));
        assertEquals("D1_30", ReportRules.bucket(1)); assertEquals("D1_30", ReportRules.bucket(30));
        assertEquals("D31_60", ReportRules.bucket(31)); assertEquals("D31_60", ReportRules.bucket(60));
        assertEquals("D61_90", ReportRules.bucket(61)); assertEquals("D61_90", ReportRules.bucket(90));
        assertEquals("D90_PLUS", ReportRules.bucket(91));
    }

    @Test
    void daysOverdueCountsOnlyPastDueDates() {
        assertEquals(0L, ReportRules.daysOverdue(null, TODAY));
        assertEquals(0L, ReportRules.daysOverdue(TODAY, TODAY));
        assertEquals(0L, ReportRules.daysOverdue(TODAY.plusDays(5), TODAY));
        assertEquals(1L, ReportRules.daysOverdue(TODAY.minusDays(1), TODAY));
        assertEquals(45L, ReportRules.daysOverdue(TODAY.minusDays(45), TODAY));
    }

    @Test
    void outstandingIsNeverNegative() {
        assertEquals(new BigDecimal("40.00"), ReportRules.outstanding(new BigDecimal("100"), new BigDecimal("60")));
        assertEquals(new BigDecimal("0.00"), ReportRules.outstanding(new BigDecimal("100"), new BigDecimal("150")));
        assertEquals(new BigDecimal("100.00"), ReportRules.outstanding(new BigDecimal("100"), null));
    }

    @Test
    void percentHandlesNothingInvoiced() {
        assertEquals(new BigDecimal("0.0"), ReportRules.percent(new BigDecimal("50"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.0"), ReportRules.percent(BigDecimal.ZERO, null));
        assertEquals(new BigDecimal("33.3"), ReportRules.percent(new BigDecimal("100"), new BigDecimal("300")));
        assertEquals(new BigDecimal("100.0"), ReportRules.percent(new BigDecimal("300"), new BigDecimal("300")));
    }

    @Test
    void rangesAreChecked() {
        assertNull(ReportRules.rangeProblem(TODAY, TODAY));
        assertNotNull(ReportRules.rangeProblem(null, TODAY));
        assertNotNull(ReportRules.rangeProblem(TODAY, TODAY.minusDays(1)));
        assertNotNull(ReportRules.rangeProblem(TODAY.minusYears(11), TODAY));
        assertNull(ReportRules.rangeProblem(TODAY.minusYears(9), TODAY));
    }

    @Test
    void csvQuotesAndGuardsFormulas() {
        assertEquals("plain", ReportRules.cell("plain"));
        assertEquals("\"Smith, J\"", ReportRules.cell("Smith, J"));
        assertEquals("\"say \"\"hi\"\"\"", ReportRules.cell("say \"hi\""));
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", ReportRules.cell("=HYPERLINK(\"x\")"));
        assertEquals("'+1", ReportRules.cell("+1")); assertEquals("'-cmd", ReportRules.cell("-cmd")); assertEquals("'@SUM(A1)", ReportRules.cell("@SUM(A1)"));
        assertEquals("", ReportRules.cell(null));
    }

    @Test
    void csvLeavesNumbersAndDatesAlone() {
        assertEquals("-12.50", ReportRules.cell(new BigDecimal("-12.50")));
        assertEquals("2026-06-30", ReportRules.cell(TODAY));
        assertEquals("7", ReportRules.cell(7L));
    }

    @Test
    void csvHasAHeaderAndCrlfLines() {
        String out = ReportRules.csv(List.of("A", "B"), List.of(List.of("x", 1), java.util.Arrays.asList("y", null)));
        assertEquals("A,B\r\nx,1\r\ny,\r\n", out);
    }
}
