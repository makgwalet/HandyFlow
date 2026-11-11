package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelPortalAccessGrant;
import za.co.handyflow.platform.fuel.domain.model.FuelSalesOrder;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.OrderRequest;
import za.co.handyflow.platform.fuel.dto.OrderResponse;
import za.co.handyflow.platform.fuel.dto.PortalDtos.OrderRequestBody;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalUserRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelPortalDataServiceTest {

    static final UUID TENANT = UUID.randomUUID(), USER = UUID.randomUUID(), ACCOUNT = UUID.randomUUID();

    @Mock FuelPortalAccessGrantRepository grants;
    @Mock FuelCustomerAccountRepository accounts;
    @Mock FuelCustomerSiteRepository sites;
    @Mock FuelSalesOrderRepository orders;
    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelOrderService orderService;
    @Mock CrmFacade crm;
    @Mock PortalUserRepository portalUsers;
    @Mock NotificationService notifications;
    @Mock TenantAdminRecipients adminRecipients;
    @InjectMocks FuelPortalDataService service;

    private FuelPortalAccessGrant activeGrant() {
        FuelPortalAccessGrant g = FuelPortalAccessGrant.createInvite(TENANT, ACCOUNT, "A@Customer.com", null);
        g.acceptInvite(USER);
        return g;
    }

    private static OrderResponse resp() {
        BigDecimal z = BigDecimal.ZERO;
        return new OrderResponse(UUID.randomUUID(), "SO-1", ACCOUNT, UUID.randomUUID(), "Cust", null, null, "DIESEL", new BigDecimal("5000"), new BigDecimal("22"),
                new BigDecimal("110000"), "QUOTE", null, null, null, null, z, z, new BigDecimal("5000"), null, null, "PO-77", "n", java.time.Instant.now(), List.of());
    }

    private OrderRequestBody body() { return new OrderRequestBody(null, "DIESEL", new BigDecimal("5000"), null, "PO-77", "Gate code 1234"); }

    @Test @DisplayName("a customer with no active grant gets 'not found' for orders, deliveries, sites and requests")
    void noGrantNoAccess() {
        when(grants.findActiveGrant(USER, ACCOUNT)).thenReturn(Optional.empty());
        assertThrows(HandyFlowException.class, () -> service.myOrders(USER, ACCOUNT));
        assertThrows(HandyFlowException.class, () -> service.myDeliveries(USER, ACCOUNT));
        assertThrows(HandyFlowException.class, () -> service.mySites(USER, ACCOUNT));
        assertThrows(HandyFlowException.class, () -> service.requestOrder(USER, ACCOUNT, body()));
        verifyNoInteractions(orderService);
    }

    @Test @DisplayName("a grant for one account does not open another account")
    void otherAccountClosed() {
        UUID other = UUID.randomUUID();
        when(grants.findActiveGrant(USER, ACCOUNT)).thenReturn(Optional.of(activeGrant()));
        when(grants.findActiveGrant(USER, other)).thenReturn(Optional.empty());
        assertThrows(HandyFlowException.class, () -> service.myOrders(USER, other));
    }

    @Test @DisplayName("a revoked grant stops access")
    void revokedStops() {
        FuelPortalAccessGrant g = activeGrant();
        g.revoke(UUID.randomUUID());
        assertEquals("REVOKED", g.getStatus());
        when(grants.findActiveGrant(USER, ACCOUNT)).thenReturn(Optional.empty()); // the query only returns ACTIVE
        assertThrows(HandyFlowException.class, () -> service.myOrders(USER, ACCOUNT));
    }

    @Test @DisplayName("a request is saved with no typed price and a note saying it came from the portal")
    void requestUsesPriceList() {
        when(grants.findActiveGrant(USER, ACCOUNT)).thenReturn(Optional.of(activeGrant()));
        when(orders.findByAccount(any(), eq(ACCOUNT))).thenReturn(List.of());
        when(orderService.create(any(), any(), any())).thenReturn(resp());
        service.requestOrder(USER, ACCOUNT, body());
        ArgumentCaptor<OrderRequest> req = ArgumentCaptor.forClass(OrderRequest.class);
        verify(orderService).create(any(), req.capture(), eq(null));
        assertNull(req.getValue().pricePerLitre());
        assertTrue(req.getValue().notes().startsWith(FuelPortalDataService.PORTAL_NOTE));
        assertTrue(req.getValue().notes().contains("Gate code 1234"));
        assertEquals("PO-77", req.getValue().customerReference());
    }

    @Test @DisplayName("staff are told when a customer asks for fuel, and a notification failure does not lose the request")
    void staffNotified() {
        when(grants.findActiveGrant(USER, ACCOUNT)).thenReturn(Optional.of(activeGrant()));
        when(orders.findByAccount(any(), eq(ACCOUNT))).thenReturn(List.of());
        when(orderService.create(any(), any(), any())).thenReturn(resp());
        when(adminRecipients.resolveTenantAdmins(any())).thenReturn(List.of(new Recipient(UUID.randomUUID(), "Admin", "admin@acme.co.za", null)));
        service.requestOrder(USER, ACCOUNT, body());
        ArgumentCaptor<NotificationRequest> n = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(n.capture());
        assertEquals(NotificationType.FUEL_PORTAL_REQUEST, n.getValue().type());

        doThrow(new RuntimeException("smtp down")).when(notifications).send(any());
        assertNotNull(service.requestOrder(USER, ACCOUNT, body()));
    }

    @Test @DisplayName("no admins to tell is not an error")
    void noAdmins() {
        when(grants.findActiveGrant(USER, ACCOUNT)).thenReturn(Optional.of(activeGrant()));
        when(orders.findByAccount(any(), eq(ACCOUNT))).thenReturn(List.of());
        when(orderService.create(any(), any(), any())).thenReturn(resp());
        when(adminRecipients.resolveTenantAdmins(any())).thenReturn(List.of());
        assertNotNull(service.requestOrder(USER, ACCOUNT, body()));
        verifyNoInteractions(notifications);
    }

    @Test @DisplayName("an unpriced request tells the customer to call, not 'Add one under Pricing'")
    void unpricedMessage() {
        when(grants.findActiveGrant(USER, ACCOUNT)).thenReturn(Optional.of(activeGrant()));
        when(orders.findByAccount(any(), eq(ACCOUNT))).thenReturn(List.of());
        when(orderService.create(any(), any(), any())).thenThrow(new IllegalArgumentException("No price is set for DIESEL. Add one under Pricing."));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.requestOrder(USER, ACCOUNT, body()));
        assertFalse(e.getMessage().contains("Pricing"));
        assertTrue(e.getMessage().contains("call us"));
    }

    @Test @DisplayName("too many unreviewed requests are refused")
    void cap() {
        when(grants.findActiveGrant(USER, ACCOUNT)).thenReturn(Optional.of(activeGrant()));
        List<FuelSalesOrder> open = new ArrayList<>();
        for (int i = 0; i < FuelPortalDataService.MAX_OPEN_REQUESTS; i++) {
            FuelSalesOrder o = mock(FuelSalesOrder.class);
            when(o.getStatus()).thenReturn(FuelSalesOrder.QUOTE);
            when(o.getNotes()).thenReturn(FuelPortalDataService.PORTAL_NOTE + " by x");
            open.add(o);
        }
        when(orders.findByAccount(any(), eq(ACCOUNT))).thenReturn(open);
        assertThrows(HandyFlowException.class, () -> service.requestOrder(USER, ACCOUNT, body()));
        verifyNoInteractions(orderService);
    }

    @Test @DisplayName("staff-made quotations do not count against the customer's request limit")
    void staffQuotesNotCounted() {
        when(grants.findActiveGrant(USER, ACCOUNT)).thenReturn(Optional.of(activeGrant()));
        List<FuelSalesOrder> staff = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            FuelSalesOrder o = mock(FuelSalesOrder.class);
            when(o.getStatus()).thenReturn(FuelSalesOrder.QUOTE);
            when(o.getNotes()).thenReturn("Quoted by phone");
            staff.add(o);
        }
        when(orders.findByAccount(any(), eq(ACCOUNT))).thenReturn(staff);
        when(orderService.create(any(), any(), any())).thenReturn(resp());
        assertDoesNotThrow(() -> service.requestOrder(USER, ACCOUNT, body()));
    }

    @Test @DisplayName("an invite works once, then the token is gone")
    void inviteSingleUse() {
        FuelPortalAccessGrant g = FuelPortalAccessGrant.createInvite(TENANT, ACCOUNT, "x@y.com", null);
        assertTrue(g.isInviteValid());
        g.acceptInvite(USER);
        assertFalse(g.isInviteValid());
        assertNull(g.getInviteToken());
        assertThrows(IllegalStateException.class, () -> g.acceptInvite(UUID.randomUUID()));
    }
}
