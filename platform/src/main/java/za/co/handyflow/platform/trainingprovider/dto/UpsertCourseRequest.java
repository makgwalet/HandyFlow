package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpsertCourseRequest(
        @NotBlank String title,
        String description,
        String unitStandardNumber,
        Integer nqfLevel,
        Integer credits,
        BigDecimal durationDays,
        @NotNull BigDecimal pricePerDelegate,
        boolean certificationOffered,
        Integer certificateValidityMonths
) {}
