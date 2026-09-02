package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record UpsertProfileRequest(
        @NotBlank String warehouseName, String registrationNumber, BigDecimal defaultStorageRatePerUnitPerMonth,
        BigDecimal defaultReceivingFeePerUnit, BigDecimal defaultPickFeePerUnit, BigDecimal defaultPackFeePerOrder,
        String contactEmail, String contactPhone, String physicalAddress
) {}
