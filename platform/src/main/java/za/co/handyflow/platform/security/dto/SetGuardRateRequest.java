package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;

public record SetGuardRateRequest(
        @NotNull @Min(1) Integer hourlyRateCents,
        @NotNull LocalDate effectiveFrom,
        String reason
) {}
