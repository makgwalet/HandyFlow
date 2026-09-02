package za.co.handyflow.platform.training.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** Shared shape for create and update — mirrors UpsertProfileRequest's own single-request convention used across the provider modules. */
public record UpsertCourseRequest(
        @NotBlank String title,
        String description,
        String category,
        String deliveryMode,
        BigDecimal durationHours,
        String defaultTrainerName,
        BigDecimal cost,
        boolean certificationOffered,
        Integer certificateValidityMonths
) {}
