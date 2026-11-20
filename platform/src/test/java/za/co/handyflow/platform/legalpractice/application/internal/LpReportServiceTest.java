package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatter;
import za.co.handyflow.platform.legalpractice.dto.ReportDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Report maths, ageing, walls and CSV, against an in-memory stand-in for the SQL. The SQL itself was run against PostgreSQL separately. */
class LpReportServiceTest {

    private final TenantId firm = TenantId.of(UUID.randomUUID());
    private final LocalDate today = LocalDate.now(LpReportService.ZONE);
    private final List<UUID> walledMatters = new ArrayList<>(), walledClients = new ArrayList<>();
    private final List<List<UUID>> seenMatterLists = new ArrayList<>(), seenClientLists = new ArrayList<>();

    private List<MatterCount> counts = new ArrayList<>();
    private List<UnbilledRow> unbilled = new ArrayList<>();
    private List<DebtorRow> invoices = new ArrayList<>();
    private List<BillingRow> billing = new ArrayList<>();
    private List<TrustRow> trust = new ArrayList<>();
    private LocalDate trustAsAt;

    private LpReportService service;

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @BeforeEach
    void setUp() {
        walledMatters.clear(); walledClients.clear(); seenMatterLists.clear(); seenClientLists.clear();
        counts = new ArrayList<>(); unbilled = new ArrayList<>(); invoices = new ArrayList<>(); billing = new ArrayList<>(); trust = new ArrayList<>(); trustAsAt = null;
        LpReportData data = new LpReportData() {
            private void seen(List<UUID> m, List<UUID> c) { seenMatterLists.add(m); seenClientLists.add(c); }
            @Override public List<MatterCount> matterCounts(TenantId t, List<UUID> m, List<UUID> c) { seen(m, c); return counts; }
            @Override public List<UnbilledRow> unbilled(TenantId t, List<UUID> m, List<UUID> c, int limit) { seen(m, c); return unbilled.size() > limit ? unbilled.subList(0, limit) : unbilled; }
            @Override public List<DebtorRow> openInvoices(TenantId t, List<UUID> m, List<UUID> c, int limit) { seen(m, c); return invoices.size() > limit ? invoices.subList(0, limit) : invoices; }
            @Override public List<BillingRow> invoicesIssued(TenantId t, LocalDate f, LocalDate to, List<UUID> m, List<UUID> c) { seen(m, c); return billing; }
            @Override public List<TrustRow> trustBalances(TenantId t, LocalDate asAt, List<UUID> m, List<UUID> c) { seen(m, c); trustAsAt = asAt; return trust; }
        };
        LpWallGuard guard = new LpWallGuard() {
            @Override public void requireAccess(TenantId t, LpMatter m) { }
            @Override public void requireMatterAccess(TenantId t, UUID id) { }
            @Override public void requireClientAccess(TenantId t, UUID id) { }
            @Override public void requireScopeAccess(TenantId t, UUID m, UUID c) { }
            @Override public Barred barred(TenantId t) { return Barred.of(walledMatters, walledClients); }
        };
        service = new LpReportService(data, guard);
    }

    private static DebtorRow inv(String no, String client, LocalDate due, String total, String paid) {
        return new DebtorRow(UUID.randomUUID(), no, client, null, due == null ? null : due.minusDays(30), due, d(total), d(paid), null, 0, null);
    }

    // ---- matters -------------------------------------------------------------------------------------------------------

    @Test
    void mattersAreCountedAndOpenMeansOpenOrOnHold() {
        counts = List.of(new MatterCount("OPEN", "LITIGATION", 4), new MatterCount("OPEN", "ESTATES", 2), new MatterCount("ON_HOLD", "LITIGATION", 1), new MatterCount("CLOSED", "LITIGATION", 10));
        var r = service.matters(firm);
        assertEquals(17L, r.total());
        assertEquals(7L, r.open());
        assertFalse(r.hidden());
    }

    // ---- unbilled ------------------------------------------------------------------------------------------------------

    @Test
    void unbilledTotalsAddUpAndRoundToTwoDecimals() {
        unbilled = List.of(new UnbilledRow(UUID.randomUUID(), "M-1 A", "Client A", d("2.5"), d("1250"), d("100.5"), null), new UnbilledRow(UUID.randomUUID(), "M-2 B", "Client B", d("1"), d("800"), d("0"), null));
        var r = service.unbilled(firm);
        assertEquals(d("3.50"), r.hours());
        assertEquals(d("2050.00"), r.timeValue());
        assertEquals(d("100.50"), r.disbursements());
        assertEquals(d("2150.50"), r.total());
        assertEquals(d("1350.50"), r.rows().get(0).total());
        assertFalse(r.truncated());
    }

