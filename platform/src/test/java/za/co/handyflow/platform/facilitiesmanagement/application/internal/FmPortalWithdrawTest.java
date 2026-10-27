package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** A client withdrawing their own request, and the decline note on the work order. */
class FmPortalWithdrawTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID client = UUID.randomUUID();
    private final UUID portalUser = UUID.randomUUID();
    private final List<String> audit = new ArrayList<>();
    private final List<NotificationType> alertsSent = new ArrayList<>();
    private final List<FmWorkOrder> saved = new ArrayList<>();
    private FmWorkOrder stored;

    private final FmPortalDataService data = new FmPortalDataService(null, null, null, null, null, null) {
        @Override public Scope resolveScope(UUID user, UUID selected) { return new Scope(tenant, client); }
    };
    private final FmWorkOrderRepository repo = (FmWorkOrderRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmWorkOrderRepository.class}, (p, m, a) -> {
                if ("findActiveById".equals(m.getName())) return stored != null && stored.getId().equals(a[1]) ? Optional.of(stored) : Optional.empty();
                if ("save".equals(m.getName())) { saved.add((FmWorkOrder) a[0]); return a[0]; }
                throw new UnsupportedOperationException(m.getName());
            });
    private final FmAuditService auditService = new FmAuditService(null) {
        @Override public void recordPortal(TenantId t, UUID user, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FmAlerts alerts = new FmAlerts(null, null) {
        @Override public void toAdmins(TenantId t, NotificationType type, String title, String message, String url, String id) { alertsSent.add(type); }
    };
    private final FmPortalRequestService service = new FmPortalRequestService(data, null, null, repo, null, null, auditService, alerts);

    private FmWorkOrder portalRequest(UUID clientId, UUID byUser) {
        return FmWorkOrder.createPortalRequest(tenant, "WO-1", clientId, UUID.randomUUID(), null, "CORRECTIVE", "NORMAL",
                "Tap is leaking badly", "Client portal", byUser, false, null);
    }

    @Test
    void theSubmitterCanWithdrawAnUndecidedRequest() {
        stored = portalRequest(client, portalUser);
        FmWorkOrder wo = service.withdraw(portalUser, null, stored.getId());
        assertEquals("CANCELLED", wo.getStatus());
        assertEquals(List.of("PORTAL_REQUEST_WITHDRAWN"), audit);
        assertEquals(List.of(NotificationType.FM_WORKORDER_REQUEST_WITHDRAWN), alertsSent);
        assertEquals(1, saved.size());
    }

    @Test
    void anotherContactOfTheSameClientCannotWithdrawIt() {
        stored = portalRequest(client, UUID.randomUUID());
        assertThrows(ResourceNotFoundException.class, () -> service.withdraw(portalUser, null, stored.getId()));
        assertEquals("REQUESTED", stored.getStatus());
        assertTrue(audit.isEmpty());
    }

    @Test
    void anotherClientsRequestLooksLikeItDoesNotExist() {
        stored = portalRequest(UUID.randomUUID(), portalUser);
        assertThrows(ResourceNotFoundException.class, () -> service.withdraw(portalUser, null, stored.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.withdraw(portalUser, null, UUID.randomUUID()));
    }

    @Test
    void onceStaffHaveApprovedItItCannotBeWithdrawn() {
        stored = portalRequest(client, portalUser);
        stored.approve(UUID.randomUUID(), null, null, null);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.withdraw(portalUser, null, stored.getId()));
        assertEquals("REQUEST_NOT_WITHDRAWABLE", e.getErrorCode());
        assertEquals("OPEN", stored.getStatus());
        assertTrue(saved.isEmpty());
        assertTrue(audit.isEmpty());
        assertTrue(alertsSent.isEmpty());
    }

    @Test
    void aWithdrawnRequestCannotBeApprovedAfterwards() {
        stored = portalRequest(client, portalUser);
        service.withdraw(portalUser, null, stored.getId());
        assertThrows(IllegalStateException.class, () -> stored.approve(UUID.randomUUID(), null, null, null));
    }

    @Test
    void declineKeepsTheInternalReasonSeparateFromTheClientMessage() {
        FmWorkOrder wo = portalRequest(client, portalUser);
        wo.reject(UUID.randomUUID(), "Out of contract; chase their account first", "  This site is not covered by your agreement  ");
        assertEquals("REJECTED", wo.getStatus());
        assertEquals("Out of contract; chase their account first", wo.getRejectionReason());
        assertEquals("This site is not covered by your agreement", wo.getClientMessage());
        FmWorkOrder plain = portalRequest(client, portalUser);
        plain.reject(UUID.randomUUID(), "dup", "   ");
        assertNull(plain.getClientMessage());
    }
}
