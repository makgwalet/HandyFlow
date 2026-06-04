package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateProfileRequest(
        @NotBlank String firmName,
        String practiceNumber,
        String registrationNumber,
        String vatNumber,
        @NotBlank String contactEmail,
        String contactPhone,
        java.math.BigDecimal defaultHourlyRate,
        @Min(1) @Max(12) int yearEndMonth
) {}
