package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionTemplateResponse(
        UUID id,
        String name,
        String inspectionType,
        String description,
        Integer intervalDays,
        String appliesToVehicleType,
        boolean signatureRequired,
        boolean active,
        List<InspectionTemplateItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {}