package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.FacilityAsset;
import za.co.handyflow.platform.facilities.domain.model.FacilityWorkOrder;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/** In-house facilities dashboard, contractor and asset-reliability reports: arithmetic, scope and period rules. */
class FacilityReportServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID site = UUID.randomUUID();
    private final UUID otherSite = UUID.randomUUID();
    private final UUID vendor1 = UUID.randomUUID();
    private final UUID vendor2 = UUID.randomUUID();
    private final UUID pump = UUID.randomUUID();
    private final UUID lift = UUID.randomUUID();

    private final List<FacilityWorkOrder> orders = new ArrayList<>();
    private final Map<UUID, FacilityAsset> assets = new HashMap<>();
    private AccessScope scope = AccessScope.unrestricted();
    private long ppmOverdue, expiring, lapsed;
    private Boolean lastAllSites;
    private Collection<UUID> lastSiteIds;

    @SuppressWarnings("unchecked")
    private <T> T fake(Class<T> type, BiFunction<String, Object[], Object> h) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            Object r = h.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    private static final Object NOPE = new Object();

    @SuppressWarnings("unchecked")
    private boolean inScope(FacilityWorkOrder w, Object[] a, int at) {
        boolean allSites = (boolean) a[at]; Collection<UUID> siteIds = (Collection<UUID>) a[at + 1];
        boolean allJobs = (boolean) a[at + 2]; Collection<UUID> techIds = (Collection<UUID>) a[at + 3];
        boolean allReq = (boolean) a[at + 4]; UUID requester = (UUID) a[at + 5];
        lastAllSites = allSites; lastSiteIds = siteIds;
        return (allSites || siteIds.contains(w.getSiteId()))
                && (allJobs || (w.getTechnicianId() != null && techIds.contains(w.getTechnicianId())))
                && (allReq || requester.equals(w.getRequestedByUserId()));
    }

    private final FacilityWorkOrderRepository woRepo = fake(FacilityWorkOrderRepository.class, (n, a) -> switch (n) {
        case "findOpenInScope" -> orders.stream().filter(w -> !Set.of("COMPLETED", "CANCELLED", "REJECTED").contains(w.getStatus()))
                .filter(w -> inScope(w, a, 1)).toList();
        case "findCompletedBetweenInScope" -> orders.stream().filter(w -> "COMPLETED".equals(w.getStatus()))
                .filter(w -> !w.getCompletedAt().isBefore((Instant) a[1]) && w.getCompletedAt().isBefore((Instant) a[2]))
                .filter(w -> inScope(w, a, 3)).toList();
        case "findCreatedBetweenInScope" -> orders.stream().filter(w -> !Set.of("REQUESTED", "REJECTED").contains(w.getStatus()))
                .filter(w -> !w.getCreatedAt().isBefore((Instant) a[1]) && w.getCreatedAt().isBefore((Instant) a[2]))
                .filter(w -> inScope(w, a, 3)).toList();
        default -> NOPE;
    });
    private final FacilityPpmScheduleRepository ppmRepo = fake(FacilityPpmScheduleRepository.class, (n, a) ->
            "countOverdueInScope".equals(n) ? (Object) ppmOverdue : NOPE);
    private final FacilityComplianceCertificateRepository certRepo = fake(FacilityComplianceCertificateRepository.class, (n, a) -> switch (n) {
        case "countExpiringInScope" -> (Object) expiring;
        case "countLapsedInScope" -> (Object) lapsed;
        default -> NOPE;
    });
    @SuppressWarnings("unchecked")
    private final FacilityAssetRepository assetRepo = fake(FacilityAssetRepository.class, (n, a) ->
            "findAllById".equals(n) ? ((Collection<UUID>) a[0]).stream().map(assets::get).filter(Objects::nonNull).toList() : NOPE);
    private final FacilityAccessPolicy policy = new FacilityAccessPolicy(null, null, null) {
        @Override public AccessScope scope(TenantId t) { return scope; }
    };
    private final FacilityReportService service = new FacilityReportService(woRepo, ppmRepo, certRepo, assetRepo, policy);

    private static void set(Object o, String f, Object v) {
        try { Field x = o.getClass().getDeclaredField(f); x.setAccessible(true); x.set(o, v); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static LocalDate today() { return FacilityTime.today(); }
    private LocalDate from() { return today().minusDays(30); }

    private FacilityWorkOrder wo(String status, String priority) {
        FacilityWorkOrder w = FacilityWorkOrder.create(tenant, "WO-" + (orders.size() + 1), site, null, null, "CORRECTIVE",
                priority, "Fault", "Reception", null);
        set(w, "status", status);
        set(w, "createdAt", Instant.now().minus(Duration.ofHours(10)));
        orders.add(w);
        return w;
    }

    private FacilityWorkOrder completed(int hours, String cost, int rework, Integer downtime) {
        FacilityWorkOrder w = wo("COMPLETED", "NORMAL");
        Instant created = Instant.now().minus(Duration.ofDays(2));
        set(w, "createdAt", created);
        set(w, "completedAt", created.plus(Duration.ofHours(hours)));
        set(w, "cost", bd(cost));
        set(w, "reworkCount", rework);
        set(w, "downtimeMinutes", downtime);
        return w;
    }

    // ── dashboard ────────────────────────────────────────────────────────────

    @Test
    void dashboardCountsOpenWorkAndKeepsPendingRequestsSeparate() {
        wo("OPEN", "URGENT");
        wo("ASSIGNED", "NORMAL");
        wo("IN_PROGRESS", "NORMAL");
        wo("ON_HOLD", "LOW");
        wo("PENDING_VERIFICATION", "HIGH");
        wo("REQUESTED", "NORMAL");
        FacilityDashboardResponse d = service.dashboard(tenant);
        assertEquals(5, d.open());
        assertEquals(1, d.unassigned());
        assertEquals(1, d.onHold());
        assertEquals(1, d.awaitingVerification());
        assertEquals(1, d.awaitingApproval());
        assertEquals(2L, (long) d.openByPriority().get("NORMAL"));
    }

    @Test
    void overdueIgnoresWorkAwaitingAVerifierAndWorkDueToday() {
        set(wo("IN_PROGRESS", "NORMAL"), "scheduledDate", today().minusDays(2));
        set(wo("PENDING_VERIFICATION", "NORMAL"), "scheduledDate", today().minusDays(2));
        set(wo("OPEN", "NORMAL"), "scheduledDate", today());
        assertEquals(1, service.dashboard(tenant).overdue());
    }

    @Test
    void thirtyDayFiguresCoverOnlyRecentlyCompletedWork() {
        completed(2, "100.00", 0, 30);
        completed(6, "300.00", 1, 90);
        FacilityWorkOrder old = completed(1, "999.00", 0, 500);
        set(old, "completedAt", Instant.now().minus(Duration.ofDays(60)));
        FacilityDashboardResponse d = service.dashboard(tenant);
        assertEquals(2, d.completedLast30Days());
        assertEquals(0, bd("4.00").compareTo(d.avgCompletionHoursLast30Days()));
        assertEquals(0, bd("50.00").compareTo(d.reworkRatePercentLast30Days()));
        assertEquals(120, d.downtimeMinutesLast30Days());
        assertEquals(0, bd("400.00").compareTo(d.costLast30Days()));
    }

    @Test
    void anEmptyDashboardHasZerosAndNoAverages() {
        FacilityDashboardResponse d = service.dashboard(tenant);
        assertEquals(0, d.open());
        assertNull(d.avgCompletionHoursLast30Days());
        assertNull(d.reworkRatePercentLast30Days());
        assertEquals(0, BigDecimal.ZERO.compareTo(d.costLast30Days()));
    }

    @Test
    void planningAndComplianceCountsComeFromTheirRepositories() {
        ppmOverdue = 4; expiring = 2; lapsed = 1;
        FacilityDashboardResponse d = service.dashboard(tenant);
        assertEquals(4, d.ppmSchedulesOverdue());
        assertEquals(2, d.certificatesExpiringIn30Days());
        assertEquals(1, d.certificatesExpired());
    }

    @Test
    void siteScopeIsAppliedToTheDashboard() {
        scope = AccessScope.restrictedToSites(List.of(otherSite));
        wo("OPEN", "NORMAL"); // at `site`, not the caller's
        FacilityWorkOrder mine = wo("OPEN", "NORMAL");
        set(mine, "siteId", otherSite);
        assertEquals(1, service.dashboard(tenant).open());
        assertFalse(lastAllSites);
    }

    @Test
    void aRequesterSeesOnlyTheirOwnRequests() {
        UUID me = UUID.randomUUID();
        scope = AccessScope.forRequester(List.of(), me);
        wo("REQUESTED", "NORMAL");
        set(wo("REQUESTED", "NORMAL"), "requestedByUserId", me);
        assertEquals(1, service.dashboard(tenant).awaitingApproval());
    }

    // ── contractors ──────────────────────────────────────────────────────────

    private FacilityWorkOrder vendorJob(UUID vendor, String name, String status, int hours, String cost, int rework) {
        FacilityWorkOrder w = wo(status, "NORMAL");
        set(w, "vendorId", vendor);
        set(w, "vendorName", name);
        if ("COMPLETED".equals(status)) set(w, "completedAt", w.getCreatedAt().plus(Duration.ofHours(hours)));
        if (cost != null) set(w, "cost", bd(cost));
        set(w, "reworkCount", rework);
        return w;
    }

    @Test
    void contractorPerformanceSummarisesEachVendor() {
        vendorJob(vendor1, "ACME", "COMPLETED", 2, "100.00", 0);
        vendorJob(vendor1, "ACME", "COMPLETED", 4, "300.00", 1);
        vendorJob(vendor1, "ACME", "IN_PROGRESS", 0, null, 0);
        vendorJob(vendor1, "ACME", "CANCELLED", 0, null, 0);
        vendorJob(vendor2, "Bolt", "COMPLETED", 1, "50.00", 0);
        wo("COMPLETED", "NORMAL"); // in-house job: no vendor
        set(orders.get(orders.size() - 1), "completedAt", Instant.now());

        FacilityVendorPerformanceResponse r = service.vendorPerformance(tenant, from(), today());
        assertEquals(2, r.vendors().size());
        FacilityVendorPerformanceResponse.Row acme = r.vendors().get(0);
        assertEquals("ACME", acme.vendorName());
        assertEquals(4, acme.jobs());
        assertEquals(2, acme.completed());
        assertEquals(1, acme.open());
        assertEquals(1, acme.cancelled());
        assertEquals(0, bd("3.00").compareTo(acme.avgCompletionHours()));
        assertEquals(0, bd("50.00").compareTo(acme.reworkRatePercent()));
        assertEquals(0, bd("400.00").compareTo(acme.totalCost()));
        assertEquals(0, bd("200.00").compareTo(acme.avgCost()));
    }

    @Test
    void requestsAwaitingApprovalAreNotContractorWork() {
        vendorJob(vendor1, "ACME", "REQUESTED", 0, null, 0);
        assertTrue(service.vendorPerformance(tenant, from(), today()).vendors().isEmpty());
    }

    @Test
    void managementReportsRefuseTechniciansAndRequesters() {
        List<AccessScope> restricted = List.of(AccessScope.assignedJobs(List.of(site), List.of(UUID.randomUUID())),
                AccessScope.forRequester(List.of(), UUID.randomUUID()));
        for (AccessScope s : restricted) {
            scope = s;
            for (Runnable r : List.<Runnable>of(() -> service.vendorPerformance(tenant, from(), today()),
                    () -> service.assetReliability(tenant, from(), today(), null))) {
                assertEquals("SCOPE_RESTRICTED", assertThrows(HandyFlowException.class, r::run).getErrorCode());
            }
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

    private FacilityWorkOrder fault(UUID asset, int hoursAgo, Integer downtime, String cause) {
        FacilityWorkOrder w = wo("COMPLETED", "NORMAL");
        Instant created = Instant.now().minus(Duration.ofHours(hoursAgo));
        set(w, "createdAt", created);
        set(w, "completedAt", created.plus(Duration.ofHours(2)));
        set(w, "assetId", asset);
        set(w, "downtimeMinutes", downtime);
        set(w, "failureCause", cause);
        set(w, "cost", bd("100.00"));
        return w;
    }

    private void addAsset(UUID id, String tag, String name) {
        FacilityAsset a = FacilityAsset.create(tenant, site, tag, name, "PLANT", null, null, null, null, null, null, "HIGH", null);
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
        FacilityAssetReliabilityResponse r = service.assetReliability(tenant, from(), today(), null);
        assertEquals(20, r.limit());
        FacilityAssetReliabilityResponse.Row worst = r.assets().get(0);
        assertEquals(pump, worst.assetId());
        assertEquals("P-1", worst.assetTag());
        assertEquals(2, worst.failures());
        assertEquals(1, worst.repeatFailures());
        assertEquals(90, worst.downtimeMinutes());
        assertEquals("Seal", worst.topFailureCause());
        assertEquals(0, bd("200.00").compareTo(worst.totalCost()));
        assertEquals(2, r.assets().size());
    }

    @Test
    void plannedAndInspectionWorkAreNotFailures() {
        set(fault(pump, 10, null, null), "category", "PPM");
        set(fault(pump, 20, null, null), "category", "INSPECTION");
        assertTrue(service.assetReliability(tenant, from(), today(), null).assets().isEmpty());
    }

    @Test
    void theLimitIsClampedAndAFutureStartRejected() {
        fault(pump, 10, null, null);
        assertEquals(1, service.assetReliability(tenant, from(), today(), 0).limit());
        assertEquals(100, service.assetReliability(tenant, from(), today(), 9999).limit());
        assertThrows(IllegalArgumentException.class, () -> service.assetReliability(tenant, today().plusDays(1), today().plusDays(3), null));
    }

    @Test
    void aPeriodRunningIntoTheFutureIsMeasuredOnlyUpToNow() {
        fault(pump, 5, null, null);
        BigDecimal mtbf = service.assetReliability(tenant, today().minusDays(10), today().plusDays(20), null).assets().get(0).mtbfHours();
        assertTrue(mtbf.compareTo(bd("264.1")) < 0 && mtbf.compareTo(bd("240")) >= 0, "was " + mtbf);
    }
}
