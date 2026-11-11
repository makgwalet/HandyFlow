package za.co.handyflow.platform.events.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventGuest;
import za.co.handyflow.platform.events.domain.repository.*;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Group registration, walk-ins and ticket transfers. */
class GuestOperationsServiceTest {

    private final EventRepository           eventRepo   = mock(EventRepository.class);
    private final EventTicketTierRepository tierRepo    = mock(EventTicketTierRepository.class);
    private final EventGuestRepository      guestRepo   = mock(EventGuestRepository.class);
    private final EventCheckInRepository    checkInRepo = mock(EventCheckInRepository.class);
    private final GuestRegistrationEngine   engine      = mock(GuestRegistrationEngine.class);
    private final EventNumberGenerator      numberGen   = mock(EventNumberGenerator.class);
    private final EmailService              emailService = mock(EmailService.class);
    private final EventAuditService         audit       = mock(EventAuditService.class);
    private final PaymentLedgerService      payments    = mock(PaymentLedgerService.class);

    private GuestOperationsService service;
    private final TenantId tenant = TenantId.generate();
    private Event event;

    @BeforeEach
    void setUp() {
        service = new GuestOperationsService(eventRepo, tierRepo, guestRepo, checkInRepo, engine, numberGen,
                emailService, audit, payments);
        event = Event.create(tenant, "EVT-2037-00001", "Gala", null, "GENERAL", null, null, 100,
                LocalDateTime.of(2037, 1, 10, 18, 0), LocalDateTime.of(2037, 1, 10, 22, 0),
                false, false, null, null, null);
        event.publish();
        when(eventRepo.findActiveByIdForUpdate(tenant, event.getId())).thenReturn(Optional.of(event));
        when(numberGen.nextTicket(anyString(), anyLong())).thenAnswer(i -> "TKT-" + i.getArgument(1));
    }

    private static String code(Throwable t) {
        return ((HandyFlowException) t).getErrorCode();
    }

    private EventGuest guest(String name, String email) {
        return EventGuest.create(tenant, event.getId(), null, null, name, email, null, null, null, "TKT-1", null, false);
    }

    // ── Transfer ──────────────────────────────────────────────────────────────

    private TransferGuestRequest transferTo(String name, String email) {
        return new TransferGuestRequest(name, email, null, null, null, "Cannot attend");
    }

    @Test
    void transferringATicket_killsTheOldOne_issuesANewOne_andMovesAnyPaymentAcross() {
        EventGuest old = guest("Old Holder", "old@example.co.za");
        when(guestRepo.findByIdAndEvent(old.getId(), event.getId())).thenReturn(Optional.of(old));
        when(guestRepo.countAllByEvent(event.getId())).thenReturn(1L);

        service.transferGuest(tenant, event.getId(), old.getId(), transferTo("New Holder", "New@Example.co.za"));

        assertThat(old.getStatus()).isEqualTo("TRANSFERRED");
        ArgumentCaptor<EventGuest> saved = ArgumentCaptor.forClass(EventGuest.class);
        verify(guestRepo, atLeastOnce()).save(saved.capture());
        EventGuest fresh = saved.getAllValues().stream().filter(g -> g != old).findFirst().orElseThrow();
        assertThat(fresh.getFullName()).isEqualTo("New Holder");
        assertThat(fresh.getEmail()).isEqualTo("new@example.co.za");
        assertThat(fresh.getTicketNumber()).isEqualTo("TKT-2");
        assertThat(fresh.getQrCode()).isNotEqualTo(old.getQrCode());          // the old QR code is genuinely dead
        verify(payments).onGuestTransferred(old.getId(), fresh.getId());
    }

