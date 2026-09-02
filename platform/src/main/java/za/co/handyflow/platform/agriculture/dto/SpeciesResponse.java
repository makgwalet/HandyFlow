package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SpeciesResponse(
        UUID id,
        String name,
        String category,
        String defaultUnitOfMeasure,
        String trackingMode,
        Integer gestationDays,
        BigDecimal maturityWeightKg,
        String status,
        Instant createdAt
) {}
