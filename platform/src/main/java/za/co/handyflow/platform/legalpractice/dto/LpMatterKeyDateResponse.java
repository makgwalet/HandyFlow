package za.co.handyflow.platform.legalpractice.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LpMatterKeyDateResponse(
        UUID id,
        UUID matterId,
        String dateType,
        LocalDate dueDate,
        String description,
        boolean acknowledged,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
