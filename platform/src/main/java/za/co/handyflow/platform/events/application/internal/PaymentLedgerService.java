package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.events.application.internal.PaymentDecider.Action;
import za.co.handyflow.platform.events.application.internal.payment.PaymentGateway;
import za.co.handyflow.platform.events.application.internal.payment.PaymentGateway.PaymentNotification;
import za.co.handyflow.platform.events.domain.model.*;
import za.co.handyflow.platform.events.domain.repository.*;
import za.co.handyflow.platform.events.dto.PaymentResponse;
import za.co.handyflow.platform.events.dto.RefundResponse;
import za.co.handyflow.platform.events.dto.StartCheckoutRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Every payment state change happens here, inside a transaction, and every
 * one of them follows the same discipline:
 * <ol>
 *   <li><b>Lock the event row first</b> (SELECT ... FOR UPDATE). Registration,
 *       cancellation, expiry and payment callbacks for one event therefore run
 *       one at a time, so a late payment can never race a seat being re-sold.</li>
 *   <li><b>Only then read the payment</b>, so what is read reflects anything
 *       committed while waiting for the lock (see {@link PaymentRoute}).</li>
 *   <li><b>Decide with {@link PaymentDecider}</b>, a pure rulebook, never with
 *       ad-hoc ifs scattered through the code.</li>
 * </ol>
 * <p>
 * IDEMPOTENT CALLBACKS: a provider notification is first recorded in
 * event_payment_events under a unique (provider, provider_event_id). A repeat
 * delivery hits that constraint and does nothing. Because the record is written
 * in the SAME transaction as the work, a crash mid-processing rolls both back,
 * and the provider's retry is then processed for real — exactly-once effect.
 * <p>
 * NO NETWORK CALLS HERE. Talking to a provider is done by the orchestrators
 * (PaymentCheckoutService, PaymentRefundService) between short transactions,
 * so a slow provider can never hold the event lock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentLedgerService {

    private final EventRepository           eventRepo;
    private final EventTicketTierRepository tierRepo;
    private final EventGuestRepository      guestRepo;
    private final EventPaymentRepository    paymentRepo;
    private final EventRefundRepository     refundRepo;
    private final GuestRegistrationEngine   engine;
    private final WaitlistService           waitlist;
    private final EventAuditService         audit;
    private final JdbcTemplate              jdbc;

    /** A seat held for a payer, with everything the orchestrator needs to start the provider checkout. */
    public record Reserved(EventPayment payment, EventGuest guest, EventTicketTier tier, String eventTitle) {}

    /** What the orchestrator needs to ask the provider for a refund after the refund record is saved. */
    public record RefundTicket(UUID refundId, String provider, String providerPaymentRef,
                               BigDecimal amount, String reason) {}

    public enum NotificationOutcome {
        APPLIED_PAID, REINSTATED, APPLIED_FAILED, REQUIRES_REFUND, IGNORED, DUPLICATE, UNKNOWN_PAYMENT, REJECTED
    }

    private record Locked(TenantId tenant, Event event, EventPayment payment) {}

    // ══════════════════════════════════════════════════════════════════════════
    // Checkout: reserve a seat and open a payment
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Transaction 1 of a checkout: hold a place and record a PENDING payment.
     * The seat is claimed through the shared registration engine, so capacity,
     * duplicate and tier-inventory rules are exactly those of every other
     * registration path. The price is snapshotted from the tier now.
     */
    @Transactional
    public Reserved reserve(TenantId tenantId, UUID eventId, StartCheckoutRequest req,
                            String provider, int holdMinutes) {
        Event event = lockEvent(tenantId, eventId);
        if (event.isFree()) {
            throw new HandyFlowException("This event is free — register guests directly instead of using checkout",
                    HttpStatus.CONFLICT, "FREE_EVENT");
        }

        EventTicketTier tier = tierRepo.findByIdAndEvent(req.tierId(), eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Tier", req.tierId().toString()));
        if (tier.getPrice() == null || tier.getPrice().signum() <= 0) {
            throw new HandyFlowException("Ticket tier '" + tier.getName() + "' has no price — register guests directly instead",
                    HttpStatus.CONFLICT, "TIER_NOT_PAID");
        }

        GuestRegistrationEngine.Placed placed = engine.register(tenantId, event,
                GuestRegistrationEngine.Command.builder()
                        .fullName(req.fullName())
                        .email(req.email())
                        .phone(req.phone())
                        .company(req.company())
                        .dietaryRequirements(req.dietaryRequirements())
                        .tierId(req.tierId())
                        .customerId(req.customerId())
                        .source("CHECKOUT")
                        .reserve(true)
                        .audit(true)
                        .build());

        EventPayment payment = EventPayment.pending(tenantId, eventId, placed.guest().getId(),
                tier.getPrice(), provider, Instant.now().plus(Duration.ofMinutes(holdMinutes)));
        paymentRepo.save(payment);

        audit.record(tenantId, eventId, "CHECKOUT_STARTED", "PAYMENT", payment.getId(),
                "Checkout started for ticket " + placed.guest().getTicketNumber() + " — " + money(payment.getAmount())
                        + " via " + provider + ", held until " + payment.getExpiresAt());
        return new Reserved(payment, placed.guest(), tier, event.getTitle());
    }

    /** Transaction 2: store the provider's reference. Locks first — a callback may already have landed. */
    @Transactional
    public void attachProviderRef(UUID paymentId, String providerRef) {
        Locked l = lockByPaymentId(paymentId);
        if (l.payment().isPending()) {
            l.payment().attachProviderRef(providerRef);
            paymentRepo.save(l.payment());
        }
    }

    /** The provider could not start the payment: release the hold at once rather than leave a seat tied up. */
    @Transactional
    public void abandonReservation(UUID paymentId, String reason) {
        Locked l = lockByPaymentId(paymentId);
        EventPayment p = l.payment();
        if (!p.isPending()) return;
        p.fail(reason);
        paymentRepo.save(p);
        guestRepo.findById(p.getGuestId()).ifPresent(g -> {
            if ("RESERVED".equals(g.getStatus())) releaseSeat(l, g, true);
        });
        auditPayment(l, "CHECKOUT_ABANDONED", "Checkout abandoned — " + reason, null);
    }

    /** Staff cancel an open checkout; the seat goes back to inventory and the waitlist. */
    @Transactional
    public PaymentResponse cancelCheckout(TenantId tenantId, UUID eventId, UUID paymentId, String reason) {
        Event event = lockEvent(tenantId, eventId);
        EventPayment p = findPayment(eventId, paymentId);
        if (!p.isPending()) {
            throw new HandyFlowException("Only an open checkout can be cancelled (this payment is " + p.getStatus() + ")",
                    HttpStatus.CONFLICT, "PAYMENT_NOT_PENDING");
        }
        Locked l = new Locked(tenantId, event, p);
        p.cancel(reason);
        paymentRepo.save(p);
        guestRepo.findById(p.getGuestId()).ifPresent(g -> {
            if ("RESERVED".equals(g.getStatus())) releaseSeat(l, g, false);
        });
        auditPayment(l, "CHECKOUT_CANCELLED", "Checkout cancelled — " + reason, null);
        return toResponse(p, ticketNumberOf(p.getGuestId()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Expiry (called by the scheduler, one payment per transaction)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Releases one expired hold. Safe to run twice, or concurrently on two
     * instances: it re-checks under the lock that the payment is STILL pending
     * and STILL past its expiry, so the second run simply finds nothing to do.
     *
     * @return true if it released a hold
     */
    @Transactional
    public boolean expireOne(UUID paymentId) {
        Locked l = lockByPaymentId(paymentId);
        EventPayment p = l.payment();
        if (!p.isPending() || p.getExpiresAt().isAfter(Instant.now())) return false;

        p.expire();
        paymentRepo.save(p);
        guestRepo.findById(p.getGuestId()).ifPresent(g -> {
            if ("RESERVED".equals(g.getStatus())) releaseSeat(l, g, true);
        });
        auditPayment(l, "RESERVATION_EXPIRED", "Reservation expired unpaid — seat released", null);
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Payment notifications
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Applies a VERIFIED provider notification, exactly once. The caller (the
     * webhook controller) has already had the adapter prove it is genuine; this
     * method trusts that and guards only what it can see: duplicates, unknown
     * payments, and a notification arriving from a different provider than the
     * payment was opened with.
     */
    @Transactional
    public NotificationOutcome processNotification(String provider, PaymentNotification n, String payloadSha256) {
        String providerCode = provider.toUpperCase(Locale.ROOT);
        if (!recordInbox(providerCode, n.providerEventId(), n.paymentId(), payloadSha256)) {
            return NotificationOutcome.DUPLICATE;
        }
        Optional<PaymentRoute> route = paymentRepo.findRoute(n.paymentId());
        if (route.isEmpty()) {
            finishInbox(providerCode, n.providerEventId(), NotificationOutcome.UNKNOWN_PAYMENT.name());
            log.warn("[Events] Payment notification for unknown payment provider={}", providerCode);
            return NotificationOutcome.UNKNOWN_PAYMENT;
        }
        Locked l = lockRoute(route.get(), n.paymentId());
        if (!providerCode.equalsIgnoreCase(l.payment().getProvider())) {
            finishInbox(providerCode, n.providerEventId(), NotificationOutcome.REJECTED.name());
            log.warn("[Events] Notification from {} for a payment opened with {} — rejected",
                    providerCode, l.payment().getProvider());
            return NotificationOutcome.REJECTED;
        }
        return apply(l, n, providerCode, null);
    }

    /**
     * Staff confirm money arrived outside a provider (EFT, cash, card machine), or recover from a lost
     * notification. Goes through the SAME decision table as a provider callback. If the amount differs
     * from the price asked for, the ticket is NOT issued: the payment is flagged REQUIRES_REFUND.
     */
    @Transactional
    public PaymentResponse confirmManually(TenantId tenantId, UUID eventId, UUID paymentId,
                                           BigDecimal amountReceived, String reference, BigDecimal fee) {
        Event event = lockEvent(tenantId, eventId);
        EventPayment p = findPayment(eventId, paymentId);

        String eventKey = "manual:" + paymentId + ":" + UUID.randomUUID();
        recordInbox("MANUAL", eventKey, paymentId, null);
        PaymentNotification n = new PaymentNotification(eventKey, paymentId,
                PaymentGateway.Outcome.PAID, amountReceived, fee);
        String note = (reference != null && !reference.isBlank()) ? "reference: " + reference.trim() : null;

        NotificationOutcome outcome = apply(new Locked(tenantId, event, p), n, "MANUAL", note);
        if (outcome == NotificationOutcome.IGNORED) {
            throw new HandyFlowException("This payment is already settled (status is " + p.getStatus() + ")",
                    HttpStatus.CONFLICT, "PAYMENT_ALREADY_SETTLED");
        }
        return toResponse(p, ticketNumberOf(p.getGuestId()));
    }

    /** The one place a notification changes anything — a thin shell around the pure decision table. */
    private NotificationOutcome apply(Locked l, PaymentNotification n, String inboxProvider, String note) {
        EventPayment p = l.payment();
        EventGuest guest = guestRepo.findById(p.getGuestId()).orElse(null);

        boolean paid = n.outcome() == PaymentGateway.Outcome.PAID;
        boolean amountMatches = paid && n.amountReceived().compareTo(p.getAmount()) == 0;
        boolean canReinstate = paid && amountMatches && guest != null
                && "EXPIRED".equals(guest.getStatus()) && canPlaceAgain(l.event(), guest);

        Action action = PaymentDecider.decide(p.getStatus(), n.outcome(), amountMatches, canReinstate);

        NotificationOutcome outcome = switch (action) {
            case CONFIRM -> confirm(l, p, guest, n, note);
            case REINSTATE -> reinstate(l, p, guest, n, note);
            case REQUIRE_REFUND_AMOUNT_MISMATCH -> requireRefund(l, p, n, note,
                    "Received " + money(n.amountReceived()) + " but " + money(p.getAmount()) + " was due");
            case REQUIRE_REFUND_NO_SEAT -> requireRefund(l, p, n, note, noSeatReason(p, guest));
            case FAIL -> fail(l, p, guest, note);
            case IGNORE_DUPLICATE, IGNORE -> NotificationOutcome.IGNORED;
        };
        finishInbox(inboxProvider, n.providerEventId(), outcome.name());
        return outcome;
    }

    private NotificationOutcome confirm(Locked l, EventPayment p, EventGuest guest, PaymentNotification n, String note) {
        if (guest == null || !"RESERVED".equals(guest.getStatus())) {
            return requireRefund(l, p, n, note, "Payment arrived but the reservation was no longer active");
        }
        guest.confirmPaid(p.getAmount());
        guestRepo.save(guest);
        p.markPaid(Instant.now(), n.amountReceived(), n.fee());
        paymentRepo.save(p);

        auditPayment(l, "PAYMENT_RECEIVED", "Payment received — " + money(n.amountReceived())
                + " for ticket " + guest.getTicketNumber() + " via " + p.getProvider(), note);
        sendTicketAfterCommit(l.event(), guest);
        return NotificationOutcome.APPLIED_PAID;
    }

    private NotificationOutcome reinstate(Locked l, EventPayment p, EventGuest guest, PaymentNotification n, String note) {
        // The hold had expired and the seat was released. Honour the payment only if a seat can be taken back
        // right now — the atomic claim below is the real test, canPlaceAgain() was just a cheap pre-check.
        if (guest.getTierId() != null && tierRepo.claimSeat(guest.getTierId(), Instant.now()) == 0) {
            return requireRefund(l, p, n, note, "Paid after the hold expired and the ticket tier has since sold out");
        }
        guest.reinstate(p.getAmount());
        guestRepo.save(guest);
        p.markPaid(Instant.now(), n.amountReceived(), n.fee());
        paymentRepo.save(p);

        Event event = l.event();
        if ("PUBLISHED".equals(event.getStatus()) && tierRepo.countAvailableTiers(event.getId()) == 0) {
            event.markSoldOut();
            eventRepo.save(event);
        }
        auditPayment(l, "PAYMENT_RECEIVED_LATE", "Payment received after the hold expired — seat re-secured, ticket "
                + guest.getTicketNumber() + " honoured (" + money(n.amountReceived()) + ")", note);
        sendTicketAfterCommit(event, guest);
        return NotificationOutcome.REINSTATED;
    }

    private NotificationOutcome requireRefund(Locked l, EventPayment p, PaymentNotification n, String note, String reason) {
        p.receivedButUnhonoured(Instant.now(), n.amountReceived(), n.fee(), reason);
        paymentRepo.save(p);
        auditPayment(l, "PAYMENT_NEEDS_REFUND", "Money received but no ticket issued — " + reason
                + ". Refund of " + money(n.amountReceived()) + " is owed", note);
        log.warn("[Events] Payment flagged REQUIRES_REFUND event={} reason={}", l.event().getEventNumber(), reason);
        return NotificationOutcome.REQUIRES_REFUND;
    }

    private NotificationOutcome fail(Locked l, EventPayment p, EventGuest guest, String note) {
        p.fail("Payment failed at the provider");
        paymentRepo.save(p);
        if (guest != null && "RESERVED".equals(guest.getStatus())) releaseSeat(l, guest, true);
        auditPayment(l, "PAYMENT_FAILED", "Payment failed — hold released", note);
        return NotificationOutcome.APPLIED_FAILED;
    }

    private String noSeatReason(EventPayment p, EventGuest guest) {
        if (EventPayment.CANCELLED.equals(p.getStatus()) || (guest != null && "CANCELLED".equals(guest.getStatus()))) {
            return "The reservation was cancelled before the payment arrived";
        }
        return "Paid after the hold expired and the seat is no longer available";
    }

    /** Cheap pre-check before re-securing an expired hold: event open, room under capacity, not now a duplicate. */
    private boolean canPlaceAgain(Event event, EventGuest guest) {
        if (!event.acceptsRegistrations()) return false;
        if (event.getVenueCapacity() != null && guestRepo.countActive(event.getId()) >= event.getVenueCapacity()) return false;
        return guest.getEmail() == null
                || !guestRepo.existsActiveDuplicate(event.getId(), guest.getEmail(), guest.getFullName());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Hooks called from guest cancellation (event row already locked by the caller)
    // ══════════════════════════════════════════════════════════════════════════

    /** A RESERVED guest was cancelled by staff: their open checkout is cancelled with them. */
    @Transactional
    public void onReservationCancelled(UUID guestId) {
        paymentRepo.findOpenForGuest(guestId).ifPresent(p -> {
            p.cancel("Reservation cancelled by staff");
            paymentRepo.save(p);
        });
    }

    /**
     * A guest who had PAID was cancelled by staff. The ticket is gone but the money is still held, so
     * the payment is flagged REQUIRES_REFUND — it can't quietly stay "paid" with nobody owed anything.
     * Staff then refund all or part of it (e.g. keeping a cancellation fee).
     */
    @Transactional
    public void onPaidGuestCancelled(UUID guestId) {
        for (EventPayment p : paymentRepo.findPaidForGuest(guestId)) {
            p.flagRefundDue("Registration cancelled after payment — refund due per cancellation policy");
            paymentRepo.save(p);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Refunds
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Transaction 1 of a refund: validate, optionally cancel the ticket, and record the refund as
     * REQUESTED. Recording it reserves the amount, so two refunds raised together can't add up to more
     * than was received. The provider is called afterwards by PaymentRefundService.
     */
    @Transactional
    public RefundTicket createRefundRecord(TenantId tenantId, UUID eventId, UUID paymentId,
                                           BigDecimal requested, String reason, boolean cancelGuest) {
        Event event = lockEvent(tenantId, eventId);
        EventPayment p = findPayment(eventId, paymentId);
        if (!p.isMoneyHeld()) {
            throw new HandyFlowException(
                    "Only a payment that has been received can be refunded (this one is " + p.getStatus() + ")",
                    HttpStatus.CONFLICT, "PAYMENT_NOT_REFUNDABLE");
        }

        BigDecimal alreadyPromised = nz(refundRepo.sumRequestedForPayment(paymentId));
        BigDecimal available = p.refundable().subtract(alreadyPromised);
        BigDecimal amount = requested != null ? requested.setScale(2, RoundingMode.HALF_UP) : available;

        if (amount.signum() <= 0) {
            throw new HandyFlowException("There is nothing left to refund on this payment",
                    HttpStatus.CONFLICT, "NOTHING_TO_REFUND");
        }
        if (amount.compareTo(available) > 0) {
            throw new HandyFlowException("Cannot refund " + money(amount) + " — only " + money(available)
                    + " is still refundable on this payment", HttpStatus.CONFLICT, "REFUND_EXCEEDS_PAID");
        }

        if (cancelGuest) {
            EventGuest guest = guestRepo.findById(p.getGuestId()).orElse(null);
            if (guest != null && "CHECKED_IN".equals(guest.getStatus())) {
                throw new HandyFlowException("This guest has already checked in — their ticket can't be cancelled",
                        HttpStatus.CONFLICT, "GUEST_ALREADY_CHECKED_IN");
            }
            if (guest != null && ("REGISTERED".equals(guest.getStatus()) || "CONFIRMED".equals(guest.getStatus()))) {
                releaseSeat(new Locked(tenantId, event, p), guest, false);
            }
        }

        EventRefund refund = EventRefund.requested(tenantId, eventId, paymentId, amount, reason,
                TenantContext.getCurrentUserName());
        refundRepo.save(refund);
        audit.record(tenantId, eventId, "REFUND_REQUESTED", "REFUND", refund.getId(),
                "Refund of " + money(amount) + " requested — " + reason + (cancelGuest ? " (ticket cancelled)" : ""));
        return new RefundTicket(refund.getId(), p.getProvider(), p.getProviderRef(), amount, reason);
    }

    /**
     * Transaction 2 of a refund: record what the provider said. Idempotent — a refund that is no longer
     * REQUESTED is returned unchanged, so a repeated completion can never refund twice.
     */
    @Transactional
    public RefundResponse applyRefundResult(TenantId tenantId, UUID eventId, UUID refundId,
                                            PaymentGateway.RefundResult result) {
        lockEvent(tenantId, eventId);
        EventRefund r = refundRepo.findByIdAndEvent(refundId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund", refundId.toString()));
        if (!r.isRequested()) return toRefundResponse(r);

        switch (result.status()) {
            case COMPLETED -> {
                EventPayment p = findPayment(eventId, r.getPaymentId());
                r.complete(result.providerRef());
                p.applyRefund(r.getAmount());
                refundRepo.save(r);
                paymentRepo.save(p);
                guestRepo.findById(p.getGuestId()).ifPresent(g -> {
                    g.markRefund(p.refundable().signum() == 0);
                    guestRepo.save(g);
                });
                audit.record(tenantId, eventId, "REFUND_COMPLETED", "REFUND", r.getId(),
                        "Refund of " + money(r.getAmount()) + " completed");
            }
            case FAILED -> {
                r.fail(result.message() != null ? result.message() : "Refund failed");
                refundRepo.save(r);
                audit.record(tenantId, eventId, "REFUND_FAILED", "REFUND", r.getId(),
                        "Refund of " + money(r.getAmount()) + " failed — " + r.getFailureReason());
            }
            case PENDING -> {
                if (result.providerRef() != null) r.attachProviderRef(result.providerRef());
                refundRepo.save(r);
            }
        }
        return toRefundResponse(r);
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> listRefunds(TenantId tenantId, UUID eventId) {
        findEvent(tenantId, eventId);
        return refundRepo.findByEvent(eventId).stream().map(this::toRefundResponse).toList();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Reads
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<PaymentResponse> listPayments(TenantId tenantId, UUID eventId, String status, Pageable pageable) {
        findEvent(tenantId, eventId);
        String wanted = (status == null || status.isBlank()) ? null : status.trim().toUpperCase(Locale.ROOT);
        Page<EventPayment> page = paymentRepo.search(eventId, wanted, pageable);
        Map<UUID, String> tickets = ticketNumbers(page.getContent());
        return page.map(p -> toResponse(p, tickets.get(p.getGuestId())));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(TenantId tenantId, UUID eventId, UUID paymentId) {
        findEvent(tenantId, eventId);
        EventPayment p = findPayment(eventId, paymentId);
        return toResponse(p, ticketNumberOf(p.getGuestId()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    /** Returns the seat to inventory and offers it to the waitlist. expired=true marks the guest EXPIRED, false CANCELLED. */
    private void releaseSeat(Locked l, EventGuest guest, boolean expired) {
        if (expired) guest.expire(); else guest.cancel();
        guestRepo.save(guest);
        if (guest.getTierId() != null) {
            tierRepo.releaseSeat(guest.getTierId(), Instant.now());
        }
        Event event = l.event();
        if ("SOLD_OUT".equals(event.getStatus()) && tierRepo.countAvailableTiers(event.getId()) > 0) {
            event.reopenSales();
            eventRepo.save(event);
        }
        waitlist.promoteEligible(l.tenant(), event);
    }

    private void sendTicketAfterCommit(Event event, EventGuest guest) {
        String tierName = guest.getTierId() != null
                ? tierRepo.findById(guest.getTierId()).map(EventTicketTier::getName).orElse(null)
                : null;
        AfterCommit.run(() -> engine.sendTicketEmail(event, guest, tierName));
    }

    private void auditPayment(Locked l, String action, String summary, String note) {
        audit.record(l.tenant(), l.event().getId(), action, "PAYMENT", l.payment().getId(), summary,
                note == null ? null : List.of(note));
    }

    /** Inserts the idempotency record; false means this exact notification was already received. */
    private boolean recordInbox(String provider, String providerEventId, UUID paymentId, String sha256) {
        int inserted = jdbc.update(
                "INSERT INTO event_payment_events (id, provider, provider_event_id, payment_id, payload_sha256, received_at) "
                        + "VALUES (?, ?, ?, ?, ?, now()) ON CONFLICT (provider, provider_event_id) DO NOTHING",
                UUID.randomUUID(), provider, providerEventId, paymentId, sha256);
        return inserted == 1;
    }

    private void finishInbox(String provider, String providerEventId, String outcome) {
        jdbc.update("UPDATE event_payment_events SET outcome = ?, processed_at = now() "
                + "WHERE provider = ? AND provider_event_id = ?", outcome, provider, providerEventId);
    }

    private Event findEvent(TenantId tenantId, UUID eventId) {
        return eventRepo.findActiveById(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    private Event lockEvent(TenantId tenantId, UUID eventId) {
        return eventRepo.findActiveByIdForUpdate(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    private EventPayment findPayment(UUID eventId, UUID paymentId) {
        return paymentRepo.findByIdAndEvent(paymentId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId.toString()));
    }

    /** Route (no entity load) -> lock the event -> only THEN load the payment. See PaymentRoute. */
    private Locked lockByPaymentId(UUID paymentId) {
        PaymentRoute route = paymentRepo.findRoute(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId.toString()));
        return lockRoute(route, paymentId);
    }

    private Locked lockRoute(PaymentRoute route, UUID paymentId) {
        TenantId tenant = TenantId.of(route.getTenantId());
        Event event = lockEvent(tenant, route.getEventId());
        EventPayment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId.toString()));
        return new Locked(tenant, event, payment);
    }

    private String ticketNumberOf(UUID guestId) {
        return guestRepo.findById(guestId).map(EventGuest::getTicketNumber).orElse(null);
    }

    private Map<UUID, String> ticketNumbers(List<EventPayment> payments) {
        List<UUID> ids = payments.stream().map(EventPayment::getGuestId).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return guestRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(EventGuest::getId, EventGuest::getTicketNumber, (a, b) -> a));
    }

    PaymentResponse toResponse(EventPayment p, String ticketNumber) {
        return new PaymentResponse(p.getId(), p.getGuestId(), ticketNumber, p.getAmount(), p.getReceivedAmount(),
                p.getFeeAmount(), p.getRefundedAmount(), p.refundable(), p.getCurrency(), p.getProvider(),
                p.getProviderRef(), p.getStatus(), p.getExpiresAt(), p.getPaidAt(), p.getFailureReason(),
                p.getReviewReason(), p.getCreatedAt());
    }

    private RefundResponse toRefundResponse(EventRefund r) {
        return new RefundResponse(r.getId(), r.getPaymentId(), r.getAmount(), r.getReason(), r.getStatus(),
                r.getProviderRef(), r.getRequestedByName(), r.getRequestedAt(), r.getCompletedAt(), r.getFailureReason());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v;
    }

    private static String money(BigDecimal amount) {
        return String.format(Locale.ENGLISH, "R%,.2f", amount);
    }
}