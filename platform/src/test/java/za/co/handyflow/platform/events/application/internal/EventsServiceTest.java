package za.co.handyflow.platform.events.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventGuest;
import za.co.handyflow.platform.events.domain.repository.*;
import za.co.handyflow.platform.events.dto.CancelEventRequest;
import za.co.handyflow.platform.events.dto.CheckInRequest;
import za.co.handyflow.platform.events.dto.CheckInResponse;
import za.co.handyflow.platform.events.dto.CreateTierRequest;
import za.co.handyflow.platform.events.dto.UpdateEventRequest;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Event lifecycle, tiers, guest cancellation and the door scan, with the repositories mocked. */
class EventsServiceTest {

    private final EventRepository           eventRepo   = mock(EventRepository.class);
    private final EventTicketTierRepository tierRepo    = mock(EventTicketTierRepository.class);
    private final EventGuestRepository      guestRepo   = mock(EventGuestRepository.class);
    private final EventVendorRepository     vendorRepo  = mock(EventVendorRepository.class);
    private final EventCheckInRepository    checkInRepo = mock(EventCheckInRepository.class);
    private final EventNumberGenerator      numberGen   = mock(EventNumberGenerator.class);
    private final EmailService              emailService = mock(EmailService.class);
    private final EventAuditService         audit       = mock(EventAuditService.class);
    private final GuestRegistrationEngine   engine      = mock(GuestRegistrationEngine.class);
    private final WaitlistService           waitlist    = mock(WaitlistService.class);
    private final PaymentLedgerService      payments    = mock(PaymentLedgerService.class);

    private EventsService service;
    private final TenantId tenant = TenantId.generate();
    private Event event;

    @BeforeEach
    void setUp() {
        service = new EventsService(eventRepo, tierRepo, guestRepo, vendorRepo, checkInRepo, numberGen, emailService,
                audit, engine, waitlist, payments);
        event = Event.create(tenant, "EVT-2037-00001", "Gala", null, "GENERAL", null, null, 100,
                LocalDateTime.of(2037, 1, 10, 18, 0), LocalDateTime.of(2037, 1, 10, 22, 0),
                false, false, null, null, null);
        event.publish();
        when(eventRepo.findActiveById(tenant, event.getId())).thenReturn(Optional.of(event));
        when(eventRepo.findActiveByIdForUpdate(tenant, event.getId())).thenReturn(Optional.of(event));
    }

    private static String code(Throwable t) {
        return ((HandyFlowException) t).getErrorCode();
    }

    private EventGuest guest(String status) {
        EventGuest g = EventGuest.create(tenant, event.getId(), null, null, "Thandi", "t@example.co.za", null, null,
                null, "TKT-1", null, false);
        switch (status) {
            case "REGISTERED" -> { }
            case "RESERVED" -> g.reserve();
            case "CHECKED_IN" -> g.checkIn(UUID.randomUUID());
            case "CANCELLED" -> g.cancel();
            case "TRANSFERRED" -> g.markTransferred();
            default -> throw new IllegalArgumentException(status);
        }
        return g;
    }

    // ── Cancelling the event ──────────────────────────────────────────────────

    @Test
    void cancellingAnEvent_cancelsItsOpenCheckouts_closesTheWaitlist_andRecordsTheReason() {
        when(payments.cancelOpenCheckouts(eq(tenant), eq(event), any())).thenReturn(2);

        service.cancelEvent(tenant, event.getId(), new CancelEventRequest("Venue flooded", false));

        assertThat(event.getStatus()).isEqualTo("CANCELLED");
        verify(payments).cancelOpenCheckouts(eq(tenant), eq(event), any());
        verify(waitlist).closeWaitlist(event.getId());
        verify(audit).record(eq(tenant), eq(event.getId()), eq("EVENT_CANCELLED"), eq("EVENT"), eq(event.getId()),
                org.mockito.ArgumentMatchers.contains("Venue flooded"));
    }

