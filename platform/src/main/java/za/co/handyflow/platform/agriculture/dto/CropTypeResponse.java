package za.co.handyflow.platform.agriculture.dto;

import java.time.Instant;
import java.util.UUID;

public record CropTypeResponse(
        UUID id,
        String name,
        String category,
        Integer typicalGrowingDays,
        String defaultUnitOfMeasure,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
