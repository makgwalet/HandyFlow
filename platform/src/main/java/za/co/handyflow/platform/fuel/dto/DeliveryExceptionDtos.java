package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** C04: delivery windows and failed delivery attempts. */
public final class DeliveryExceptionDtos {
    private DeliveryExceptionDtos() {}

    public record WindowRequest(Instant windowStart, Instant windowEnd) {}

    public record FailedAttemptRequest(@NotBlank String reasonCode, @Size(max = 500) String note, BigDecimal litresReturned,
                                       Instant rescheduleTo, UUID clientRequestId) {}

    public record ExceptionView(UUID id, UUID deliveryId, String kind, String reasonCode, String note, BigDecimal litresReturned,
                                Instant rescheduledTo, int attemptNumber, UUID recordedBy, Instant recordedAt) {}
}
