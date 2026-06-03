package za.co.handyflow.platform.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Refund against a previously COMPLETED transaction.
 * Full refund: pass all original items with original qty.
 * Partial refund: pass only the items being returned, with the qty being returned.
 *
 * refundMethod defaults to original paymentMethod if null.
 */
public record ProcessRefundRequest(
        @NotEmpty List<RefundLine> items,
        String   refundMethod,    // CASH | CARD | EFT | STORE_CREDIT — null = original method
        @NotBlank String reason
) {
    public record RefundLine(
            UUID       transactionItemId,   // must belong to the original transaction
            BigDecimal qtyReturned          // must be <= original qty
    ) {}
}
