package za.co.handyflow.platform.training.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseResponse(
        UUID id,
        String courseCode,
        String title,
        String description,
        String category,
        String deliveryMode,
        BigDecimal durationHours,
        String defaultTrainerName,
        BigDecimal cost,
        boolean certificationOffered,
        Integer certificateValidityMonths,
        String status,
        Instant createdAt
) {}
