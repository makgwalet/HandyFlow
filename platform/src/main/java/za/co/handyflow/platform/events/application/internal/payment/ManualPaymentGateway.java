package za.co.handyflow.platform.events.application.internal.payment;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Offline payments: EFT, cash, card machine at the door. There is no provider to
 * talk to, so staff confirm receipt in HandyFlow (POST .../payments/{id}/confirm)
 * and pay refunds out themselves, then mark the refund complete.
 * <p>
 * It sends no notifications, so the public webhook endpoint answers 404 for it.
 * Always registered, so the whole reservation / confirm / expire / refund flow
 * works end-to-end before any online provider is connected.
 */
@Component
public class ManualPaymentGateway implements PaymentGateway {

    public static final String PROVIDER = "MANUAL";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean supportsNotifications() {
        return false;
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        String reference = "MANUAL-" + request.paymentId();
        String shortRef = request.paymentId().toString().substring(0, 8).toUpperCase();
        return new CheckoutSession(reference, null,
                "Pay " + request.currency() + " " + request.amount().toPlainString()
                        + " by EFT and quote reference " + shortRef
                        + ". Your place is held until payment is confirmed or the hold expires.");
    }

    @Override
    public Optional<PaymentNotification> parseNotification(Map<String, String> headers, String rawBody) {
        return Optional.empty();   // never sends any
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        // Nothing to call. Stays REQUESTED until staff have paid it out and mark it complete.
        return RefundResult.pending(null, "Pay this refund out manually, then mark it complete");
    }
}