    @Test
    void aTicketCannotBeTransferredToSomeoneAlreadyRegistered() {
        EventGuest old = guest("Old Holder", "old@example.co.za");
        when(guestRepo.findByIdAndEvent(old.getId(), event.getId())).thenReturn(Optional.of(old));
        when(guestRepo.existsActiveDuplicate(event.getId(), "new@example.co.za", "New Holder")).thenReturn(true);

        assertThatThrownBy(() -> service.transferGuest(tenant, event.getId(), old.getId(),
                transferTo("New Holder", "new@example.co.za")))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("DUPLICATE_REGISTRATION"));
        assertThat(old.getStatus()).isEqualTo("REGISTERED");
        verify(payments, never()).onGuestTransferred(any(), any());
    }

    @Test
    void aTicketThatHasBeenUsedOrCancelled_cannotBeTransferred() {
        EventGuest used = guest("Used", "u@example.co.za");
        used.checkIn(UUID.randomUUID());
        when(guestRepo.findByIdAndEvent(used.getId(), event.getId())).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.transferGuest(tenant, event.getId(), used.getId(), transferTo("X", "x@example.co.za")))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("TICKET_NOT_TRANSFERABLE"));
    }

    @Test
    void anUnpaidReservation_cannotBeTransferred() {
        EventGuest held = guest("Held", "h@example.co.za");
        held.reserve();
        when(guestRepo.findByIdAndEvent(held.getId(), event.getId())).thenReturn(Optional.of(held));

        assertThatThrownBy(() -> service.transferGuest(tenant, event.getId(), held.getId(), transferTo("X", "x@example.co.za")))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("TICKET_NOT_TRANSFERABLE"));
    }

    @Test
    void tickets_cannotBeTransferredForACancelledEvent() {
        EventGuest old = guest("Old Holder", "old@example.co.za");
        event.cancel();

        assertThatThrownBy(() -> service.transferGuest(tenant, event.getId(), old.getId(), transferTo("X", "x@example.co.za")))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("EVENT_CLOSED"));
    }

    // ── Walk-in ───────────────────────────────────────────────────────────────

    private WalkInRequest walkIn() {
        return new WalkInRequest(null, "Walk Inn", null, null, null, null, null, "Gate B");
    }

    @Test
    void aWalkIn_isRefusedUnlessTheEventIsLive() {
        assertThatThrownBy(() -> service.walkIn(tenant, event.getId(), walkIn(), UUID.randomUUID()))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("EVENT_NOT_LIVE"));
        verifyNoInteractions(engine);
    }

    @Test
    void aWalkIn_isRegisteredAndCheckedInInOneStep() {
        event.goLive();
        EventGuest g = guest("Walk Inn", null);
        when(engine.register(eq(tenant), eq(event), any())).thenReturn(new GuestRegistrationEngine.Placed(g, null));
        when(guestRepo.countCheckedIn(event.getId())).thenReturn(1L);

        CheckInResponse r = service.walkIn(tenant, event.getId(), walkIn(), UUID.randomUUID());

        assertThat(r.result()).isEqualTo("WALK_IN");
        assertThat(g.getStatus()).isEqualTo("CHECKED_IN");
        verify(checkInRepo).save(any());
    }

    // ── Group registration ────────────────────────────────────────────────────

    private GroupMemberRequest member(String name, String email) {
        return new GroupMemberRequest(name, email, null, null, null);
    }

    @Test
    void aGroupWithTheSamePersonTwice_isRefusedBeforeAnyoneIsPlaced() {
        GroupRegistrationRequest req = new GroupRegistrationRequest(null, null, null,
                List.of(member("Ann", "ann@example.co.za"), member("ann", "ANN@example.co.za")));

        assertThatThrownBy(() -> service.registerGroup(tenant, event.getId(), req))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("DUPLICATE_IN_GROUP"));
        verify(engine, never()).register(any(), any(), any());
    }

    @Test
    void aGroupLargerThanTheRoomLeft_isRefusedAsAWhole() {
        when(engine.remainingSeats(eq(event), any())).thenReturn(2L);
        GroupRegistrationRequest req = new GroupRegistrationRequest(null, null, null,
                List.of(member("A", null), member("B", null), member("C", null)));

        assertThatThrownBy(() -> service.registerGroup(tenant, event.getId(), req))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("GROUP_EXCEEDS_CAPACITY"));
        verify(engine, never()).register(any(), any(), any());
    }

    @Test
    void aGroup_isPlacedTogetherUnderOneReference_withOneAuditLine() {
        when(engine.remainingSeats(eq(event), any())).thenReturn(10L);
        when(engine.register(eq(tenant), eq(event), any()))
                .thenAnswer(i -> new GuestRegistrationEngine.Placed(guest("Member", null), null));
        GroupRegistrationRequest req = new GroupRegistrationRequest(null, null, null,
                List.of(member("A", null), member("B", null)));

        GroupRegistrationResponse r = service.registerGroup(tenant, event.getId(), req);

        assertThat(r.count()).isEqualTo(2);
        verify(engine, times(2)).register(eq(tenant), eq(event), any());
        verify(audit, times(1)).record(eq(tenant), eq(event.getId()), eq("GROUP_REGISTERED"), eq("GUEST"), any(), any());
    }
}
