package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record ReceiveFuelRequest(
        @NotNull @Positive(message = "Litres received must be greater than zero") BigDecimal litresReceived,
        @NotNull @Positive(message = "Price per litre must be greater than zero") BigDecimal pricePerLitre,
        @NotNull Instant receivedAt,
        UUID supplierId, String deliveryNote, String invoiceRef,
        UUID purchaseOrderId,   // optional: the supplier order this delivery is against
        @PositiveOrZero(message = "Freight cannot be negative") BigDecimal freightAmount,   // optional: total inbound freight for this delivery, in rand
        @Size(max = 200) String freightNote,
        // Made once on the phone; a retry with the same id returns the first receipt instead of adding stock twice.
        UUID clientRequestId
) {
    /** No freight. Keeps every caller that does not know about freight working as before. */
    public ReceiveFuelRequest(BigDecimal litresReceived, BigDecimal pricePerLitre, Instant receivedAt, UUID supplierId, String deliveryNote, String invoiceRef, UUID purchaseOrderId) {
        this(litresReceived, pricePerLitre, receivedAt, supplierId, deliveryNote, invoiceRef, purchaseOrderId, null, null, null);
    }

    /** Freight but no client request id: the shape before the phone app. */
    public ReceiveFuelRequest(BigDecimal litresReceived, BigDecimal pricePerLitre, Instant receivedAt, UUID supplierId, String deliveryNote, String invoiceRef, UUID purchaseOrderId, BigDecimal freightAmount, String freightNote) {
        this(litresReceived, pricePerLitre, receivedAt, supplierId, deliveryNote, invoiceRef, purchaseOrderId, freightAmount, freightNote, null);
    }
}