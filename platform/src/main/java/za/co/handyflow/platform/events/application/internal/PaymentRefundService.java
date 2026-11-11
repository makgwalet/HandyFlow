package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.events.application.internal.payment.PaymentGateway;
import za.co.handyflow.platform.events.application.internal.payment.PaymentGateways;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventPayment;
import za.co.handyflow.platform.events.domain.repository.EventPaymentRepository;
import za.co.handyflow.platform.events.domain.repository.EventRepository;
import za.co.handyflow.platform.events.dto.BulkRefundResponse;
import za.co.handyflow.platform.events.dto.RefundPaymentRequest;
import za.co.handyflow.platform.events.dto.RefundResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Refunds in three steps, like checkout, and for the same reason — the provider call sits
 * between two short transactions, never inside one:
 * <ol>
 *   <li>record the refund as REQUESTED (this reserves the amount) and optionally cancel the ticket;</li>
 *   <li>ask the provider;</li>
 *   <li>record the answer: COMPLETED, FAILED, or still PENDING.</li>
 * </ol>
 * <b>If the provider call itself blows up the refund is left PENDING, not FAILED.</b> We cannot
 * know whether the provider received the request before the connection dropped, and marking it
 * FAILED invites a retry that refunds the customer twice. PENDING keeps the amount reserved and
 * puts the decision in front of a person, who can check with the provider and then mark the
 * refund complete or failed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    private static final int BULK_LIMIT = 200;

    private final PaymentLedgerService ledger;
    private final PaymentGateways      gateways;
    private final EventRepository      eventRepo;
    private final EventPaymentRepository paymentRepo;

    public RefundResponse requestRefund(TenantId tenantId, UUID eventId, UUID paymentId, RefundPaymentRequest req) {
        return refund(tenantId, eventId, paymentId, req.amount(), req.reason().trim(), req.cancelGuest());
    }

    /** Staff paid a MANUAL refund out (or confirmed it with the provider): record it as done. */
    public RefundResponse completeRefund(TenantId tenantId, UUID eventId, UUID refundId, String reference) {
        return ledger.applyRefundResult(tenantId, eventId, refundId,
                PaymentGateway.RefundResult.completed(blankToNull(reference)));
    }

    public RefundResponse failRefund(TenantId tenantId, UUID eventId, UUID refundId, String reason) {
        return ledger.applyRefundResult(tenantId, eventId, refundId,
                PaymentGateway.RefundResult.failed(reason.trim()));
    }

    /**
     * Event cancellation: raise a full refund for every payment that still holds money. Only for a
     * CANCELLED event. Capped per call so one request can't tie up the server; if the cap is hit,
     * {@code moreRemaining} is true and calling again continues where this left off (payments already
     * covered by a requested refund have nothing left to refund, so they are not picked up twice).
     * One failing payment never stops the rest.
     */
    public BulkRefundResponse refundAllForCancelledEvent(TenantId tenantId, UUID eventId) {
        Event event = eventRepo.findActiveById(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
        if (!"CANCELLED".equals(event.getStatus())) {
            throw new HandyFlowException("Refund-all is only available for a CANCELLED event (this one is "
                    + event.getStatus() + ")", HttpStatus.CONFLICT, "EVENT_NOT_CANCELLED");
        }

        List<EventPayment> refundable = paymentRepo.findRefundable(eventId, PageRequest.of(0, BULK_LIMIT + 1));
        boolean more = refundable.size() > BULK_LIMIT;

        List<RefundResponse> raised = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (EventPayment p : refundable.stream().limit(BULK_LIMIT).toList()) {
            try {
                raised.add(refund(tenantId, eventId, p.getId(), null, "Event cancelled", false));
            } catch (HandyFlowException e) {
                // Includes NOTHING_TO_REFUND for payments already fully covered by a pending refund.
                if (!"NOTHING_TO_REFUND".equals(e.getErrorCode())) {
                    failures.add("Payment " + p.getId() + ": " + e.getMessage());
                }
            } catch (RuntimeException e) {
                log.error("[Events] Bulk refund failed for payment={}: {}", p.getId(), e.getMessage(), e);
                failures.add("Payment " + p.getId() + ": unexpected error");
            }
        }
        return new BulkRefundResponse(raised.size(), failures.size(), more, raised, failures);
    }

    // ── The three-step core ───────────────────────────────────────────────────

    private RefundResponse refund(TenantId tenantId, UUID eventId, UUID paymentId,
                                  java.math.BigDecimal amount, String reason, boolean cancelGuest) {
        // Step 1 — short transaction.
        PaymentLedgerService.RefundTicket ticket =
                ledger.createRefundRecord(tenantId, eventId, paymentId, amount, reason, cancelGuest);

        // Step 2 — provider call, no transaction held.
        PaymentGateway.RefundResult result;
        try {
            PaymentGateway gateway = gateways.find(ticket.provider()).orElse(null);
            result = gateway == null
                    ? PaymentGateway.RefundResult.pending(null,
                    "Provider " + ticket.provider() + " is not installed — settle this refund manually")
                    : gateway.refund(new PaymentGateway.RefundRequest(
                    ticket.refundId(), ticket.providerPaymentRef(), ticket.amount(), ticket.reason()));
        } catch (RuntimeException e) {
            log.error("[Events] Refund call to provider failed refund={}: {}", ticket.refundId(), e.getMessage(), e);
            result = PaymentGateway.RefundResult.pending(null,
                    "The provider call errored — verify with the provider whether it went through before retrying");
        }

        // Step 3 — short transaction.
        return ledger.applyRefundResult(tenantId, eventId, ticket.refundId(), result);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}