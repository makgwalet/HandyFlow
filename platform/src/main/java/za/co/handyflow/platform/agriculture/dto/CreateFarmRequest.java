package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateFarmRequest(
        @NotBlank String name,
        @NotBlank String farmType,
        String registrationNumber,
        String province,
        String region,
        BigDecimal totalHectares
) {}
