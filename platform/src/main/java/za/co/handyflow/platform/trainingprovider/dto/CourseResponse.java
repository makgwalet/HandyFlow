package za.co.handyflow.platform.trainingprovider.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseResponse(
        UUID id,
        String courseCode,
        String title,
        String description,
        String unitStandardNumber,
        Integer nqfLevel,
        Integer credits,
        BigDecimal durationDays,
        BigDecimal pricePerDelegate,
        boolean certificationOffered,
        Integer certificateValidityMonths,
        String status,
        Instant createdAt
) {}
