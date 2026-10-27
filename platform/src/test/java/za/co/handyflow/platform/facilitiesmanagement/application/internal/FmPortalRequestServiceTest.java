package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmServiceAgreement;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSite;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSlaTarget;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalRaiseRequest;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Client self-service intake: scoping, no client-set priority, throttle, audit and alerts. Plain JUnit with fakes. */
class FmPortalRequestServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID client = UUID.randomUUID();
    private final UUID portalUser = UUID.randomUUID();
    private final FmSite mySite = FmSite.create(tenant, client, "Head office", "OFFICE", Map.of(), null);
    private final UUID otherSiteId = UUID.randomUUID();

    private final List<FmWorkOrder> saved = new ArrayList<>();
    private final List<FmServiceAgreement> agreements = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();
    private final List<NotificationType> alertsSent = new ArrayList<>();
    private final Map<UUID, FmAsset> assets = new HashMap<>();
    private long portalRequestsToday = 0;
    private List<UUID> openOnAsset = List.of();
    private int numbersIssued = 0;
    private boolean noAccess = false;

    private static <T> T fake(Class<T> type, java.lang.reflect.InvocationHandler h) {
        return type.cast(Proxy.newProxyInstance(FmPortalRequestServiceTest.class.getClassLoader(), new Class<?>[]{type}, h));
    }

    private final FmPortalDataService data = new FmPortalDataService(null, null, null, null, null, null) {
        @Override public Scope resolveScope(UUID user, UUID selected) {
            if (noAccess) throw new HandyFlowException("none", org.springframework.http.HttpStatus.FORBIDDEN, "NO_CLIENT_ACCESS");
            return new Scope(tenant, client);
        }
    };

    private final FmSiteRepository siteRepo = fake(FmSiteRepository.class, (p, m, a) ->
            "findActiveByIdForClient".equals(m.getName()) && a[1].equals(client) && a[2].equals(mySite.getId())
                    ? Optional.of(mySite) : Optional.empty());

    private final FmAssetRepository assetRepo = fake(FmAssetRepository.class, (p, m, a) ->
            "findActiveById".equals(m.getName()) ? Optional.ofNullable(assets.get(a[1])) : null);

    private final FmWorkOrderRepository workOrderRepo = fake(FmWorkOrderRepository.class, (p, m, a) -> {
        switch (m.getName()) {
            case "countPortalRequestsSince": return portalRequestsToday;
            case "findOpenReactiveIdsForAsset": return openOnAsset;
            case "save": saved.add((FmWorkOrder) a[0]); return a[0];
            default: throw new UnsupportedOperationException(m.getName());
        }
    });

    private final FmServiceAgreementRepository agreementRepo = fake(FmServiceAgreementRepository.class, (p, m, a) -> {
        if ("findActiveAsOfDate".equals(m.getName())) return agreements.stream().filter(x -> x.coversDate((LocalDate) a[2])).toList();
        if ("findActiveById".equals(m.getName())) return agreements.stream().filter(x -> x.getId().equals(a[1])).findFirst();
        throw new UnsupportedOperationException(m.getName());
    });
    private final FmBusinessCalendarRepository calendarRepo = fake(FmBusinessCalendarRepository.class, (p, m, a) -> Optional.empty());

    private final FmNumberGenerator numbers = new FmNumberGenerator(null, null) {
        @Override public String nextWorkOrderNumber(TenantId t) { return "WO-" + (++numbersIssued); }
    };
    private final FmAuditService auditService = new FmAuditService(null) {
        @Override public void recordPortal(TenantId t, UUID user, String action, String type, UUID id, String details) {
            assertEquals(portalUser, user);
            audit.add(action);
        }
    };
    private final FmAlerts alerts = new FmAlerts(null, null) {
        @Override public void toAdmins(TenantId t, NotificationType type, String title, String message, String url, String id) {
            alertsSent.add(type);
        }
    };

    private final FmPortalRequestService service = new FmPortalRequestService(data, siteRepo, assetRepo, workOrderRepo, numbers,
            new FmContractTermsService(agreementRepo, calendarRepo), auditService, alerts);

    private FmPortalRaiseRequest req(String description, String category, boolean safety) {
        return new FmPortalRaiseRequest(mySite.getId(), null, description, category, safety, null);
    }

    @Test
    void aClientRequestIsSavedAsAnUnapprovedRequestRaisedByThePortalUser() {
        FmWorkOrder wo = service.raise(portalUser, null, req("The tap in the kitchen is leaking", null, false));
        assertEquals("REQUESTED", wo.getStatus());
        assertTrue(wo.isPortalRequest());
        assertEquals(portalUser, wo.getRequestedByPortalUserId());
        assertNull(wo.getRequestedByUserId());
        assertEquals("CORRECTIVE", wo.getCategory());
        assertEquals("NORMAL", wo.getPriority());
        assertEquals("Client portal", wo.getReportedBy());
        assertEquals(client, wo.getClientId());
        assertEquals(List.of(wo), saved);
        assertEquals(List.of("PORTAL_REQUEST_RAISED"), audit);
        assertEquals(List.of(NotificationType.FM_WORKORDER_REQUESTED), alertsSent);
    }

    @Test
    void theClientNeverChoosesThePriorityButSafetyAndEmergencyRaiseTheFloor() {
        FmWorkOrder safety = service.raise(portalUser, null, req("Exposed wiring near the entrance", null, true));
        assertEquals("HIGH", safety.getPriority());
        FmWorkOrder emergency = service.raise(portalUser, null, req("Burst pipe flooding the basement", "emergency", false));
        assertEquals("EMERGENCY", emergency.getPriority());
        assertEquals(List.of(NotificationType.FM_WORKORDER_URGENT, NotificationType.FM_WORKORDER_URGENT), alertsSent);
    }

    @Test
    void theContactNameBecomesTheReporter() {
        FmWorkOrder wo = service.raise(portalUser, null,
                new FmPortalRaiseRequest(mySite.getId(), null, "Aircon is not cooling", null, false, "  Thandi Reception "));
        assertEquals("Thandi Reception", wo.getReportedBy());
    }

    @Test
    void onlyCorrectiveEmergencyAndOtherCanBeRequested() {
        assertThrows(IllegalArgumentException.class, () -> service.raise(portalUser, null, req("Please do the PPM visit", "PPM", false)));
        assertThrows(IllegalArgumentException.class, () -> service.raise(portalUser, null, req("Please inspect the roof", "INSPECTION", false)));
        assertTrue(saved.isEmpty());
        assertEquals(0, numbersIssued);
    }

    @Test
    void aTooShortDescriptionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.raise(portalUser, null, req("   broken   ", null, false)));
        assertTrue(saved.isEmpty());
    }

    @Test
    void aSiteThatIsNotTheClientsLooksLikeItDoesNotExist() {
        assertThrows(ResourceNotFoundException.class, () -> service.raise(portalUser, null,
                new FmPortalRaiseRequest(otherSiteId, null, "Light is out in the corridor", null, false, null)));
        assertTrue(saved.isEmpty());
        assertEquals(0, numbersIssued);
    }

    @Test
    void anAssetMustExistAndBeAtTheChosenSite() {
        UUID unknown = UUID.randomUUID();
        assertThrows(ResourceNotFoundException.class, () -> service.raise(portalUser, null,
                new FmPortalRaiseRequest(mySite.getId(), unknown, "Lift makes a grinding noise", null, false, null)));
        FmAsset elsewhere = FmAsset.create(tenant, otherSiteId, "AST-1", "Lift", "LIFT", null, null, null, null, null, null, "MEDIUM", null);
        assets.put(elsewhere.getId(), elsewhere);
        assertThrows(IllegalArgumentException.class, () -> service.raise(portalUser, null,
                new FmPortalRaiseRequest(mySite.getId(), elsewhere.getId(), "Lift makes a grinding noise", null, false, null)));
        assertTrue(saved.isEmpty());
    }

    @Test
    void anOpenJobOnTheSameAssetIsFlaggedAsAPossibleDuplicate() {
        FmAsset lift = FmAsset.create(tenant, mySite.getId(), "AST-2", "Lift", "LIFT", null, null, null, null, null, null, "MEDIUM", null);
        assets.put(lift.getId(), lift);
        UUID existing = UUID.randomUUID();
        openOnAsset = List.of(existing);
        FmWorkOrder wo = service.raise(portalUser, null,
                new FmPortalRaiseRequest(mySite.getId(), lift.getId(), "Lift makes a grinding noise", null, false, null));
        assertEquals(existing, wo.getDuplicateOfWorkOrderId());
    }

    @Test
    void intakeIsThrottledPerClient() {
        portalRequestsToday = FmPortalRequestService.MAX_REQUESTS_PER_DAY - 1;
        service.raise(portalUser, null, req("Door handle has come off", null, false));
        portalRequestsToday = FmPortalRequestService.MAX_REQUESTS_PER_DAY;
        HandyFlowException e = assertThrows(HandyFlowException.class,
                () -> service.raise(portalUser, null, req("Another door handle has come off", null, false)));
        assertEquals("REQUEST_LIMIT", e.getErrorCode());
        assertEquals(1, saved.size());
        assertEquals(1, numbersIssued);
    }

    @Test
    void aUserWithoutClientAccessCannotRaiseAnything() {
        noAccess = true;
        assertThrows(HandyFlowException.class, () -> service.raise(portalUser, null, req("Window will not close", null, false)));
        assertTrue(saved.isEmpty());
        assertTrue(audit.isEmpty());
    }

    @Test
    void theAgreementsCoverageAndSlaAreStampedAtSubmission() {
        agreements.add(FmServiceAgreement.create(tenant, client, "T&M", "TIME_AND_MATERIALS", null, new java.math.BigDecimal("500"), null,
                LocalDate.of(2020, 1, 1), null, Set.of(), Set.of(), List.of(FmSlaTarget.of("NORMAL", 60, 480, "24X7"))));
        FmWorkOrder wo = service.raise(portalUser, null, req("Toilet is blocked on the second floor", null, false));
        assertNotNull(wo.getAgreementId());
        assertEquals("CHARGEABLE", wo.getCoverage());
        assertTrue(wo.hasSla());
    }
}
