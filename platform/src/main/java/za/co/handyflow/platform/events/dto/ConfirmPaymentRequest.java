package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Staff confirming money arrived outside a provider (EFT, cash, card machine) — or recovering from a
 * lost provider notification. amountReceived must equal the price asked for; any other amount does NOT
 * issue the ticket but flags the payment REQUIRES_REFUND, because accepting a short or over payment
 * silently is how tills go out of balance.
 */
public record ConfirmPaymentRequest(
        @NotNull @Positive BigDecimal amountReceived,
        @Size(max = 100) String reference,      // bank / receipt reference, kept in the audit trail
        @PositiveOrZero BigDecimal fee
) {}