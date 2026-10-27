package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dashboard, vendor, asset and portfolio reports: arithmetic, access scope, cost visibility and period rules.
 * Plain JUnit with hand-written fakes — no Mockito.
 */
class FmReportingServiceTest {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final TenantId tenant = TenantId.generate();
    private final UUID clientA = UUID.randomUUID();
    private final UUID clientB = UUID.randomUUID();
    private final UUID site = UUID.randomUUID();
    private final UUID vendor1 = UUID.randomUUID();
    private final UUID vendor2 = UUID.randomUUID();
    private final UUID pump = UUID.randomUUID();
    private final UUID lift = UUID.randomUUID();

    private final List<FmWorkOrder> orders = new ArrayList<>();
    private final List<FmInvoice> invoices = new ArrayList<>();
    private final Map<UUID, FmAsset> assets = new HashMap<>();
    private final Map<UUID, FmClient> clients = new HashMap<>();
    private final Set<String> authorities = new HashSet<>();
    private FmAccessScope scope = FmAccessScope.unrestrictedScope();
    private long draftCreditNotes;
    private BigDecimal unbilled;
    /** The scope flags the service passed to the last work-order query, to prove scope is forwarded. */
    private Boolean lastAllClients;
    private Collection<UUID> lastClientIds;
    private Boolean lastAllJobs;

    @SuppressWarnings("unchecked")
    private <T> T fake(Class<T> type, BiFunction<String, Object[], Object> h) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            Object r = h.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    private static final Object NOPE = new Object();

    private boolean inScope(FmWorkOrder w, boolean allClients, Collection<UUID> clientIds, boolean allJobs, Collection<UUID> techIds) {
        return (allClients || clientIds.contains(w.getClientId())) && (allJobs || (w.getTechnicianId() != null && techIds.contains(w.getTechnicianId())));
    }

    private final FmWorkOrderRepository woRepo = fake(FmWorkOrderRepository.class, (n, a) -> switch (n) {
        case "findOpenInScope" -> {
            record(a, 1);
            yield orders.stream().filter(w -> !Set.of("COMPLETED", "CANCELLED", "REJECTED").contains(w.getStatus()))
                    .filter(w -> inScope(w, (boolean) a[1], (Collection<UUID>) a[2], (boolean) a[3], (Collection<UUID>) a[4])).toList();
        }
        case "findCompletedBetweenInScope" -> orders.stream().filter(w -> "COMPLETED".equals(w.getStatus()))
                .filter(w -> !w.getCompletedAt().isBefore((Instant) a[1]) && w.getCompletedAt().isBefore((Instant) a[2]))
                .filter(w -> inScope(w, (boolean) a[3], (Collection<UUID>) a[4], (boolean) a[5], (Collection<UUID>) a[6])).toList();
        case "findCreatedBetweenInScope" -> {
            record(a, 3);
            yield orders.stream().filter(w -> !Set.of("REQUESTED", "REJECTED").contains(w.getStatus()))
                    .filter(w -> !w.getCreatedAt().isBefore((Instant) a[1]) && w.getCreatedAt().isBefore((Instant) a[2]))
                    .filter(w -> inScope(w, (boolean) a[3], (Collection<UUID>) a[4], (boolean) a[5], (Collection<UUID>) a[6])).toList();
        }
        case "sumUnbilledChargeInScope" -> unbilled;
        default -> NOPE;
    });

    @SuppressWarnings("unchecked")
    private void record(Object[] a, int at) {
        lastAllClients = (Boolean) a[at];
        lastClientIds = (Collection<UUID>) a[at + 1];
        lastAllJobs = (Boolean) a[at + 2];
    }

