package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FarmResponse(
        UUID id,
        String name,
        String farmType,
        String registrationNumber,
        String province,
        String region,
        Double gpsLatitude,
        Double gpsLongitude,
        BigDecimal totalHectares,
        UUID managerId,
        String managerName,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
