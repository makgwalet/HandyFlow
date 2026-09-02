package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record UpdateClientRequest(
        @NotBlank String tradingName, String registrationNumber, BigDecimal storageRatePerUnitPerMonth,
        BigDecimal receivingFeePerUnit, BigDecimal pickFeePerUnit, BigDecimal packFeePerOrder, String contactName,
        String contactEmail, String contactPhone, String address, String notes
) {}
