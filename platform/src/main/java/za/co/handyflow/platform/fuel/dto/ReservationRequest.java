package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationRequest(
        @NotNull UUID tankId,
        @NotNull @Positive(message = "Litres must be greater than zero") BigDecimal litres,
        UUID customerId,
        @Size(max = 20) String referenceType,
        UUID referenceId,
        Instant expiresAt,
        @Size(max = 300) String note
) {}
