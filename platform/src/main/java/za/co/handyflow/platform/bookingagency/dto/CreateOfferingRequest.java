package za.co.handyflow.platform.bookingagency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateOfferingRequest(
        @NotNull UUID clientId,
        @NotBlank String name,
        @Positive int durationMinutes,
        int bufferMinutes,
        BigDecimal price
) {}