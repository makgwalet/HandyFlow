package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateProtectionDetailRequest(
        @NotNull UUID principalId,
        @NotBlank String detailType,   // STATIC | MOBILE | EVENT | TRAVEL
        @NotNull Instant startAt,
        Instant endAt,
        BigDecimal billingRate,
        String clientReference,
        String notes
) {}
