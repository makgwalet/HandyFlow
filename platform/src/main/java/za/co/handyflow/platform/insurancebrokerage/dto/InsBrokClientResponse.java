package za.co.handyflow.platform.insurancebrokerage.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InsBrokClientResponse(
        UUID id,
        String clientName,
        String clientType,
        String registrationOrIdNumber,
        String contactName,
        String contactEmail,
        String contactPhone,
        String address,
        BigDecimal defaultCommissionRatePct,
        String notes,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