    private final FmInvoiceRepository invRepo = fake(FmInvoiceRepository.class, (n, a) -> switch (n) {
        case "findByStatusesInScope" -> invoices.stream().filter(i -> ((Collection<String>) a[1]).contains(i.getStatus())).toList();
        case "findIssuedBetweenInScope" -> invoices.stream().filter(i -> !Set.of("DRAFT", "CANCELLED").contains(i.getStatus())).toList();
        default -> NOPE;
    });
    private final FmCreditNoteRepository cnRepo = fake(FmCreditNoteRepository.class, (n, a) ->
            "countByStatusInScope".equals(n) ? (Object) draftCreditNotes : NOPE);
    private final FmAssetRepository assetRepo = fake(FmAssetRepository.class, (n, a) ->
            "findAllById".equals(n) ? ((Collection<UUID>) a[0]).stream().map(assets::get).filter(Objects::nonNull).toList() : NOPE);
    private final FmClientRepository clientRepo = fake(FmClientRepository.class, (n, a) ->
            "findAllById".equals(n) ? ((Collection<UUID>) a[0]).stream().map(clients::get).filter(Objects::nonNull).toList() : NOPE);

    private final FmAccessPolicy policy = new FmAccessPolicy(null, null, null, null) {
        @Override public FmAccessScope scope(TenantId t) { return scope; }
        @Override public boolean hasAuthority(String a) { return authorities.contains(a); }
    };
    private final FmReportingService service = new FmReportingService(woRepo, invRepo, cnRepo, assetRepo, clientRepo, policy);

    // ── builders ─────────────────────────────────────────────────────────────

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private FmWorkOrder wo(UUID client, String status, String priority) {
        FmWorkOrder w = FmWorkOrder.create(tenant, "WO-" + (orders.size() + 1), client, site, null, null, "CORRECTIVE", priority,
                "Fault", "Reception", null);
        set(w, "status", status);
        set(w, "createdAt", Instant.now().minus(Duration.ofHours(10)));
        orders.add(w);
        return w;
    }

    private FmWorkOrder completed(UUID client, int hoursToFix, Instant dueAt) {
        FmWorkOrder w = wo(client, "COMPLETED", "NORMAL");
        Instant created = Instant.now().minus(Duration.ofDays(2));
        set(w, "createdAt", created);
        set(w, "completedAt", created.plus(Duration.ofHours(hoursToFix)));
        if (dueAt != null) set(w, "resolutionDueAt", dueAt);
        return w;
    }

