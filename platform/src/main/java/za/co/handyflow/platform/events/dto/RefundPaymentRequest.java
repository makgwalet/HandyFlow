package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * amount null = refund everything still refundable. A smaller amount is a partial refund — e.g. a
 * cancellation fee kept. cancelGuest also cancels the ticket and returns its seat to inventory (and the
 * waitlist), which only makes sense while the ticket is still live.
 */
public record RefundPaymentRequest(
        @Positive BigDecimal amount,
        @NotBlank @Size(max = 500) String reason,
        boolean cancelGuest
) {}