package za.co.handyflow.platform.accountant.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DocumentRequestResponse(
        UUID id,
        String description,
        List<String> items,
        String status,
        LocalDate dueDate,
        Instant completedAt,
        Instant createdAt
) {
}