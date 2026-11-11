package za.co.handyflow.platform.events.application.internal.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for a payment provider. The rest of the Events module depends only on
 * this interface — same shape as FileStorageService and SmsSender elsewhere in
 * the codebase — so adding PayFast, Yoco, Stripe, Ozow etc. means writing one
 * adapter class and nothing else.
 * <p>
 * TO ADD A PROVIDER:
 * <ol>
 *   <li>Implement this interface as a Spring bean ({@code @Component}), with
 *       {@link #provider()} returning its code, e.g. "PAYFAST".</li>
 *   <li>Set {@code app.events.payments.provider=PAYFAST}.</li>
 *   <li>If the provider sends notifications, return true from
 *       {@link #supportsNotifications()} and permit
 *       {@code /api/v1/events/payments/webhook/**} in the security config
 *       (the provider's server is not a logged-in user).</li>
 * </ol>
 * <p>
 * WHAT AN ADAPTER MUST GUARANTEE
 * <ul>
 *   <li>{@link #parseNotification} must prove the notification is genuine
 *       BEFORE returning it (signature check, and a server-side confirmation
 *       call to the provider where its protocol offers one). The core trusts
 *       whatever it returns. If it can't prove it, throw
 *       {@link PaymentVerificationException}.</li>
 *   <li>{@link PaymentNotification#providerEventId()} must be stable across
 *       the provider's retries of the same event — it is the idempotency key.</li>
 *   <li>{@link #createCheckout} and {@link #refund} may make network calls;
 *       the core never calls them inside a database transaction.</li>
 * </ul>
 */
public interface PaymentGateway {

    /** Upper-case provider code stored on every payment, e.g. "MANUAL", "PAYFAST". */
    String provider();

    /** True if this provider sends server-to-server notifications to our webhook endpoint. */
    boolean supportsNotifications();

    /** Start a payment. The returned reference is stored on the payment. */
    CheckoutSession createCheckout(CheckoutRequest request);

    /**
     * Verify and parse an inbound notification.
     *
     * @param headers request headers, keys case-insensitive
     * @param rawBody the exact request body — signatures are computed over these bytes, never a re-serialised copy
     * @return the verified notification, or empty if the call carries nothing to act on (a ping)
     * @throws PaymentVerificationException if it can't be proven authentic or is malformed
     */
    Optional<PaymentNotification> parseNotification(Map<String, String> headers, String rawBody);

    /** Ask the provider to return money. May complete immediately, be pending, or fail. */
    RefundResult refund(RefundRequest request);

    // ── Data carried across the port ──────────────────────────────────────────

    record CheckoutRequest(UUID paymentId, BigDecimal amount, String currency, String description,
                           String payerName, String payerEmail, Instant expiresAt) {}

    /**
     * @param providerRef  the provider's reference for this payment (must be unique per provider)
     * @param redirectUrl  where to send the payer to pay; null for offline methods such as EFT
     * @param instructions human-readable payment instructions to show the payer, if any
     */
    record CheckoutSession(String providerRef, String redirectUrl, String instructions) {}

    enum Outcome { PAID, FAILED }

    /**
     * @param providerEventId stable id of THIS notification across retries — the idempotency key
     * @param paymentId       our payment id, echoed back by the provider
     * @param amountReceived  required (and positive) for PAID
     * @param fee             the provider's fee if it reports one, else null
     */
    record PaymentNotification(String providerEventId, UUID paymentId, Outcome outcome,
                               BigDecimal amountReceived, BigDecimal fee) {
        public PaymentNotification {
            if (providerEventId == null || providerEventId.isBlank())
                throw new IllegalArgumentException("providerEventId is required");
            if (paymentId == null)
                throw new IllegalArgumentException("paymentId is required");
            if (outcome == null)
                throw new IllegalArgumentException("outcome is required");
            if (outcome == Outcome.PAID && (amountReceived == null || amountReceived.signum() <= 0))
                throw new IllegalArgumentException("A PAID notification must carry a positive amountReceived");
        }
    }

    record RefundRequest(UUID refundId, String providerPaymentRef, BigDecimal amount, String reason) {}

    enum RefundStatus { COMPLETED, PENDING, FAILED }

    record RefundResult(RefundStatus status, String providerRef, String message) {
        public static RefundResult completed(String providerRef) {
            return new RefundResult(RefundStatus.COMPLETED, providerRef, null);
        }
        public static RefundResult pending(String providerRef, String message) {
            return new RefundResult(RefundStatus.PENDING, providerRef, message);
        }
        public static RefundResult failed(String message) {
            return new RefundResult(RefundStatus.FAILED, null, message);
        }
    }
}