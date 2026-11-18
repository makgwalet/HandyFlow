package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;
import za.co.handyflow.platform.fuel.domain.model.FuelPortalAccessGrant;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStayRequest;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRequestRepository;
import za.co.handyflow.platform.fuel.dto.StayDtos.*;
import za.co.handyflow.platform.fuel.dto.StayRequestDtos.*;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalUserRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Driver self-service at the truck stop: grant-gated, a request not a booking, approved through the normal stay booking. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelStayRequestServiceTest {

    static final UUID TENANT = UUID.randomUUID(), USER = UUID.randomUUID(), ACCOUNT = UUID.randomUUID(), STAFF = UUID.randomUUID();
    static final TenantId T = TenantId.of(TENANT);

    @Mock FuelPortalDataService portal;
    @Mock FuelTruckStopStayRequestRepository requests;
    @Mock FuelTruckStopStayRepository stays;
    @Mock FuelTruckStopStayService stayService;
    @Mock FuelDivisionGuard divisions;
    @Mock FuelCustomerAccountRepository accounts;
    @Mock CrmFacade crm;
    @Mock PortalUserRepository portalUsers;
    @Mock NotificationService notifications;
    @Mock TenantAdminRecipients adminRecipients;
    @Mock FuelPushService push;

    FuelStayRequestService svc;
    LocalDate today, arrive, leave;
    List<FuelTruckStopStayRequest> store;

    @BeforeEach void setUp() {
        svc = new FuelStayRequestService(portal, requests, stays, stayService, divisions, accounts, crm, portalUsers, notifications, adminRecipients, push);
        today = FuelStayRequestService.today();
        arrive = today.plusDays(3); leave = today.plusDays(5);
        FuelPortalAccessGrant g = FuelPortalAccessGrant.createInvite(TENANT, ACCOUNT, "d@fleet.co.za", null);
        g.acceptInvite(USER);
        when(portal.requireGrant(USER, ACCOUNT)).thenReturn(g);
        when(stayService.settings(T)).thenReturn(new SettingsView(new BigDecimal("180"), 2));
        when(stayService.capacity(eq(T), any(LocalDate.class), anyInt(), any(LocalDate.class))).thenAnswer(i -> {
            LocalDate from = i.getArgument(1); int n = i.getArgument(2);
            List<NightView> nights = new ArrayList<>();
            for (int k = 0; k < n; k++) nights.add(new NightView(from.plusDays(k), 0, 0, 0, 2));
            return new CapacityView(from, 2, nights, List.of());
        });
        when(accounts.findActiveById(T, ACCOUNT)).thenReturn(Optional.of(mock(FuelCustomerAccount.class)));
        store = new ArrayList<>();
        when(requests.save(any(FuelTruckStopStayRequest.class))).thenAnswer(i -> { FuelTruckStopStayRequest r = i.getArgument(0); if (!store.contains(r)) store.add(r); return r; });
        when(requests.findForUpdate(eq(T), any(UUID.class))).thenAnswer(i -> store.stream().filter(r -> r.getId().equals(i.getArgument(1))).findFirst());
        when(adminRecipients.resolveTenantAdmins(T)).thenReturn(List.of());
    }

    StayRequestBody body(String reg) { return new StayRequestBody(reg, "Sipho Nkosi", "0825550123", arrive, leave, "Night stop"); }

    // ── customer side ────────────────────────────────────────────────────────

    @Test @DisplayName("a customer with no active grant gets 'not found' everywhere and nothing is saved")
    void noGrant() {
        when(portal.requireGrant(USER, ACCOUNT)).thenThrow(new HandyFlowException("Not found", org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND"));
        assertThrows(HandyFlowException.class, () -> svc.request(USER, ACCOUNT, body("GP 55 TS GP")));
        assertThrows(HandyFlowException.class, () -> svc.myRequests(USER, ACCOUNT));
        assertThrows(HandyFlowException.class, () -> svc.myStays(USER, ACCOUNT));
        assertThrows(HandyFlowException.class, () -> svc.availability(USER, ACCOUNT, null, 7));
        verify(requests, never()).save(any());
    }

    @Test @DisplayName("without the Truck Stop division the customer gets 'not found', not a division message")
    void noDivision() {
        doThrow(new HandyFlowException("needs division", org.springframework.http.HttpStatus.FORBIDDEN, "FUEL_DIVISION_NOT_ACTIVE")).when(divisions).require(eq(T), any());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> svc.request(USER, ACCOUNT, body("GP 55 TS GP")));
        assertEquals("NOT_FOUND", e.getErrorCode());
    }

    @Test @DisplayName("staff phones are alerted to a new request, with only the registration and date on the lock screen")
    void staffPushed() {
        UUID boss = UUID.randomUUID();
        when(adminRecipients.resolveTenantAdmins(T)).thenReturn(List.of(new Recipient(boss, "Boss", "b@x.co.za", null), new Recipient(null, "External", "e@x.co.za", null)));
        svc.request(USER, ACCOUNT, body("GP 55 TS GP"));
        verify(push).notifyUsers(eq(T), eq(List.of(boss)), eq("Stay request"), argThat(b -> b.contains("GP 55 TS GP")), eq(java.util.Map.of("screen", "StayRequests")));
    }

    @Test @DisplayName("a push that cannot be queued never fails the request")
    void pushFailureHarmless() {
        when(adminRecipients.resolveTenantAdmins(T)).thenReturn(List.of(new Recipient(UUID.randomUUID(), "Boss", "b@x.co.za", null)));
        doThrow(new RuntimeException("boom")).when(push).notifyUsers(any(), any(), any(), any(), any());
        assertEquals("PENDING", svc.request(USER, ACCOUNT, body("GP 55 TS GP")).status());
        assertEquals(1, store.size());
    }

    @Test @DisplayName("a request is saved pending, with the registration tidied, and books nothing")
    void requestSaved() {
        PortalStayRequestView v = svc.request(USER, ACCOUNT, body(" gp 55 ts gp "));
        assertEquals("PENDING", v.status());
        assertEquals("GP 55 TS GP", v.vehicleRegistration());
        assertEquals(2, v.nights());
        assertEquals(1, store.size());
        verify(stayService, never()).book(any(), any(), any(), any());
    }

    @Test @DisplayName("requests are not open until the truck stop has set a nightly rate")
    void notOpen() {
        when(stayService.settings(T)).thenReturn(new SettingsView(null, null));
        assertEquals("NOT_OPEN", assertThrows(HandyFlowException.class, () -> svc.request(USER, ACCOUNT, body("GP 1"))).getErrorCode());
        assertFalse(svc.availability(USER, ACCOUNT, null, 7).open());
    }

    @Test @DisplayName("dates must make sense: not in the past, departure after arrival, at most 30 nights")
    void dates() {
        assertThrows(IllegalArgumentException.class, () -> svc.request(USER, ACCOUNT, new StayRequestBody("GP 1", null, null, today.minusDays(1), today.plusDays(1), null)));
        assertThrows(IllegalArgumentException.class, () -> svc.request(USER, ACCOUNT, new StayRequestBody("GP 1", null, null, arrive, arrive, null)));
        assertThrows(IllegalArgumentException.class, () -> svc.request(USER, ACCOUNT, new StayRequestBody("GP 1", null, null, arrive, arrive.plusDays(31), null)));
        assertDoesNotThrow(() -> svc.request(USER, ACCOUNT, new StayRequestBody("GP 1", null, null, arrive, arrive.plusDays(30), null)));
    }

    @Test @DisplayName("the same truck on the same dates cannot be requested twice while pending")
    void duplicate() {
        when(requests.countSamePending(eq(T), eq(ACCOUNT), eq("GP 55 TS GP"), eq(arrive), eq(leave))).thenReturn(1L);
        assertEquals("DUPLICATE_REQUEST", assertThrows(HandyFlowException.class, () -> svc.request(USER, ACCOUNT, body("GP 55 TS GP"))).getErrorCode());
    }

    @Test @DisplayName("a customer cannot pile up more than ten open requests")
    void capped() {
        when(requests.countPending(T, ACCOUNT)).thenReturn(10L);
        assertEquals("TOO_MANY_REQUESTS", assertThrows(HandyFlowException.class, () -> svc.request(USER, ACCOUNT, body("GP 55 TS GP"))).getErrorCode());
        verify(requests, never()).save(any());
    }

    @Test @DisplayName("a night with no free bay is refused early, naming the night")
    void full() {
        when(stayService.capacity(eq(T), any(LocalDate.class), anyInt(), any(LocalDate.class))).thenAnswer(i -> {
            LocalDate from = i.getArgument(1);
            return new CapacityView(from, 2, List.of(new NightView(from, 2, 0, 0, 0), new NightView(from.plusDays(1), 0, 0, 0, 2)), List.of());
        });
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> svc.request(USER, ACCOUNT, body("GP 55 TS GP")));
        assertEquals("NO_BAY", e.getErrorCode());
        assertTrue(e.getMessage().contains(arrive.toString()));
    }

    @Test @DisplayName("availability shows free-bay counts and the rate, never trucks")
    void availability() {
        PortalAvailability a = svc.availability(USER, ACCOUNT, null, 3);
        assertTrue(a.open());
        assertEquals(0, new BigDecimal("180").compareTo(a.nightlyRate()));
        assertEquals(3, a.nights().size());
        assertEquals(2, a.nights().get(0).freeBays());
    }

    @Test @DisplayName("only the person who made a pending request can cancel it")
    void cancel() {
        svc.request(USER, ACCOUNT, body("GP 55 TS GP"));
        UUID id = store.get(0).getId();
        assertThrows(IllegalStateException.class, () -> svc.cancel(UUID.randomUUID(), ACCOUNT, id));
        assertEquals("CANCELLED", svc.cancel(USER, ACCOUNT, id).status());
        assertThrows(IllegalStateException.class, () -> svc.cancel(USER, ACCOUNT, id));
    }

    @Test @DisplayName("a request on another account is 'not found' to the caller")
    void otherAccountsRequest() {
        FuelTruckStopStayRequest other = FuelTruckStopStayRequest.create(T, UUID.randomUUID(), USER, "x", "GP 9", null, null, arrive, leave, null, today);
        store.add(other);
        assertThrows(HandyFlowException.class, () -> svc.cancel(USER, ACCOUNT, other.getId()));
        assertTrue(other.isPending());
    }

    // ── staff side ───────────────────────────────────────────────────────────

    private StayView booked(UUID id) {
        return new StayView(id, "GP 55 TS GP", "Sipho Nkosi", "0825550123", arrive, leave, null, "BOOKED", new BigDecimal("180"), 2, new BigDecimal("360"), null, null,
                false, null, null, ACCOUNT, null, null, null);
    }

    @Test @DisplayName("approve books the stay through the normal booking, charged to the customer's account, and marks the request approved")
    void approve() {
        svc.request(USER, ACCOUNT, body("GP 55 TS GP"));
        UUID stayId = UUID.randomUUID();
        when(stayService.book(eq(T), any(BookRequest.class), eq(STAFF), any(LocalDate.class))).thenReturn(booked(stayId));
        StaffStayRequestView v = svc.approve(T, store.get(0).getId(), "See you then", STAFF);
        ArgumentCaptor<BookRequest> cap = ArgumentCaptor.forClass(BookRequest.class);
        verify(stayService).book(eq(T), cap.capture(), eq(STAFF), any(LocalDate.class));
        assertEquals(ACCOUNT, cap.getValue().accountId());
        assertEquals("GP 55 TS GP", cap.getValue().vehicleRegistration());
        assertEquals(arrive, cap.getValue().arrivalDate());
        assertTrue(cap.getValue().note().startsWith(FuelStayRequestService.PORTAL_NOTE));
        assertEquals("APPROVED", v.status());
        assertEquals(stayId, v.stayId());
    }

    @Test @DisplayName("when the booking is refused (no bay, credit hold) the request stays pending")
    void approveRefused() {
        svc.request(USER, ACCOUNT, body("GP 55 TS GP"));
        when(stayService.book(any(), any(), any(), any())).thenThrow(new IllegalStateException("This customer is on credit hold."));
        assertThrows(IllegalStateException.class, () -> svc.approve(T, store.get(0).getId(), null, STAFF));
        assertTrue(store.get(0).isPending());
    }

    @Test @DisplayName("a request that is already decided cannot be approved or declined again")
    void decidedOnce() {
        svc.request(USER, ACCOUNT, body("GP 55 TS GP"));
        UUID id = store.get(0).getId();
        svc.decline(T, id, "We are closed that week", STAFF);
        assertThrows(IllegalStateException.class, () -> svc.approve(T, id, null, STAFF));
        assertThrows(IllegalStateException.class, () -> svc.decline(T, id, "Again please", STAFF));
        verify(stayService, never()).book(any(), any(), any(), any());
    }

    @Test @DisplayName("declining needs a reason the customer can read")
    void declineReason() {
        svc.request(USER, ACCOUNT, body("GP 55 TS GP"));
        UUID id = store.get(0).getId();
        assertThrows(IllegalArgumentException.class, () -> svc.decline(T, id, "no", STAFF));
        StaffStayRequestView v = svc.decline(T, id, "We are closed that week", STAFF);
        assertEquals("DECLINED", v.status());
        when(requests.findByAccount(eq(T), eq(ACCOUNT), any())).thenReturn(store);
        assertEquals("We are closed that week", svc.myRequests(USER, ACCOUNT).get(0).decisionNote());
    }

    @Test @DisplayName("staff actions need the Truck Stop division")
    void staffNeedsDivision() {
        doThrow(new HandyFlowException("needs division", org.springframework.http.HttpStatus.FORBIDDEN, "FUEL_DIVISION_NOT_ACTIVE")).when(divisions).require(eq(T), eq(Set.of(FuelDivision.TRUCK_STOP)));
        assertThrows(HandyFlowException.class, () -> svc.approve(T, UUID.randomUUID(), null, STAFF));
        assertThrows(HandyFlowException.class, () -> svc.decline(T, UUID.randomUUID(), "Not possible", STAFF));
    }

    @Test @DisplayName("an unknown status filter is refused")
    void badStatus() {
        assertThrows(IllegalArgumentException.class, () -> svc.list(T, "MAYBE", 10));
    }
}
