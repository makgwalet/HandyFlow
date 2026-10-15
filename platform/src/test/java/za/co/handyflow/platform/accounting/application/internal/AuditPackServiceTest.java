package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accounting.application.internal.AuditPackBuilder.Table;
import za.co.handyflow.platform.accounting.application.internal.AuditPackService.Verification;
import za.co.handyflow.platform.accounting.dto.AgingReportResponse;
import za.co.handyflow.platform.accounting.dto.FinancialReportResponse;
import za.co.handyflow.platform.accounting.dto.FinancialReportResponse.ReportLine;
import za.co.handyflow.platform.accounting.dto.FinancialReportResponse.ReportSection;
import za.co.handyflow.platform.accounting.dto.Vat201Response;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditPackServiceTest {
    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static final LocalDate FROM = LocalDate.of(2025, 3, 1), TO = LocalDate.of(2026, 2, 28);

    private static FinancialReportResponse report(LocalDate f, LocalDate t, ReportLine... lines) {
        return new FinancialReportResponse("TRIAL_BALANCE", f, t, List.of(new ReportSection("Assets", List.of(lines), BigDecimal.ZERO)), BigDecimal.ZERO);
    }

    @Test
    void periodMustBeChosenForwardsAndNotAbsurdlyLong() {
        assertEquals("PERIOD_REQUIRED", assertThrows(HandyFlowException.class, () -> AuditPackService.checkPeriod(null, TO)).getErrorCode());
        assertEquals("PERIOD_BACKWARDS", assertThrows(HandyFlowException.class, () -> AuditPackService.checkPeriod(TO, FROM)).getErrorCode());
        assertEquals("PERIOD_TOO_LONG", assertThrows(HandyFlowException.class, () -> AuditPackService.checkPeriod(FROM, FROM.plusYears(3))).getErrorCode());
        assertDoesNotThrow(() -> AuditPackService.checkPeriod(FROM, TO));
    }

    @Test
    void trialBalanceLaysPriorPeriodBesideAndKeepsAccountsThatOnlyExistInOne() {
        var cur = report(FROM, TO, new ReportLine("1000", "Bank", bd("500.00"), bd("700.00"), bd("200.00")), new ReportLine("2000", "Loan", bd("-50"), bd("0"), bd("50")));
        var prior = report(FROM.minusYears(1), TO.minusYears(1), new ReportLine("1000", "Bank", bd("300.00")), new ReportLine("3000", "Equity", bd("-10")));
        Table t = AuditPackService.trialBalance(cur, prior);
        assertEquals(3, t.rows().size());
        assertEquals(List.of("1000", "Bank", "Assets", "700.00", "200.00", "500.00", "300.00"), t.rows().get(0));
        assertEquals("0.00", t.rows().get(2).get(5));   // 3000 had no movement this period
        assertEquals("-10.00", t.rows().get(2).get(6));
    }

    @Test
    void trialBalanceRowsComeOutInAccountOrderSoTheFileIsRepeatable() {
        var a = report(FROM, TO, new ReportLine("2000", "Loan", bd("1")), new ReportLine("1000", "Bank", bd("2")));
        var b = report(FROM, TO, new ReportLine("1000", "Bank", bd("2")), new ReportLine("2000", "Loan", bd("1")));
        var empty = report(FROM, TO);
        assertEquals(AuditPackService.trialBalance(a, empty).rows(), AuditPackService.trialBalance(b, empty).rows());
    }

    @Test
    void debtorsAreOrderedByCustomerThenInvoiceAndCarryTheAsAtDate() {
        var lines = List.of(
                new AgingReportResponse.AgingLine(UUID.randomUUID(), "INV-2", "Zeta", TO, 10, bd("100"), "1-30"),
                new AgingReportResponse.AgingLine(UUID.randomUUID(), "INV-9", "Alpha", TO, 0, bd("50"), "CURRENT"),
                new AgingReportResponse.AgingLine(UUID.randomUUID(), "INV-1", "Alpha", TO, 40, bd("75"), "31-60"));
        var t = AuditPackService.debtors(new AgingReportResponse(LocalDate.of(2026, 10, 15), lines, bd("0"), bd("0"), bd("0"), bd("0"), bd("0"), bd("0")));
        assertEquals(List.of("Alpha", "Alpha", "Zeta"), t.rows().stream().map(r -> r.get(1)).toList());
        assertEquals(List.of("INV-1", "INV-9", "INV-2"), t.rows().stream().map(r -> r.get(2)).toList());
        assertEquals("2026-10-15", t.rows().get(0).get(0));
    }

    @Test
    void vatRowCarriesThePeriodAndTotals() {
        var t = AuditPackService.vat(new Vat201Response(FROM, TO, 12, bd("1150"), bd("150"), bd("50"), bd("100")));
        assertEquals(List.of("2025-03-01", "2026-02-28", "12", "1150.00", "150.00", "50.00", "100.00"), t.rows().get(0));
    }

    private AuditPackBuilder.Pack packOf(String cell) {
        return AuditPackBuilder.build(Map.of(), List.of(new Table("01.csv", "d", List.of("x"), List.of(List.of(cell)))), List.of(), List.of(), Instant.now(), "v");
    }

    @Test
    void verifyPassesWhenTheBooksHaveNotChanged() {
        var original = packOf("100.00");
        Verification v = AuditPackService.compare(original.packSha256(), original.manifestJson(), packOf("100.00"));
        assertTrue(v.matches());
        assertTrue(v.changedFiles().isEmpty());
    }

    @Test
    void verifyNamesTheFilesThatChanged() {
        var original = packOf("100.00");
        Verification v = AuditPackService.compare(original.packSha256(), original.manifestJson(), packOf("250.00"));
        assertFalse(v.matches());
        assertEquals(List.of("01.csv"), v.changedFiles());
        assertNotEquals(v.storedSha256(), v.currentSha256());
    }
}
