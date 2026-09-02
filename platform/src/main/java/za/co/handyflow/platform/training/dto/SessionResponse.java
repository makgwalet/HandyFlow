package za.co.handyflow.platform.training.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID courseId,
        String courseTitle,
        LocalDate startDate,
        LocalDate endDate,
        String venue,
        String trainerName,
        Integer capacity,
        long enrolledCount,
        String status,
        String notes,
        String cancelReason,
        Instant createdAt
) {}
