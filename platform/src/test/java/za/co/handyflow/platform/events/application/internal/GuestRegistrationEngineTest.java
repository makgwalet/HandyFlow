package za.co.handyflow.platform.events.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.events.application.internal.GuestRegistrationEngine.Command;
import za.co.handyflow.platform.events.application.internal.GuestRegistrationEngine.Placed;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventTicketTier;
import za.co.handyflow.platform.events.domain.repository.EventGuestRepository;
import za.co.handyflow.platform.events.domain.repository.EventRepository;
import za.co.handyflow.platform.events.domain.repository.EventTicketTierRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The one place a person is placed at an event: capacity, duplicates, sale windows and the oversell guard. */
class GuestRegistrationEngineTest {

    private final EventRepository           eventRepo = mock(EventRepository.class);
    private final EventTicketTierRepository tierRepo  = mock(EventTicketTierRepository.class);
    private final EventGuestRepository      guestRepo = mock(EventGuestRepository.class);
    private final EventNumberGenerator      numberGen = mock(EventNumberGenerator.class);
    private final EventTicketPdfService     pdf       = mock(EventTicketPdfService.class);
    private final EmailService              email     = mock(EmailService.class);
    private final EventAuditService         audit     = mock(EventAuditService.class);

    private GuestRegistrationEngine engine;
    private final TenantId tenant = TenantId.generate();
    private Event event;

    @BeforeEach
    void setUp() {
        engine = new GuestRegistrationEngine(eventRepo, tierRepo, guestRepo, numberGen, pdf, email, audit);
        event = newEvent(100, null);
        event.publish();
        when(numberGen.nextTicket(anyString(), anyLong())).thenAnswer(i -> "TKT-" + i.getArgument(1));
        when(guestRepo.countAllByEvent(any())).thenReturn(0L);
        when(guestRepo.countActive(any())).thenReturn(0L);
    }

    private Event newEvent(Integer capacity, LocalDateTime deadline) {
        return Event.create(tenant, "EVT-2027-00001", "Gala", null, "GENERAL", null, null, capacity,
                LocalDateTime.of(2037, 1, 10, 18, 0), LocalDateTime.of(2037, 1, 10, 22, 0),
                false, false, deadline, null, null);
    }

    private Command.CommandBuilder person() {
        return Command.builder().fullName("  Sipho Dlamini ").email(" Sipho@Example.co.za ").source("STAFF");
    }

    private EventTicketTier tier(int quantity, LocalDateTime saleStart, LocalDateTime saleEnd) {
        EventTicketTier t = EventTicketTier.create(tenant, event.getId(), "General", null,
                new BigDecimal("250.00"), quantity, saleStart, saleEnd);
        when(tierRepo.findByIdAndEvent(t.getId(), event.getId())).thenReturn(Optional.of(t));
        return t;
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void placesAPerson_trimmingTheNameAndLowercasingTheEmail() {
        Placed placed = engine.register(tenant, event, person().build());

        assertThat(placed.guest().getFullName()).isEqualTo("Sipho Dlamini");
        assertThat(placed.guest().getEmail()).isEqualTo("sipho@example.co.za");
        assertThat(placed.guest().getStatus()).isEqualTo("REGISTERED");
        assertThat(placed.guest().getTicketNumber()).isEqualTo("TKT-1");
        verify(guestRepo).save(placed.guest());
    }

    @Test
    void ticketNumbersContinueFromEveryGuestEverCreated_soCancellationsNeverCauseARepeat() {
        when(guestRepo.countAllByEvent(event.getId())).thenReturn(7L);

        assertThat(engine.register(tenant, event, person().build()).guest().getTicketNumber()).isEqualTo("TKT-8");
    }

    @Test
    void aTier_claimsOneSeatAtomically() {
        EventTicketTier t = tier(50, null, null);
        when(tierRepo.claimSeat(any(), any())).thenReturn(1);
        when(tierRepo.countAvailableTiers(event.getId())).thenReturn(1L);

        Placed placed = engine.register(tenant, event, person().tierId(t.getId()).build());

        assertThat(placed.tier()).isSameAs(t);
        verify(tierRepo).claimSeat(eq(t.getId()), any());
    }

    @Test
    void takingTheLastSeatOfTheLastTier_marksTheEventSoldOut() {
        EventTicketTier t = tier(1, null, null);
        when(tierRepo.claimSeat(any(), any())).thenReturn(1);
        when(tierRepo.countAvailableTiers(event.getId())).thenReturn(0L);

        engine.register(tenant, event, person().tierId(t.getId()).build());

        assertThat(event.getStatus()).isEqualTo("SOLD_OUT");
        verify(eventRepo).save(event);
    }

    // ── Refusals ──────────────────────────────────────────────────────────────

    @Test
    void aSecondLiveRegistrationForTheSamePerson_isRefused() {
        when(guestRepo.existsActiveDuplicate(eq(event.getId()), eq("sipho@example.co.za"), eq("Sipho Dlamini"))).thenReturn(true);

        assertThatThrownBy(() -> engine.register(tenant, event, person().build()))
                .isInstanceOf(HandyFlowException.class)
                .extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("DUPLICATE_REGISTRATION");
        verify(guestRepo, never()).save(any());
    }

    @Test
    void aFullVenue_refusesTheRegistration() {
        when(guestRepo.countActive(event.getId())).thenReturn(100L);

        assertThatThrownBy(() -> engine.register(tenant, event, person().build()))
                .isInstanceOf(HandyFlowException.class)
                .extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("EVENT_AT_CAPACITY");
    }

    @Test
    void aSoldOutTier_refusesTheRegistration_andWritesNothing() {
        EventTicketTier t = tier(1, null, null);
        when(tierRepo.claimSeat(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> engine.register(tenant, event, person().tierId(t.getId()).build()))
                .isInstanceOf(HandyFlowException.class)
                .extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("TIER_SOLD_OUT");
        verify(guestRepo, never()).save(any());
    }

    @Test
    void aTierOutsideItsSaleWindow_isRefused_unlessStaffOverrideTheWindow() {
        EventTicketTier closed = tier(10, LocalDateTime.of(2020, 1, 1, 0, 0), LocalDateTime.of(2020, 2, 1, 0, 0));
        when(tierRepo.claimSeat(any(), any())).thenReturn(1);
        when(tierRepo.countAvailableTiers(event.getId())).thenReturn(1L);

        assertThatThrownBy(() -> engine.register(tenant, event, person().tierId(closed.getId()).build()))
                .isInstanceOf(HandyFlowException.class)
                .extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("TIER_NOT_ON_SALE");

        Placed placed = engine.register(tenant, event, person().tierId(closed.getId()).ignoreSaleWindow(true).build());
        assertThat(placed.guest().getStatus()).isEqualTo("REGISTERED");
    }

    @Test
    void aTierFromAnotherEvent_isNotFound() {
        UUID foreign = UUID.randomUUID();
        when(tierRepo.findByIdAndEvent(foreign, event.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine.register(tenant, event, person().tierId(foreign).build()))
                .isInstanceOf(za.co.handyflow.platform.shared.ResourceNotFoundException.class);
        verify(tierRepo, never()).claimSeat(any(), any());
    }

    @Test
    void aDraftEvent_isNotOpenForRegistration() {
        Event draft = newEvent(100, null);

        assertThatThrownBy(() -> engine.register(tenant, draft, person().build()))
                .isInstanceOf(HandyFlowException.class)
                .extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("REGISTRATION_NOT_OPEN");
    }

    @Test
    void aCancelledEvent_isNotOpenForRegistration() {
        event.cancel();

        assertThatThrownBy(() -> engine.register(tenant, event, person().build()))
                .isInstanceOf(HandyFlowException.class)
                .extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("REGISTRATION_NOT_OPEN");
    }

    @Test
    void afterTheDeadline_registrationIsClosed_unlessStaffIgnoreTheDeadline() {
        Event late = newEvent(100, LocalDateTime.of(2020, 1, 1, 0, 0));
        // an event whose start is in 2037 cannot have a deadline in 2020 by creation rules? it can: deadline only has to be before the start
        late.publish();

        assertThatThrownBy(() -> engine.register(tenant, late, person().build()))
                .isInstanceOf(HandyFlowException.class)
                .extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("REGISTRATION_CLOSED");

        assertThat(engine.register(tenant, late, person().ignoreDeadline(true).build()).guest().getStatus())
                .isEqualTo("REGISTERED");
    }

    // ── Paid checkout hold ────────────────────────────────────────────────────

    @Test
    void aReservation_isHeldUnpaid_andSendsNoTicketEmail() {
        Placed placed = engine.register(tenant, event, person().reserve(true).sendTicketEmail(true).build());

        assertThat(placed.guest().getStatus()).isEqualTo("RESERVED");
        assertThat(placed.guest().getPaymentStatus()).isEqualTo("PENDING");
        verifyNoInteractions(email);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Test
    void normalizeEmail_trimsLowercasesAndTreatsBlankAsNone() {
        assertThat(GuestRegistrationEngine.normalizeEmail("  A@B.Co ")).isEqualTo("a@b.co");
        assertThat(GuestRegistrationEngine.normalizeEmail("   ")).isNull();
        assertThat(GuestRegistrationEngine.normalizeEmail(null)).isNull();
    }

    @Test
    void remainingSeats_isTheTighterOfVenueAndTier() {
        EventTicketTier t = tier(50, null, null);
        when(guestRepo.countActive(event.getId())).thenReturn(90L);        // venue has 10 left
        when(tierRepo.availableSeats(t.getId())).thenReturn(4);            // tier has 4 left

        assertThat(engine.remainingSeats(event, t)).isEqualTo(4L);
        assertThat(engine.remainingSeats(event, null)).isEqualTo(10L);
    }
}
