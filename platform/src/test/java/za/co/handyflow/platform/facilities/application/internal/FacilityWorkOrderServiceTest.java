package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.facilities.domain.model.FacilityAsset;
import za.co.handyflow.platform.facilities.domain.model.FacilitySite;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendor;
import za.co.handyflow.platform.facilities.domain.model.FacilityWorkOrder;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Internal facilities work-order service: audit trail, admin alerts, segregation of duties and the evidence rule.
 * Plain JUnit with hand-written fakes — no Mockito.
 */
class FacilityWorkOrderServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID me = UUID.randomUUID();
    private final UUID someoneElse = UUID.randomUUID();
    private final Map<UUID, FacilityWorkOrder> orders = new LinkedHashMap<>();
    private final List<String> audit = new ArrayList<>();
    private final List<NotificationType> alertTypes = new ArrayList<>();
    private final Set<String> authorities = new HashSet<>();
    private final List<UUID> evidence = new ArrayList<>();
    private BigDecimal lineTotal;
    private boolean alertsDown;
    private int seq;

    private FacilitySite site = FacilitySite.create(tenant, "Head Office", "OFFICE", null, null);
    private FacilityAsset asset;
    private final List<UUID> openReactive = new ArrayList<>();

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

    private final FacilityWorkOrderRepository woRepo = fake(FacilityWorkOrderRepository.class, (n, a) -> switch (n) {
        case "save" -> { FacilityWorkOrder w = (FacilityWorkOrder) a[0]; orders.put(w.getId(), w); yield w; }
        case "findActiveById" -> Optional.ofNullable(orders.get(a[1]));
        case "findOpenReactiveIdsForAsset" -> List.copyOf(openReactive);
        default -> NOPE;
    });
    private final FacilitySiteRepository siteRepo = fake(FacilitySiteRepository.class, (n, a) ->
            "findActiveById".equals(n) ? Optional.ofNullable(site.getId().equals(a[1]) ? site : null) : NOPE);
    private final FacilityAssetRepository assetRepo = fake(FacilityAssetRepository.class, (n, a) ->
            "findActiveById".equals(n) ? Optional.ofNullable(asset != null && asset.getId().equals(a[1]) ? asset : null) : NOPE);
    private final FacilityVendorRepository vendorRepo = fake(FacilityVendorRepository.class, (n, a) ->
            "findActiveById".equals(n) ? Optional.of(FacilityVendor.create(tenant, "ACME Plumbing", "PLUMBING", null, null, null, null)) : NOPE);
    private final FacilityWorkOrderCostLineRepository costRepo = fake(FacilityWorkOrderCostLineRepository.class, (n, a) ->
            "sumForWorkOrder".equals(n) ? lineTotal : NOPE);

    private final FacilityAccessPolicy policy = new FacilityAccessPolicy(null, null, null) {
        @Override public AccessScope scope(TenantId t) { return AccessScope.unrestricted(); }
        @Override public boolean hasAuthority(String a) { return authorities.contains(a); }
    };
    private final FacilityAuditService auditService = new FacilityAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FacilityNumberGenerator numbers = new FacilityNumberGenerator(null) {
        @Override public String nextWorkOrderNumber(TenantId t) { return "WO-" + (++seq); }
    };
    private final List<UUID> advanced = new ArrayList<>();
    private final FacilityPpmScheduleService ppm = new FacilityPpmScheduleService(null, null, null) {
        @Override public void applyCompletion(TenantId t, UUID scheduleId, LocalDate d) { advanced.add(scheduleId); }
    };
    private final FacilityAlerts alerts = new FacilityAlerts(
            new NotificationService(null, null) {
                @Override public void send(za.co.handyflow.platform.notifications.application.NotificationRequest r) {
                    if (alertsDown) throw new IllegalStateException("mail server down");
                    alertTypes.add(r.type());
                }
            },
            t -> List.of(new za.co.handyflow.platform.notifications.application.Recipient(UUID.randomUUID(), "Admin", "admin@x.co.za", null)));
    private final EvidenceFacade evidenceFacade = fake(EvidenceFacade.class, (n, a) ->
            "listFor".equals(n) ? List.copyOf(evidence) : NOPE);

    private final FacilityWorkOrderService service = new FacilityWorkOrderService(woRepo, siteRepo, assetRepo, null, null, vendorRepo,
            costRepo, numbers, ppm, alerts, evidenceFacade, policy, auditService);

    private void actAs(UUID user) { TenantContext.setUserId(user.toString()); TenantContext.setUserName("Tester"); }

    private WorkOrderResponse create(String priority, Boolean verify) {
        actAs(me);
        return service.createWorkOrder(tenant, new CreateWorkOrderRequest(site.getId(), null, "CORRECTIVE", priority,
                "Burst pipe", "Reception", null, verify));
    }

    private WorkOrderResponse request(boolean safety, String priority) {
        actAs(me);
        return service.raiseRequest(tenant, new RaiseWorkRequest(site.getId(), null, "Broken door", "CORRECTIVE", priority, safety, null));
    }

    private WorkOrderResponse runUntilInProgress(WorkOrderResponse wo) {
        service.assign(tenant, wo.id(), new AssignWorkOrderRequest(null, null, UUID.randomUUID(), "ACME"));
        return service.start(tenant, wo.id());
    }

    // ── creation & audit ─────────────────────────────────────────────────────

    @Test
    void creatingAWorkOrderIsAudited() {
        WorkOrderResponse wo = create("NORMAL", null);
        assertEquals("OPEN", wo.status());
        assertEquals(List.of("WORK_ORDER_CREATED"), audit);
    }

    @Test
    void urgentWorkOrdersAlertAdminsButNormalOnesDoNot() {
        create("NORMAL", null);
        assertTrue(alertTypes.isEmpty());
        create("URGENT", null);
        assertEquals(List.of(NotificationType.FACILITY_WORKORDER_URGENT), alertTypes);
    }

    @Test
    void startAndHoldAreAuditedToo() {
        WorkOrderResponse wo = create("NORMAL", null);
        runUntilInProgress(wo);
        service.putOnHold(tenant, wo.id(), new HoldWorkOrderRequest("Waiting for parts"));
        assertTrue(audit.containsAll(List.of("WORK_ORDER_ASSIGNED", "WORK_ORDER_STARTED", "WORK_ORDER_ON_HOLD")), audit.toString());
    }

    @Test
    void anAssetFromAnotherSiteIsRejected() {
        FacilitySite other = FacilitySite.create(tenant, "Warehouse", "WAREHOUSE", null, null);
        asset = FacilityAsset.create(tenant, other.getId(), "A-1", "Pump", "PLUMBING", null, null, null, null, null, null, "MEDIUM", null);
        actAs(me);
        assertThrows(IllegalArgumentException.class, () -> service.createWorkOrder(tenant,
                new CreateWorkOrderRequest(site.getId(), asset.getId(), "CORRECTIVE", "NORMAL", "Noisy", null, null, null)));
        assertTrue(orders.isEmpty());
    }

    @Test
    void anUnknownSiteIsNotFound() {
        actAs(me);
        assertThrows(ResourceNotFoundException.class, () -> service.createWorkOrder(tenant,
                new CreateWorkOrderRequest(UUID.randomUUID(), null, "CORRECTIVE", "NORMAL", "x", null, null, null)));
    }

    // ── requests ─────────────────────────────────────────────────────────────

    @Test
    void anOrdinaryRequestTellsAdminsItIsWaiting() {
        WorkOrderResponse r = request(false, null);
        assertEquals("REQUESTED", r.status());
        assertEquals(List.of(NotificationType.FACILITY_WORKORDER_REQUESTED), alertTypes);
    }

    @Test
    void aSafetyRequestIsUrgentNotJustRequested() {
        WorkOrderResponse r = request(true, null);
        assertEquals(List.of(NotificationType.FACILITY_WORKORDER_URGENT), alertTypes);
        assertNotEquals("NORMAL", r.priority());
    }

    @Test
    void aFailingAlertNeverRollsBackTheRequest() {
        alertsDown = true;
        WorkOrderResponse r = request(true, null);
        assertEquals("REQUESTED", r.status());
        assertEquals(1, orders.size());
        assertTrue(audit.contains("WORK_REQUEST_RAISED"));
    }

    @Test
    void anOpenFaultOnTheSameAssetIsFlaggedAsPossibleDuplicate() {
        FacilityAsset a = FacilityAsset.create(tenant, site.getId(), "A-2", "Lift", "LIFT", null, null, null, null, null, null, "HIGH", null);
        asset = a;
        UUID earlier = UUID.randomUUID();
        openReactive.add(earlier);
        actAs(me);
        WorkOrderResponse r = service.raiseRequest(tenant, new RaiseWorkRequest(site.getId(), a.getId(), "Lift stuck", "CORRECTIVE", null, false, null));
        assertEquals(earlier, r.duplicateOfWorkOrderId());
    }

    @Test
    void ppmCannotBeRequested() {
        actAs(me);
        assertThrows(IllegalArgumentException.class, () -> service.raiseRequest(tenant,
                new RaiseWorkRequest(site.getId(), null, "x", "PPM", null, false, null)));
    }

    // ── segregation of duties ────────────────────────────────────────────────

    @Test
    void youCannotApproveYourOwnRequest() {
        WorkOrderResponse r = request(false, null);
        HandyFlowException ex = assertThrows(HandyFlowException.class, () -> service.approve(tenant, r.id(), null));
        assertEquals("SEGREGATION_OF_DUTIES", ex.getErrorCode());
        assertEquals("REQUESTED", orders.get(r.id()).getStatus());
    }

    @Test
    void anotherPersonCanApprove() {
        WorkOrderResponse r = request(false, null);
        actAs(someoneElse);
        assertEquals("OPEN", service.approve(tenant, r.id(), null).status());
        assertTrue(audit.contains("WORK_REQUEST_APPROVED"));
    }

    @Test
    void anAdminMayOverrideButItIsLogged() {
        WorkOrderResponse r = request(false, null);
        authorities.add("FACILITIES_ADMIN");
        assertEquals("OPEN", service.approve(tenant, r.id(), null).status());
        assertTrue(audit.contains("REQUEST_SELF_APPROVED"));
    }

    // ── completion, evidence, verification ───────────────────────────────────

    @Test
    void verificationJobsNeedEvidenceBeforeTheyCanBeCompleted() {
        WorkOrderResponse wo = create("NORMAL", true);
        runUntilInProgress(wo);
        assertThrows(IllegalStateException.class, () ->
                service.complete(tenant, wo.id(), new CompleteWorkOrderRequest("Done", BigDecimal.TEN, null, null, null)));
        assertEquals("IN_PROGRESS", orders.get(wo.id()).getStatus());
    }

    @Test
    void completingWithEvidenceWaitsForVerificationAndAlertsAdmins() {
        WorkOrderResponse wo = create("NORMAL", true);
        runUntilInProgress(wo);
        evidence.add(UUID.randomUUID());
        WorkOrderResponse done = service.complete(tenant, wo.id(), new CompleteWorkOrderRequest("Done", BigDecimal.TEN, null, 30, "Burst"));
        assertEquals("PENDING_VERIFICATION", done.status());
        assertEquals(List.of(NotificationType.FACILITY_WORKORDER_AWAITING_VERIFICATION), alertTypes);
    }

    @Test
    void youCannotVerifyYourOwnWorkButSomeoneElseCan() {
        WorkOrderResponse wo = create("NORMAL", true);
        runUntilInProgress(wo);
        evidence.add(UUID.randomUUID());
        service.complete(tenant, wo.id(), new CompleteWorkOrderRequest("Done", BigDecimal.TEN, null, null, null));
        HandyFlowException ex = assertThrows(HandyFlowException.class, () -> service.verify(tenant, wo.id()));
        assertEquals("SEGREGATION_OF_DUTIES", ex.getErrorCode());
        actAs(someoneElse);
        assertEquals("COMPLETED", service.verify(tenant, wo.id()).status());
    }

    @Test
    void itemisedCostLinesOverrideTheTypedCost() {
        WorkOrderResponse wo = create("NORMAL", null);
        runUntilInProgress(wo);
        lineTotal = new BigDecimal("812.50");
        WorkOrderResponse done = service.complete(tenant, wo.id(), new CompleteWorkOrderRequest("Done", BigDecimal.ONE, null, null, null));
        assertEquals(0, new BigDecimal("812.50").compareTo(done.cost()));
    }

    @Test
    void completingAPpmOrderAdvancesItsScheduleOnlyOnceTrulyCompleted() {
        actAs(me);
        UUID schedule = UUID.randomUUID();
        FacilityWorkOrder ppmOrder = FacilityWorkOrder.create(tenant, "WO-P1", site.getId(), null, schedule, "PPM", "NORMAL",
                "Service AHU", "System", LocalDate.now());
        ppmOrder.requireVerification(true);
        orders.put(ppmOrder.getId(), ppmOrder);
        runUntilInProgress(new WorkOrderResponse(ppmOrder.getId(), null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, false, null, null, null, false, null, 0, null, null, null));
        evidence.add(UUID.randomUUID());
        service.complete(tenant, ppmOrder.getId(), new CompleteWorkOrderRequest("Done", null, null, null, null));
        assertTrue(advanced.isEmpty(), "must wait for verification");
        actAs(someoneElse);
        service.verify(tenant, ppmOrder.getId());
        assertEquals(List.of(schedule), advanced);
    }

    @Test
    void rejectedVerificationIsAuditedWithTheReworkCount() {
        WorkOrderResponse wo = create("NORMAL", true);
        runUntilInProgress(wo);
        evidence.add(UUID.randomUUID());
        service.complete(tenant, wo.id(), new CompleteWorkOrderRequest("Done", BigDecimal.TEN, null, null, null));
        service.rejectVerification(tenant, wo.id(), "Still leaking");
        assertEquals("IN_PROGRESS", orders.get(wo.id()).getStatus());
        assertTrue(audit.contains("WORK_ORDER_REWORK_REQUESTED"));
    }

    // ── scope ────────────────────────────────────────────────────────────────

    @Test
    void outOfScopeWorkOrdersLookNonExistent() {
        WorkOrderResponse wo = create("NORMAL", null);
        FacilityAccessPolicy narrow = new FacilityAccessPolicy(null, null, null) {
            @Override public AccessScope scope(TenantId t) { return AccessScope.restrictedToSites(List.of(UUID.randomUUID())); }
        };
        FacilityWorkOrderService restricted = new FacilityWorkOrderService(woRepo, siteRepo, assetRepo, null, null, vendorRepo,
                costRepo, numbers, ppm, alerts, evidenceFacade, narrow, auditService);
        assertThrows(ResourceNotFoundException.class, () -> restricted.getWorkOrder(tenant, wo.id()));
        assertThrows(ResourceNotFoundException.class, () -> restricted.start(tenant, wo.id()));
    }
}