    @Test
    void unbilledSaysWhenItWasCutShort() {
        for (int i = 0; i < ReportRules.ROW_LIMIT + 5; i++) unbilled.add(new UnbilledRow(UUID.randomUUID(), "M-" + i, "C", d("1"), d("10"), d("0"), null));
        var r = service.unbilled(firm);
        assertTrue(r.truncated());
        assertEquals(ReportRules.ROW_LIMIT, r.rows().size());
        assertEquals(d("2000.00"), r.hours());
    }

    // ---- debtors -------------------------------------------------------------------------------------------------------

    @Test
    void debtorsAreAgedByDueDate() {
        invoices = List.of(inv("I-1", "A", today.plusDays(10), "1000", "0"), inv("I-2", "B", today.minusDays(5), "500", "100"), inv("I-3", "C", today.minusDays(45), "200", "0"),
                inv("I-4", "D", today.minusDays(75), "300", "0"), inv("I-5", "E", today.minusDays(200), "400", "0"), inv("I-6", "F", null, "50", "0"));
        var r = service.debtors(firm);
        assertEquals(d("2350.00"), r.outstanding());
        assertEquals(d("1300.00"), r.overdue());
        Map<String, Bucket> b = new HashMap<>();
        r.buckets().forEach(x -> b.put(x.key(), x));
        assertEquals(d("1050.00"), b.get("CURRENT").amount()); assertEquals(2L, b.get("CURRENT").count());
        assertEquals(d("400.00"), b.get("D1_30").amount());
        assertEquals(d("200.00"), b.get("D31_60").amount());
        assertEquals(d("300.00"), b.get("D61_90").amount());
        assertEquals(d("400.00"), b.get("D90_PLUS").amount());
        assertEquals(5, r.buckets().size());
        assertEquals(45L, r.rows().get(2).daysOverdue());
        assertEquals("D31_60", r.rows().get(2).bucket());
        assertEquals(d("400.00"), r.rows().get(1).outstanding());
    }

    @Test
    void anInvoiceOneDayLateAndOneDueTodayAreTreatedCorrectly() {
        invoices = List.of(inv("I-1", "A", today.minusDays(1), "100", "0"), inv("I-2", "B", today, "50", "0"));
        var r = service.debtors(firm);
        assertEquals(d("100.00"), r.overdue());
        assertEquals("D1_30", r.rows().get(0).bucket());
        assertEquals("CURRENT", r.rows().get(1).bucket());
    }

    @Test
    void fullyPaidInvoicesDoNotAppear() {
        invoices = List.of(inv("I-1", "A", today.minusDays(5), "500", "500"), inv("I-2", "B", today.minusDays(5), "500", "600"));
        var r = service.debtors(firm);
        assertTrue(r.rows().isEmpty());
        assertEquals(d("0.00"), r.outstanding());
    }

    // ---- billing -------------------------------------------------------------------------------------------------------

    @Test
    void billingTotalsByMonthAndKeepsWriteOffsApart() {
        billing = List.of(new BillingRow("2026-04", "PAID", 2, d("1000"), d("150"), d("1150"), d("1150")), new BillingRow("2026-04", "SENT", 1, d("500"), d("75"), d("575"), d("0")),
                new BillingRow("2026-05", "PARTIALLY_PAID", 1, d("2000"), d("300"), d("2300"), d("1000")), new BillingRow("2026-05", "WRITTEN_OFF", 1, d("100"), d("15"), d("115"), d("0")));
        var r = service.billing(firm, today.minusMonths(3), today);
        assertEquals(2, r.months().size());
        assertEquals(d("1725.00"), r.months().get(0).invoiced());
        assertEquals(3L, r.months().get(0).invoices());
        assertEquals(d("2300.00"), r.months().get(1).invoiced());
        assertEquals(d("115.00"), r.months().get(1).writtenOff());
        assertEquals(2L, r.months().get(1).invoices());
        assertEquals(d("4025.00"), r.invoiced());
        assertEquals(d("525.00"), r.vat());
        assertEquals(d("2150.00"), r.paid());
        assertEquals(d("115.00"), r.writtenOff());
        assertEquals(d("53.4"), r.collectedPercent());
    }

    @Test
    void billingWithNothingIssuedIsZeroNotAnError() {
        var r = service.billing(firm, today.minusDays(30), today);
        assertTrue(r.months().isEmpty());
        assertEquals(d("0.0"), r.collectedPercent());
    }

