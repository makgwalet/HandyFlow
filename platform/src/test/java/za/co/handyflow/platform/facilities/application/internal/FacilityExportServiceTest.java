package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import za.co.handyflow.platform.facilities.domain.model.*;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.FacilityVendorPerformanceResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** CSV exports: content, formula safety, manager-only scope, row limit, audit. */
class FacilityExportServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = FacilityTime.today();
    private final FacilitySite site = FacilitySite.create(tenant, "Head Office", "OFFICE", null, null);
    private final List<FacilityWorkOrder> orders = new ArrayList<>();
    private final List<FacilityComplianceCertificate> certs = new ArrayList<>();
    private final List<FacilityCorrectiveAction> actions = new ArrayList<>();
    private final List<FacilityTechnicianLicence> licences = new ArrayList<>();
    private final FacilityTechnician thabo = FacilityTechnician.create(tenant, "Thabo", null, null, "GAS", null);
    private final List<String> audit = new ArrayList<>();
    private long reportedTotal = -1;
    private Object lastStatus = "unset";
    private AccessScope scope = AccessScope.unrestricted();

    @SuppressWarnings("unchecked")
    private <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> h) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if ("toString".equals(m.getName())) return type.getSimpleName();
            Object r = h.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    private static final Object NOPE = new Object();

    private final FacilityAccessPolicy policy = new FacilityAccessPolicy(null, null, null) {
        @Override public AccessScope scope(TenantId t) { return scope; }
    };
    private final FacilityReportService reports = new FacilityReportService(null, null, null, null, policy) {
        @Override public List<FacilityWorkOrder> workOrdersCreatedBetween(TenantId t, LocalDate from, LocalDate to) {
            if (scope.assignedOnly() || scope.requesterOnly())
                throw new HandyFlowException("Not permitted", org.springframework.http.HttpStatus.FORBIDDEN, "SCOPE_RESTRICTED");
            return orders;
        }
        @Override public FacilityVendorPerformanceResponse vendorPerformance(TenantId t, LocalDate from, LocalDate to) {
            return new FacilityVendorPerformanceResponse(from, to, List.of(new FacilityVendorPerformanceResponse.Row(
                    UUID.randomUUID(), "ACME, Plumbing", 3, 2, 0, 1, new java.math.BigDecimal("5.50"), 1, new java.math.BigDecimal("50.00"),
                    new java.math.BigDecimal("300.00"), new java.math.BigDecimal("150.00"))));
        }
    };
    private final FacilityComplianceCertificateRepository certRepo = fake(FacilityComplianceCertificateRepository.class, (n, a) ->
            "findAllInScope".equals(n) ? new PageImpl<>(certs, null, reportedTotal >= 0 ? reportedTotal : certs.size()) : NOPE);
    private final FacilityCorrectiveActionRepository actionRepo = fake(FacilityCorrectiveActionRepository.class, (n, a) -> {
        if (!"findAllInScope".equals(n)) return NOPE;
        lastStatus = a[2];
        return new PageImpl<>(actions);
    });
    private final FacilityTechnicianLicenceRepository licenceRepo = fake(FacilityTechnicianLicenceRepository.class, (n, a) ->
            "findAllForTenant".equals(n) ? licences : NOPE);
    private final FacilityTechnicianRepository techRepo = fake(FacilityTechnicianRepository.class, (n, a) ->
            "findAllActive".equals(n) ? new PageImpl<>(List.of(thabo)) : NOPE);
    private final FacilitySiteRepository siteRepo = fake(FacilitySiteRepository.class, (n, a) ->
            "findAllActiveInScope".equals(n) ? new PageImpl<>(List.of(site)) : NOPE);
    @SuppressWarnings("unchecked")
    private final FacilityAssetRepository assetRepo = fake(FacilityAssetRepository.class, (n, a) ->
            "findAllById".equals(n) ? List.of() : NOPE);
    private final FacilityAuditService auditService = new FacilityAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action + ":" + details); }
    };
    private final FacilityExportService service = new FacilityExportService(reports, certRepo, actionRepo, licenceRepo, techRepo,
            siteRepo, assetRepo, policy, auditService);

    private List<String> lines(FacilityExportService.Export e) { return Arrays.asList(e.csv().substring(1).split("\r\n")); }

    @Test
    void workOrdersExportHasOneRowPerOrderWithSiteNames() {
        orders.add(FacilityWorkOrder.create(tenant, "WO-1", site.getId(), null, null, "CORRECTIVE", "HIGH", "Leaking tap, level 2",
                "Reception", null));
        FacilityExportService.Export e = service.workOrders(tenant, today.minusDays(30), today);
        assertEquals("facilities-work-orders-" + today.minusDays(30) + "-to-" + today + ".csv", e.filename());
        List<String> l = lines(e);
        assertEquals(2, l.size());
        assertTrue(l.get(0).startsWith("Work order,Site,Asset tag"));
        assertTrue(l.get(1).startsWith("WO-1,Head Office,,,CORRECTIVE,HIGH,OPEN,\"Leaking tap, level 2\",Reception"));
        assertEquals(1, audit.size());
        assertTrue(audit.get(0).startsWith("REPORT_EXPORTED:report=work-orders"));
        assertTrue(audit.get(0).endsWith("rows=1"));
    }

    @Test
    void freeTextThatLooksLikeAFormulaIsNeutralised() {
        orders.add(FacilityWorkOrder.create(tenant, "WO-2", site.getId(), null, null, "CORRECTIVE", "NORMAL",
                "=HYPERLINK(\"http://evil\")", "@someone", null));
        String csv = service.workOrders(tenant, today.minusDays(1), today).csv();
        assertTrue(csv.contains("'=HYPERLINK"));
        assertTrue(csv.contains("'@someone"));
    }

    @Test
    void vendorPerformanceExportQuotesNamesWithCommas() {
        List<String> l = lines(service.vendorPerformance(tenant, today.minusDays(30), today));
        assertEquals("\"ACME, Plumbing\",3,2,0,1,5.50,1,50.00,300.00,150.00", l.get(1));
    }

    @Test
    void certificateRegisterShowsDaysToExpiry() {
        certs.add(FacilityComplianceCertificate.create(tenant, site.getId(), null, "GAS", "G-1", "SAQCC",
                today.minusYears(1), today.plusDays(12), null));
        List<String> l = lines(service.certificates(tenant));
        assertEquals("Head Office,GAS,G-1,SAQCC," + today.minusYears(1) + "," + today.plusDays(12) + ",12,VALID", l.get(1));
        assertTrue(service.certificates(tenant).filename().endsWith(today + ".csv"));
    }

    @Test
    void correctiveActionsExportPassesTheStatusFilterUpperCased() {
        actions.add(FacilityCorrectiveAction.create(tenant, "MANUAL", null, site.getId(), null, "Fix door", "NORMAL", false,
                today.minusDays(2), null));
        List<String> l = lines(service.correctiveActions(tenant, "open"));
        assertEquals("OPEN", lastStatus);
        assertTrue(l.get(1).startsWith("Head Office,MANUAL,Fix door,NORMAL,false,OPEN," + today.minusDays(2) + ",true"));
        service.correctiveActions(tenant, " ");
        assertNull(lastStatus);
    }

    @Test
    void licenceExportIncludesRevokedOnesAndTechnicianNames() {
        FacilityTechnicianLicence ok = FacilityTechnicianLicence.create(tenant, thabo.getId(), "PLUMBING", "P1", null, null, today.plusDays(5));
        FacilityTechnicianLicence gone = FacilityTechnicianLicence.create(tenant, thabo.getId(), "FIRST_AID", null, null, null, today.plusDays(50));
        gone.revoke("Struck off");
        licences.addAll(List.of(ok, gone));
        List<String> l = lines(service.technicianLicences(tenant));
        assertEquals(3, l.size());
        assertTrue(l.get(1).startsWith("Thabo,PLUMBING,P1"));
        assertTrue(l.get(1).endsWith(",VALID,true"));
        assertTrue(l.get(2).endsWith(",REVOKED,false"));
    }

    @Test
    void onlyManagersMayExport() {
        scope = AccessScope.assignedJobs(List.of(site.getId()), List.of(UUID.randomUUID()));
        for (Runnable r : List.<Runnable>of(() -> service.workOrders(tenant, today.minusDays(1), today), () -> service.certificates(tenant),
                () -> service.correctiveActions(tenant, null), () -> service.technicianLicences(tenant))) {
            assertEquals("SCOPE_RESTRICTED", assertThrows(HandyFlowException.class, r::run).getErrorCode());
        }
        assertTrue(audit.isEmpty());
    }

    @Test
    void anOversizedExportIsRefusedNotTruncated() {
        reportedTotal = FacilityExportService.MAX_ROWS + 1;
        assertThrows(IllegalArgumentException.class, () -> service.certificates(tenant));
        assertTrue(audit.isEmpty());
    }
}
