package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record ReceiveFuelRequest(
        @NotNull @Positive(message = "Litres received must be greater than zero") BigDecimal litresReceived,
        @NotNull @Positive(message = "Price per litre must be greater than zero") BigDecimal pricePerLitre,
        @NotNull Instant receivedAt,
        UUID supplierId, String deliveryNote, String invoiceRef
) {}