    @Test
    void billingNeedsAWorkableRange() {
        assertThrows(IllegalArgumentException.class, () -> service.billing(firm, today, today.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.billing(firm, null, today));
        assertThrows(IllegalArgumentException.class, () -> service.billing(firm, today.minusYears(12), today));
    }

    // ---- trust ---------------------------------------------------------------------------------------------------------

    @Test
    void trustTotalsAndCountsOverdrawnClients() {
        trust = List.of(new TrustRow(UUID.randomUUID(), "A", d("1000"), today), new TrustRow(UUID.randomUUID(), "B", d("-50.5"), today), new TrustRow(UUID.randomUUID(), "C", d("250.255"), today), new TrustRow(UUID.randomUUID(), "Z", d("0"), today));
        var r = service.trust(firm, null);
        assertEquals(today, r.asAt());
        assertEquals(today, trustAsAt);
        assertEquals(d("1199.76"), r.total());
        assertEquals(1L, r.overdrawn());
        assertEquals(d("250.26"), r.rows().get(2).balance());
        assertEquals(4, r.rows().size());
    }

    @Test
    void trustAsAtCannotBeInTheFuture() {
        assertThrows(IllegalArgumentException.class, () -> service.trust(firm, today.plusDays(1)));
        service.trust(firm, today.minusDays(20));
        assertEquals(today.minusDays(20), trustAsAt);
    }

    // ---- walls ---------------------------------------------------------------------------------------------------------

    @Test
    void everyReportPassesTheWallsDownAndSaysWhenSomethingIsHidden() {
        UUID m = UUID.randomUUID(), c = UUID.randomUUID();
        walledMatters.add(m); walledClients.add(c);
        assertTrue(service.matters(firm).hidden());
        assertTrue(service.unbilled(firm).hidden());
        assertTrue(service.debtors(firm).hidden());
        assertTrue(service.billing(firm, today.minusDays(10), today).hidden());
        assertTrue(service.trust(firm, null).hidden());
        assertEquals(5, seenMatterLists.size());
        assertTrue(seenMatterLists.stream().allMatch(l -> l.equals(List.of(m))));
        assertTrue(seenClientLists.stream().allMatch(l -> l.equals(List.of(c))));
    }

    @Test
    void withoutWallsNothingIsHiddenAndAPlaceholderIsPassed() {
        assertFalse(service.matters(firm).hidden());
        assertFalse(service.trust(firm, null).hidden());
        assertTrue(seenMatterLists.stream().allMatch(l -> l.equals(List.of(LpWallGuard.Barred.NONE_ID))));
    }

    @Test
    void aWallOnlyOnAClientStillCountsAsHidden() {
        walledClients.add(UUID.randomUUID());
        assertTrue(service.matters(firm).hidden());
    }

    // ---- CSV -----------------------------------------------------------------------------------------------------------

    @Test
    void csvFilesHaveHeadersAndGuardNames() {
        unbilled = List.of(new UnbilledRow(UUID.randomUUID(), "M-1 =Evil", "Smith, J", d("2"), d("1000"), d("0"), null));
        String csv = service.unbilledCsv(firm);
        String[] lines = csv.split("\r\n");
        assertEquals("Matter,Client,Unbilled hours,Time value,Disbursements,Total", lines[0]);
        assertEquals("M-1 =Evil,\"Smith, J\",2.00,1000.00,0.00,1000.00", lines[1]);
        unbilled = List.of(new UnbilledRow(UUID.randomUUID(), "=cmd|' /C calc'!A0", "ok", d("1"), d("1"), d("0"), null));
        assertTrue(service.unbilledCsv(firm).split("\r\n")[1].startsWith("'=cmd"));
    }

    @Test
    void debtorsAndTrustCsvAreWrittenFromTheSameNumbers() {
        invoices = List.of(inv("I-1", "A", today.minusDays(40), "500", "100"));
        assertEquals("I-1,A,," + today.minusDays(70) + "," + today.minusDays(40) + ",500.00,100.00,400.00,40,D31_60", service.debtorsCsv(firm).split("\r\n")[1]);
        trust = List.of(new TrustRow(UUID.randomUUID(), "A", d("-5"), null));
        assertEquals("A,-5.00,", service.trustCsv(firm, null).split("\r\n")[1]);
        assertEquals("Month,Invoices,Invoiced (incl. VAT),Paid,Written off", service.billingCsv(firm, today.minusDays(5), today).split("\r\n")[0]);
        counts = List.of(new MatterCount("OPEN", "LITIGATION", 3));
        assertEquals("OPEN,LITIGATION,3", service.mattersCsv(firm).split("\r\n")[1]);
    }
}
