package za.co.handyflow.platform.insurancebrokerage.dto;

import java.time.Instant;
import java.util.UUID;

public record InsBrokInsurerResponse(
        UUID id,
        String name,
        String contactName,
        String contactEmail,
        String contactPhone,
        String notes,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
