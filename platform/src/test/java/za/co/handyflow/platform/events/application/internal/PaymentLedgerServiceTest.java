package za.co.handyflow.platform.events.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.events.application.internal.PaymentLedgerService.NotificationOutcome;
import za.co.handyflow.platform.events.application.internal.payment.PaymentGateway;
import za.co.handyflow.platform.events.application.internal.payment.PaymentGateway.PaymentNotification;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventGuest;
import za.co.handyflow.platform.events.domain.model.EventPayment;
import za.co.handyflow.platform.events.domain.repository.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Money paths of the payment ledger, with the repositories mocked: what happens when a provider notification
 * arrives, an event is cancelled with checkouts open, or a paid ticket is transferred.
 */
class PaymentLedgerServiceTest {

    private final EventRepository           eventRepo   = mock(EventRepository.class);
    private final EventTicketTierRepository tierRepo    = mock(EventTicketTierRepository.class);
    private final EventGuestRepository      guestRepo   = mock(EventGuestRepository.class);
    private final EventPaymentRepository    paymentRepo = mock(EventPaymentRepository.class);
    private final EventRefundRepository     refundRepo  = mock(EventRefundRepository.class);
    private final GuestRegistrationEngine   engine      = mock(GuestRegistrationEngine.class);
    private final WaitlistService           waitlist    = mock(WaitlistService.class);
    private final EventAuditService         audit       = mock(EventAuditService.class);
    private final org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);

    private PaymentLedgerService ledger;
    private final TenantId tenant = TenantId.generate();
    private final UUID tierId = UUID.randomUUID();

    private Event event;
    private EventGuest guest;
    private EventPayment payment;

    @BeforeEach
    void setUp() {
        ledger = new PaymentLedgerService(eventRepo, tierRepo, guestRepo, paymentRepo, refundRepo,
                engine, waitlist, audit, jdbc);
        event = Event.create(tenant, "EVT-2027-00001", "Gala", null, "GENERAL", null, null, 100,
                LocalDateTime.of(2027, 1, 10, 18, 0), LocalDateTime.of(2027, 1, 10, 22, 0),
                false, false, null, null, null);
        event.publish();

        guest = EventGuest.create(tenant, event.getId(), tierId, null, "Ann Payer", "ann@example.co.za", null,
                null, null, "EVT-2027-00001-0001", null, false);
        guest.reserve();

        payment = EventPayment.pending(tenant, event.getId(), guest.getId(), new BigDecimal("500.00"), "MANUAL",
                Instant.now().plusSeconds(900));

        when(eventRepo.findActiveByIdForUpdate(tenant, event.getId())).thenReturn(Optional.of(event));
        when(guestRepo.findById(guest.getId())).thenReturn(Optional.of(guest));
        when(paymentRepo.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepo.findByIdAndEvent(payment.getId(), event.getId())).thenReturn(Optional.of(payment));
        when(tierRepo.claimSeat(any(), any())).thenReturn(1);

        PaymentRoute route = mock(PaymentRoute.class);
        when(route.getTenantId()).thenReturn(tenant.getValue());
        when(route.getEventId()).thenReturn(event.getId());
        when(paymentRepo.findRoute(payment.getId())).thenReturn(Optional.of(route));
        // the idempotency inbox accepts the first delivery
        when(jdbc.update(contains("INSERT INTO event_payment_events"), any(Object[].class))).thenReturn(1);
    }

    private PaymentNotification paid(String amount) {
        return new PaymentNotification("evt-" + UUID.randomUUID(), payment.getId(), PaymentGateway.Outcome.PAID,
                new BigDecimal(amount), BigDecimal.ZERO);
    }

    private PaymentNotification failed() {
        return new PaymentNotification("evt-" + UUID.randomUUID(), payment.getId(), PaymentGateway.Outcome.FAILED,
                null, null);
    }

    // ── A payment for an open checkout ────────────────────────────────────────

    @Test
    void rightAmountForAnOpenCheckout_issuesTheTicket() {
        NotificationOutcome out = ledger.processNotification("MANUAL", paid("500.00"), "sha");

        assertThat(out).isEqualTo(NotificationOutcome.APPLIED_PAID);
        assertThat(payment.getStatus()).isEqualTo(EventPayment.PAID);
        assertThat(guest.getStatus()).isEqualTo("REGISTERED");
        assertThat(guest.getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    void wrongAmount_doesNotIssueATicket_andTheMoneyIsFlaggedForRefund() {
        NotificationOutcome out = ledger.processNotification("MANUAL", paid("450.00"), "sha");

        assertThat(out).isEqualTo(NotificationOutcome.REQUIRES_REFUND);
        assertThat(payment.getStatus()).isEqualTo(EventPayment.REQUIRES_REFUND);
        assertThat(payment.getReceivedAmount()).isEqualByComparingTo("450.00");
        assertThat(guest.getStatus()).isEqualTo("RESERVED");
    }

    @Test
    void aRepeatedNotification_isRecognisedAndChangesNothing() {
        when(jdbc.update(contains("INSERT INTO event_payment_events"), any(Object[].class))).thenReturn(0);

        NotificationOutcome out = ledger.processNotification("MANUAL", paid("500.00"), "sha");

        assertThat(out).isEqualTo(NotificationOutcome.DUPLICATE);
        assertThat(payment.getStatus()).isEqualTo(EventPayment.PENDING);
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void aNotificationFromAnotherProvider_isRejected() {
        NotificationOutcome out = ledger.processNotification("SOMEONE_ELSE", paid("500.00"), "sha");

        assertThat(out).isEqualTo(NotificationOutcome.REJECTED);
        assertThat(payment.getStatus()).isEqualTo(EventPayment.PENDING);
    }

    @Test
    void aNotificationForAnUnknownPayment_isAcknowledgedAndIgnored() {
        when(paymentRepo.findRoute(payment.getId())).thenReturn(Optional.empty());

        assertThat(ledger.processNotification("MANUAL", paid("500.00"), "sha")).isEqualTo(NotificationOutcome.UNKNOWN_PAYMENT);
    }

    @Test
    void aFailedPayment_releasesTheHeldSeat() {
        NotificationOutcome out = ledger.processNotification("MANUAL", failed(), "sha");

        assertThat(out).isEqualTo(NotificationOutcome.APPLIED_FAILED);
        assertThat(payment.getStatus()).isEqualTo(EventPayment.FAILED);
        assertThat(guest.getStatus()).isEqualTo("EXPIRED");   // a failed checkout frees the seat the same way an unpaid hold does
        verify(tierRepo).releaseSeat(eq(tierId), any());
    }

    // ── The event was cancelled while the checkout was open ───────────────────

    @Test
    void moneyForAnOpenCheckoutOfACancelledEvent_isFlaggedForRefund_notTurnedIntoATicket() {
        event.cancel();

        NotificationOutcome out = ledger.processNotification("MANUAL", paid("500.00"), "sha");

        assertThat(out).isEqualTo(NotificationOutcome.REQUIRES_REFUND);
        assertThat(payment.getStatus()).isEqualTo(EventPayment.REQUIRES_REFUND);
        assertThat(payment.getReceivedAmount()).isEqualByComparingTo("500.00");
        assertThat(guest.getStatus()).isEqualTo("CANCELLED");
        verify(tierRepo).releaseSeat(eq(tierId), any());
    }

    @Test
    void moneyForAnOpenCheckoutOfACompletedEvent_isFlaggedForRefund() {
        event.goLive();
        event.complete();

        assertThat(ledger.processNotification("MANUAL", paid("500.00"), "sha")).isEqualTo(NotificationOutcome.REQUIRES_REFUND);
        assertThat(guest.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancellingAnEvent_cancelsEveryOpenCheckoutAndReleasesItsSeat() {
        when(paymentRepo.findPendingByEvent(event.getId())).thenReturn(List.of(payment));
        event.cancel();

        int cancelled = ledger.cancelOpenCheckouts(tenant, event, "Event cancelled");

        assertThat(cancelled).isEqualTo(1);
        assertThat(payment.getStatus()).isEqualTo(EventPayment.CANCELLED);
        assertThat(guest.getStatus()).isEqualTo("CANCELLED");
        verify(tierRepo).releaseSeat(eq(tierId), any());
    }

    @Test
    void cancellingAnEventWithNoOpenCheckouts_doesNothing() {
        when(paymentRepo.findPendingByEvent(event.getId())).thenReturn(List.of());

        assertThat(ledger.cancelOpenCheckouts(tenant, event, "Event cancelled")).isEqualTo(0);
        verify(tierRepo, never()).releaseSeat(any(), any());
    }

    @Test
    void aPaymentThatArrivesAfterTheCheckoutWasCancelled_needsARefund() {
        when(paymentRepo.findPendingByEvent(event.getId())).thenReturn(List.of(payment));
        event.cancel();
        ledger.cancelOpenCheckouts(tenant, event, "Event cancelled");

        NotificationOutcome out = ledger.processNotification("MANUAL", paid("500.00"), "sha");

        assertThat(out).isEqualTo(NotificationOutcome.REQUIRES_REFUND);
        assertThat(payment.getStatus()).isEqualTo(EventPayment.REQUIRES_REFUND);
    }

    // ── Expiry ────────────────────────────────────────────────────────────────

    @Test
    void anExpiredHold_isReleased() {
        EventPayment overdue = EventPayment.pending(tenant, event.getId(), guest.getId(), new BigDecimal("500.00"),
                "MANUAL", Instant.now().minusSeconds(5));
        PaymentRoute route = mock(PaymentRoute.class);
        when(route.getTenantId()).thenReturn(tenant.getValue());
        when(route.getEventId()).thenReturn(event.getId());
        when(paymentRepo.findRoute(overdue.getId())).thenReturn(Optional.of(route));
        when(paymentRepo.findById(overdue.getId())).thenReturn(Optional.of(overdue));

        assertThat(ledger.expireOne(overdue.getId())).isTrue();
        assertThat(overdue.getStatus()).isEqualTo(EventPayment.EXPIRED);
        assertThat(guest.getStatus()).isEqualTo("EXPIRED");
        verify(tierRepo).releaseSeat(eq(tierId), any());
    }

    @Test
    void aHoldStillInsideItsWindow_isNotReleased() {
        assertThat(ledger.expireOne(payment.getId())).isFalse();
        assertThat(payment.getStatus()).isEqualTo(EventPayment.PENDING);
        verify(tierRepo, never()).releaseSeat(any(), any());
    }

    // ── A paid ticket changes hands ───────────────────────────────────────────

    @Test
    void whenAPaidTicketIsTransferred_thePaymentFollowsTheTicket() {
        payment.markPaid(Instant.now(), new BigDecimal("500.00"), BigDecimal.ZERO);
        UUID newGuestId = UUID.randomUUID();
        when(paymentRepo.findMoneyHeldForGuest(guest.getId())).thenReturn(List.of(payment));

        ledger.onGuestTransferred(guest.getId(), newGuestId);

        assertThat(payment.getGuestId()).isEqualTo(newGuestId);
        verify(paymentRepo).save(payment);
    }

    @Test
    void aTicketTransferredWithNoPayment_movesNothing() {
        when(paymentRepo.findMoneyHeldForGuest(guest.getId())).thenReturn(List.of());

        ledger.onGuestTransferred(guest.getId(), UUID.randomUUID());

        verify(paymentRepo, never()).save(any());
    }
}
