package za.co.handyflow.platform.fuel.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record ReceiveFuelRequest(
        @NotNull BigDecimal litresReceived,
        @NotNull BigDecimal pricePerLitre,
        @NotNull Instant receivedAt,
        UUID supplierId, String deliveryNote, String invoiceRef
) {}