package za.co.handyflow.platform.events.application.internal;

import za.co.handyflow.platform.events.application.internal.payment.PaymentGateway.Outcome;
import za.co.handyflow.platform.events.domain.model.EventPayment;

/**
 * THE rulebook for what to do when a provider says a payment succeeded or failed,
 * as a pure function of the payment's current state — no database, no clock, no
 * Spring — so every combination can be tested exhaustively.
 * <p>
 * Providers retry, deliver late, and deliver out of order, so the same outcome can
 * land on a payment in ANY state. This table is what makes that safe:
 * <pre>
 * outcome  payment status                  -> action
 * PAID     PENDING, right amount           -> CONFIRM            issue the ticket
 * PAID     PENDING, wrong amount           -> REQUIRE_REFUND     money arrived but doesn't match: owed back
 * PAID     EXPIRED/FAILED, can re-secure   -> REINSTATE          paid late and a seat is still free: honour it
 * PAID     EXPIRED/FAILED/CANCELLED, can't -> REQUIRE_REFUND     paid late, no seat (or ticket cancelled): owed back
 * PAID     PAID / REQUIRES_REFUND          -> IGNORE_DUPLICATE   already dealt with — never double-issue
 * FAILED   PENDING                         -> FAIL               release the hold
 * FAILED   anything else                   -> IGNORE             a failure never undoes money we already hold
 * </pre>
 */
final class PaymentDecider {

    enum Action {
        CONFIRM,
        REINSTATE,
        REQUIRE_REFUND_AMOUNT_MISMATCH,
        REQUIRE_REFUND_NO_SEAT,
        FAIL,
        IGNORE_DUPLICATE,
        IGNORE
    }

    private PaymentDecider() {}

    /**
     * @param amountMatches true if the amount received equals the amount asked for (only meaningful for PAID)
     * @param canReinstate  true if an expired/failed reservation could be honoured now (event open, room
     *                      under capacity, not a duplicate of someone else's registration). The tier seat
     *                      itself is claimed atomically AFTER this decision; if that claim fails the caller
     *                      falls back to REQUIRE_REFUND_NO_SEAT.
     */
    static Action decide(String paymentStatus, Outcome outcome, boolean amountMatches, boolean canReinstate) {
        if (outcome == Outcome.FAILED) {
            return EventPayment.PENDING.equals(paymentStatus) ? Action.FAIL : Action.IGNORE;
        }

        // outcome == PAID
        return switch (paymentStatus) {
            case EventPayment.PAID, EventPayment.REQUIRES_REFUND -> Action.IGNORE_DUPLICATE;
            case EventPayment.PENDING -> amountMatches ? Action.CONFIRM : Action.REQUIRE_REFUND_AMOUNT_MISMATCH;
            case EventPayment.EXPIRED, EventPayment.FAILED, EventPayment.CANCELLED -> {
                if (!amountMatches) yield Action.REQUIRE_REFUND_AMOUNT_MISMATCH;
                boolean reinstatable = canReinstate && !EventPayment.CANCELLED.equals(paymentStatus);
                yield reinstatable ? Action.REINSTATE : Action.REQUIRE_REFUND_NO_SEAT;
            }
            default -> Action.IGNORE;
        };
    }
}