package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductionAreaResponse(
        UUID id,
        UUID farmId,
        String name,
        String areaType,
        BigDecimal sizeHectares,
        Integer capacity,
        String soilType,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