    @Test
    void aCompletedEventCannotBeCancelled() {
        event.goLive();
        event.complete();

        assertThatThrownBy(() -> service.cancelEvent(tenant, event.getId(), null))
                .isInstanceOf(IllegalStateException.class);
        verify(payments, never()).cancelOpenCheckouts(any(), any(), any());
    }

    // ── Editing ───────────────────────────────────────────────────────────────

    private UpdateEventRequest update(Integer capacity, boolean free, LocalDateTime start) {
        return new UpdateEventRequest("Gala", null, "GENERAL", null, null, capacity, start,
                start.plusHours(4), free, false, null, null);
    }

    @Test
    void capacityCannotBeLoweredBelowTheGuestsAlreadyRegistered() {
        when(guestRepo.countActive(event.getId())).thenReturn(60L);

        assertThatThrownBy(() -> service.updateEvent(tenant, event.getId(), update(50, false, event.getStartDatetime())))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("CAPACITY_BELOW_REGISTERED"));
    }

    @Test
    void freeAndPaidCannotBeSwitchedOnceGuestsHaveRegistered() {
        when(guestRepo.countAllByEvent(event.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.updateEvent(tenant, event.getId(), update(100, true, event.getStartDatetime())))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("PRICING_LOCKED"));
    }

    @Test
    void aLiveEventsStartTimeIsLocked() {
        event.goLive();

        assertThatThrownBy(() -> service.updateEvent(tenant, event.getId(),
                update(100, false, event.getStartDatetime().plusDays(1))))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("EVENT_LIVE_LOCKED"));
    }

    @Test
    void raisingCapacity_offersTheFreedRoomToTheWaitlist() {
        service.updateEvent(tenant, event.getId(), update(150, false, event.getStartDatetime()));

        verify(waitlist).promoteEligible(tenant, event);
    }

    @Test
    void anUnchangedEdit_writesNothing() {
        service.updateEvent(tenant, event.getId(), update(100, false, event.getStartDatetime()));

        verify(eventRepo, never()).save(any());
        verify(waitlist, never()).promoteEligible(any(), any());
    }

    // ── Ticket tiers ──────────────────────────────────────────────────────────

    @Test
    void aTierCannotBeAddedToACancelledEvent() {
        event.cancel();

        assertThatThrownBy(() -> service.createTier(tenant, event.getId(),
                new CreateTierRequest("VIP", null, new BigDecimal("500"), 10, null, null)))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("EVENT_CLOSED"));
        verify(tierRepo, never()).save(any());
    }

    @Test
    void aSaleWindowMustEndAfterItStarts() {
        LocalDateTime start = LocalDateTime.of(2036, 1, 1, 0, 0);

        assertThatThrownBy(() -> service.createTier(tenant, event.getId(),
                new CreateTierRequest("Early bird", null, new BigDecimal("100"), 10, start, start)))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("SALE_WINDOW_INVALID"));
    }

    @Test
    void aValidTier_isSavedAndAudited() {
        service.createTier(tenant, event.getId(), new CreateTierRequest("VIP", null, new BigDecimal("500"), 10, null, null));

        verify(tierRepo).save(any());
        verify(audit).record(eq(tenant), eq(event.getId()), eq("TIER_CREATED"), eq("TIER"), any(), any());
    }

    // ── Cancelling a guest ────────────────────────────────────────────────────

    @Test
    void cancellingAReservedGuest_cancelsTheirOpenCheckout() {
        EventGuest g = guest("RESERVED");
        when(guestRepo.findByIdAndEvent(g.getId(), event.getId())).thenReturn(Optional.of(g));

        service.cancelGuest(tenant, event.getId(), g.getId());

        assertThat(g.getStatus()).isEqualTo("CANCELLED");
        verify(payments).onReservationCancelled(g.getId());
        verify(waitlist).promoteEligible(tenant, event);
    }

    @Test
    void cancellingAPaidGuest_flagsTheMoneyForRefund() {
        EventGuest g = guest("RESERVED");
        g.confirmPaid(new BigDecimal("250.00"));      // paid: REGISTERED with payment status PAID
        when(guestRepo.findByIdAndEvent(g.getId(), event.getId())).thenReturn(Optional.of(g));

        service.cancelGuest(tenant, event.getId(), g.getId());

        verify(payments).onPaidGuestCancelled(g.getId());
    }

    @Test
    void aCheckedInGuestCannotBeCancelled() {
        EventGuest g = guest("CHECKED_IN");
        when(guestRepo.findByIdAndEvent(g.getId(), event.getId())).thenReturn(Optional.of(g));

        assertThatThrownBy(() -> service.cancelGuest(tenant, event.getId(), g.getId()))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("GUEST_ALREADY_CHECKED_IN"));
    }

    @Test
    void cancellingAnAlreadyCancelledGuest_isANoOp_andReleasesNoSecondSeat() {
        EventGuest g = guest("CANCELLED");
        when(guestRepo.findByIdAndEvent(g.getId(), event.getId())).thenReturn(Optional.of(g));

        service.cancelGuest(tenant, event.getId(), g.getId());

        verify(tierRepo, never()).releaseSeat(any(), any());
        verify(waitlist, never()).promoteEligible(any(), any());
    }

    // ── The door ──────────────────────────────────────────────────────────────

    private CheckInResponse scan(String code) {
        return service.checkIn(tenant, event.getId(), new CheckInRequest(code, "Gate A", "scanner-1"), UUID.randomUUID());
    }

    private void live() {
        event.goLive();
    }

    @Test
    void checkInIsRefusedUnlessTheEventIsLive() {
        assertThatThrownBy(() -> scan("QR"))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("EVENT_NOT_LIVE"));
        verify(checkInRepo, never()).save(any());
    }

    @Test
    void aValidTicket_checksIn_once() {
        live();
        EventGuest g = guest("REGISTERED");
        when(guestRepo.findForCheckIn(event.getId(), "QR")).thenReturn(Optional.of(g));

        assertThat(scan("QR").result()).isEqualTo("SUCCESS");
        assertThat(g.getStatus()).isEqualTo("CHECKED_IN");

        assertThat(scan("QR").result()).isEqualTo("ALREADY_CHECKED_IN");
    }

    @Test
    void anUnpaidReservation_isNotAdmitted() {
        live();
        EventGuest g = guest("RESERVED");
        when(guestRepo.findForCheckIn(event.getId(), "QR")).thenReturn(Optional.of(g));

        assertThat(scan("QR").result()).isEqualTo("PAYMENT_PENDING");
        assertThat(g.getStatus()).isEqualTo("RESERVED");
    }

    @Test
    void aCancelledOrTransferredTicket_isNotAdmitted() {
        live();
        EventGuest cancelled = guest("CANCELLED");
        EventGuest transferred = guest("TRANSFERRED");
        when(guestRepo.findForCheckIn(event.getId(), "CANCELLED")).thenReturn(Optional.of(cancelled));
        when(guestRepo.findForCheckIn(event.getId(), "MOVED")).thenReturn(Optional.of(transferred));

        assertThat(scan("CANCELLED").result()).isEqualTo("CANCELLED_TICKET");
        assertThat(scan("MOVED").result()).isEqualTo("TRANSFERRED_TICKET");
    }

    @Test
    void anUnknownCode_isRecordedAsNotFound() {
        live();
        when(guestRepo.findForCheckIn(event.getId(), "NOPE")).thenReturn(Optional.empty());

        assertThat(scan("NOPE").result()).isEqualTo("NOT_FOUND");
        verify(checkInRepo).save(any());      // every scan is logged, even a failed one
    }

    @Test
    void aTicketForAnotherEvent_isNotFound_becauseTheLookupIsScopedToThisEvent() {
        live();
        UUID otherEvent = UUID.randomUUID();
        when(guestRepo.findForCheckIn(otherEvent, "QR")).thenReturn(Optional.of(guest("REGISTERED")));
        when(guestRepo.findForCheckIn(event.getId(), "QR")).thenReturn(Optional.empty());

        assertThat(scan("QR").result()).isEqualTo("NOT_FOUND");
    }
}
