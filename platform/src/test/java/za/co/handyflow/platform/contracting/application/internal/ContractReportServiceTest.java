package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.ContractObligation;
import za.co.handyflow.platform.contracting.dto.ContractReportDtos.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractReportServiceTest {

    static final ZoneId Z = ContractReportService.ZONE;

    private Object[] signing(String no, Instant sent, Instant signed) { return new Object[]{no, "T " + no, sent, signed}; }

    @Test
    void turnaroundStatsAreCalculatedFromSendToSign() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        Turnaround t = ContractReportService.turnaround(List.of(
                signing("A", t0, t0.plus(Duration.ofDays(1))), signing("B", t0, t0.plus(Duration.ofDays(2))),
                signing("C", t0, t0.plus(Duration.ofDays(3))), signing("D", t0, t0.plus(Duration.ofDays(10)))));
        assertEquals(4, t.count());
        assertEquals(4.0, t.averageDays());
        assertEquals(2.5, t.medianDays());
        assertEquals(10.0, t.p90Days());          // nearest rank: ceil(0.9*4) = 4th value
        assertEquals("D", t.slowest().get(0).contractNumber());
    }

    @Test
    void turnaroundIgnoresRowsWithMissingOrBackwardsTimes() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        Turnaround t = ContractReportService.turnaround(List.of(
                signing("A", null, t0), signing("B", t0, null), signing("C", t0, t0.minusSeconds(60))));
        assertEquals(0, t.count());
        assertTrue(t.slowest().isEmpty());
    }

    @Test
    void signedValueByTypeCountsContractsWithoutAValueInsteadOfGuessing() {
        List<TypeValue> r = ContractReportService.signedByType(List.of(
                new Object[]{"LEASE", new BigDecimal("1000.00"), null, "ZAR"}, new Object[]{"LEASE", null, null, "ZAR"}, new Object[]{"NDA", null, null, "ZAR"}));
        TypeValue lease = r.stream().filter(x -> x.contractType().equals("LEASE")).findFirst().orElseThrow();
        assertEquals(2, lease.contracts());
        assertEquals(new BigDecimal("1000.00"), lease.totalValue());
        assertEquals(1, lease.withoutValue());
    }

    @Test
    void expiryProfileHasTwelveMonthsAndSkipsPastAndMissingDates() {
        LocalDate today = LocalDate.of(2026, 11, 18);
        List<ExpiryMonth> p = ContractReportService.expiryProfile(List.of(
                new Object[]{"LEASE", new BigDecimal("500"), LocalDate.of(2026, 11, 30), "ZAR"},
                new Object[]{"LEASE", new BigDecimal("700"), LocalDate.of(2027, 1, 5), "ZAR"},
                new Object[]{"LEASE", new BigDecimal("999"), LocalDate.of(2026, 11, 1), "ZAR"},      // already past
                new Object[]{"LEASE", new BigDecimal("999"), LocalDate.of(2028, 1, 1), "ZAR"},       // beyond twelve months
                new Object[]{"LEASE", new BigDecimal("999"), null, "ZAR"}), today);
        assertEquals(12, p.size());
        assertEquals("2026-11", p.get(0).month());
        assertEquals(1, p.get(0).contracts());
        assertEquals(new BigDecimal("500"), p.get(0).valueByCurrency().get("ZAR"));
        assertEquals(1, p.get(2).contracts());
        assertEquals(2, p.stream().mapToLong(ExpiryMonth::contracts).sum());
    }

    private ContractObligation ob(LocalDate due) {
        return ContractObligation.create(UUID.randomUUID(), UUID.randomUUID(), "x", null, "OTHER", "OURS", null, due, "NONE", null);
    }

    @Test
    void obligationStatsSplitOnTimeLateWaivedAndOpen() {
        LocalDate today = LocalDate.now(Z);
        ContractObligation onTime = ob(today.plusDays(5));      // completed now, before due
        onTime.complete(null, null);
        ContractObligation late = ob(today.minusDays(5));       // completed now, after due
        late.complete(null, null);
        ContractObligation waived = ob(today);
        waived.waive("not needed");
        ObligationStats s = ContractReportService.obligationStats(
                List.of(onTime, late, waived, ob(today.minusDays(1)), ob(today.plusDays(9))), today);
        assertEquals(5, s.due());
        assertEquals(1, s.completedOnTime());
        assertEquals(1, s.completedLate());
        assertEquals(1, s.waived());
        assertEquals(1, s.stillOpenOverdue());
        assertEquals(1, s.stillOpenNotYetDue());
        assertEquals(0.5, s.onTimeRate());
    }

    @Test
    void onTimeRateIsNullWhenNothingWasCompleted() {
        assertNull(ContractReportService.obligationStats(List.of(ob(LocalDate.now(Z).plusDays(1))), LocalDate.now(Z)).onTimeRate());
    }

    @Test
    void csvQuotesAndNeutralisesFormulas() {
        assertEquals("\"say \"\"hi\"\"\"", ContractReportService.csv("say \"hi\""));
        assertEquals("\"'=SUM(A1)\"", ContractReportService.csv("=SUM(A1)"));
        assertEquals("\"'@cmd\"", ContractReportService.csv("@cmd"));
        assertEquals("\"-500\"", ContractReportService.csv(new BigDecimal("-500")));   // a real negative number stays a number
        assertEquals("", ContractReportService.csv(null));
    }

    @Test
    void valuesInDifferentCurrenciesAreNeverAddedTogether() {
        List<TypeValue> r = ContractReportService.signedByType(List.of(
                new Object[]{"LEASE", new BigDecimal("1000"), null, "ZAR"}, new Object[]{"LEASE", new BigDecimal("50"), null, "USD"},
                new Object[]{"LEASE", new BigDecimal("200"), null, "ZAR"}, new Object[]{"LEASE", new BigDecimal("5"), null, null}));
        assertEquals(2, r.size());
        TypeValue zar = r.stream().filter(x -> x.currency().equals("ZAR")).findFirst().orElseThrow();
        TypeValue usd = r.stream().filter(x -> x.currency().equals("USD")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1205"), zar.totalValue(), "a missing currency counts as ZAR");
        assertEquals(3, zar.contracts());
        assertEquals(new BigDecimal("50"), usd.totalValue());

        LocalDate today = LocalDate.of(2026, 11, 18);
        List<ExpiryMonth> p = ContractReportService.expiryProfile(List.of(
                new Object[]{"LEASE", new BigDecimal("500"), LocalDate.of(2026, 11, 30), "ZAR"},
                new Object[]{"LEASE", new BigDecimal("20"), LocalDate.of(2026, 11, 29), "EUR"}), today);
        assertEquals(new BigDecimal("500"), p.get(0).valueByCurrency().get("ZAR"));
        assertEquals(new BigDecimal("20"), p.get(0).valueByCurrency().get("EUR"));
    }
}
