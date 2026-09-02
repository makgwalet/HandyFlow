package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertProfileRequest(
        @NotBlank String agencyName, String firmRegistrationNumber, LocalDate firmRegistrationExpiryDate,
        BigDecimal defaultCommissionPct, String contactEmail, String contactPhone, String physicalAddress
) {}
