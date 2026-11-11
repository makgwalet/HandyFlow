package za.co.handyflow.platform.events.application.internal.payment;

/**
 * Thrown by a gateway adapter when an incoming notification is not authentic
 * (bad signature, wrong merchant, failed server-side validation) or is
 * malformed. The webhook endpoint answers 400 and processes NOTHING.
 */
public class PaymentVerificationException extends RuntimeException {
    public PaymentVerificationException(String message) {
        super(message);
    }
}