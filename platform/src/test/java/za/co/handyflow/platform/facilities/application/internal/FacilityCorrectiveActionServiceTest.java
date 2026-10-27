package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.*;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FacilityCorrectiveActionServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final FacilitySite site = FacilitySite.create(tenant, "Head Office", "OFFICE", null, null);
    private final Map<UUID, FacilityCorrectiveAction> actions = new LinkedHashMap<>();
    private final Map<UUID, FacilityWorkOrder> orders = new LinkedHashMap<>();
    private final List<String> audit = new ArrayList<>();
    private final List<CreateWorkOrderRequest> raised = new ArrayList<>();
    private AccessScope scope = AccessScope.unrestricted();
    private FacilityComplianceCertificate certificate;

    @SuppressWarnings("unchecked")
    private <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> h) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            Object r = h.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    private static final Object NOPE = new Object();

    private final FacilityCorrectiveActionRepository actionRepo = fake(FacilityCorrectiveActionRepository.class, (n, a) -> switch (n) {
        case "save" -> { FacilityCorrectiveAction x = (FacilityCorrectiveAction) a[0]; actions.put(x.getId(), x); yield x; }
        case "findByIdForTenant" -> Optional.ofNullable(actions.get(a[1]));
        case "findBySource" -> actions.values().stream()
                .filter(x -> x.getSourceType().equals(a[1]) && Objects.equals(x.getSourceId(), a[2])).toList();
        default -> NOPE;
    });
    private final FacilitySiteRepository siteRepo = fake(FacilitySiteRepository.class, (n, a) ->
            "findActiveById".equals(n) ? Optional.ofNullable(site.getId().equals(a[1]) ? site : null) : NOPE);
    private final FacilityAssetRepository assetRepo = fake(FacilityAssetRepository.class, (n, a) -> "findActiveById".equals(n) ? Optional.empty() : NOPE);
    private final FacilityComplianceCertificateRepository certRepo = fake(FacilityComplianceCertificateRepository.class, (n, a) ->
            "findByIdForTenant".equals(n) ? Optional.ofNullable(certificate != null && certificate.getId().equals(a[1]) ? certificate : null) : NOPE);
    private final FacilityWorkOrderRepository woRepo = fake(FacilityWorkOrderRepository.class, (n, a) ->
            "findActiveById".equals(n) ? Optional.ofNullable(orders.get(a[1])) : NOPE);
    private final FacilityAccessPolicy policy = new FacilityAccessPolicy(null, null, null) {
        @Override public AccessScope scope(TenantId t) { return scope; }
    };
    private final FacilityAuditService auditService = new FacilityAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
        @Override public void recordSystem(TenantId t, String action, String type, UUID id, String details) { audit.add("SYSTEM:" + action); }
    };
    private final FacilityWorkOrderService woService = new FacilityWorkOrderService(null, null, null, null, null, null, null, null, null,
            null, null, null, policy, auditService) {
        @Override public WorkOrderResponse createWorkOrder(TenantId t, CreateWorkOrderRequest req) {
            raised.add(req);
            FacilityWorkOrder wo = FacilityWorkOrder.create(t, "WO-" + (orders.size() + 1), req.siteId(), req.assetId(), null,
                    req.category(), req.priority(), req.description(), req.reportedBy(), req.scheduledDate());
            orders.put(wo.getId(), wo);
            return new WorkOrderResponse(wo.getId(), wo.getWorkOrderNumber(), wo.getSiteId(), wo.getAssetId(), null, wo.getCategory(),
                    wo.getPriority(), wo.getStatus(), wo.getDescription(), wo.getReportedBy(), null, null, null, null,
                    wo.getScheduledDate(), null, null, null, null, null, null, null, false, null, null, null, false, null, 0, null, null, null);
        }
    };
    private final FacilityCorrectiveActionService service = new FacilityCorrectiveActionService(
            actionRepo, siteRepo, assetRepo, certRepo, woRepo, woService, policy, auditService);

    private CorrectiveActionRequest req(String source, UUID sourceId) {
        return new CorrectiveActionRequest(site.getId(), null, "Replace hose reel", "high", LocalDate.now().plusDays(3), source, sourceId);
    }

    private CorrectiveActionResponse create() {
        TenantContext.setUserId(UUID.randomUUID().toString());
        return service.create(tenant, req(null, null));
    }

    @Test
    void aManualActionIsLoggedAndAudited() {
        CorrectiveActionResponse r = create();
        assertEquals("MANUAL", r.sourceType());
        assertEquals("HIGH", r.priority());
        assertEquals("OPEN", r.status());
        assertEquals(List.of("ACTION_CREATED"), audit);
    }

    @Test
    void inspectionActionsCannotBeLoggedByHand() {
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, req("INSPECTION", UUID.randomUUID())));
    }

    @Test
    void aCertificateActionNeedsARealCertificateAtThatSite() {
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, req("CERTIFICATE", null)));
        assertThrows(ResourceNotFoundException.class, () -> service.create(tenant, req("CERTIFICATE", UUID.randomUUID())));
        certificate = FacilityComplianceCertificate.create(tenant, UUID.randomUUID(), null, "GAS", "G1", "x",
                LocalDate.now().minusYears(1), LocalDate.now().minusDays(1), null);
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, req("CERTIFICATE", certificate.getId())));
        certificate = FacilityComplianceCertificate.create(tenant, site.getId(), null, "GAS", "G1", "x",
                LocalDate.now().minusYears(1), LocalDate.now().minusDays(1), null);
        assertEquals("CERTIFICATE", service.create(tenant, req("certificate", certificate.getId())).sourceType());
    }

    @Test
    void outOfScopeSitesAreNotFound() {
        scope = AccessScope.restrictedToSites(List.of(UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> service.create(tenant, req(null, null)));
        scope = AccessScope.unrestricted();
        CorrectiveActionResponse r = create();
        scope = AccessScope.restrictedToSites(List.of(UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> service.get(tenant, r.id()));
        assertThrows(ResourceNotFoundException.class, () -> service.close(tenant, r.id(), "x"));
    }

    @Test
    void raisingAWorkOrderCreatesItLinksItAndMovesTheActionInProgress() {
        CorrectiveActionResponse r = create();
        CorrectiveActionResponse linked = service.raiseWorkOrder(tenant, r.id());
        assertEquals("IN_PROGRESS", linked.status());
        assertNotNull(linked.workOrderId());
        assertEquals("CORRECTIVE", raised.get(0).category());
        assertEquals("HIGH", raised.get(0).priority());
        assertEquals("Replace hose reel", raised.get(0).description());
        assertTrue(audit.contains("ACTION_WORK_ORDER_RAISED"));
        assertThrows(IllegalStateException.class, () -> service.raiseWorkOrder(tenant, r.id()));
        assertEquals(1, raised.size());
    }

    @Test
    void anActionWithAnUnfinishedWorkOrderCannotBeClosed() {
        CorrectiveActionResponse r = create();
        service.raiseWorkOrder(tenant, r.id());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.close(tenant, r.id(), "done"));
        assertEquals("WORK_ORDER_NOT_COMPLETE", e.getErrorCode());
        assertEquals("IN_PROGRESS", actions.get(r.id()).getStatus());
    }

    @Test
    void onceTheWorkOrderIsCompletedTheActionCanBeClosed() throws Exception {
        CorrectiveActionResponse r = create();
        UUID woId = service.raiseWorkOrder(tenant, r.id()).workOrderId();
        FacilityWorkOrder wo = orders.get(woId);
        setField(wo, "status", "COMPLETED");
        setField(wo, "completedAt", Instant.now());
        CorrectiveActionResponse closed = service.close(tenant, r.id(), "Hose reel replaced");
        assertEquals("CLOSED", closed.status());
        assertEquals("Hose reel replaced", closed.closureNote());
        assertTrue(audit.contains("ACTION_CLOSED"));
    }

    @Test
    void anActionWithoutAWorkOrderCanBeClosedDirectlyButNeedsANote() {
        CorrectiveActionResponse r = create();
        assertThrows(IllegalArgumentException.class, () -> service.close(tenant, r.id(), " "));
        assertEquals("CLOSED", service.close(tenant, r.id(), "Fixed by caretaker").status());
    }

    @Test
    void cancellingAndReschedulingAreAudited() {
        CorrectiveActionResponse r = create();
        LocalDate later = LocalDate.now().plusDays(30);
        assertEquals(later, service.reschedule(tenant, r.id(), later).dueDate());
        assertEquals("CANCELLED", service.cancel(tenant, r.id(), "Duplicate").status());
        assertTrue(audit.containsAll(List.of("ACTION_RESCHEDULED", "ACTION_CANCELLED")));
        assertThrows(IllegalStateException.class, () -> service.reschedule(tenant, r.id(), later));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ── lapsed certificates ──────────────────────────────────────────────────

    private FacilityComplianceCertificate lapsed() {
        return FacilityComplianceCertificate.create(tenant, site.getId(), null, "GAS", "G-77", "SAQCC",
                FacilityTime.today().minusYears(1), FacilityTime.today().minusDays(1), null);
    }

    @Test
    void aLapsedCertificateRaisesOneUrgentCriticalActionDueInAWeek() {
        FacilityComplianceCertificate cert = lapsed();
        Optional<UUID> id = service.raiseForLapsedCertificate(cert);
        assertTrue(id.isPresent());
        FacilityCorrectiveAction a = actions.get(id.get());
        assertEquals("CERTIFICATE", a.getSourceType());
        assertEquals(cert.getId(), a.getSourceId());
        assertEquals(site.getId(), a.getSiteId());
        assertEquals("URGENT", a.getPriority());
        assertTrue(a.isCritical());
        assertEquals(FacilityTime.today().plusDays(7), a.getDueDate());
        assertTrue(a.getDescription().contains("GAS G-77"));
        assertEquals(List.of("SYSTEM:ACTION_CREATED"), audit); // no signed-in user in the sweep
    }

    @Test
    void theSweepRunningAgainDoesNotDuplicateTheAction() {
        FacilityComplianceCertificate cert = lapsed();
        service.raiseForLapsedCertificate(cert);
        assertTrue(service.raiseForLapsedCertificate(cert).isEmpty());
        assertEquals(1, actions.size());
    }

    @Test
    void aClosedActionDoesNotBlockANewOneIfTheCertificateLapsesAgain() {
        FacilityComplianceCertificate cert = lapsed();
        UUID first = service.raiseForLapsedCertificate(cert).orElseThrow();
        actions.get(first).close("Renewed");
        assertTrue(service.raiseForLapsedCertificate(cert).isPresent());
        assertEquals(2, actions.size());
    }
}
