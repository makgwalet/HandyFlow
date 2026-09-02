package za.co.handyflow.platform.legalpractice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LpClientResponse(
        UUID id,
        String name,
        String email,
        String phone,
        String clientType,
        String idOrRegistrationNumber,
        BigDecimal trustBalance,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