    private FmInvoice invoice(String status, String total, String paid, int dueInDays) {
        try {
            Constructor<FmInvoice> c = FmInvoice.class.getDeclaredConstructor();
            c.setAccessible(true);
            FmInvoice i = c.newInstance();
            set(i, "status", status);
            set(i, "total", new BigDecimal(total));
            set(i, "amountPaid", new BigDecimal(paid));
            set(i, "creditedAmount", ZERO);
            set(i, "dueDate", FmTime.today().plusDays(dueInDays));
            invoices.add(i);
            return i;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static LocalDate today() { return FmTime.today(); }

    // ── dashboard: work orders ───────────────────────────────────────────────

    @Test
    void dashboardCountsOpenWorkByStatusAndPriority() {
        wo(clientA, "OPEN", "URGENT");
        wo(clientA, "ASSIGNED", "NORMAL");
        wo(clientA, "IN_PROGRESS", "NORMAL");
        wo(clientA, "ON_HOLD", "LOW");
        wo(clientA, "PENDING_VERIFICATION", "HIGH");
        wo(clientA, "REQUESTED", "NORMAL");
        FmDashboardResponse.WorkOrderKpis k = service.dashboard(tenant).workOrders();
        assertEquals(5, k.open());
        assertEquals(1, k.unassigned());
        assertEquals(1, k.onHold());
        assertEquals(1, k.awaitingVerification());
        assertEquals(1, k.awaitingApproval());
        assertEquals(2L, (long) k.openByPriority().get("NORMAL"));
        assertEquals(1L, (long) k.openByPriority().get("URGENT"));
        assertEquals(1L, (long) k.openByStatus().get("ON_HOLD"));
    }

    @Test
    void awaitingApprovalRequestsAreNotCountedAsOpenWork() {
        wo(clientA, "REQUESTED", "NORMAL");
        FmDashboardResponse.WorkOrderKpis k = service.dashboard(tenant).workOrders();
        assertEquals(0, k.open());
        assertEquals(1, k.awaitingApproval());
    }

    @Test
    void overdueMeansPastScheduledDateAndNotWaitingOnAVerifier() {
        FmWorkOrder late = wo(clientA, "IN_PROGRESS", "NORMAL");
        set(late, "scheduledDate", today().minusDays(2));
        FmWorkOrder verifying = wo(clientA, "PENDING_VERIFICATION", "NORMAL");
        set(verifying, "scheduledDate", today().minusDays(2));
        FmWorkOrder dueToday = wo(clientA, "OPEN", "NORMAL");
        set(dueToday, "scheduledDate", today());
        assertEquals(1, service.dashboard(tenant).workOrders().overdue());
    }

    @Test
    void breachedOrAtRiskSlaIsCountedOncePerWorkOrder() {
        Instant now = Instant.now();
        FmWorkOrder breachedBoth = wo(clientA, "IN_PROGRESS", "URGENT");
        set(breachedBoth, "responseDueAt", now.minus(Duration.ofHours(1)));
        set(breachedBoth, "resolutionDueAt", now.minus(Duration.ofMinutes(5)));
        FmWorkOrder atRisk = wo(clientA, "IN_PROGRESS", "URGENT");
        set(atRisk, "createdAt", now.minus(Duration.ofMinutes(50)));
        set(atRisk, "resolutionDueAt", now.plus(Duration.ofMinutes(10)));
        FmWorkOrder fine = wo(clientA, "IN_PROGRESS", "NORMAL");
        set(fine, "createdAt", now.minus(Duration.ofMinutes(5)));
        set(fine, "resolutionDueAt", now.plus(Duration.ofDays(2)));
        FmDashboardResponse.WorkOrderKpis k = service.dashboard(tenant).workOrders();
        assertEquals(1, k.slaBreached());
        assertEquals(1, k.slaAtRisk());
    }

    @Test
    void thirtyDayThroughputAveragesRepairTimeAndSlaCompliance() {
        Instant now = Instant.now();
        completed(clientA, 2, now);                       // met (done 2h after creation, due now)
        completed(clientA, 4, now.minus(Duration.ofDays(5))); // breached
        completed(clientA, 6, null);                      // no SLA
        FmWorkOrder old = completed(clientA, 100, now);
        set(old, "completedAt", Instant.now().minus(Duration.ofDays(45))); // outside the window
        FmDashboardResponse.WorkOrderKpis k = service.dashboard(tenant).workOrders();
        assertEquals(3, k.completedLast30Days());
        assertEquals(0, bd("4.00").compareTo(k.avgResolutionHoursLast30Days()));
        assertEquals(0, bd("50.00").compareTo(k.resolutionSlaComplianceLast30Days()));
    }

    @Test
    void anEmptyDashboardHasZerosAndNoAveragesNotErrors() {
        FmDashboardResponse.WorkOrderKpis k = service.dashboard(tenant).workOrders();
        assertEquals(0, k.open());
        assertNull(k.avgResolutionHoursLast30Days());
        assertNull(k.resolutionSlaComplianceLast30Days());
    }

    @Test
    void theCallersScopeIsPassedToTheQueries() {
        UUID mine = UUID.randomUUID();
        scope = FmAccessScope.restrictedToClients(List.of(mine), List.of(site));
        wo(clientA, "OPEN", "NORMAL"); // someone else's client
        wo(mine, "OPEN", "NORMAL");
        assertEquals(1, service.dashboard(tenant).workOrders().open());
        assertFalse(lastAllClients);
        assertEquals(List.of(mine), List.copyOf(lastClientIds));
    }

    @Test
    void aTechnicianSeesOnlyTheirOwnJobs() {
        UUID tech = UUID.randomUUID();
        scope = FmAccessScope.assignedJobs(List.of(site), List.of(tech));
        FmWorkOrder mine = wo(clientA, "ASSIGNED", "NORMAL");
        set(mine, "technicianId", tech);
        wo(clientA, "ASSIGNED", "NORMAL");
        assertEquals(1, service.dashboard(tenant).workOrders().open());
        assertFalse(lastAllJobs);
    }

    // ── dashboard: finance ───────────────────────────────────────────────────

    @Test
    void financeIsHiddenWithoutACostOrBillingAuthority() {
        invoice("SENT", "1000.00", "0.00", 5);
        authorities.add("FACILITIESMANAGEMENT_READ");
        assertNull(service.dashboard(tenant).finance());
    }

    @Test
    void financeIsHiddenFromTechniciansEvenWithAnAuthority() {
        authorities.add("FACILITIESMANAGEMENT_ADMIN");
        scope = FmAccessScope.assignedJobs(List.of(site), List.of(UUID.randomUUID()));
        assertNull(service.dashboard(tenant).finance());
    }

    @Test
    void anyCostOrBillingAuthorityUnlocksFinance() {
        for (String a : List.of("FACILITIESMANAGEMENT_ADMIN", "FACILITIESMANAGEMENT_COST_READ",
                "FACILITIESMANAGEMENT_BILLING_MANAGE", "FACILITIESMANAGEMENT_BILLING_APPROVE")) {
            authorities.clear();
            authorities.add(a);
            assertNotNull(service.dashboard(tenant).finance(), a);
        }
    }

    @Test
    void receivablesAreBalancesAfterPaymentsAndCreditsWithOverdueSplitOut() {
        authorities.add("FACILITIESMANAGEMENT_BILLING_MANAGE");
        invoice("SENT", "1000.00", "0.00", 10);      // not yet due
        invoice("PARTIAL", "2000.00", "500.00", -3); // overdue, balance 1500
        FmInvoice credited = invoice("SENT", "800.00", "0.00", -1);
        set(credited, "creditedAmount", bd("300.00")); // overdue, balance 500
        invoice("PAID", "999.00", "999.00", -30);     // not a receivable
        FmDashboardResponse.FinanceKpis f = service.dashboard(tenant).finance();
        assertEquals(0, bd("3000.00").compareTo(f.outstandingReceivables()));
        assertEquals(0, bd("2000.00").compareTo(f.overdueReceivables()));
        assertEquals(2, f.overdueInvoices());
    }

    @Test
    void financeCountsWorkAwaitingAction() {
        authorities.add("FACILITIESMANAGEMENT_ADMIN");
        invoice("DRAFT", "100.00", "0.00", 30);
        invoice("DRAFT", "100.00", "0.00", 30);
        invoice("APPROVED", "100.00", "0.00", 30);
        draftCreditNotes = 3;
        unbilled = bd("4500.00");
        FmDashboardResponse.FinanceKpis f = service.dashboard(tenant).finance();
        assertEquals(2, f.draftInvoices());
        assertEquals(1, f.approvedNotSent());
        assertEquals(3, f.creditNotesAwaitingApproval());
        assertEquals(0, bd("4500.00").compareTo(f.unbilledCompletedWork()));
    }

    @Test
    void invoicedThisMonthSumsIssuedInvoiceTotalsAndNothingUnbilledIsZeroNotNull() {
        authorities.add("FACILITIESMANAGEMENT_ADMIN");
        invoice("SENT", "1150.00", "0.00", 30);
        invoice("PAID", "2300.00", "2300.00", 30);
        FmDashboardResponse.FinanceKpis f = service.dashboard(tenant).finance();
        assertEquals(0, bd("3450.00").compareTo(f.invoicedThisMonth()));
        assertEquals(0, ZERO.compareTo(f.unbilledCompletedWork()));
    }

    // ── vendor performance ───────────────────────────────────────────────────

    private FmWorkOrder vendorJob(UUID vendor, String name, String status, int hours, String cost) {
        FmWorkOrder w = wo(clientA, status, "NORMAL");
        set(w, "vendorId", vendor);
        set(w, "vendorName", name);
        if ("COMPLETED".equals(status)) {
            set(w, "completedAt", w.getCreatedAt().plus(Duration.ofHours(hours)));
        }
        if (cost != null) set(w, "internalCost", bd(cost));
        return w;
    }

    private LocalDate from() { return today().minusDays(30); }

    @Test
    void vendorPerformanceSummarisesEachVendorsJobs() {
        authorities.add("FACILITIESMANAGEMENT_COST_READ");
        vendorJob(vendor1, "ACME", "COMPLETED", 2, "100.00");
        vendorJob(vendor1, "ACME", "COMPLETED", 4, "300.00");
        vendorJob(vendor1, "ACME", "IN_PROGRESS", 0, null);
        vendorJob(vendor1, "ACME", "CANCELLED", 0, null);
        vendorJob(vendor2, "Bolt", "COMPLETED", 1, "50.00");
        wo(clientA, "COMPLETED", "NORMAL"); // no vendor — in-house
        set(orders.get(orders.size() - 1), "completedAt", Instant.now());

        FmVendorPerformanceResponse r = service.vendorPerformance(tenant, from(), today());
        assertEquals(2, r.vendors().size());
        FmVendorPerformanceResponse.Row acme = r.vendors().get(0);
        assertEquals("ACME", acme.vendorName());
        assertEquals(4, acme.jobs());
        assertEquals(2, acme.completed());
        assertEquals(1, acme.open());
        assertEquals(1, acme.cancelled());
        assertEquals(0, bd("3.00").compareTo(acme.avgResolutionHours()));
        assertEquals(0, bd("400.00").compareTo(acme.totalCost()));
    }

    @Test
    void vendorSlaCompliesWhenResolvedBeforeTheTargetAndBreachesAfter() {
        FmWorkOrder good = vendorJob(vendor1, "ACME", "COMPLETED", 2, null);
        set(good, "resolutionDueAt", good.getCreatedAt().plus(Duration.ofHours(3)));
        FmWorkOrder bad = vendorJob(vendor1, "ACME", "COMPLETED", 5, null);
        set(bad, "resolutionDueAt", bad.getCreatedAt().plus(Duration.ofHours(3)));
        FmVendorPerformanceResponse.Row row = service.vendorPerformance(tenant, from(), today()).vendors().get(0);
        assertEquals(1, row.slaMet());
        assertEquals(1, row.slaBreached());
        assertEquals(0, bd("50.00").compareTo(row.slaCompliancePercent()));
    }

    @Test
    void vendorCostsAreOmittedWithoutACostAuthority() {
        authorities.add("FACILITIESMANAGEMENT_READ");
        vendorJob(vendor1, "ACME", "COMPLETED", 2, "100.00");
        FmVendorPerformanceResponse.Row row = service.vendorPerformance(tenant, from(), today()).vendors().get(0);
        assertNull(row.totalCost());
        assertNull(row.avgCost());
        assertEquals(1, row.completed());
    }

    @Test
    void requestsAwaitingApprovalAreNotVendorWork() {
        vendorJob(vendor1, "ACME", "REQUESTED", 0, null);
        assertTrue(service.vendorPerformance(tenant, from(), today()).vendors().isEmpty());
    }

    @Test
    void managementReportsRefuseFieldTechnicians() {
        scope = FmAccessScope.assignedJobs(List.of(site), List.of(UUID.randomUUID()));
        for (Runnable r : List.<Runnable>of(
                () -> service.vendorPerformance(tenant, from(), today()),
                () -> service.assetReliability(tenant, from(), today(), null),
                () -> service.slaPortfolio(tenant, from(), today()))) {
            HandyFlowException e = assertThrows(HandyFlowException.class, r::run);
            assertEquals("SCOPE_RESTRICTED", e.getErrorCode());
        }
    }

    @Test
    void periodsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> service.vendorPerformance(tenant, today(), today().minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.vendorPerformance(tenant, today().minusDays(400), today()));
        assertThrows(IllegalArgumentException.class, () -> service.vendorPerformance(tenant, null, today()));
        assertDoesNotThrow(() -> service.vendorPerformance(tenant, today().minusDays(366), today()));
    }

    // ── asset reliability ────────────────────────────────────────────────────

    private FmWorkOrder fault(UUID asset, int hoursAgo, Integer downtime, String cause) {
        FmWorkOrder w = wo(clientA, "COMPLETED", "NORMAL");
        Instant created = Instant.now().minus(Duration.ofHours(hoursAgo));
        set(w, "createdAt", created);
        set(w, "completedAt", created.plus(Duration.ofHours(2)));
        set(w, "assetId", asset);
        set(w, "downtimeMinutes", downtime);
        set(w, "failureCause", cause);
        set(w, "internalCost", bd("100.00"));
        return w;
    }

    private void addAsset(UUID id, String tag, String name) {
        FmAsset a = FmAsset.create(tenant, site, tag, name, "PLANT", null, null, null, null, null, null, "HIGH", null);
        set(a, "id", id);
        assets.put(id, a);
    }

    @Test
    void assetReliabilityRanksWorstFirstWithNames() {
        addAsset(pump, "P-1", "Booster pump");
        addAsset(lift, "L-1", "Lift 2");
        fault(pump, 100, 60, "Seal");
        fault(pump, 90, 30, "seal");
        fault(lift, 50, 10, "Cable");
        FmAssetReliabilityResponse r = service.assetReliability(tenant, from(), today(), null);
        assertEquals(20, r.limit());
        assertEquals(2, r.assets().size());
        FmAssetReliabilityResponse.Row worst = r.assets().get(0);
        assertEquals(pump, worst.assetId());
        assertEquals("P-1", worst.assetTag());
        assertEquals("Booster pump", worst.assetName());
        assertEquals(2, worst.failures());
        assertEquals(1, worst.repeatFailures());
        assertEquals(90, worst.downtimeMinutes());
        assertEquals("Seal", worst.topFailureCause());
        assertEquals(0, bd("2.00").compareTo(worst.mttrHours()));
    }

    @Test
    void plannedAndInspectionWorkAreNotFailures() {
        FmWorkOrder ppm = fault(pump, 10, null, null);
        set(ppm, "category", "PPM");
        FmWorkOrder inspection = fault(pump, 20, null, null);
        set(inspection, "category", "INSPECTION");
        assertTrue(service.assetReliability(tenant, from(), today(), null).assets().isEmpty());
    }

    @Test
    void anAssetMissingFromTheRegisterStillReportsWithoutNames() {
        fault(pump, 10, null, null);
        FmAssetReliabilityResponse.Row row = service.assetReliability(tenant, from(), today(), null).assets().get(0);
        assertEquals(pump, row.assetId());
        assertNull(row.assetTag());
    }

    @Test
    void theLimitIsClampedToOneToOneHundred() {
        fault(pump, 10, null, null);
        assertEquals(20, service.assetReliability(tenant, from(), today(), null).limit());
        assertEquals(1, service.assetReliability(tenant, from(), today(), 0).limit());
        assertEquals(1, service.assetReliability(tenant, from(), today(), -5).limit());
        assertEquals(100, service.assetReliability(tenant, from(), today(), 5000).limit());
    }

    @Test
    void aPeriodRunningIntoTheFutureIsMeasuredOnlyUpToNow() {
        // 10 days (+ today so far) elapsed, 20 days still to come; one failure, no downtime => MTBF 240-264h, not 720h
        fault(pump, 5, null, null);
        BigDecimal mtbf = service.assetReliability(tenant, today().minusDays(10), today().plusDays(20), null).assets().get(0).mtbfHours();
        assertTrue(mtbf.compareTo(bd("264.1")) < 0 && mtbf.compareTo(bd("240")) >= 0, "was " + mtbf);
    }

    @Test
    void aPeriodStartingInTheFutureIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.assetReliability(tenant, today().plusDays(1), today().plusDays(5), null));
    }

    @Test
    void assetCostIsHiddenWithoutACostAuthority() {
        authorities.add("FACILITIESMANAGEMENT_READ");
        fault(pump, 10, null, null);
        assertNull(service.assetReliability(tenant, from(), today(), null).assets().get(0).totalCost());
        authorities.add("FACILITIESMANAGEMENT_ADMIN");
        assertEquals(0, bd("100.00").compareTo(service.assetReliability(tenant, from(), today(), null).assets().get(0).totalCost()));
    }

    // ── portfolio SLA ────────────────────────────────────────────────────────

    private void slaJob(UUID client, boolean met) {
        FmWorkOrder w = wo(client, "COMPLETED", "NORMAL");
        Instant created = Instant.now().minus(Duration.ofHours(20));
        set(w, "createdAt", created);
        set(w, "completedAt", created.plus(Duration.ofHours(4)));
        set(w, "resolutionDueAt", created.plus(Duration.ofHours(met ? 8 : 2)));
    }

    private void addClient(UUID id, String name) {
        FmClient c = FmClient.create(tenant, "C" + id.toString().substring(0, 4), name, null, null, null, null, null);
        set(c, "id", id);
        clients.put(id, c);
    }

    @Test
    void portfolioRanksClientsByWorstComplianceAndTotalsAcrossThem() {
        addClient(clientA, "Alpha Mall");
        addClient(clientB, "Beta Towers");
        slaJob(clientA, true);
        slaJob(clientA, true);
        slaJob(clientB, true);
        slaJob(clientB, false);
        FmSlaPortfolioResponse r = service.slaPortfolio(tenant, from(), today());
        assertEquals(4, r.workOrdersWithSla());
        assertEquals(0, bd("75.00").compareTo(r.resolutionCompliancePercent()));
        assertEquals("Beta Towers", r.clients().get(0).clientName()); // 50% beats 100% for attention
        assertEquals("Alpha Mall", r.clients().get(1).clientName());
        assertEquals(0, bd("50.00").compareTo(r.clients().get(0).resolutionCompliancePercent()));
    }

    @Test
    void portfolioIgnoresWorkWithoutAnSlaAndCancelledWork() {
        addClient(clientA, "Alpha Mall");
        wo(clientA, "COMPLETED", "NORMAL"); // no SLA targets
        FmWorkOrder cancelled = wo(clientA, "CANCELLED", "NORMAL");
        set(cancelled, "resolutionDueAt", Instant.now());
        FmSlaPortfolioResponse r = service.slaPortfolio(tenant, from(), today());
        assertEquals(0, r.workOrdersWithSla());
        assertTrue(r.clients().isEmpty());
        assertNull(r.resolutionCompliancePercent());
    }

    @Test
    void portfolioOnlyCoversClientsInTheCallersScope() {
        scope = FmAccessScope.restrictedToClients(List.of(clientA), List.of(site));
        addClient(clientA, "Alpha Mall");
        addClient(clientB, "Beta Towers");
        slaJob(clientA, true);
        slaJob(clientB, false);
        FmSlaPortfolioResponse r = service.slaPortfolio(tenant, from(), today());
        assertEquals(1, r.clients().size());
        assertEquals("Alpha Mall", r.clients().get(0).clientName());
    }

    @Test
    void clientsWithNothingConcludedSortLast() {
        addClient(clientA, "Alpha Mall");
        addClient(clientB, "Beta Towers");
        slaJob(clientA, true);
        FmWorkOrder open = wo(clientB, "IN_PROGRESS", "NORMAL");
        set(open, "resolutionDueAt", Instant.now().plus(Duration.ofDays(3)));
        FmSlaPortfolioResponse r = service.slaPortfolio(tenant, from(), today());
        assertEquals("Alpha Mall", r.clients().get(0).clientName());
        assertNull(r.clients().get(1).resolutionCompliancePercent());
    }
}
