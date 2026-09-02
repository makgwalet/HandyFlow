package za.co.handyflow.platform.agriculture.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EnterpriseResponse(
        UUID id,
        UUID farmId,
        String name,
        String enterpriseType,
        String speciesFocus,
        LocalDate startDate,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
